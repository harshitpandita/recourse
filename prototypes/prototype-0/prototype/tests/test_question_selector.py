import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from question_selector import score_question, select_next_question, should_stop
from scorer import apply_answer
from user_model import UserModel

Q_REASONING = {
    "id": "q_reasoning",
    "text": "reasoning question",
    "type": "multiple_choice",
    "dimensions_measured": ["reasoning"],
    "reliability": 0.8,
    "effort": 1,
    "options": [
        {"id": "a", "text": "a", "scores": {"reasoning": 80}},
        {"id": "b", "text": "b", "scores": {"reasoning": 20}},
    ],
}

Q_CREATIVITY = {
    "id": "q_creativity",
    "text": "creativity question",
    "type": "multiple_choice",
    "dimensions_measured": ["creativity"],
    "reliability": 0.8,
    "effort": 1,
    "options": [
        {"id": "a", "text": "a", "scores": {"creativity": 80}},
        {"id": "b", "text": "b", "scores": {"creativity": 20}},
    ],
}

Q_IRRELEVANT = {
    "id": "q_irrelevant",
    "text": "an unused dimension probe",
    "type": "multiple_choice",
    "dimensions_measured": ["values_ambition"],
    "reliability": 0.1,  # deliberately low-value question
    "effort": 3,          # deliberately high effort
    "options": [
        {"id": "a", "text": "a", "scores": {"values_ambition": 80}},
        {"id": "b", "text": "b", "scores": {"values_ambition": 20}},
    ],
}

BANK = [Q_REASONING, Q_CREATIVITY, Q_IRRELEVANT]


def test_low_confidence_dimension_is_prioritized():
    model = UserModel()
    # Manually push creativity's confidence up so reasoning is clearly
    # the more uncertain (and thus more valuable) dimension.
    model.get_dimension("creativity").confidence = 0.9

    question, score, reason = select_next_question(model, BANK)
    assert question["id"] == "q_reasoning"
    assert "reasoning" in reason


def test_low_effort_high_reliability_question_beats_costly_low_value_one():
    model = UserModel()
    question, score, reason = select_next_question(model, [Q_IRRELEVANT, Q_CREATIVITY])
    # Q_IRRELEVANT has low reliability and high effort, so despite being
    # the only source of values_ambition evidence it should score lower.
    assert question["id"] == "q_creativity"


def test_selector_does_not_simply_follow_bank_order():
    model = UserModel()
    model.get_dimension("reasoning").confidence = 0.95  # make it low value
    question, score, reason = select_next_question(model, BANK)
    # q_reasoning is first in BANK but should not be picked once satisfied
    assert question["id"] != "q_reasoning"


def test_selection_is_deterministic_for_identical_inputs():
    model_a = UserModel()
    model_b = UserModel()
    result_a = select_next_question(model_a, BANK)
    result_b = select_next_question(model_b, BANK)
    assert result_a[0]["id"] == result_b[0]["id"]
    assert result_a[1] == result_b[1]


def test_already_asked_questions_are_skipped():
    model = UserModel()
    apply_answer(model, Q_REASONING, "a", timestamp="t1")
    remaining_ids = {q["id"] for q in BANK if q["id"] not in model.questions_asked}
    question, score, reason = select_next_question(model, BANK)
    assert question["id"] in remaining_ids
    assert question["id"] != "q_reasoning"


def test_select_next_question_returns_none_when_bank_exhausted():
    model = UserModel()
    apply_answer(model, Q_REASONING, "a", timestamp="t1")
    apply_answer(model, Q_CREATIVITY, "a", timestamp="t2")
    apply_answer(model, Q_IRRELEVANT, "a", timestamp="t3")
    assert select_next_question(model, BANK) is None


def test_should_stop_respects_min_questions_floor():
    model = UserModel()
    # Even if every dimension is already "confident", we shouldn't stop
    # before MIN_QUESTIONS have been asked.
    for dim in model.dimensions.values():
        dim.confidence = 1.0
    assert len(model.questions_asked) == 0
    assert should_stop(model) is False


def test_should_stop_true_once_max_questions_reached():
    model = UserModel()
    for i in range(12):
        model.record_question_asked(f"q{i}")
    assert should_stop(model) is True


def test_score_question_prefers_uncertain_important_dimension():
    model = UserModel()
    high_uncertainty_score = score_question(model, Q_REASONING)
    model.get_dimension("reasoning").confidence = 0.9
    low_uncertainty_score = score_question(model, Q_REASONING)
    assert high_uncertainty_score > low_uncertainty_score
