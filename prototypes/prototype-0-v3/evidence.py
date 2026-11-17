"""Turning an answered question into Evidence records, and recording them.

This module is deliberately thin: it reads the evidence entries already
present on the chosen option in questions.json and turns them into typed
Evidence objects attached to the person. It never invents a score.
"""

from __future__ import annotations

from typing import Dict, List, Optional

from models import Direction, Evidence, GOAL_FIELDS, Person, Source, category_of

# Question "type" (how the question is presented) is a slightly richer
# vocabulary than Evidence "source" (what kind of observation this is).
# "ranking" questions produce forced-choice-style evidence (a relative
# preference between options), so they map onto the same source unless the
# question bank tags them explicitly.
QUESTION_TYPE_TO_SOURCE: Dict[str, str] = {
    "scenario": "scenario",
    "forced_choice": "forced_choice",
    "likert": "likert",
    "ranking": "ranking",
    "mini_task": "mini_task",
    "reflection": "reflection",
    "self_report": "self_report",
}


def build_evidence_from_option(
    question: dict,
    option: dict,
    *,
    timestamp: float = 0.0,
) -> List[Evidence]:
    """Convert one answered option into one or more Evidence records.

    `question` and `option` are plain dicts as loaded from questions.json.
    An option's "evidence" list may contain multiple entries (an answer can
    speak to more than one attribute, possibly across categories).
    """

    source = Source(QUESTION_TYPE_TO_SOURCE.get(question["type"], question["type"]))
    reliability = float(question.get("reliability", 0.5))
    # context is per-answer if present (some options imply a different
    # context than the question's default), otherwise falls back to the
    # question's primary context (first entry of "contexts").
    default_context = (question.get("contexts") or [None])[0]
    context = option.get("context", default_context)

    records: List[Evidence] = []
    for entry in option.get("evidence", []):
        attribute = entry["attribute"]
        records.append(
            Evidence(
                question_id=question["id"],
                answer_id=option["id"],
                category=category_of(attribute),
                attribute=attribute,
                direction=Direction(entry["direction"]),
                strength=float(entry["strength"]),
                source=source,
                reliability=reliability,
                context=context,
                timestamp=timestamp,
            )
        )
    return records


def record_answer(
    person: Person,
    question: dict,
    option_id: str,
    *,
    timestamp: float = 0.0,
) -> List[Evidence]:
    """Record a person's answer: log it, derive evidence, attach to the
    person, and capture any free-text goal statement the option carries.

    Returns the list of Evidence records created so the caller (e.g. the
    inference engine) can fold them into attribute beliefs.
    """

    option = _find_option(question, option_id)
    if option is None:
        raise ValueError(f"Unknown option {option_id!r} for question {question['id']!r}")

    new_evidence = build_evidence_from_option(question, option, timestamp=timestamp)

    person.questions_asked.append(question["id"])
    person.answers.append({"question_id": question["id"], "answer_id": option_id})

    start_index = len(person.evidence)
    person.evidence.extend(new_evidence)

    for offset, ev in enumerate(new_evidence):
        belief = person.get_belief(ev.attribute)
        belief.evidence_ids.append(start_index + offset)

    goal_field = option.get("goal_field")
    if goal_field:
        if goal_field not in GOAL_FIELDS:
            raise ValueError(f"Unknown goal_field {goal_field!r} on {question['id']}/{option_id}")
        person.add_goal_text(goal_field, option.get("text", ""))

    return new_evidence


def _find_option(question: dict, option_id: str) -> Optional[dict]:
    for option in question.get("options", []):
        if option["id"] == option_id:
            return option
    return None


def evidence_for_attribute(person: Person, attribute: str) -> List[Evidence]:
    return [e for e in person.evidence if e.attribute == attribute]
