import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from user_model import DIMENSIONS, Evidence, UserModel


def test_dimensions_initialize_correctly():
    model = UserModel()
    assert set(model.dimensions.keys()) == set(DIMENSIONS)
    for name in DIMENSIONS:
        dim = model.get_dimension(name)
        assert dim.score == 50.0
        assert dim.confidence == 0.0
        assert dim.evidence == []


def test_custom_dimension_list_supported():
    model = UserModel(dimensions=["reasoning", "creativity"])
    assert set(model.dimensions.keys()) == {"reasoning", "creativity"}


def test_evidence_is_recorded():
    model = UserModel()
    ev = Evidence(
        question_id="q01",
        answer="a",
        evidence_score=80,
        reliability=0.8,
        timestamp="2026-01-01T00:00:00",
    )
    model.add_evidence("reasoning", ev)
    dim = model.get_dimension("reasoning")
    assert dim.evidence_count == 1
    assert dim.evidence[0].question_id == "q01"


def test_record_question_and_answer_updates_bookkeeping():
    model = UserModel()
    model.record_question_asked("q01")
    model.record_answer("q01", "a")
    assert model.questions_asked == ["q01"]
    assert model.answers == {"q01": "a"}
    assert model.progress["questions_asked"] == 1


def test_low_confidence_dimensions_filters_by_threshold():
    model = UserModel(dimensions=["reasoning", "creativity"])
    model.get_dimension("reasoning").confidence = 0.9
    model.get_dimension("creativity").confidence = 0.1
    assert model.low_confidence_dimensions(0.5) == ["creativity"]


def test_unknown_dimension_raises():
    model = UserModel()
    try:
        model.get_dimension("not_a_dimension")
        assert False, "expected KeyError"
    except KeyError:
        pass
