package com.recourse.engine;

import java.time.Instant;
import java.util.Objects;

/**
 * One immutable observation: "this answer (or this objection) is evidence of
 * {@code direction} on {@code attribute}, with this strength and reliability".
 *
 * Evidence is never edited or deleted. A contest adds new evidence instead,
 * which is why {@code disputes} can point at the evidence being disputed.
 */
public record Evidence(
        String id,
        Attribute attribute,
        Direction direction,
        double strength,
        double reliability,
        Source source,
        String questionId,
        String optionId,
        String context,
        String disputes,
        Instant recordedAt) {

    public Evidence {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(source, "source");
        if (strength < 0 || strength > 1) throw new IllegalArgumentException("strength must be in [0,1]: " + strength);
        if (reliability < 0 || reliability > 1) throw new IllegalArgumentException("reliability must be in [0,1]: " + reliability);
        if (recordedAt == null) recordedAt = Instant.EPOCH;
    }

    /** How much this single item moves the needle. */
    public double weight() {
        return strength * reliability;
    }

    public boolean isContest() {
        return source == Source.CONTEST;
    }
}
