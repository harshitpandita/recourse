"""Evidence -> profile belief inference.

This is deliberately simple, deterministic arithmetic - not a validated
psychometric model. See models.py docstrings and PROJECT_CONTEXT.md for
that disclaimer.

Design, in words:

- Supporting and contradicting evidence are accumulated SEPARATELY using a
  noisy-OR style combination: each new piece of evidence closes part of the
  remaining "gap" to 1.0, weighted by strength * reliability. This means
  repeated similar evidence has a diminishing effect (the first strong
  question moves the needle a lot; the fifth near-identical one barely
  moves it at all), while still never mathematically capping out on exactly
  one question.
- "Evidence mass" is a separate noisy-OR accumulation (over ALL evidence
  for the attribute, regardless of direction) representing how much we've
  actually learned, independent of what we learned.
- confidence = evidence_mass * (1 - conflict), where conflict is high when
  support and contradiction are both substantial (the evidence disagrees
  with itself). This keeps confidence conceptually separate from the
  strength/direction of the belief: you can lean strongly toward X while
  being unconfident overall, if the evidence is contradictory.
"""

from __future__ import annotations

from typing import Iterable, List

from models import AttributeBelief, Direction, Evidence, Person, category_of


def _noisy_or_accumulate(weights: Iterable[float]) -> float:
    """Combine a sequence of 0..1 weights so the result saturates at 1.0
    with diminishing returns per additional item (order-independent).
    """

    acc = 0.0
    for w in weights:
        w = max(0.0, min(1.0, w))
        acc = acc + w * (1.0 - acc)
    return acc


def compute_attribute_belief(attribute: str, evidence_list: List[Evidence]) -> AttributeBelief:
    """Pure function: evidence -> belief. Deterministic given the same input."""

    relevant = [e for e in evidence_list if e.attribute == attribute]

    support_weights = [
        e.strength * e.reliability for e in relevant if e.direction == Direction.SUPPORTS
    ]
    contradiction_weights = [
        e.strength * e.reliability for e in relevant if e.direction == Direction.CONTRADICTS
    ]
    # Neutral evidence contributes to how much we've learned (evidence mass)
    # but not to support or contradiction.
    all_weights = [e.strength * e.reliability for e in relevant]

    support = _noisy_or_accumulate(support_weights)
    contradiction = _noisy_or_accumulate(contradiction_weights)
    evidence_mass = _noisy_or_accumulate(all_weights)

    # Conflict is high when support and contradiction are both substantial.
    # Using min() means one-sided evidence (even a lot of it) never counts
    # as conflict; it's specifically agreement-vs-disagreement that hurts
    # confidence.
    conflict = min(support, contradiction) * 2.0
    conflict = max(0.0, min(1.0, conflict))

    confidence = evidence_mass * (1.0 - conflict)

    belief = AttributeBelief(
        attribute=attribute,
        category=category_of(attribute),
        support_strength=support,
        contradiction_strength=contradiction,
        confidence=confidence,
        evidence_ids=[i for i, e in enumerate(evidence_list) if e.attribute == attribute],
    )
    return belief


def update_person_beliefs(person: Person, attributes: Iterable[str] = None) -> None:
    """Recompute attribute beliefs for the given attributes (or all
    attributes touched by the person's evidence) from scratch. Deterministic
    and idempotent: calling it twice with the same evidence produces the
    same result.
    """

    if attributes is None:
        attributes = {e.attribute for e in person.evidence}

    for attribute in attributes:
        person.attribute_beliefs[attribute] = compute_attribute_belief(attribute, person.evidence)
