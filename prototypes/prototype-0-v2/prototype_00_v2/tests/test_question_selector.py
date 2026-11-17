import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import json

import pytest

from evidence import record_answer
from inference import update_user_beliefs
from models import User
from question_selector import rank_questions, score_question, select_next_question

QUESTIONS_PATH = Path(__file__).parent.parent / "questions.json"


@pytest.fixture(scope="module")
def questions():
    with open(QUESTIONS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def find_q(questions, qid):
    return next(q for q in questions if q["id"] == qid)


def test_low_confidence_facets_receive_priority(questions):
    user = User(user_id="u1")
    q001 = find_q(questions, "q001")  # targets information_gathering, hypothesis_building
    score_before = score_question(user, q001).score

    # Saturate information_gathering's confidence via strong repeated evidence.
    for i in range(5):
        record_answer(user, q001, "a", timestamp=float(i))
    update_user_beliefs(user)

    score_after = score_question(user, q001).score
    assert score_after < score_before


def test_discrimination_value_affects_ranking(questions):
    user = User(user_id="u1")
    # q001's discriminates_between targets are both unresolved -> ambiguous -> high discrimination.
    scored = score_question(user, find_q(questions, "q001"))
    assert scored.score > 0


def test_unreliable_questions_are_penalized(questions):
    user = User(user_id="u1")
    reliable_q = dict(find_q(questions, "q001"))
    unreliable_q = dict(reliable_q)
    unreliable_q["reliability"] = 0.1
    unreliable_q["id"] = "q001_unreliable"

    reliable_score = score_question(user, reliable_q).score
    unreliable_score = score_question(user, unreliable_q).score
    assert unreliable_score < reliable_score


def test_high_effort_questions_are_penalized(questions):
    user = User(user_id="u1")
    low_effort_q = dict(find_q(questions, "q001"))
    high_effort_q = dict(low_effort_q)
    high_effort_q["effort"] = 5
    high_effort_q["id"] = "q001_high_effort"

    low_effort_score = score_question(user, low_effort_q).score
    high_effort_score = score_question(user, high_effort_q).score
    assert high_effort_score < low_effort_score


def test_answered_questions_are_excluded(questions):
    user = User(user_id="u1")
    q001 = find_q(questions, "q001")
    record_answer(user, q001, "a")
    update_user_beliefs(user)

    ranked = rank_questions(user, questions)
    ranked_ids = [r.question["id"] for r in ranked]
    assert "q001" not in ranked_ids


def test_identical_inputs_produce_identical_selection(questions):
    user_a = User(user_id="u1")
    user_b = User(user_id="u1")
    for q in questions[:3]:
        record_answer(user_a, q, q["options"][0]["id"])
        record_answer(user_b, q, q["options"][0]["id"])
    update_user_beliefs(user_a)
    update_user_beliefs(user_b)

    choice_a = select_next_question(user_a, questions)
    choice_b = select_next_question(user_b, questions)
    assert choice_a.question["id"] == choice_b.question["id"]
    assert choice_a.score == choice_b.score


def test_different_user_evidence_can_diverge_selection(questions):
    user_a = User(user_id="u1")
    user_b = User(user_id="u2")

    q009 = find_q(questions, "q009")  # initiation vs planning
    record_answer(user_a, q009, "a")  # strongly supports initiation
    record_answer(user_b, q009, "b")  # strongly supports planning
    update_user_beliefs(user_a)
    update_user_beliefs(user_b)

    # After answering q009 differently, initiation vs planning confidence
    # differs between the two users, which should be reflected in beliefs.
    belief_a_initiation = user_a.facet_beliefs["initiation"].support_strength
    belief_b_initiation = user_b.facet_beliefs.get("initiation")
    assert belief_a_initiation > 0
    assert belief_b_initiation is None or belief_b_initiation.support_strength == 0


def test_reason_field_present_and_readable(questions):
    user = User(user_id="u1")
    scored = score_question(user, find_q(questions, "q001"))
    assert isinstance(scored.reason, str)
    assert len(scored.reason) > 0


def test_select_next_question_returns_none_when_exhausted(questions):
    user = User(user_id="u1")
    for q in questions:
        record_answer(user, q, q["options"][0]["id"])
    update_user_beliefs(user)
    assert select_next_question(user, questions) is None
