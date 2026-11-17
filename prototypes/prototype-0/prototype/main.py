"""
main.py

Demonstration CLI. Runs the adaptive assessment for every simulated
persona and prints:
  - the question sequence each persona was asked, with selection reasoning
  - the resulting per-dimension profile
  - which dimensions remain insufficiently understood at stop time

This module contains no assessment logic itself - it only orchestrates
simulator.py and formats output. Run with: python main.py
"""

from __future__ import annotations

from question_selector import DIMENSION_IMPORTANCE, IMPORTANCE_STOP_THRESHOLD, MIN_CONFIDENCE
from simulator import PERSONAS, load_question_bank, run_assessment

DISCLAIMER = (
    "NOTE: dimensions and scores below are provisional research constructs "
    "for this prototype, not validated psychological measurements."
)


def print_question_sequence(log):
    print("\nQuestion sequence:")
    for i, entry in enumerate(log, start=1):
        print(f"  {i}. [{entry['question_id']}] {entry['question_text']}")
        print(f"     -> answered: '{entry['answer_id']}'")
        print(f"     reason chosen: {entry['reason']}")


def print_profile(user_model):
    print("\nResulting profile:")
    print(f"  {'dimension':<18}{'score':>8}{'confidence':>12}{'evidence_count':>16}")
    for row in user_model.profile_summary():
        print(
            f"  {row['dimension']:<18}{row['score']:>8}{row['confidence']:>12}"
            f"{row['evidence_count']:>16}"
        )


def print_remaining_uncertainty(user_model):
    weak = user_model.low_confidence_dimensions(MIN_CONFIDENCE)
    print("\nRemaining uncertainty (confidence below "
          f"{MIN_CONFIDENCE}):")
    if not weak:
        print("  none - all dimensions reached the confidence threshold.")
    else:
        for name in weak:
            conf = user_model.get_dimension(name).confidence
            importance = DIMENSION_IMPORTANCE.get(name, 0.5)
            flag = "important" if importance >= IMPORTANCE_STOP_THRESHOLD else "lower priority"
            print(f"  - {name}: confidence {conf:.2f} ({flag})")


def main():
    print(DISCLAIMER)
    question_bank = load_question_bank()

    for persona_name in PERSONAS:
        print("\n" + "=" * 70)
        print(f"PERSONA: {persona_name}")
        print("=" * 70)

        user_model, log = run_assessment(persona_name, question_bank)

        print(f"\nQuestions asked: {len(log)}  |  progress: {user_model.progress}")
        print_question_sequence(log)
        print_profile(user_model)
        print_remaining_uncertainty(user_model)


if __name__ == "__main__":
    main()
