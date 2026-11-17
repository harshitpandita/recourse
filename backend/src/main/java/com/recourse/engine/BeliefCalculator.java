package com.recourse.engine;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Evidence -> belief. Pure, deterministic arithmetic (ported from prototype v3).
 *
 * Support and contradiction are accumulated separately with a noisy-OR:
 * each new item closes part of the remaining gap to 1.0, so repeated similar
 * answers have diminishing effect. Confidence is a separate axis: it grows
 * with the total amount of evidence and shrinks when the evidence disagrees
 * with itself.
 */
public final class BeliefCalculator {

    /** Conflict above this is flagged as a contradiction worth showing the student. */
    public static final double CONTRADICTION_FLAG = 0.25;

    private BeliefCalculator() {}

    static double noisyOr(Collection<Double> weights) {
        double acc = 0;
        for (double w : weights) {
            double clamped = Math.max(0, Math.min(1, w));
            acc = acc + clamped * (1 - acc);
        }
        return acc;
    }

    public static Belief compute(Attribute attribute, List<Evidence> allEvidence) {
        List<Evidence> relevant = allEvidence.stream().filter(e -> e.attribute() == attribute).toList();
        if (relevant.isEmpty()) return Belief.empty(attribute);

        double support = noisyOr(relevant.stream()
                .filter(e -> e.direction() == Direction.SUPPORTS).map(Evidence::weight).toList());
        double contradiction = noisyOr(relevant.stream()
                .filter(e -> e.direction() == Direction.CONTRADICTS).map(Evidence::weight).toList());
        double mass = noisyOr(relevant.stream().map(Evidence::weight).toList());

        double conflict = Math.min(1, Math.min(support, contradiction) * 2);
        double confidence = mass * (1 - conflict);

        return new Belief(
                attribute,
                support,
                contradiction,
                confidence,
                relevant.stream().map(Evidence::id).toList(),
                conflict >= CONTRADICTION_FLAG);
    }

    public static Map<Attribute, Belief> computeAll(List<Evidence> allEvidence) {
        Map<Attribute, Belief> beliefs = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.values()) beliefs.put(a, compute(a, allEvidence));
        return beliefs;
    }
}
