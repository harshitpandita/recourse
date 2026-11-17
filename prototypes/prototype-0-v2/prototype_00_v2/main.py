"""Demonstration driver: run each simulated user through the adaptive
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
from inference import update_user_beliefs
from models import User
from question_selector import select_next_question, should_stop
from simulator import PROFILES, SimulatedAnswerer

QUESTIONS_PATH = Path(__file__).parent / "questions.json"


def load_questions() -> List[dict]:
    with open(QUESTIONS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def run_session(user_id: str, answerer: SimulatedAnswerer, questions: List[dict]) -> User:
    user = User(user_id=user_id)
    step = 0

    while True:
        if should_stop(user, questions):
            break

        choice = select_next_question(user, questions)
        if choice is None:
            break

        question = choice.question
        option_id = answerer.choose_option(question)

        print(f"  [{step + 1}] -> {question['id']} (score={choice.score:.3f})")
        print(f"        reason: {choice.reason}")
        print(f"        answered: option '{option_id}'")

        record_answer(user, question, option_id, timestamp=float(step))
        update_user_beliefs(user)
        step += 1

    return user


def print_evidence(user: User) -> None:
    print("  Evidence accumulated:")
    for i, e in enumerate(user.evidence):
        print(
            f"    #{i:02d} q={e.question_id} facet={e.facet:<32} "
            f"dir={e.direction.value:<11} strength={e.strength:.2f} "
            f"reliability={e.reliability:.2f} source={e.source.value:<13} context={e.context}"
        )


def print_beliefs(user: User) -> None:
    print("  Facet beliefs:")
    for facet in sorted(user.facet_beliefs.keys()):
        belief = user.facet_beliefs[facet]
        print(
            f"    {facet:<38} support={belief.support_strength:.2f} "
            f"contradiction={belief.contradiction_strength:.2f} "
            f"confidence={belief.confidence:.2f} (n={len(belief.evidence_ids)})"
        )


def print_remaining_uncertainty(user: User, questions: List[dict]) -> None:
    all_targeted = set()
    for q in questions:
        all_targeted.update(q.get("facets_targeted", []))

    low_confidence = sorted(
        f for f in all_targeted
        if user.facet_beliefs.get(f, None) is None
        or user.facet_beliefs[f].confidence < 0.4
    )
    print(f"  Remaining low-confidence facets ({len(low_confidence)}):")
    print(f"    {', '.join(low_confidence) if low_confidence else '(none)'}")


def main() -> None:
    questions = load_questions()

    print("=" * 78)
    print("Prototype 0 v2 - Evidence-Based Adaptive Assessment")
    print("Engineering demonstration only. Not a validated psychological")
    print("measurement instrument.")
    print("=" * 78)

    sessions: Dict[str, User] = {}

    for profile in PROFILES:
        print()
        print(f"--- User: {profile.user_id} ---")
        print(f"    pattern: {profile.description}")
        answerer = SimulatedAnswerer(profile)
        user = run_session(profile.user_id, answerer, questions)
        sessions[profile.user_id] = user

        print()
        print(f"  Questions asked ({len(user.questions_asked)}): {user.questions_asked}")
        print()
        print_evidence(user)
        print()
        print_beliefs(user)
        print()
        print_remaining_uncertainty(user, questions)

    print()
    print("=" * 78)
    print("Cross-user check: question sequences differ")
    print("=" * 78)
    for uid, user in sessions.items():
        print(f"  {uid}: {user.questions_asked}")

    seqs = [tuple(u.questions_asked) for u in sessions.values()]
    if len(set(seqs)) > 1:
        print("  -> confirmed: different users received different question sequences")
    else:
        print("  -> WARNING: all users received identical sequences")

    print()
    print("=" * 78)
    print("Determinism check: re-running one user's session reproduces the")
    print("same sequence and beliefs")
    print("=" * 78)
    profile = PROFILES[0]
    rerun_answerer = SimulatedAnswerer(profile)
    rerun_user = run_session(profile.user_id, rerun_answerer, questions)
    original = sessions[profile.user_id]
    same_sequence = rerun_user.questions_asked == original.questions_asked
    same_beliefs = all(
        abs(rerun_user.facet_beliefs[f].confidence - original.facet_beliefs[f].confidence) < 1e-9
        for f in original.facet_beliefs
    )
    print(f"  identical question sequence: {same_sequence}")
    print(f"  identical belief confidences: {same_beliefs}")


if __name__ == "__main__":
    main()
