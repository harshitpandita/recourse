import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from inference import compute_facet_belief, update_user_beliefs
from models import Direction, Evidence, Source, User


def make_evidence(facet, direction, strength=0.8, reliability=0.7, qid="q1", aid="a"):
    return Evidence(
        question_id=qid,
        answer_id=aid,
        facet=facet,
        direction=Direction(direction),
        strength=strength,
        source=Source.SCENARIO,
        reliability=reliability,
    )


def test_supporting_evidence_increases_belief():
    before = compute_facet_belief("initiation", [])
    after = compute_facet_belief(
        "initiation", [make_evidence("initiation", "supports", strength=0.8, reliability=0.7)]
    )
    assert after.support_strength > before.support_strength
    assert after.confidence > before.confidence


def test_contradictory_evidence_reduces_certainty():
    one_sided = compute_facet_belief(
        "initiation",
        [make_evidence("initiation", "supports", strength=0.9, reliability=0.9, qid="q1")],
    )
    conflicted = compute_facet_belief(
        "initiation",
        [
            make_evidence("initiation", "supports", strength=0.9, reliability=0.9, qid="q1"),
            make_evidence("initiation", "contradicts", strength=0.9, reliability=0.9, qid="q2"),
        ],
    )
    assert conflicted.confidence < one_sided.confidence


def test_repeated_identical_evidence_has_diminishing_effect():
    one = compute_facet_belief(
        "initiation", [make_evidence("initiation", "supports", strength=0.6, reliability=0.6, qid="q1")]
    )
    two = compute_facet_belief(
        "initiation",
        [
            make_evidence("initiation", "supports", strength=0.6, reliability=0.6, qid="q1"),
            make_evidence("initiation", "supports", strength=0.6, reliability=0.6, qid="q2"),
        ],
    )
    three = compute_facet_belief(
        "initiation",
        [
            make_evidence("initiation", "supports", strength=0.6, reliability=0.6, qid="q1"),
            make_evidence("initiation", "supports", strength=0.6, reliability=0.6, qid="q2"),
            make_evidence("initiation", "supports", strength=0.6, reliability=0.6, qid="q3"),
        ],
    )
    first_gain = two.support_strength - one.support_strength
    second_gain = three.support_strength - two.support_strength
    assert first_gain > 0
    assert second_gain > 0
    assert second_gain < first_gain  # diminishing returns


def test_conflicting_evidence_lowers_confidence_vs_consistent_evidence():
    consistent = compute_facet_belief(
        "initiation",
        [
            make_evidence("initiation", "supports", strength=0.7, reliability=0.7, qid="q1"),
            make_evidence("initiation", "supports", strength=0.7, reliability=0.7, qid="q2"),
        ],
    )
    conflicting = compute_facet_belief(
        "initiation",
        [
            make_evidence("initiation", "supports", strength=0.7, reliability=0.7, qid="q1"),
            make_evidence("initiation", "contradicts", strength=0.7, reliability=0.7, qid="q2"),
        ],
    )
    assert conflicting.confidence < consistent.confidence


def test_deterministic_results():
    evidence = [
        make_evidence("initiation", "supports", strength=0.7, reliability=0.7, qid="q1"),
        make_evidence("initiation", "contradicts", strength=0.3, reliability=0.5, qid="q2"),
    ]
    result_1 = compute_facet_belief("initiation", evidence)
    result_2 = compute_facet_belief("initiation", evidence)
    assert result_1.support_strength == result_2.support_strength
    assert result_1.contradiction_strength == result_2.contradiction_strength
    assert result_1.confidence == result_2.confidence


def test_support_and_contradiction_tracked_separately():
    belief = compute_facet_belief(
        "initiation",
        [
            make_evidence("initiation", "supports", strength=0.8, reliability=0.7, qid="q1"),
            make_evidence("initiation", "contradicts", strength=0.3, reliability=0.5, qid="q2"),
        ],
    )
    assert belief.support_strength > 0
    assert belief.contradiction_strength > 0
    assert belief.support_strength != belief.contradiction_strength


def test_confidence_separate_from_belief_strength():
    # Two scenarios with the same net "lean" (support > contradiction) but
    # very different confidence, because one has much more conflicting
    # evidence mixed in.
    low_conflict = compute_facet_belief(
        "initiation",
        [make_evidence("initiation", "supports", strength=0.6, reliability=0.6, qid="q1")],
    )
    high_conflict = compute_facet_belief(
        "initiation",
        [
            make_evidence("initiation", "supports", strength=0.9, reliability=0.9, qid="q1"),
            make_evidence("initiation", "contradicts", strength=0.9, reliability=0.9, qid="q2"),
        ],
    )
    # high_conflict has a much higher support_strength but lower confidence -
    # proving confidence is not merely derived from support magnitude.
    assert high_conflict.support_strength > low_conflict.support_strength
    assert high_conflict.confidence < low_conflict.confidence


def test_update_user_beliefs_only_touches_relevant_facets():
    user = User(user_id="u1")
    user.evidence.append(make_evidence("initiation", "supports", strength=0.7, reliability=0.7))
    update_user_beliefs(user)
    assert "initiation" in user.facet_beliefs
    assert "planning" not in user.facet_beliefs


def test_unrelated_facet_evidence_does_not_affect_this_facet():
    belief = compute_facet_belief(
        "initiation",
        [
            make_evidence("initiation", "supports", strength=0.5, reliability=0.5, qid="q1"),
            make_evidence("planning", "contradicts", strength=0.99, reliability=0.99, qid="q2"),
        ],
    )
    isolated = compute_facet_belief(
        "initiation",
        [make_evidence("initiation", "supports", strength=0.5, reliability=0.5, qid="q1")],
    )
    assert belief.support_strength == isolated.support_strength
    assert belief.confidence == isolated.confidence
