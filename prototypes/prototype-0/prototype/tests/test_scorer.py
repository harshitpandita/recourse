import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from scorer import apply_answer, extract_evidence_scores, update_dimension
from user_model import UserModel

QUESTION_HIGH_REL = {
    "id": "qh",
    "text": "test question, high reliability",
    "type": "multiple_choice",
    "dimensions_measured": ["reasoning"],
    "reliability": 0.9,
    "effort": 1,
    "options": [
        {"id": "a", "text": "opt a", "scores": {"reasoning": 90}},
        {"id": "b", "text": "opt b", "scores": {"reasoning": 10}},
    ],
}

QUESTION_LOW_REL = {
    "id": "ql",
    "text": "test question, low reliability",
    "type": "multiple_choice",
    "dimensions_measured": ["reasoning"],
    "reliability": 0.3,
    "effort": 1,
    "options": [
        {"id": "a", "text": "opt a", "scores": {"reasoning": 90}},
        {"id": "b", "text": "opt b", "scores": {"reasoning": 10}},
    ],
}

MULTI_DIM_QUESTION = {
    "id": "qm",
    "text": "multi-dim test question",
    "type": "forced_choice",
    "dimensions_measured": ["reasoning", "creativity"],
    "reliability": 0.8,
    "effort": 1,
    "options": [
        {"id": "a", "text": "opt a", "scores": {"reasoning": 80, "creativity": 20}},
        {"id": "b", "text": "opt b", "scores": {"reasoning": 20, "creativity": 80}},
    ],
}


def test_extract_evidence_scores_maps_chosen_option():
    scores = extract_evidence_scores(QUESTION_HIGH_REL, "a")
    assert scores == {"reasoning": 90}


def test_extract_evidence_scores_invalid_answer_raises():
    try:
        extract_evidence_scores(QUESTION_HIGH_REL, "z")
        assert False, "expected ValueError"
    except ValueError:
        pass


def test_deterministic_answer_produces_deterministic_score():
    model1 = UserModel()
    apply_answer(model1, QUESTION_HIGH_REL, "a", timestamp="2026-01-01T00:00:00")

    model2 = UserModel()
    apply_answer(model2, QUESTION_HIGH_REL, "a", timestamp="2026-01-01T00:00:00")

    assert model1.get_dimension("reasoning").score == model2.get_dimension("reasoning").score
    assert (
        model1.get_dimension("reasoning").confidence
        == model2.get_dimension("reasoning").confidence
    )


def test_higher_reliability_produces_stronger_score_update():
    model_high = UserModel()
    update_dimension(model_high, "reasoning", QUESTION_HIGH_REL, evidence_score=90,
                      answer_id="a", timestamp="t")

    model_low = UserModel()
    update_dimension(model_low, "reasoning", QUESTION_LOW_REL, evidence_score=90,
                      answer_id="a", timestamp="t")

    # both start at 50 and move toward 90; higher reliability should move further
    high_delta = model_high.get_dimension("reasoning").score - 50
    low_delta = model_low.get_dimension("reasoning").score - 50
    assert high_delta > low_delta > 0


def test_higher_reliability_produces_stronger_confidence_gain():
    model_high = UserModel()
    update_dimension(model_high, "reasoning", QUESTION_HIGH_REL, evidence_score=90,
                      answer_id="a", timestamp="t")

    model_low = UserModel()
    update_dimension(model_low, "reasoning", QUESTION_LOW_REL, evidence_score=90,
                      answer_id="a", timestamp="t")

    assert (
        model_high.get_dimension("reasoning").confidence
        > model_low.get_dimension("reasoning").confidence
    )


def test_conflicting_evidence_does_not_break_model():
    model = UserModel()
    update_dimension(model, "reasoning", QUESTION_HIGH_REL, evidence_score=90,
                      answer_id="a", timestamp="t1")
    update_dimension(model, "reasoning", QUESTION_HIGH_REL, evidence_score=5,
                      answer_id="b", timestamp="t2")

    dim = model.get_dimension("reasoning")
    assert 0 <= dim.score <= 100
    assert 0 <= dim.confidence <= 1
    assert dim.evidence_count == 2


def test_apply_answer_updates_all_measured_dimensions():
    model = UserModel()
    apply_answer(model, MULTI_DIM_QUESTION, "a", timestamp="t1")

    assert model.get_dimension("reasoning").evidence_count == 1
    assert model.get_dimension("creativity").evidence_count == 1
    assert model.get_dimension("reasoning").score > 50
    assert model.get_dimension("creativity").score < 50


def test_apply_answer_records_question_and_answer():
    model = UserModel()
    apply_answer(model, QUESTION_HIGH_REL, "a", timestamp="t1")
    assert model.questions_asked == ["qh"]
    assert model.answers == {"qh": "a"}
