"""Core data model for the evidence-based adaptive assessment prototype (v3).

This module defines the seven-category profile vocabulary and the plain-data
structures (Evidence, AttributeBelief, Person) used throughout the engine.
Nothing here computes anything - it is pure structure. See inference.py for
the belief-update math and question_selector.py for question selection.

v3 replaces the v2 47-facet vocabulary with the seven profile categories
described in PROJECT_CONTEXT.md:

    interests, abilities, work_characteristics, values,
    goals_aspirations, career_adaptability, knowledge_skills

The architecture (answer -> evidence -> belief update, support/contradiction
kept separate from confidence) is carried over from v2 unchanged; only the
vocabulary and category structure changed.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Dict, List, Optional


# ---------------------------------------------------------------------------
# Attribute vocabulary (the seven profile categories)
# ---------------------------------------------------------------------------
# This is the single configurable source of truth for what attributes exist
# and which of the seven categories they belong to. These are provisional,
# experimental categories (see PROJECT_CONTEXT.md section 6) - NOT a
# validated psychological instrument. career_adaptability's four attributes
# (concern/control/curiosity/confidence) reference Savickas' Career
# Adapt-Abilities construct, which has more external grounding than the
# others; the rest are working categories for this prototype only.

CATEGORIES: Dict[str, List[str]] = {
    "interests": [
        "technology",
        "science",
        "mathematics",
        "people",
        "business",
        "design",
        "information",
        "nature",
        "creative_expression",
        "practical_making",
    ],
    "abilities": [
        "quantitative_reasoning",
        "verbal_reasoning",
        "spatial_reasoning",
        "analytical_problem_solving",
        "creative_thinking",
        "pattern_recognition",
    ],
    "work_characteristics": [
        "autonomy_preference",
        "collaboration_preference",
        "structure_preference",
        "ambiguity_tolerance",
        "pace_preference",
        "variety_preference",
        "social_interaction_preference",
    ],
    "values": [
        "financial_reward",
        "security",
        "autonomy",
        "achievement",
        "impact",
        "status",
        "intellectual_challenge",
        "lifestyle",
        "freedom",
    ],
    # Goals & aspirations attributes describe patterns in how someone relates
    # to their own goals (clarity, orientation), not the goal content itself.
    # The literal free-text goals (e.g. "wants to study mechanical
    # engineering") are stored separately on Person.goals - see below.
    "goals_aspirations": [
        "career_direction_clarity",
        "education_direction_clarity",
        "short_term_goal_orientation",
        "long_term_goal_orientation",
        "ambition_level",
    ],
    # Savickas' Career Adapt-Abilities Scale (CAAS) constructs.
    "career_adaptability": [
        "concern",
        "control",
        "curiosity",
        "confidence",
    ],
    # Evidence-based indicators of learning/skill-building behavior, kept
    # separate from any claim of literal skill mastery (see
    # PROJECT_CONTEXT.md section 6: "Education alone should not
    # automatically be treated as evidence of mastery").
    "knowledge_skills": [
        "self_directed_learning",
        "practical_experience",
        "technical_skill_depth",
        "domain_knowledge_breadth",
        "skill_transfer_ability",
    ],
}

# Flat set of all valid attribute names, derived from the categories above.
ALL_ATTRIBUTES: List[str] = [
    attribute for attributes in CATEGORIES.values() for attribute in attributes
]

ATTRIBUTE_TO_CATEGORY: Dict[str, str] = {
    attribute: category
    for category, attributes in CATEGORIES.items()
    for attribute in attributes
}


def is_valid_attribute(name: str) -> bool:
    return name in ATTRIBUTE_TO_CATEGORY


def category_of(attribute: str) -> str:
    return ATTRIBUTE_TO_CATEGORY[attribute]


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
    RANKING = "ranking"
    MINI_TASK = "mini_task"
    REFLECTION = "reflection"
    REAL_WORLD_EVIDENCE = "real_world_evidence"


# Contexts are intentionally a plain string, not a closed enum: the spec
# calls for extensibility, and context is descriptive metadata rather than
# something the engine branches on. This constant documents the contexts
# used in this prototype's question set.
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
    """A single observation linking one answer to one profile attribute.

    Evidence is never a personality score. It is a small, provenance-carrying
    claim: "this answer, on this question, is evidence of direction X on
    attribute Y (in category C), with this strength, from this kind of
    source, in this context."
    """

    question_id: str
    answer_id: str
    category: str
    attribute: str
    direction: Direction
    strength: float  # 0..1
    source: Source
    reliability: float  # 0..1
    context: Optional[str] = None
    timestamp: float = 0.0

    def __post_init__(self):
        if not is_valid_attribute(self.attribute):
            raise ValueError(f"Unknown attribute: {self.attribute}")
        if self.category != category_of(self.attribute):
            raise ValueError(
                f"category {self.category!r} does not match attribute "
                f"{self.attribute!r} (expected {category_of(self.attribute)!r})"
            )
        if not (0.0 <= self.strength <= 1.0):
            raise ValueError(f"strength must be in [0,1], got {self.strength}")
        if not (0.0 <= self.reliability <= 1.0):
            raise ValueError(f"reliability must be in [0,1], got {self.reliability}")


# ---------------------------------------------------------------------------
# Attribute belief
# ---------------------------------------------------------------------------

@dataclass
class AttributeBelief:
    """The engine's current belief state about one profile attribute.

    support_strength and contradiction_strength are tracked separately and
    are NOT collapsed into a single "score". confidence is a separate axis
    describing how much we trust this belief, not how extreme it is.
    """

    attribute: str
    category: str
    support_strength: float = 0.0
    contradiction_strength: float = 0.0
    confidence: float = 0.0
    evidence_ids: List[int] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {
            "attribute": self.attribute,
            "category": self.category,
            "support_strength": round(self.support_strength, 3),
            "contradiction_strength": round(self.contradiction_strength, 3),
            "confidence": round(self.confidence, 3),
            "evidence_count": len(self.evidence_ids),
        }


# ---------------------------------------------------------------------------
# Person
# ---------------------------------------------------------------------------

# Free-text goal buckets. These hold literal aspirations expressed in
# natural language, kept separate from the evidence/belief machinery per
# PROJECT_CONTEXT.md section 6: "Goals are not personality traits and may
# change."
GOAL_FIELDS = [
    "career_direction",
    "education_direction",
    "short_term_goals",
    "long_term_goals",
    "ambitions",
]


@dataclass
class Person:
    """Everything the assessment engine has accumulated about one individual.

    Named Person (not User) to match the "Person model" language in
    PROJECT_CONTEXT.md section 6.
    """

    person_id: str
    evidence: List[Evidence] = field(default_factory=list)
    attribute_beliefs: Dict[str, AttributeBelief] = field(default_factory=dict)
    questions_asked: List[str] = field(default_factory=list)
    answers: List[Dict[str, str]] = field(default_factory=list)
    goals: Dict[str, List[str]] = field(
        default_factory=lambda: {f: [] for f in GOAL_FIELDS}
    )
    assessment_progress: Dict[str, object] = field(default_factory=dict)

    def get_belief(self, attribute: str) -> AttributeBelief:
        if attribute not in self.attribute_beliefs:
            self.attribute_beliefs[attribute] = AttributeBelief(
                attribute=attribute, category=category_of(attribute)
            )
        return self.attribute_beliefs[attribute]

    def beliefs_by_category(self, category: str) -> List[AttributeBelief]:
        return [
            self.attribute_beliefs[a]
            for a in CATEGORIES.get(category, [])
            if a in self.attribute_beliefs
        ]

    def add_goal_text(self, field_name: str, text: str) -> None:
        if field_name not in GOAL_FIELDS:
            raise ValueError(f"Unknown goal field: {field_name}")
        self.goals[field_name].append(text)
