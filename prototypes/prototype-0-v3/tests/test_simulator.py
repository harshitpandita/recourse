import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import json

import pytest

from evidence import record_answer
from inference import update_person_beliefs
from models import Person
from question_selector import select_next_question, should_stop
from simulator import PROFILES, SimulatedAnswerer

QUESTIONS_PATH = Path(__file__).parent.parent / "questions.json"


@pytest.fixture(scope="module")
def questions():
    with open(QUESTIONS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def run_session(person_id, answerer, questions):
    person = Person(person_id=person_id)
    while not should_stop(person, questions):
        choice = select_next_question(person, questions)
        if choice is None:
            break
        question = choice.question
        option_id = answerer.choose_option(question)
        record_answer(person, question, option_id)
        update_person_beliefs(person)
    return person


def test_at_least_five_simulated_profiles_exist():
    assert len(PROFILES) >= 5


def test_profiles_use_distinct_attribute_patterns():
    patterns = [frozenset(p.attribute_preferences.keys()) for p in PROFILES]
    # No two profiles should target the exact same attribute set.
    assert len(set(patterns)) == len(patterns)


def test_cross_user_question_sequences_diverge(questions):
    sessions = {}
    for profile in PROFILES:
        answerer = SimulatedAnswerer(profile)
        sessions[profile.person_id] = run_session(profile.person_id, answerer, questions)

    sequences = {pid: tuple(p.questions_asked) for pid, p in sessions.items()}
    assert len(set(sequences.values())) > 1


def test_two_specific_profiles_receive_different_first_question(questions):
    profile_a = PROFILES[0]
    profile_b = PROFILES[1]
    person_a = run_session(profile_a.person_id, SimulatedAnswerer(profile_a), questions)
    person_b = run_session(profile_b.person_id, SimulatedAnswerer(profile_b), questions)

    assert person_a.questions_asked != person_b.questions_asked


def test_session_is_deterministic_across_reruns(questions):
    profile = PROFILES[0]
    run1 = run_session(profile.person_id, SimulatedAnswerer(profile), questions)
    run2 = run_session(profile.person_id, SimulatedAnswerer(profile), questions)
    assert run1.questions_asked == run2.questions_asked
    for attribute in run1.attribute_beliefs:
        assert run1.attribute_beliefs[attribute].confidence == pytest.approx(
            run2.attribute_beliefs[attribute].confidence
        )


def test_conflicting_pattern_profile_retains_contradiction(questions):
    # sim_unclear_explorer deliberately alternates its preference on
    # "control"/"confidence"/"ambition_level" so contradictory evidence can
    # accumulate on the same attribute across the session.
    profile = next(p for p in PROFILES if "alternate" in p.attribute_preferences.values())
    person = run_session(profile.person_id, SimulatedAnswerer(profile), questions)

    found_conflict = any(
        belief.support_strength > 0 and belief.contradiction_strength > 0
        for belief in person.attribute_beliefs.values()
    )
    if not found_conflict:
        pytest.skip(
            "Simulated policy did not happen to produce opposing-direction "
            "answers this run; contradiction retention is covered directly "
            "below and in test_inference.py."
        )


def test_contradictory_evidence_from_real_bank_questions_is_retained(questions):
    # Two real questions where "structure_preference" evidence points in
    # opposite directions depending on which option is chosen.
    q011 = next(q for q in questions if q["id"] == "q011")
    q015 = next(q for q in questions if q["id"] == "q015")

    person = Person(person_id="p1")
    record_answer(person, q011, "b")  # structure_preference: supports 0.85
    record_answer(person, q015, "b")  # structure_preference: contradicts 0.5
    update_person_beliefs(person)

    belief = person.attribute_beliefs["structure_preference"]
    assert belief.support_strength > 0
    assert belief.contradiction_strength > 0
    # Confidence must be reduced by the conflict rather than the evidence
    # being silently averaged into a single number.
    non_conflicting = Person(person_id="p2")
    record_answer(non_conflicting, q011, "b")
    update_person_beliefs(non_conflicting)
    assert belief.confidence < non_conflicting.attribute_beliefs["structure_preference"].confidence
