"""
user_model.py

Defines the in-memory representation of what the system currently believes
about a person: one score/confidence/evidence trio per dimension, plus
bookkeeping about the assessment itself (questions asked, answers given,
progress).

This module intentionally knows nothing about scoring math or question
selection - it only stores state and exposes small, predictable accessors.
Keeping it dumb makes it easy to add/remove dimensions later (see DIMENSIONS
below) without touching scoring or selection logic.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional


# The nine provisional research dimensions this prototype tracks.
# These are NOT validated psychological constructs - they are working
# labels for the kind of evidence we want to collect. Adding/removing a
# dimension only requires editing this list plus the question bank; the
# rest of the system (model, scorer, selector) is dimension-agnostic.
DIMENSIONS: List[str] = [
    "reasoning",
    "learning",
    "curiosity",
    "creativity",
    "execution",
    "motivation",
    "work_preferences",
    "values_ambition",
    "adaptability",
]

# Starting point for every dimension before any evidence exists.
DEFAULT_SCORE = 50.0
DEFAULT_CONFIDENCE = 0.0


@dataclass
class Evidence:
    """A single observation contributing to one dimension's belief state."""

    question_id: str
    answer: str
    evidence_score: float  # 0-100, what this single answer implies
    reliability: float     # 0-1, how much we trust this question's signal
    timestamp: str          # ISO-8601 string; simulator/CLI supply the clock


@dataclass
class DimensionModel:
    """Current belief state for a single dimension."""

    name: str
    score: float = DEFAULT_SCORE
    confidence: float = DEFAULT_CONFIDENCE
    evidence: List[Evidence] = field(default_factory=list)

    @property
    def evidence_count(self) -> int:
        return len(self.evidence)

    def to_dict(self) -> dict:
        return {
            "dimension": self.name,
            "score": round(self.score, 1),
            "confidence": round(self.confidence, 2),
            "evidence_count": self.evidence_count,
        }


class UserModel:
    """
    Full belief state for one person's assessment, plus process bookkeeping.

    This class is a container: it does not decide *how* scores or
    confidence change (that's scorer.py) and it does not decide *what*
    question to ask next (that's question_selector.py). It only records
    what happened and exposes it for those modules to read/update.
    """

    def __init__(self, dimensions: Optional[List[str]] = None):
        self.dimension_names: List[str] = list(dimensions or DIMENSIONS)
        self.dimensions: Dict[str, DimensionModel] = {
            name: DimensionModel(name=name) for name in self.dimension_names
        }
        self.questions_asked: List[str] = []
        self.answers: Dict[str, str] = {}

    def get_dimension(self, name: str) -> DimensionModel:
        if name not in self.dimensions:
            raise KeyError(f"Unknown dimension: {name}")
        return self.dimensions[name]

    def record_question_asked(self, question_id: str) -> None:
        self.questions_asked.append(question_id)

    def record_answer(self, question_id: str, answer: str) -> None:
        self.answers[question_id] = answer

    def add_evidence(self, dimension_name: str, evidence: Evidence) -> None:
        self.get_dimension(dimension_name).evidence.append(evidence)

    @property
    def progress(self) -> dict:
        """A cheap summary of how far the assessment has gotten."""
        return {
            "questions_asked": len(self.questions_asked),
            "average_confidence": round(
                sum(d.confidence for d in self.dimensions.values())
                / len(self.dimensions),
                3,
            ),
        }

    def profile_summary(self) -> List[dict]:
        """Per-dimension score/confidence/evidence_count, for display."""
        return [self.dimensions[name].to_dict() for name in self.dimension_names]

    def low_confidence_dimensions(self, threshold: float) -> List[str]:
        """Dimensions still below `threshold` confidence, in fixed order."""
        return [
            name
            for name in self.dimension_names
            if self.dimensions[name].confidence < threshold
        ]
