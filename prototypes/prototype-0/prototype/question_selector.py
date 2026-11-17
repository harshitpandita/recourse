"""
question_selector.py

Decides which question to ask next, given what the system currently knows
(the UserModel) and what's left in the question bank. This is the piece
that makes the assessment adaptive instead of a fixed questionnaire.

Conceptually, for each unanswered question we estimate:

    question_value = information_value * dimension_importance
                      * reliability / effort

- information_value: how uncertain we still are about the dimension(s)
  this question measures (1 - confidence). A question about a dimension
  we're already confident on has little left to teach us.
- dimension_importance: a configurable weight reflecting how relevant a
  dimension is to the eventual career-guidance objective. Provisional and
  hand-set for v0 (see DIMENSION_IMPORTANCE below).
- reliability: the question's own evidence quality, taken straight from
  the question bank.
- effort: a cost term (higher effort = longer/more demanding question),
  so two equally informative questions favor the cheaper one.

A question can measure multiple dimensions; we sum its per-dimension
contributions so multi-dimension questions are naturally favored when
several relevant dimensions are still uncertain - this is the "wider net"
justification the reasoning string can point to.

Everything here is a pure function of the UserModel + question bank, so
selection is deterministic: identical history in => identical question
out.
"""

from __future__ import annotations

from typing import Dict, List, Optional, Tuple

from user_model import UserModel

# Provisional weights reflecting how useful each dimension currently is to
# the eventual career/education guidance objective. These are hand-set
# for v0 and easy to retune; they are not derived from any validated
# model of career fit.
DIMENSION_IMPORTANCE: Dict[str, float] = {
    "reasoning": 1.0,
    "learning": 0.9,
    "curiosity": 0.8,
    "creativity": 0.8,
    "execution": 1.0,
    "motivation": 0.9,
    "work_preferences": 0.85,
    "values_ambition": 0.85,
    "adaptability": 0.85,
}

# Assessment stops once dimensions at or above this importance reach
# MIN_CONFIDENCE - "important dimensions have sufficient confidence".
IMPORTANCE_STOP_THRESHOLD = 0.85
MIN_CONFIDENCE = 0.6

MIN_QUESTIONS = 8
MAX_QUESTIONS = 12


def _information_value(user_model: UserModel, dimension_name: str) -> float:
    """How much is left to learn about a dimension: 0 (certain) - 1 (blank)."""
    return 1.0 - user_model.get_dimension(dimension_name).confidence


def score_question(user_model: UserModel, question: dict) -> float:
    """
    Compute question_value for a single question against the current
    user model. Higher is more worth asking right now.
    """
    total = 0.0
    for dimension_name in question["dimensions_measured"]:
        info_value = _information_value(user_model, dimension_name)
        importance = DIMENSION_IMPORTANCE.get(dimension_name, 0.5)
        total += info_value * importance

    reliability = question["reliability"]
    effort = question["effort"]
    return (total * reliability) / effort


def _reason_for(user_model: UserModel, question: dict) -> str:
    """Human-readable justification for why this question was chosen."""
    dims = question["dimensions_measured"]
    # Point at whichever measured dimension currently has the lowest
    # confidence - that's the main driver of the question's value.
    weakest = min(dims, key=lambda d: user_model.get_dimension(d).confidence)
    confidence = user_model.get_dimension(weakest).confidence
    dim_list = ", ".join(dims)
    return (
        f"'{weakest}' currently has low confidence ({confidence:.2f}) and this "
        f"{question['type']} question (reliability {question['reliability']}) "
        f"provides evidence about: {dim_list}."
    )


def select_next_question(
    user_model: UserModel,
    question_bank: List[dict],
) -> Optional[Tuple[dict, float, str]]:
    """
    Pick the single best next question from `question_bank`, excluding
    ones already asked. Returns (question, score, reason), or None if
    nothing is left to ask.

    Ties are broken by question id (ascending) so results are fully
    deterministic regardless of dict/list ordering upstream.
    """
    asked = set(user_model.questions_asked)
    candidates = [q for q in question_bank if q["id"] not in asked]
    if not candidates:
        return None

    scored = [(score_question(user_model, q), q) for q in candidates]
    scored.sort(key=lambda pair: (-pair[0], pair[1]["id"]))

    best_score, best_question = scored[0]
    reason = _reason_for(user_model, best_question)
    return best_question, best_score, reason


def should_stop(user_model: UserModel) -> bool:
    """
    Stopping conditions:
      A) every "important" dimension (importance >= IMPORTANCE_STOP_THRESHOLD)
         has reached MIN_CONFIDENCE, and we've asked at least MIN_QUESTIONS
      B) we've hit MAX_QUESTIONS regardless of confidence
    """
    asked_count = len(user_model.questions_asked)
    if asked_count >= MAX_QUESTIONS:
        return True

    if asked_count < MIN_QUESTIONS:
        return False

    important_dims = [
        name
        for name, weight in DIMENSION_IMPORTANCE.items()
        if weight >= IMPORTANCE_STOP_THRESHOLD and name in user_model.dimensions
    ]
    return all(
        user_model.get_dimension(name).confidence >= MIN_CONFIDENCE
        for name in important_dims
    )
