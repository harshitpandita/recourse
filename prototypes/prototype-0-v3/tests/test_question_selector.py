import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import json

import pytest

from evidence import record_answer
from inference import update_person_beliefs
from models import Person
from question_selector import rank_questions, score_question, select_next_question

QUESTIONS_PATH = Path(__file__).parent.parent / "questions.json"


@pytest.fixture(scope="module")
def questions():
    with open(QUESTIONS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def find_q(questions, qid):
    return next(q for q in questions if q["id"] == qid)


def test_low_confidence_attributes_receive_priority(questions):
    person = Person(person_id="p1")
    q001 = find_q(questions, "q001")  # targets technology, science, design, nature
    score_before = score_question(person, q001).score

    # Saturate those attributes' confidence via strong repeated evidence.
    for i in range(5):
        record_answer(person, q001, "a", timestamp=float(i))
    update_person_beliefs(person)

    score_after = score_question(person, q001).score
    assert score_after < score_before


def test_discrimination_value_affects_ranking(questions):
    person = Person(person_id="p1")
    # q001's discriminates_between targets are both unresolved -> ambiguous -> positive discrimination.
    scored = score_question(person, find_q(questions, "q001"))
    assert scored.score > 0


def test_unreliable_questions_are_penalized(questions):
    person = Person(person_id="p1")
    reliable_q = dict(find_q(questions, "q001"))
    unreliable_q = dict(reliable_q)
    unreliable_q["reliability"] = 0.1
    unreliable_q["id"] = "q001_unreliable"

    reliable_score = score_question(person, reliable_q).score
    unreliable_score = score_question(person, unreliable_q).score
    assert unreliable_score < reliable_score


def test_high_effort_questions_are_penalized(questions):
    person = Person(person_id="p1")
    low_effort_q = dict(find_q(questions, "q001"))
    high_effort_q = dict(low_effort_q)
    high_effort_q["effort"] = 5
    high_effort_q["id"] = "q001_high_effort"

    low_effort_score = score_question(person, low_effort_q).score
    high_effort_score = score_question(person, high_effort_q).score
    assert high_effort_score < low_effort_score


def test_answered_questions_are_excluded(questions):
    person = Person(person_id="p1")
    q001 = find_q(questions, "q001")
    record_answer(person, q001, "a")
    update_person_beliefs(person)

    ranked = rank_questions(person, questions)
    ranked_ids = [r.question["id"] for r in ranked]
    assert "q001" not in ranked_ids


def test_identical_inputs_produce_identical_selection(questions):
    person_a = Person(person_id="p1")
    person_b = Person(person_id="p1")
    for q in questions[:3]:
        record_answer(person_a, q, q["options"][0]["id"])
        record_answer(person_b, q, q["options"][0]["id"])
    update_person_beliefs(person_a)
    update_person_beliefs(person_b)

    choice_a = select_next_question(person_a, questions)
    choice_b = select_next_question(person_b, questions)
    assert choice_a.question["id"] == choice_b.question["id"]
    assert choice_a.score == choice_b.score


def test_different_person_evidence_can_diverge_selection(questions):
    person_a = Person(person_id="p1")
    person_b = Person(person_id="p2")

    q011 = find_q(questions, "q011")  # autonomy_preference vs structure_preference
    record_answer(person_a, q011, "a")  # strongly supports autonomy_preference
    record_answer(person_b, q011, "b")  # strongly supports structure_preference
    update_person_beliefs(person_a)
    update_person_beliefs(person_b)

    belief_a_autonomy = person_a.attribute_beliefs["autonomy_preference"].support_strength
    belief_b_autonomy = person_b.attribute_beliefs.get("autonomy_preference")
    assert belief_a_autonomy > 0
    # person_b answered "b", which only contradicts autonomy_preference, so
    # its support_strength should stay at zero.
    assert belief_b_autonomy is None or belief_b_autonomy.support_strength == 0


def test_reason_field_present_and_readable(questions):
    person = Person(person_id="p1")
    scored = score_question(person, find_q(questions, "q001"))
    assert isinstance(scored.reason, str)
    assert len(scored.reason) > 0


def test_select_next_question_returns_none_when_exhausted(questions):
    person = Person(person_id="p1")
    for q in questions:
        record_answer(person, q, q["options"][0]["id"])
    update_person_beliefs(person)
    assert select_next_question(person, questions) is None


def test_all_questions_reference_valid_attributes(questions):
    from models import is_valid_attribute

    for q in questions:
        for attribute in q.get("attributes_targeted", []):
            assert is_valid_attribute(attribute), f"{q['id']} targets invalid attribute {attribute!r}"
        for attribute in q.get("discriminates_between", []):
            assert is_valid_attribute(attribute), f"{q['id']} discriminates on invalid attribute {attribute!r}"


def test_question_bank_has_approximately_thirty_questions(questions):
    assert 25 <= len(questions) <= 35


def test_question_bank_covers_all_seven_categories(questions):
    from models import category_of

    categories_seen = set()
    for q in questions:
        for attribute in q.get("attributes_targeted", []):
            categories_seen.add(category_of(attribute))
    assert categories_seen == {
        "interests",
        "abilities",
        "work_characteristics",
        "values",
        "goals_aspirations",
        "career_adaptability",
        "knowledge_skills",
    }


def test_question_bank_uses_likert_sparingly(questions):
    likert_count = sum(1 for q in questions if q["type"] == "likert")
    assert likert_count <= 4
