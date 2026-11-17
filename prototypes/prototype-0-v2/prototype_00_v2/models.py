"""Core data model for the evidence-based adaptive assessment prototype.

This module defines the facet vocabulary and the plain-data structures
(Evidence, FacetBelief, User) used throughout the engine. Nothing here
computes anything - it is pure structure. See inference.py for the
belief-update math and question_selector.py for question selection.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Dict, List, Optional


# ---------------------------------------------------------------------------
# Facet vocabulary
# ---------------------------------------------------------------------------
# This is the single configurable source of truth for what facets exist and
# which category they belong to. Nothing about scoring lives here - it is
# purely a vocabulary + grouping so questions/evidence can reference facets
# by name and the rest of the system can validate/organize around them.

FACET_CATEGORIES: Dict[str, List[str]] = {
    "reasoning": [
        "information_gathering",
        "problem_decomposition",
        "pattern_recognition",
        "hypothesis_building",
        "evidence_evaluation",
        "belief_updating",
    ],
    "learning": [
        "theory_before_experimentation",
        "learning_by_doing",
        "depth_of_understanding",
        "feedback_orientation",
        "self_directed_learning",
        "persistence_with_difficulty",
    ],
    "curiosity": [
        "breadth_of_exploration",
        "depth_of_investigation",
        "question_generation",
        "intrinsic_exploration",
    ],
    "creative_thinking": [
        "idea_generation",
        "flexibility",
        "originality",
        "problem_reframing",
        "practical_creativity",
    ],
    "execution": [
        "initiation",
        "follow_through",
        "planning",
        "iteration",
        "quality_standards",
        "resourcefulness",
    ],
    "motivation": [
        "mastery_orientation",
        "autonomy_orientation",
        "financial_motivation",
        "impact_orientation",
        "recognition_orientation",
        "security_orientation",
        "competition_orientation",
        "meaning_orientation",
    ],
    "work_environment": [
        "collaboration_preference",
        "autonomy_preference",
        "structure_preference",
        "ambiguity_tolerance",
        "pace_preference",
        "variety_preference",
        "social_intensity_preference",
    ],
    "values_and_ambitions": [
        "desired_lifestyle",
        "achievement_orientation",
        "impact_value",
        "intellectual_challenge",
        "financial_goal",
        "status_value",
        "freedom_value",
        "stability_value",
        "long_term_ambition",
    ],
    "adaptability": [
        "response_to_change",
        "willingness_to_revise",
        "uncertainty_tolerance",
        "setback_recovery",
        "transfer_to_unfamiliar_contexts",
    ],
}

# Flat set of all valid facet names, derived from the categories above.
ALL_FACETS: List[str] = [
    facet for facets in FACET_CATEGORIES.values() for facet in facets
]

FACET_TO_CATEGORY: Dict[str, str] = {
    facet: category
    for category, facets in FACET_CATEGORIES.items()
    for facet in facets
}


def is_valid_facet(name: str) -> bool:
    return name in FACET_TO_CATEGORY


# ---------------------------------------------------------------------------
# Enums
# ---------------------------------------------------------------------------

class Direction(str, Enum):
    SUPPORTS = "supports"
    CONTRADICTS = "contradicts"
    NEUTRAL = "neutral"


class Source(str, Enum):
    SELF_REPORT = "self_report"
    SCENARIO = "scenario"
    FORCED_CHOICE = "forced_choice"
    LIKERT = "likert"
    MINI_TASK = "mini_task"
    REFLECTION = "reflection"
    REAL_WORLD_EVIDENCE = "real_world_evidence"


# Contexts are intentionally a plain string, not a closed enum: the spec
# calls for extensibility, and context is descriptive metadata rather than
# something the engine branches on. This constant is documentation of the
# contexts used in this prototype's question set.
KNOWN_CONTEXTS = [
    "academic",
    "work",
    "personal",
    "hypothetical",
    "learning",
    "problem_solving",
    "social",
    "financial",
]


# ---------------------------------------------------------------------------
# Evidence
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class Evidence:
    """A single observation linking one answer to one facet.

    Evidence is never a personality score. It is a small, provenance-carrying
    claim: "this answer, on this question, is evidence of direction X on
    facet Y, with this strength, from this kind of source, in this context."
    """

    question_id: str
    answer_id: str
    facet: str
    direction: Direction
    strength: float  # 0..1
    source: Source
    reliability: float  # 0..1
    context: Optional[str] = None
    timestamp: float = 0.0

    def __post_init__(self):
        if not is_valid_facet(self.facet):
            raise ValueError(f"Unknown facet: {self.facet}")
        if not (0.0 <= self.strength <= 1.0):
            raise ValueError(f"strength must be in [0,1], got {self.strength}")
        if not (0.0 <= self.reliability <= 1.0):
            raise ValueError(f"reliability must be in [0,1], got {self.reliability}")


# ---------------------------------------------------------------------------
# Facet belief
# ---------------------------------------------------------------------------

@dataclass
class FacetBelief:
    """The engine's current belief state about one facet.

    support_strength and contradiction_strength are tracked separately and
    are NOT collapsed into a single "score". confidence is a separate axis
    describing how much we trust this belief, not how extreme it is.
    """

    facet: str
    support_strength: float = 0.0
    contradiction_strength: float = 0.0
    confidence: float = 0.0
    evidence_ids: List[int] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {
            "facet": self.facet,
            "support_strength": round(self.support_strength, 3),
            "contradiction_strength": round(self.contradiction_strength, 3),
            "confidence": round(self.confidence, 3),
            "evidence_count": len(self.evidence_ids),
        }


# ---------------------------------------------------------------------------
# User
# ---------------------------------------------------------------------------

@dataclass
class User:
    """Everything the assessment engine has accumulated about one person."""

    user_id: str
    evidence: List[Evidence] = field(default_factory=list)
    facet_beliefs: Dict[str, FacetBelief] = field(default_factory=dict)
    questions_asked: List[str] = field(default_factory=list)
    answers: List[Dict[str, str]] = field(default_factory=list)
    assessment_progress: Dict[str, object] = field(default_factory=dict)

    def get_belief(self, facet: str) -> FacetBelief:
        if facet not in self.facet_beliefs:
            self.facet_beliefs[facet] = FacetBelief(facet=facet)
        return self.facet_beliefs[facet]
