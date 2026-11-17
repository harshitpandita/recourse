"""Uncertainty-driven question selection.

For each unanswered question we estimate:

    score = information_value * facet_importance * reliability
            * discrimination_value / effort

and return the ranked list (or just the top choice) along with a
human-readable reason. Selection is purely a function of (questions,
user's current beliefs, already-asked questions) - given the same inputs
it always returns the same output, which is what lets a cold-start set
differ deterministically per user while remaining reproducible.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Optional

from models import FacetBelief, User

# Provisional, configurable importance weights. Facets not listed default
# to 1.0. These are not empirically derived - they exist so the selector
# has *some* notion of "matters more right now" beyond raw uncertainty,
# and so a human can tune priorities in one place.
DEFAULT_FACET_IMPORTANCE: Dict[str, float] = {
    "information_gathering": 1.1,
    "hypothesis_building": 1.1,
    "belief_updating": 1.2,
    "theory_before_experimentation": 1.0,
    "learning_by_doing": 1.0,
    "willingness_to_revise": 1.15,
    "mastery_orientation": 1.1,
    "autonomy_orientation": 1.0,
    "collaboration_preference": 1.0,
    "uncertainty_tolerance": 1.1,
}

# Confidence at/above this is treated as "well understood enough" - it
# still contributes a small residual information value so it's never
# exactly zero, but low-confidence facets dominate ranking.
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


def _facet_confidence(user: User, facet: str) -> float:
    belief = user.facet_beliefs.get(facet)
    return belief.confidence if belief else 0.0


def _facet_importance(facet: str) -> float:
    return DEFAULT_FACET_IMPORTANCE.get(facet, 1.0)


def _information_value(user: User, facets: List[str]) -> float:
    """Higher when the question's targeted facets are still uncertain."""

    if not facets:
        return 0.0
    uncertainties = [
        max(0.0, 1.0 - min(1.0, _facet_confidence(user, f) / CONFIDENCE_SATURATION))
        for f in facets
    ]
    return sum(uncertainties) / len(uncertainties)


def _importance_value(facets: List[str]) -> float:
    if not facets:
        return 1.0
    return sum(_facet_importance(f) for f in facets) / len(facets)


def _is_ambiguous(user: User, facet: str) -> bool:
    belief = user.facet_beliefs.get(facet)
    if belief is None:
        return True  # no evidence at all is maximally ambiguous
    separation = abs(belief.support_strength - belief.contradiction_strength)
    return (
        belief.confidence < AMBIGUITY_CONFIDENCE_THRESHOLD
        and separation < AMBIGUITY_SEPARATION_THRESHOLD
    )


def _discrimination_value(user: User, discriminates_between: List[str]) -> float:
    """Higher when the question helps distinguish between facets we can't
    yet tell apart (ambiguous / low-confidence / evidence in conflict)."""

    facets = discriminates_between or []
    if not facets:
        return 0.5  # neutral baseline for questions with no explicit target
    ambiguous_count = sum(1 for f in facets if _is_ambiguous(user, f))
    return 0.5 + 0.5 * (ambiguous_count / len(facets))


def score_question(user: User, question: dict) -> QuestionScore:
    targeted = question.get("facets_targeted", [])
    reliability = float(question.get("reliability", 0.5))
    effort = max(1, int(question.get("effort", 1)))

    info_value = _information_value(user, targeted)
    importance = _importance_value(targeted)
    discrimination = _discrimination_value(user, question.get("discriminates_between", []))

    score = (info_value * importance * reliability * discrimination) / effort

    # Build a human-readable reason around the least-confident targeted facet.
    if targeted:
        least_confident_facet = min(targeted, key=lambda f: _facet_confidence(user, f))
        confidence = _facet_confidence(user, least_confident_facet)
        discriminated = question.get("discriminates_between", [])
        if len(discriminated) >= 2:
            reason = (
                f"Uncertainty about '{least_confident_facet}' is currently "
                f"{'high' if confidence < 0.4 else 'moderate'} (confidence={confidence:.2f}), "
                f"and this question distinguishes between {discriminated[0]} and {discriminated[1]}."
            )
        else:
            reason = (
                f"Uncertainty about '{least_confident_facet}' is currently "
                f"{'high' if confidence < 0.4 else 'moderate'} (confidence={confidence:.2f})."
            )
    else:
        reason = "No specific facet target; used as a general-information question."

    return QuestionScore(question=question, score=score, reason=reason)


def rank_questions(
    user: User,
    all_questions: List[dict],
    *,
    exclude_asked: bool = True,
) -> List[QuestionScore]:
    asked = set(user.questions_asked) if exclude_asked else set()
    candidates = [q for q in all_questions if q["id"] not in asked]
    scored = [score_question(user, q) for q in candidates]
    # Sort by score desc, then by question id for a fully deterministic
    # tie-break (dict/list ordering from JSON load is already stable, but
    # this makes the guarantee explicit).
    scored.sort(key=lambda qs: (-qs.score, qs.question["id"]))
    return scored


def select_next_question(
    user: User,
    all_questions: List[dict],
) -> Optional[QuestionScore]:
    ranked = rank_questions(user, all_questions)
    return ranked[0] if ranked else None


# ---------------------------------------------------------------------------
# Stopping conditions
# ---------------------------------------------------------------------------

MAX_QUESTIONS = 12
MIN_QUESTIONS = 8
LOW_INFORMATION_VALUE_THRESHOLD = 0.08
USEFUL_CONFIDENCE_THRESHOLD = 0.6


def should_stop(user: User, all_questions: List[dict]) -> bool:
    """Stop when we've hit the question cap, or (after the minimum) the
    remaining questions no longer offer meaningful information value, or
    enough importance-weighted facets already have useful confidence.
    """

    asked_count = len(user.questions_asked)
    if asked_count >= MAX_QUESTIONS:
        return True
    if asked_count < MIN_QUESTIONS:
        return False

    ranked = rank_questions(user, all_questions)
    if not ranked or ranked[0].score < LOW_INFORMATION_VALUE_THRESHOLD:
        return True

    priority_facets = list(DEFAULT_FACET_IMPORTANCE.keys())
    if priority_facets:
        well_understood = sum(
            1 for f in priority_facets if _facet_confidence(user, f) >= USEFUL_CONFIDENCE_THRESHOLD
        )
        if well_understood / len(priority_facets) >= 0.7:
            return True

    return False
