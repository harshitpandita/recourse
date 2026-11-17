import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import json

import pytest

from evidence import build_evidence_from_option, record_answer
from models import Direction, Source, User

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
        "facets_targeted": ["initiation"],
        "reliability": 0.7,
        "effort": 1,
        "discriminates_between": ["initiation"],
        "options": [
            {
                "id": "a",
                "text": "opt a",
                "evidence": [{"facet": "initiation", "direction": "supports", "strength": 0.8}],
            },
            {
                "id": "b",
                "text": "opt b",
                "evidence": [
                    {"facet": "initiation", "direction": "contradicts", "strength": 0.6},
                    {"facet": "planning", "direction": "supports", "strength": 0.5},
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
    assert ev.facet == "initiation"
    assert ev.direction == Direction.SUPPORTS
    assert ev.strength == 0.8


def test_support_and_contradiction_are_distinct(sample_question):
    option_a = sample_question["options"][0]
    option_b = sample_question["options"][1]
    ev_a = build_evidence_from_option(sample_question, option_a)[0]
    ev_b = [e for e in build_evidence_from_option(sample_question, option_b) if e.facet == "initiation"][0]
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


def test_multi_facet_answer_creates_multiple_records(sample_question):
    option_b = sample_question["options"][1]
    records = build_evidence_from_option(sample_question, option_b)
    assert len(records) == 2
    facets = {r.facet for r in records}
    assert facets == {"initiation", "planning"}


def test_record_answer_updates_user(sample_question):
    user = User(user_id="u1")
    new_evidence = record_answer(user, sample_question, "a")
    assert user.questions_asked == ["qx"]
    assert user.answers == [{"question_id": "qx", "answer_id": "a"}]
    assert len(user.evidence) == 1
    assert user.evidence[0] is new_evidence[0]
    belief = user.get_belief("initiation")
    assert belief.evidence_ids == [0]


def test_record_answer_unknown_option_raises(sample_question):
    user = User(user_id="u1")
    with pytest.raises(ValueError):
        record_answer(user, sample_question, "does_not_exist")


def test_all_bank_questions_produce_valid_evidence(questions):
    user = User(user_id="u1")
    for q in questions:
        first_option_id = q["options"][0]["id"]
        record_answer(user, q, first_option_id)
    assert len(user.evidence) > 0
    assert len(user.questions_asked) == len(questions)
