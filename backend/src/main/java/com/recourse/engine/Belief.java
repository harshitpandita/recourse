package com.recourse.engine;

import java.util.List;

/**
 * The engine's current view of one attribute, always derived from evidence.
 *
 * @param support       accumulated supporting evidence, 0..1
 * @param contradiction accumulated contradicting evidence, 0..1
 * @param confidence    how much the engine trusts this belief, 0..1 (separate from direction)
 * @param evidenceIds   every evidence item that fed into it, so the belief is traceable
 * @param contradictory true when support and contradiction are both substantial
 */
public record Belief(
        Attribute attribute,
        double support,
        double contradiction,
        double confidence,
        List<String> evidenceIds,
        boolean contradictory) {

    public static Belief empty(Attribute attribute) {
        return new Belief(attribute, 0, 0, 0, List.of(), false);
    }

    /** Net lean from -1 (clearly not) to +1 (clearly yes). */
    public double lean() {
        return support - contradiction;
    }

    /**
     * The level used for matching, 0..1. Uncertain beliefs are pulled toward
     * the neutral 0.5, so a single lucky answer cannot dominate a recommendation.
     */
    public double level() {
        return 0.5 + 0.5 * lean() * confidence;
    }

    public int evidenceCount() {
        return evidenceIds.size();
    }
}
