import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from inference import compute_attribute_belief, update_person_beliefs
from models import Direction, Evidence, Person, Source


def make_evidence(attribute, direction, strength=0.8, reliability=0.7, qid="q1", aid="a", category="abilities"):
    return Evidence(
        question_id=qid,
        answer_id=aid,
        category=category,
        attribute=attribute,
        direction=Direction(direction),
        strength=strength,
        source=Source.SCENARIO,
        reliability=reliability,
    )


def test_supporting_evidence_increases_belief():
    before = compute_attribute_belief("analytical_problem_solving", [])
    after = compute_attribute_belief(
        "analytical_problem_solving",
        [make_evidence("analytical_problem_solving", "supports", strength=0.8, reliability=0.7)],
    )
    assert after.support_strength > before.support_strength
    assert after.confidence > before.confidence


def test_contradictory_evidence_reduces_certainty():
    one_sided = compute_attribute_belief(
        "analytical_problem_solving",
        [make_evidence("analytical_problem_solving", "supports", strength=0.9, reliability=0.9, qid="q1")],
    )
    conflicted = compute_attribute_belief(
        "analytical_problem_solving",
        [
            make_evidence("analytical_problem_solving", "supports", strength=0.9, reliability=0.9, qid="q1"),
            make_evidence("analytical_problem_solving", "contradicts", strength=0.9, reliability=0.9, qid="q2"),
        ],
    )
    assert conflicted.confidence < one_sided.confidence


def test_repeated_identical_evidence_has_diminishing_effect():
    one = compute_attribute_belief(
        "analytical_problem_solving",
        [make_evidence("analytical_problem_solving", "supports", strength=0.6, reliability=0.6, qid="q1")],
    )
    two = compute_attribute_belief(
        "analytical_problem_solving",
        [
            make_evidence("analytical_problem_solving", "supports", strength=0.6, reliability=0.6, qid="q1"),
            make_evidence("analytical_problem_solving", "supports", strength=0.6, reliability=0.6, qid="q2"),
        ],
    )
    three = compute_attribute_belief(
        "analytical_problem_solving",
        [
            make_evidence("analytical_problem_solving", "supports", strength=0.6, reliability=0.6, qid="q1"),
            make_evidence("analytical_problem_solving", "supports", strength=0.6, reliability=0.6, qid="q2"),
            make_evidence("analytical_problem_solving", "supports", strength=0.6, reliability=0.6, qid="q3"),
        ],
    )
    first_gain = two.support_strength - one.support_strength
    second_gain = three.support_strength - two.support_strength
    assert first_gain > 0
    assert second_gain > 0
    assert second_gain < first_gain  # diminishing returns


def test_conflicting_evidence_lowers_confidence_vs_consistent_evidence():
    consistent = compute_attribute_belief(
        "analytical_problem_solving",
        [
            make_evidence("analytical_problem_solving", "supports", strength=0.7, reliability=0.7, qid="q1"),
            make_evidence("analytical_problem_solving", "supports", strength=0.7, reliability=0.7, qid="q2"),
        ],
    )
    conflicting = compute_attribute_belief(
        "analytical_problem_solving",
        [
            make_evidence("analytical_problem_solving", "supports", strength=0.7, reliability=0.7, qid="q1"),
            make_evidence("analytical_problem_solving", "contradicts", strength=0.7, reliability=0.7, qid="q2"),
        ],
    )
    assert conflicting.confidence < consistent.confidence


def test_deterministic_results():
    evidence = [
        make_evidence("analytical_problem_solving", "supports", strength=0.7, reliability=0.7, qid="q1"),
        make_evidence("analytical_problem_solving", "contradicts", strength=0.3, reliability=0.5, qid="q2"),
    ]
    result_1 = compute_attribute_belief("analytical_problem_solving", evidence)
    result_2 = compute_attribute_belief("analytical_problem_solving", evidence)
    assert result_1.support_strength == result_2.support_strength
    assert result_1.contradiction_strength == result_2.contradiction_strength
    assert result_1.confidence == result_2.confidence


def test_support_and_contradiction_tracked_separately():
    belief = compute_attribute_belief(
        "analytical_problem_solving",
        [
            make_evidence("analytical_problem_solving", "supports", strength=0.8, reliability=0.7, qid="q1"),
            make_evidence("analytical_problem_solving", "contradicts", strength=0.3, reliability=0.5, qid="q2"),
        ],
    )
    assert belief.support_strength > 0
    assert belief.contradiction_strength > 0
    assert belief.support_strength != belief.contradiction_strength


def test_confidence_separate_from_belief_strength():
    # Two scenarios with the same net "lean" (support > contradiction) but
    # very different confidence, because one has much more conflicting
    # evidence mixed in.
    low_conflict = compute_attribute_belief(
        "analytical_problem_solving",
        [make_evidence("analytical_problem_solving", "supports", strength=0.6, reliability=0.6, qid="q1")],
    )
    high_conflict = compute_attribute_belief(
        "analytical_problem_solving",
        [
            make_evidence("analytical_problem_solving", "supports", strength=0.9, reliability=0.9, qid="q1"),
            make_evidence("analytical_problem_solving", "contradicts", strength=0.9, reliability=0.9, qid="q2"),
        ],
    )
    # high_conflict has a much higher support_strength but lower confidence -
    # proving confidence is not merely derived from support magnitude.
    assert high_conflict.support_strength > low_conflict.support_strength
    assert high_conflict.confidence < low_conflict.confidence


def test_update_person_beliefs_only_touches_relevant_attributes():
    person = Person(person_id="p1")
    person.evidence.append(make_evidence("analytical_problem_solving", "supports", strength=0.7, reliability=0.7))
    update_person_beliefs(person)
    assert "analytical_problem_solving" in person.attribute_beliefs
    assert "curiosity" not in person.attribute_beliefs


def test_unrelated_attribute_evidence_does_not_affect_this_attribute():
    belief = compute_attribute_belief(
        "analytical_problem_solving",
        [
            make_evidence("analytical_problem_solving", "supports", strength=0.5, reliability=0.5, qid="q1"),
            make_evidence("curiosity", "contradicts", strength=0.99, reliability=0.99, qid="q2", category="career_adaptability"),
        ],
    )
    isolated = compute_attribute_belief(
        "analytical_problem_solving",
        [make_evidence("analytical_problem_solving", "supports", strength=0.5, reliability=0.5, qid="q1")],
    )
    assert belief.support_strength == isolated.support_strength
    assert belief.confidence == isolated.confidence


def test_belief_category_matches_attribute_category():
    belief = compute_attribute_belief(
        "curiosity",
        [make_evidence("curiosity", "supports", strength=0.5, reliability=0.5, qid="q1", category="career_adaptability")],
    )
    assert belief.category == "career_adaptability"
