import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import pytest

from models import (
    AttributeBelief,
    Direction,
    Evidence,
    Person,
    Source,
    category_of,
    is_valid_attribute,
)


def test_valid_attribute_accepted():
    ev = Evidence(
        question_id="q001",
        answer_id="a",
        category="abilities",
        attribute="analytical_problem_solving",
        direction=Direction.SUPPORTS,
        strength=0.8,
        source=Source.SCENARIO,
        reliability=0.7,
        context="problem_solving",
    )
    assert ev.attribute == "analytical_problem_solving"
    assert ev.category == "abilities"


def test_invalid_attribute_rejected():
    with pytest.raises(ValueError):
        Evidence(
            question_id="q001",
            answer_id="a",
            category="abilities",
            attribute="not_a_real_attribute",
            direction=Direction.SUPPORTS,
            strength=0.8,
            source=Source.SCENARIO,
            reliability=0.7,
        )


def test_category_mismatch_rejected():
    with pytest.raises(ValueError):
        Evidence(
            question_id="q001",
            answer_id="a",
            category="values",  # wrong category for this attribute
            attribute="analytical_problem_solving",
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
            category="abilities",
            attribute="analytical_problem_solving",
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
            category="abilities",
            attribute="analytical_problem_solving",
            direction=Direction.SUPPORTS,
            strength=0.5,
            source=Source.SCENARIO,
            reliability=bad_reliability,
        )


def test_is_valid_attribute():
    assert is_valid_attribute("curiosity")
    assert not is_valid_attribute("career_adaptability")  # category name, not an attribute


def test_category_of():
    assert category_of("curiosity") == "career_adaptability"
    assert category_of("technology") == "interests"
    assert category_of("financial_reward") == "values"


def test_all_seven_categories_present():
    from models import CATEGORIES

    expected = {
        "interests",
        "abilities",
        "work_characteristics",
        "values",
        "goals_aspirations",
        "career_adaptability",
        "knowledge_skills",
    }
    assert set(CATEGORIES.keys()) == expected
    for category, attributes in CATEGORIES.items():
        assert len(attributes) > 0


def test_person_get_belief_creates_default():
    person = Person(person_id="p1")
    belief = person.get_belief("creative_thinking")
    assert belief.attribute == "creative_thinking"
    assert belief.category == "abilities"
    assert belief.support_strength == 0.0
    assert belief.confidence == 0.0
    # same object returned on subsequent calls
    assert person.get_belief("creative_thinking") is belief


def test_attribute_belief_no_single_score_field():
    belief = AttributeBelief(attribute="control", category="career_adaptability")
    d = belief.as_dict()
    assert "support_strength" in d
    assert "contradiction_strength" in d
    assert "confidence" in d
    assert "score" not in d


def test_person_goals_start_empty_and_are_not_beliefs():
    person = Person(person_id="p1")
    assert person.goals["career_direction"] == []
    person.add_goal_text("career_direction", "wants to study mechanical engineering")
    assert person.goals["career_direction"] == ["wants to study mechanical engineering"]
    # goals are not part of the belief/evidence machinery
    assert "career_direction" not in person.attribute_beliefs


def test_add_goal_text_rejects_unknown_field():
    person = Person(person_id="p1")
    with pytest.raises(ValueError):
        person.add_goal_text("not_a_real_goal_field", "text")


def test_beliefs_by_category():
    person = Person(person_id="p1")
    person.get_belief("curiosity")
    person.get_belief("control")
    person.get_belief("technology")

    adaptability_beliefs = person.beliefs_by_category("career_adaptability")
    attributes = {b.attribute for b in adaptability_beliefs}
    assert attributes == {"curiosity", "control"}
    assert person.beliefs_by_category("interests")[0].attribute == "technology"
