"""
simulator.py

Runs the adaptive assessment loop (select question -> simulate an answer
-> score -> repeat) against predefined "persona" answer patterns, so we
can demonstrate that different people get different question sequences
and different profiles without needing a real human in the loop.

Personas are just target dimension vectors (0-100 per dimension). For each
question presented, the persona picks whichever option's scores are
closest (least squared distance) to its own target vector, restricted to
the dimensions the question actually measures. This is a testing
convenience, not a claim about real personality types.
"""

from __future__ import annotations

import json
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Tuple

from question_selector import select_next_question, should_stop
from scorer import apply_answer
from user_model import UserModel

QUESTIONS_PATH = Path(__file__).parent / "questions.json"


def load_question_bank() -> List[dict]:
    with open(QUESTIONS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


# Five archetypal answer patterns, expressed as target scores per
# dimension. These are testing personas only - broad caricatures used to
# exercise the adaptive engine, not scientific personality categories.
PERSONAS: Dict[str, Dict[str, float]] = {
    "analytical": {
        "reasoning": 90, "learning": 70, "curiosity": 65, "creativity": 40,
        "execution": 60, "motivation": 55, "work_preferences": 30,
        "values_ambition": 60, "adaptability": 55,
    },
    "creative_explorer": {
        "reasoning": 45, "learning": 75, "curiosity": 90, "creativity": 90,
        "execution": 35, "motivation": 65, "work_preferences": 50,
        "values_ambition": 55, "adaptability": 70,
    },
    "collaborative_social": {
        "reasoning": 50, "learning": 55, "curiosity": 55, "creativity": 50,
        "execution": 55, "motivation": 65, "work_preferences": 90,
        "values_ambition": 50, "adaptability": 60,
    },
    "execution_builder": {
        "reasoning": 60, "learning": 50, "curiosity": 35, "creativity": 30,
        "execution": 90, "motivation": 70, "work_preferences": 45,
        "values_ambition": 65, "adaptability": 50,
    },
    "uncertain_mixed": {
        "reasoning": 50, "learning": 50, "curiosity": 50, "creativity": 50,
        "execution": 50, "motivation": 45, "work_preferences": 50,
        "values_ambition": 40, "adaptability": 45,
    },
}


def _pick_answer(persona_vector: Dict[str, float], question: dict) -> str:
    """
    Choose the option whose scores best match the persona's target vector
    on the dimensions this question measures. Deterministic: ties break on
    option id order as given in the question bank.
    """
    dims = question["dimensions_measured"]
    best_option_id = None
    best_distance = float("inf")
    for option in question["options"]:
        distance = 0.0
        for dim in dims:
            target = persona_vector.get(dim, 50)
            actual = option["scores"].get(dim, 50)
            distance += (target - actual) ** 2
        if distance < best_distance:
            best_distance = distance
            best_option_id = option["id"]
    return best_option_id


def run_assessment(
    persona_name: str,
    question_bank: List[dict],
    start_time: datetime = None,
) -> Tuple[UserModel, List[dict]]:
    """
    Run one full adaptive assessment for a persona.

    Returns (user_model, log) where `log` is an ordered list of
    {question_id, question_text, reason, answer_id} describing exactly
    what was asked, why, and how it was answered - used for the "selection
    reasoning" demo output.
    """
    persona_vector = PERSONAS[persona_name]
    user_model = UserModel()
    log: List[dict] = []
    clock = start_time or datetime(2026, 1, 1, 9, 0, 0)

    while not should_stop(user_model):
        selection = select_next_question(user_model, question_bank)
        if selection is None:
            break  # question bank exhausted
        question, value, reason = selection

        answer_id = _pick_answer(persona_vector, question)
        clock += timedelta(minutes=1)
        apply_answer(user_model, question, answer_id, timestamp=clock.isoformat())

        log.append(
            {
                "question_id": question["id"],
                "question_text": question["text"],
                "question_value": round(value, 3),
                "reason": reason,
                "answer_id": answer_id,
            }
        )

    return user_model, log


def run_all_personas(question_bank: List[dict] = None) -> Dict[str, Tuple[UserModel, List[dict]]]:
    bank = question_bank or load_question_bank()
    return {name: run_assessment(name, bank) for name in PERSONAS}
