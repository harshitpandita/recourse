"""Evidence -> facet belief inference.

This is deliberately simple, deterministic arithmetic - not a validated
psychometric model. See models.py docstrings and the project README-style
notes in main.py for that disclaimer.

Design, in words:

- Supporting and contradicting evidence are accumulated SEPARATELY using a
  noisy-OR style combination: each new piece of evidence closes part of the
  remaining "gap" to 1.0, weighted by strength * reliability. This means
  repeated similar evidence has a diminishing effect (the first strong
  question moves the needle a lot; the fifth near-identical one barely
  moves it at all), while still never mathematically capping out on exactly
  one question.
- "Evidence mass" is a separate noisy-OR accumulation (over ALL evidence
  for the facet, regardless of direction) representing how much we've
  actually learned, independent of what we learned.
- confidence = evidence_mass * (1 - conflict), where conflict is high when
  support and contradiction are both substantial (the evidence disagrees
  with itself). This keeps confidence conceptually separate from the
  strength/direction of the belief: you can be confident someone leans
  toward X, or you can have lots of evidence that is contradictory and
  therefore be unconfident about anything.
"""

from __future__ import annotations

from typing import Iterable, List

from models import Direction, Evidence, FacetBelief, User


def _noisy_or_accumulate(weights: Iterable[float]) -> float:
    """Combine a sequence of 0..1 weights so the result saturates at 1.0
    with diminishing returns per additional item (order-independent).
    """

    acc = 0.0
    for w in weights:
        w = max(0.0, min(1.0, w))
        acc = acc + w * (1.0 - acc)
    return acc


def compute_facet_belief(facet: str, evidence_list: List[Evidence]) -> FacetBelief:
    """Pure function: evidence -> belief. Deterministic given the same input."""

    relevant = [e for e in evidence_list if e.facet == facet]

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

    belief = FacetBelief(
        facet=facet,
        support_strength=support,
        contradiction_strength=contradiction,
        confidence=confidence,
        evidence_ids=[i for i, e in enumerate(evidence_list) if e.facet == facet],
    )
    return belief


def update_user_beliefs(user: User, facets: Iterable[str] = None) -> None:
    """Recompute facet beliefs for the given facets (or all facets touched
    by the user's evidence) from scratch. Deterministic and idempotent:
    calling it twice with the same evidence produces the same result.
    """

    if facets is None:
        facets = {e.facet for e in user.evidence}

    for facet in facets:
        user.facet_beliefs[facet] = compute_facet_belief(facet, user.evidence)
