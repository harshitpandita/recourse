"""Uncertainty-driven question selection.

For each unanswered question we estimate:

    score = information_value * importance * reliability
            * discrimination_value / effort

and return the ranked list (or just the top choice) along with a
human-readable reason. Selection is purely a function of (questions,
person's current beliefs, already-asked questions) - given the same inputs
it always returns the same output, which is what lets a cold-start set
differ deterministically per person while remaining reproducible.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Optional

from models import ALL_ATTRIBUTES, CATEGORIES, Person, category_of

# Provisional, configurable importance weights, one per profile category.
# These are not empirically derived - they exist so the selector has *some*
# notion of "matters more right now" beyond raw uncertainty, and so a human
# can tune priorities in one place. Categories not listed default to 1.0.
DEFAULT_CATEGORY_IMPORTANCE: Dict[str, float] = {
    "interests": 1.0,
    "abilities": 1.1,
    "work_characteristics": 1.05,
    "values": 1.0,
    "goals_aspirations": 1.1,
    "career_adaptability": 1.05,
    "knowledge_skills": 0.95,
}

# Confidence at/above this is treated as "well understood enough" - it
# still contributes a small residual information value so it's never
# exactly zero, but low-confidence attributes dominate ranking.
CONFIDENCE_SATURATION = 0.75

# A belief counts as "ambiguous" (good for discrimination) when its
# confidence is still low AND support/contradiction haven't separated much.
AMBIGUITY_CONFIDENCE_THRESHOLD = 0.55
AMBIGUITY_SEPARATION_THRESHOLD = 0.35


@dataclass
class QuestionScore:
    question: dict
    score: float
    reason: str


def _attribute_confidence(person: Person, attribute: str) -> float:
    belief = person.attribute_beliefs.get(attribute)
    return belief.confidence if belief else 0.0


def _category_importance(attribute: str) -> float:
    return DEFAULT_CATEGORY_IMPORTANCE.get(category_of(attribute), 1.0)


def _information_value(person: Person, attributes: List[str]) -> float:
    """Higher when the question's targeted attributes are still uncertain."""

    if not attributes:
        return 0.0
    uncertainties = [
        max(0.0, 1.0 - min(1.0, _attribute_confidence(person, a) / CONFIDENCE_SATURATION))
        for a in attributes
    ]
    return sum(uncertainties) / len(uncertainties)


def _importance_value(attributes: List[str]) -> float:
    if not attributes:
        return 1.0
    return sum(_category_importance(a) for a in attributes) / len(attributes)


def _is_ambiguous(person: Person, attribute: str) -> bool:
    belief = person.attribute_beliefs.get(attribute)
    if belief is None:
        return True  # no evidence at all is maximally ambiguous
    separation = abs(belief.support_strength - belief.contradiction_strength)
    return (
        belief.confidence < AMBIGUITY_CONFIDENCE_THRESHOLD
        and separation < AMBIGUITY_SEPARATION_THRESHOLD
    )


def _discrimination_value(person: Person, discriminates_between: List[str]) -> float:
    """Higher when the question helps distinguish between attributes we
    can't yet tell apart (ambiguous / low-confidence / evidence in
    conflict)."""

    attributes = discriminates_between or []
    if not attributes:
        return 0.5  # neutral baseline for questions with no explicit target
    ambiguous_count = sum(1 for a in attributes if _is_ambiguous(person, a))
    return 0.5 + 0.5 * (ambiguous_count / len(attributes))


def score_question(person: Person, question: dict) -> QuestionScore:
    targeted = question.get("attributes_targeted", [])
    reliability = float(question.get("reliability", 0.5))
    effort = max(1, int(question.get("effort", 1)))

    info_value = _information_value(person, targeted)
    importance = _importance_value(targeted)
    discrimination = _discrimination_value(person, question.get("discriminates_between", []))

    score = (info_value * importance * reliability * discrimination) / effort

    # Build a human-readable reason around the least-confident targeted attribute.
    if targeted:
        least_confident_attribute = min(targeted, key=lambda a: _attribute_confidence(person, a))
        confidence = _attribute_confidence(person, least_confident_attribute)
        discriminated = question.get("discriminates_between", [])
        if len(discriminated) >= 2:
            reason = (
                f"Uncertainty about '{least_confident_attribute}' "
                f"({category_of(least_confident_attribute)}) is currently "
                f"{'high' if confidence < 0.4 else 'moderate'} (confidence={confidence:.2f}), "
                f"and this question distinguishes between {discriminated[0]} and {discriminated[1]}."
            )
        else:
            reason = (
                f"Uncertainty about '{least_confident_attribute}' "
                f"({category_of(least_confident_attribute)}) is currently "
                f"{'high' if confidence < 0.4 else 'moderate'} (confidence={confidence:.2f})."
            )
    else:
        reason = "No specific attribute target; used as a general-information question."

    return QuestionScore(question=question, score=score, reason=reason)


def rank_questions(
    person: Person,
    all_questions: List[dict],
    *,
    exclude_asked: bool = True,
) -> List[QuestionScore]:
    asked = set(person.questions_asked) if exclude_asked else set()
    candidates = [q for q in all_questions if q["id"] not in asked]
    scored = [score_question(person, q) for q in candidates]
    # Sort by score desc, then by question id for a fully deterministic
    # tie-break (dict/list ordering from JSON load is already stable, but
    # this makes the guarantee explicit).
    scored.sort(key=lambda qs: (-qs.score, qs.question["id"]))
    return scored


def select_next_question(
    person: Person,
    all_questions: List[dict],
) -> Optional[QuestionScore]:
    ranked = rank_questions(person, all_questions)
    return ranked[0] if ranked else None


# ---------------------------------------------------------------------------
# Stopping conditions
# ---------------------------------------------------------------------------

MAX_QUESTIONS = 18
MIN_QUESTIONS = 10
LOW_INFORMATION_VALUE_THRESHOLD = 0.08
USEFUL_CONFIDENCE_THRESHOLD = 0.6


def should_stop(person: Person, all_questions: List[dict]) -> bool:
    """Stop when we've hit the question cap, or (after the minimum) the
    remaining questions no longer offer meaningful information value, or
    enough attributes across categories already have useful confidence.
    """

    asked_count = len(person.questions_asked)
    if asked_count >= MAX_QUESTIONS:
        return True
    if asked_count < MIN_QUESTIONS:
        return False

    ranked = rank_questions(person, all_questions)
    if not ranked or ranked[0].score < LOW_INFORMATION_VALUE_THRESHOLD:
        return True

    if ALL_ATTRIBUTES:
        well_understood = sum(
            1 for a in ALL_ATTRIBUTES if _attribute_confidence(person, a) >= USEFUL_CONFIDENCE_THRESHOLD
        )
        if well_understood / len(ALL_ATTRIBUTES) >= 0.7:
            return True

    return False
