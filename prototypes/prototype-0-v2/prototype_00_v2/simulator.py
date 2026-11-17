"""Simulated users, defined as answer-selection policies over evidence
patterns - not personality labels.

Each simulated user is a small deterministic policy: given a question, pick
the option whose evidence best matches a target pattern of
(facet -> preferred direction). This lets us generate consistent, distinct
evidence trails without ever writing "this user is an analytical person"
anywhere in the code.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional

from models import Direction


@dataclass
class SimulatedUserProfile:
    user_id: str
    description: str
    # facet -> preferred direction ("supports" means this user's answers
    # should, on average, generate "supports" evidence for that facet).
    facet_preferences: Dict[str, str] = field(default_factory=dict)


PROFILES: List[SimulatedUserProfile] = [
    SimulatedUserProfile(
        user_id="sim_information_theorist",
        description="strong information gathering + theory-before-experimentation pattern",
        facet_preferences={
            "information_gathering": "supports",
            "hypothesis_building": "contradicts",
            "theory_before_experimentation": "supports",
            "learning_by_doing": "contradicts",
            "evidence_evaluation": "supports",
            "self_directed_learning": "supports",
            "willingness_to_revise": "supports",
        },
    ),
    SimulatedUserProfile(
        user_id="sim_experimenter_builder",
        description="strong experimentation + practical creativity pattern",
        facet_preferences={
            "learning_by_doing": "supports",
            "theory_before_experimentation": "contradicts",
            "practical_creativity": "supports",
            "resourcefulness": "supports",
            "initiation": "supports",
            "iteration": "supports",
            "problem_reframing": "contradicts",
        },
    ),
    SimulatedUserProfile(
        user_id="sim_social_impact",
        description="high social/collaboration preference + impact motivation pattern",
        facet_preferences={
            "collaboration_preference": "supports",
            "autonomy_preference": "contradicts",
            "impact_orientation": "supports",
            "meaning_orientation": "supports",
            "recognition_orientation": "contradicts",
            "social_intensity_preference": "supports",
        },
    ),
    SimulatedUserProfile(
        user_id="sim_autonomous_explorer",
        description="high autonomy + high ambiguity tolerance pattern",
        facet_preferences={
            "autonomy_preference": "supports",
            "autonomy_orientation": "supports",
            "structure_preference": "contradicts",
            "ambiguity_tolerance": "supports",
            "uncertainty_tolerance": "supports",
            "freedom_value": "supports",
            "stability_value": "contradicts",
        },
    ),
    SimulatedUserProfile(
        user_id="sim_mixed_conflicting",
        description="mixed/conflicting evidence pattern (no clean preference)",
        facet_preferences={
            # Deliberately alternates so contradictory evidence accumulates
            # on the same facets across the session.
            "follow_through": "alternate",
            "response_to_change": "alternate",
            "willingness_to_revise": "alternate",
            "competition_orientation": "alternate",
        },
    ),
]


class SimulatedAnswerer:
    """Given a question, picks an option deterministically according to a
    profile's facet preferences. Falls back to the first option when no
    preference applies, so behavior is still fully deterministic.
    """

    def __init__(self, profile: SimulatedUserProfile):
        self.profile = profile
        self._alternate_state: Dict[str, int] = {}

    def choose_option(self, question: dict) -> str:
        best_option_id: Optional[str] = None
        best_score = float("-inf")

        for option in question.get("options", []):
            score = self._score_option(question["id"], option)
            if score > best_score:
                best_score = score
                best_option_id = option["id"]

        assert best_option_id is not None
        return best_option_id

    def _score_option(self, question_id: str, option: dict) -> float:
        score = 0.0
        for entry in option.get("evidence", []):
            facet = entry["facet"]
            pref = self.profile.facet_preferences.get(facet)
            if pref is None:
                continue
            direction = entry["direction"]
            strength = entry["strength"]

            if pref == "alternate":
                key = f"{question_id}:{facet}"
                toggle = self._alternate_state.get(facet, 0)
                self._alternate_state[facet] = toggle + 1
                wanted = "supports" if toggle % 2 == 0 else "contradicts"
            else:
                wanted = pref

            if direction == wanted:
                score += strength
            elif direction == Direction.CONTRADICTS.value and wanted == "supports":
                score -= strength
            elif direction == Direction.SUPPORTS.value and wanted == "contradicts":
                score -= strength
        return score


def get_profile(user_id: str) -> SimulatedUserProfile:
    for profile in PROFILES:
        if profile.user_id == user_id:
            return profile
    raise ValueError(f"Unknown simulated profile: {user_id}")
