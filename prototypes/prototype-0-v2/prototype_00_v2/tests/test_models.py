import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import pytest

from models import Direction, Evidence, FacetBelief, Source, User, is_valid_facet


def test_valid_facet_accepted():
    ev = Evidence(
        question_id="q001",
        answer_id="a",
        facet="information_gathering",
        direction=Direction.SUPPORTS,
        strength=0.8,
        source=Source.SCENARIO,
        reliability=0.7,
        context="problem_solving",
    )
    assert ev.facet == "information_gathering"


def test_invalid_facet_rejected():
    with pytest.raises(ValueError):
        Evidence(
            question_id="q001",
            answer_id="a",
            facet="not_a_real_facet",
            direction=Direction.SUPPORTS,
            strength=0.8,
            source=Source.SCENARIO,
            reliability=0.7,
        )


@pytest.mark.parametrize("bad_strength", [-0.1, 1.1])
def test_strength_out_of_range_rejected(bad_strength):
    with pytest.raises(ValueError):
        Evidence(
            question_id="q001",
            answer_id="a",
            facet="information_gathering",
            direction=Direction.SUPPORTS,
            strength=bad_strength,
            source=Source.SCENARIO,
            reliability=0.7,
        )


@pytest.mark.parametrize("bad_reliability", [-0.1, 1.1])
def test_reliability_out_of_range_rejected(bad_reliability):
    with pytest.raises(ValueError):
        Evidence(
            question_id="q001",
            answer_id="a",
            facet="information_gathering",
            direction=Direction.SUPPORTS,
            strength=0.5,
            source=Source.SCENARIO,
            reliability=bad_reliability,
        )


def test_is_valid_facet():
    assert is_valid_facet("belief_updating")
    assert not is_valid_facet("reasoning")  # old broad-dimension name, not a facet


def test_user_get_belief_creates_default():
    user = User(user_id="u1")
    belief = user.get_belief("idea_generation")
    assert belief.facet == "idea_generation"
    assert belief.support_strength == 0.0
    assert belief.confidence == 0.0
    # same object returned on subsequent calls
    assert user.get_belief("idea_generation") is belief


def test_facet_belief_no_single_score_field():
    belief = FacetBelief(facet="planning")
    d = belief.as_dict()
    assert "support_strength" in d
    assert "contradiction_strength" in d
    assert "confidence" in d
    assert "score" not in d
