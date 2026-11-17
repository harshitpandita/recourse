"""Demonstration driver: run each simulated person through the adaptive
evidence engine and print out what was asked, what evidence accumulated,
what beliefs formed, and why each question was selected.

IMPORTANT: this is an engineering experiment, not a validated psychological
assessment. The output below shows evidence and belief states, not
"scores" or measurements of anything real about a person.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, List

from evidence import record_answer
from inference import update_person_beliefs
from models import Person
from question_selector import select_next_question, should_stop
from simulator import PROFILES, SimulatedAnswerer

QUESTIONS_PATH = Path(__file__).parent / "questions.json"


def load_questions() -> List[dict]:
    with open(QUESTIONS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def run_session(person_id: str, answerer: SimulatedAnswerer, questions: List[dict]) -> Person:
    person = Person(person_id=person_id)
    step = 0

    while True:
        if should_stop(person, questions):
            break

        choice = select_next_question(person, questions)
        if choice is None:
            break

        question = choice.question
        option_id = answerer.choose_option(question)

        print(f"  [{step + 1}] -> {question['id']} (score={choice.score:.3f})")
        print(f"        reason: {choice.reason}")
        print(f"        answered: option '{option_id}'")

        record_answer(person, question, option_id, timestamp=float(step))
        update_person_beliefs(person)
        step += 1

    return person


def print_evidence(person: Person) -> None:
    print("  Evidence accumulated:")
    for i, e in enumerate(person.evidence):
        print(
            f"    #{i:02d} q={e.question_id} attribute={e.attribute:<28} category={e.category:<20} "
            f"dir={e.direction.value:<11} strength={e.strength:.2f} "
            f"reliability={e.reliability:.2f} source={e.source.value:<11} context={e.context}"
        )


def print_beliefs(person: Person) -> None:
    print("  Profile beliefs:")
    for attribute in sorted(person.attribute_beliefs.keys()):
        belief = person.attribute_beliefs[attribute]
        print(
            f"    [{belief.category:<19}] {attribute:<30} support={belief.support_strength:.2f} "
            f"contradiction={belief.contradiction_strength:.2f} "
            f"confidence={belief.confidence:.2f} (n={len(belief.evidence_ids)})"
        )


def print_goals(person: Person) -> None:
    print("  Goals & aspirations captured (free text):")
    any_goal = False
    for field_name, entries in person.goals.items():
        if entries:
            any_goal = True
            print(f"    {field_name}: {entries}")
    if not any_goal:
        print("    (none captured)")


def print_remaining_uncertainty(person: Person, questions: List[dict]) -> None:
    all_targeted = set()
    for q in questions:
        all_targeted.update(q.get("attributes_targeted", []))

    low_confidence = sorted(
        a for a in all_targeted
        if person.attribute_beliefs.get(a, None) is None
        or person.attribute_beliefs[a].confidence < 0.4
    )
    print(f"  Remaining low-confidence attributes ({len(low_confidence)}):")
    print(f"    {', '.join(low_confidence) if low_confidence else '(none)'}")


def main() -> None:
    questions = load_questions()

    print("=" * 78)
    print("Prototype 0 v3 - Evidence-Based Adaptive Assessment")
    print("Seven profile categories: interests, abilities, work_characteristics,")
    print("values, goals_aspirations, career_adaptability, knowledge_skills.")
    print("Engineering demonstration only. Not a validated psychological")
    print("measurement instrument.")
    print("=" * 78)

    sessions: Dict[str, Person] = {}

    for profile in PROFILES:
        print()
        print(f"--- Person: {profile.person_id} ---")
        print(f"    pattern: {profile.description}")
        answerer = SimulatedAnswerer(profile)
        person = run_session(profile.person_id, answerer, questions)
        sessions[profile.person_id] = person

        print()
        print(f"  Questions asked ({len(person.questions_asked)}): {person.questions_asked}")
        print()
        print_evidence(person)
        print()
        print_beliefs(person)
        print()
        print_goals(person)
        print()
        print_remaining_uncertainty(person, questions)

    print()
    print("=" * 78)
    print("Cross-user check: question sequences differ")
    print("=" * 78)
    for pid, person in sessions.items():
        print(f"  {pid}: {person.questions_asked}")

    seqs = [tuple(p.questions_asked) for p in sessions.values()]
    if len(set(seqs)) > 1:
        print("  -> confirmed: different people received different question sequences")
    else:
        print("  -> WARNING: all people received identical sequences")

    print()
    print("=" * 78)
    print("Determinism check: re-running one person's session reproduces the")
    print("same sequence and beliefs")
    print("=" * 78)
    profile = PROFILES[0]
    rerun_answerer = SimulatedAnswerer(profile)
    rerun_person = run_session(profile.person_id, rerun_answerer, questions)
    original = sessions[profile.person_id]
    same_sequence = rerun_person.questions_asked == original.questions_asked
    same_beliefs = all(
        abs(rerun_person.attribute_beliefs[a].confidence - original.attribute_beliefs[a].confidence) < 1e-9
        for a in original.attribute_beliefs
    )
    print(f"  identical question sequence: {same_sequence}")
    print(f"  identical belief confidences: {same_beliefs}")


if __name__ == "__main__":
    main()
