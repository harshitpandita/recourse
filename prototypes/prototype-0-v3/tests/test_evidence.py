import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import json

import pytest

from evidence import build_evidence_from_option, record_answer
from models import Direction, Person, Source

QUESTIONS_PATH = Path(__file__).parent.parent / "questions.json"


@pytest.fixture(scope="module")
def questions():
    with open(QUESTIONS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


@pytest.fixture
def sample_question():
    return {
        "id": "qx",
        "text": "sample",
        "type": "scenario",
        "contexts": ["work"],
        "attributes_targeted": ["analytical_problem_solving"],
        "reliability": 0.7,
        "effort": 1,
        "discriminates_between": ["analytical_problem_solving"],
        "options": [
            {
                "id": "a",
                "text": "opt a",
                "evidence": [{"attribute": "analytical_problem_solving", "direction": "supports", "strength": 0.8}],
            },
            {
                "id": "b",
                "text": "opt b",
                "evidence": [
                    {"attribute": "analytical_problem_solving", "direction": "contradicts", "strength": 0.6},
                    {"attribute": "creative_thinking", "direction": "supports", "strength": 0.5},
                ],
            },
        ],
    }


def test_evidence_records_correctly(sample_question):
    option = sample_question["options"][0]
    records = build_evidence_from_option(sample_question, option)
    assert len(records) == 1
    ev = records[0]
    assert ev.question_id == "qx"
    assert ev.answer_id == "a"
    assert ev.attribute == "analytical_problem_solving"
    assert ev.category == "abilities"
    assert ev.direction == Direction.SUPPORTS
    assert ev.strength == 0.8


def test_support_and_contradiction_are_distinct(sample_question):
    option_a = sample_question["options"][0]
    option_b = sample_question["options"][1]
    ev_a = build_evidence_from_option(sample_question, option_a)[0]
    ev_b = [
        e for e in build_evidence_from_option(sample_question, option_b)
        if e.attribute == "analytical_problem_solving"
    ][0]
    assert ev_a.direction == Direction.SUPPORTS
    assert ev_b.direction == Direction.CONTRADICTS


def test_source_and_context_preserved(sample_question):
    option = sample_question["options"][0]
    ev = build_evidence_from_option(sample_question, option)[0]
    assert ev.source == Source.SCENARIO
    assert ev.context == "work"


def test_reliability_preserved(sample_question):
    option = sample_question["options"][0]
    ev = build_evidence_from_option(sample_question, option)[0]
    assert ev.reliability == 0.7


def test_multi_attribute_answer_creates_multiple_records(sample_question):
    option_b = sample_question["options"][1]
    records = build_evidence_from_option(sample_question, option_b)
    assert len(records) == 2
    attributes = {r.attribute for r in records}
    assert attributes == {"analytical_problem_solving", "creative_thinking"}


def test_record_answer_updates_person(sample_question):
    person = Person(person_id="p1")
    new_evidence = record_answer(person, sample_question, "a")
    assert person.questions_asked == ["qx"]
    assert person.answers == [{"question_id": "qx", "answer_id": "a"}]
    assert len(person.evidence) == 1
    assert person.evidence[0] is new_evidence[0]
    belief = person.get_belief("analytical_problem_solving")
    assert belief.evidence_ids == [0]


def test_record_answer_unknown_option_raises(sample_question):
    person = Person(person_id="p1")
    with pytest.raises(ValueError):
        record_answer(person, sample_question, "does_not_exist")


def test_goal_field_captures_free_text():
    question = {
        "id": "qgoal",
        "text": "career question",
        "type": "reflection",
        "contexts": ["personal"],
        "attributes_targeted": ["career_direction_clarity"],
        "reliability": 0.6,
        "effort": 1,
        "discriminates_between": [],
        "options": [
            {
                "id": "a",
                "text": "I want to study computer science.",
                "goal_field": "career_direction",
                "evidence": [{"attribute": "career_direction_clarity", "direction": "supports", "strength": 0.8}],
            }
        ],
    }
    person = Person(person_id="p1")
    record_answer(person, question, "a")
    assert person.goals["career_direction"] == ["I want to study computer science."]


def test_all_bank_questions_produce_valid_evidence(questions):
    person = Person(person_id="p1")
    for q in questions:
        first_option_id = q["options"][0]["id"]
        record_answer(person, q, first_option_id)
    assert len(person.evidence) > 0
    assert len(person.questions_asked) == len(questions)


def test_all_bank_evidence_attributes_are_valid(questions):
    from models import is_valid_attribute

    for q in questions:
        for option in q["options"]:
            for entry in option.get("evidence", []):
                assert is_valid_attribute(entry["attribute"]), (
                    f"{q['id']}/{option['id']} references invalid attribute {entry['attribute']!r}"
                )
