"""Simulated users, defined as answer-selection policies over evidence
patterns - not personality labels.

Each simulated user is a small deterministic policy: given a question, pick
the option whose evidence best matches a target pattern of
(attribute -> preferred direction). This lets us generate consistent,
distinct evidence trails without ever writing "this user is an analytical
person" anywhere in the code.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional

from models import Direction


@dataclass
class SimulatedPersonProfile:
    person_id: str
    description: str
    # attribute -> preferred direction ("supports" means this person's
    # answers should, on average, generate "supports" evidence for that
    # attribute).
    attribute_preferences: Dict[str, str] = field(default_factory=dict)


PROFILES: List[SimulatedPersonProfile] = [
    SimulatedPersonProfile(
        person_id="sim_technical_builder",
        description="tech/practical-making interests, high spatial/quantitative ability, autonomy-seeking",
        attribute_preferences={
            "technology": "supports",
            "practical_making": "supports",
            "nature": "contradicts",
            "spatial_reasoning": "supports",
            "quantitative_reasoning": "supports",
            "autonomy_preference": "supports",
            "structure_preference": "contradicts",
            "self_directed_learning": "supports",
            "technical_skill_depth": "supports",
        },
    ),
    SimulatedPersonProfile(
        person_id="sim_people_impact",
        description="people/business interests, high collaboration + social interaction, impact-driven values",
        attribute_preferences={
            "people": "supports",
            "business": "supports",
            "information": "contradicts",
            "collaboration_preference": "supports",
            "social_interaction_preference": "supports",
            "autonomy_preference": "contradicts",
            "impact": "supports",
            "status": "contradicts",
            "curiosity": "supports",
        },
    ),
    SimulatedPersonProfile(
        person_id="sim_analytical_researcher",
        description="science/information interests, strong analytical + verbal reasoning, security/intellectual-challenge values",
        attribute_preferences={
            "science": "supports",
            "information": "supports",
            "people": "contradicts",
            "analytical_problem_solving": "supports",
            "verbal_reasoning": "supports",
            "creative_thinking": "contradicts",
            "intellectual_challenge": "supports",
            "financial_reward": "contradicts",
            "domain_knowledge_breadth": "supports",
        },
    ),
    SimulatedPersonProfile(
        person_id="sim_creative_freelancer",
        description="design/creative-expression interests, freedom + lifestyle values, low structure preference",
        attribute_preferences={
            "design": "supports",
            "creative_expression": "supports",
            "mathematics": "contradicts",
            "creative_thinking": "supports",
            "structure_preference": "contradicts",
            "ambiguity_tolerance": "supports",
            "freedom": "supports",
            "lifestyle": "supports",
            "security": "contradicts",
        },
    ),
    SimulatedPersonProfile(
        person_id="sim_unclear_explorer",
        description="low goal clarity, high curiosity, mixed/conflicting evidence pattern (no clean preference)",
        attribute_preferences={
            "career_direction_clarity": "contradicts",
            "education_direction_clarity": "contradicts",
            "curiosity": "supports",
            "concern": "contradicts",
            # Deliberately alternates so contradictory evidence accumulates
            # on the same attributes across the session.
            "control": "alternate",
            "confidence": "alternate",
            "ambition_level": "alternate",
        },
    ),
    SimulatedPersonProfile(
        person_id="sim_security_seeker",
        description="security/structure-seeking, low ambiguity tolerance, short-term goal orientation",
        attribute_preferences={
            "security": "supports",
            "financial_reward": "contradicts",
            "structure_preference": "supports",
            "ambiguity_tolerance": "contradicts",
            "short_term_goal_orientation": "supports",
            "long_term_goal_orientation": "contradicts",
            "practical_experience": "supports",
        },
    ),
]


class SimulatedAnswerer:
    """Given a question, picks an option deterministically according to a
    profile's attribute preferences. Falls back to the first option when no
    preference applies, so behavior is still fully deterministic.
    """

    def __init__(self, profile: SimulatedPersonProfile):
        self.profile = profile
        self._alternate_state: Dict[str, int] = {}

    def choose_option(self, question: dict) -> str:
        best_option_id: Optional[str] = None
        best_score = float("-inf")

        for option in question.get("options", []):
            score = self._score_option(option)
            if score > best_score:
                best_score = score
                best_option_id = option["id"]

        assert best_option_id is not None
        return best_option_id

    def _score_option(self, option: dict) -> float:
        score = 0.0
        for entry in option.get("evidence", []):
            attribute = entry["attribute"]
            pref = self.profile.attribute_preferences.get(attribute)
            if pref is None:
                continue
            direction = entry["direction"]
            strength = entry["strength"]

            if pref == "alternate":
                toggle = self._alternate_state.get(attribute, 0)
                self._alternate_state[attribute] = toggle + 1
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


def get_profile(person_id: str) -> SimulatedPersonProfile:
    for profile in PROFILES:
        if profile.person_id == person_id:
            return profile
    raise ValueError(f"Unknown simulated profile: {person_id}")
