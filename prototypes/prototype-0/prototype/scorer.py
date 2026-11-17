"""
scorer.py

Turns a raw answer into evidence, and folds that evidence into a
UserModel's per-dimension score and confidence.

Design notes (why the formulas look the way they do):

- Score update is a simple weighted blend of old score and new evidence,
  as suggested by the spec:
      new_score = old_score * (1 - weight) + evidence_score * weight
  `weight` is derived from the question's reliability, capped so that no
  single answer can fully overwrite the running score. This keeps the
  system deterministic and easy to reason about for v0.

- Confidence is tracked completely separately from score. It only ever
  moves up (more evidence = more certainty about *something*, even if
  that something is a mediocre score), and its growth is dampened when:
    * the question itself is low-reliability, or
    * the new evidence strongly disagrees with the existing evidence for
      that dimension (inconsistent signal shouldn't be rewarded with
      artificial certainty).
  This directly implements the spec's requirement that confidence should
  increase with more/varied/consistent evidence, and that repeated near-
  identical evidence shouldn't cheaply inflate confidence.

Everything here is deterministic: the same sequence of (question, answer)
pairs always produces the same final scores and confidences.
"""

from __future__ import annotations

from typing import Dict

from user_model import Evidence, UserModel

# Base weight applied to a maximally reliable (reliability=1.0) answer.
# Keeping this below 1.0 means the running score always retains some
# memory of prior evidence, even for a perfectly reliable question.
MAX_SCORE_WEIGHT = 0.5

# Base confidence gained from a maximally reliable, fully consistent
# answer. Chosen so it takes several questions to approach confidence 1.0,
# matching the "8-12 questions total, spread across dimensions" target.
BASE_CONFIDENCE_GAIN = 0.22

# If new evidence differs from the existing evidence average by more than
# this many points (0-100 scale), treat it as "conflicting" and dampen the
# confidence gain rather than let it look like a solid, certain data point.
CONFLICT_THRESHOLD = 30.0
CONFLICT_DAMPING = 0.4  # multiply confidence gain by this when conflicting


def extract_evidence_scores(question: dict, answer_id: str) -> Dict[str, float]:
    """
    Map a chosen option to the evidence scores it implies per dimension.

    `answer_id` is the option id the user selected (e.g. "a", "1"). Returns
    a dict of {dimension_name: evidence_score} restricted to whatever
    dimensions that option actually carries scores for.
    """
    for option in question["options"]:
        if option["id"] == answer_id:
            return dict(option["scores"])
    raise ValueError(
        f"Answer '{answer_id}' is not a valid option for question '{question['id']}'"
    )


def _score_weight(reliability: float) -> float:
    """How strongly a single answer should move the running score."""
    return max(0.05, min(MAX_SCORE_WEIGHT, reliability * MAX_SCORE_WEIGHT))


def _confidence_gain(reliability: float, is_conflicting: bool) -> float:
    gain = reliability * BASE_CONFIDENCE_GAIN
    if is_conflicting:
        gain *= CONFLICT_DAMPING
    return gain


def _is_conflicting(dimension_evidence_scores, new_score: float) -> bool:
    if not dimension_evidence_scores:
        return False
    avg = sum(dimension_evidence_scores) / len(dimension_evidence_scores)
    return abs(new_score - avg) > CONFLICT_THRESHOLD


def update_dimension(
    user_model: UserModel,
    dimension_name: str,
    question: dict,
    evidence_score: float,
    answer_id: str,
    timestamp: str,
) -> None:
    """
    Apply one piece of evidence to a single dimension: update score,
    update confidence, and append the Evidence record. Mutates user_model.
    """
    dim = user_model.get_dimension(dimension_name)
    reliability = question["reliability"]

    prior_scores = [e.evidence_score for e in dim.evidence]
    conflicting = _is_conflicting(prior_scores, evidence_score)

    weight = _score_weight(reliability)
    dim.score = dim.score * (1 - weight) + evidence_score * weight

    gain = _confidence_gain(reliability, conflicting)
    dim.confidence = min(1.0, dim.confidence + gain)

    dim.evidence.append(
        Evidence(
            question_id=question["id"],
            answer=answer_id,
            evidence_score=evidence_score,
            reliability=reliability,
            timestamp=timestamp,
        )
    )


def apply_answer(
    user_model: UserModel,
    question: dict,
    answer_id: str,
    timestamp: str,
) -> Dict[str, float]:
    """
    Full pipeline for one answer: extract evidence, update every dimension
    the question measures, record the answer on the user model.

    Returns the raw evidence scores that were applied (mostly useful for
    the CLI/demo output and tests).
    """
    evidence_scores = extract_evidence_scores(question, answer_id)

    user_model.record_question_asked(question["id"])
    user_model.record_answer(question["id"], answer_id)

    for dimension_name in question["dimensions_measured"]:
        if dimension_name not in evidence_scores:
            # Question declares it measures this dimension but the chosen
            # option didn't carry a score for it - nothing to apply.
            continue
        update_dimension(
            user_model=user_model,
            dimension_name=dimension_name,
            question=question,
            evidence_score=evidence_scores[dimension_name],
            answer_id=answer_id,
            timestamp=timestamp,
        )

    return evidence_scores
