package com.recourse.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.recourse.engine.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class BeliefCalculatorTest {

    @Test
    void noEvidenceMeansNeutralAndUnsure() {
        Belief b = BeliefCalculator.compute(Attribute.SOCIAL, List.of());
        assertEquals(0, b.confidence());
        assertEquals(0.5, b.level(), 1e-9);
        assertFalse(b.contradictory());
    }

    @Test
    void singleSupportingItemSetsSupportToItsWeight() {
        Belief b = BeliefCalculator.compute(Attribute.SOCIAL, List.of(sup("1", Attribute.SOCIAL, 0.8)));
        assertEquals(0.8 * 0.7, b.support(), 1e-9);
        assertEquals(0, b.contradiction(), 1e-9);
        assertEquals(b.support(), b.confidence(), 1e-9);
    }

    @Test
    void repeatedEvidenceHasDiminishingReturns() {
        List<Evidence> ev = new ArrayList<>();
        double previous = 0, previousGain = 1;
        for (int i = 0; i < 5; i++) {
            ev.add(sup("e" + i, Attribute.INVESTIGATIVE, 0.6));
            double now = BeliefCalculator.compute(Attribute.INVESTIGATIVE, ev).support();
            double gain = now - previous;
            assertTrue(gain < previousGain, "each extra item should add less than the one before");
            previous = now;
            previousGain = gain;
        }
        assertTrue(previous < 1.0);
    }

    @Test
    void contradictionLowersConfidenceButKeepsSupport() {
        List<Evidence> ev = new ArrayList<>(List.of(
                sup("1", Attribute.ENTERPRISING, 0.8), sup("2", Attribute.ENTERPRISING, 0.7)));
        Belief before = BeliefCalculator.compute(Attribute.ENTERPRISING, ev);
        ev.add(con("3", Attribute.ENTERPRISING, 0.8));
        Belief after = BeliefCalculator.compute(Attribute.ENTERPRISING, ev);
        assertEquals(before.support(), after.support(), 1e-9, "support must not be erased by contradiction");
        assertTrue(after.contradiction() > 0);
        assertTrue(after.confidence() < before.confidence());
        assertTrue(after.contradictory());
    }

    @Test
    void oneSidedEvidenceIsNeverFlaggedAsContradictory() {
        List<Evidence> ev = List.of(con("1", Attribute.PACE, 0.9), con("2", Attribute.PACE, 0.9), con("3", Attribute.PACE, 0.9));
        Belief b = BeliefCalculator.compute(Attribute.PACE, ev);
        assertFalse(b.contradictory());
        assertTrue(b.level() < 0.5);
    }

    @Test
    void neutralEvidenceAddsCertaintyWithoutDirection() {
        Evidence neutral = ev("1", Attribute.VARIETY, Direction.NEUTRAL, 0.6, 0.6);
        Belief b = BeliefCalculator.compute(Attribute.VARIETY, List.of(neutral));
        assertEquals(0, b.support(), 1e-9);
        assertEquals(0, b.contradiction(), 1e-9);
        assertTrue(b.confidence() > 0);
        assertEquals(0.5, b.level(), 1e-9);
    }

    @Test
    void resultDoesNotDependOnEvidenceOrder() {
        List<Evidence> ev = new ArrayList<>(List.of(
                sup("1", Attribute.ARTISTIC, 0.8), con("2", Attribute.ARTISTIC, 0.4),
                sup("3", Attribute.ARTISTIC, 0.5), con("4", Attribute.ARTISTIC, 0.3)));
        Belief a = BeliefCalculator.compute(Attribute.ARTISTIC, ev);
        Collections.reverse(ev);
        Belief b = BeliefCalculator.compute(Attribute.ARTISTIC, ev);
        assertEquals(a.support(), b.support(), 1e-12);
        assertEquals(a.contradiction(), b.contradiction(), 1e-12);
        assertEquals(a.confidence(), b.confidence(), 1e-12);
    }

    @Test
    void evidenceForOtherAttributesIsIgnored() {
        Belief b = BeliefCalculator.compute(Attribute.SOCIAL, List.of(sup("1", Attribute.ARTISTIC, 0.9)));
        assertEquals(0, b.evidenceCount());
    }

    @Test
    void beliefKeepsTraceOfEveryEvidenceId() {
        List<Evidence> ev = List.of(sup("a", Attribute.VERBAL, 0.5), con("b", Attribute.VERBAL, 0.5), sup("c", Attribute.SPATIAL, 0.5));
        assertEquals(List.of("a", "b"), BeliefCalculator.compute(Attribute.VERBAL, ev).evidenceIds());
    }

    @Test
    void uncertainBeliefsArePulledTowardNeutral() {
        Belief weak = BeliefCalculator.compute(Attribute.SOCIAL, List.of(sup("1", Attribute.SOCIAL, 0.3)));
        Belief strong = BeliefCalculator.compute(Attribute.SOCIAL, List.of(
                sup("1", Attribute.SOCIAL, 0.9), sup("2", Attribute.SOCIAL, 0.9), sup("3", Attribute.SOCIAL, 0.9)));
        assertTrue(weak.level() > 0.5 && weak.level() < 0.6);
        assertTrue(strong.level() > 0.8);
    }

    /** The evidence sequence that was also run through the Python prototype v3. */
    private static List<Evidence> prototypeSequence() {
        List<Evidence> ev = new ArrayList<>();
        ev.add(ev("1", Attribute.INVESTIGATIVE, Direction.SUPPORTS, 0.8, 0.65));
        ev.add(ev("2", Attribute.INVESTIGATIVE, Direction.SUPPORTS, 0.6, 0.7));
        ev.add(ev("3", Attribute.INVESTIGATIVE, Direction.SUPPORTS, 0.8, 0.65));
        ev.add(ev("4", Attribute.INVESTIGATIVE, Direction.CONTRADICTS, 0.7, 0.5));
        return ev;
    }

    /**
     * Documents where the engine started. Prototype v3 used conflict = min(1, 2 x min(s, c)); the app
     * no longer does (see matchesBalancedConflictNumbers). Support, contradiction and mass are unchanged,
     * so the prototype's confidence can still be reproduced from them.
     */
    @Test
    void prototypeV3FormulaForReference() {
        List<Evidence> ev = prototypeSequence();
        Belief first3 = BeliefCalculator.compute(Attribute.INVESTIGATIVE, ev.subList(0, 3));
        assertEquals(0.866368, first3.support(), 1e-6);
        assertEquals(0.866368, first3.confidence(), 1e-6, "one-sided evidence: no conflict under either formula");

        Belief b = BeliefCalculator.compute(Attribute.INVESTIGATIVE, ev);
        assertEquals(0.866368, b.support(), 1e-6);
        assertEquals(0.35, b.contradiction(), 1e-6);
        double mass = BeliefCalculator.noisyOr(ev.stream().map(Evidence::weight).toList());
        double v3Conflict = Math.min(1, 2 * Math.min(b.support(), b.contradiction()));
        assertEquals(0.273942, mass * (1 - v3Conflict), 1e-6, "prototype v3 confidence");
    }

    /**
     * Same sequence with the balanced conflict formula. By hand:
     * mass = 1 - (0.48 x 0.58 x 0.48 x 0.65) = 0.913139;
     * conflict = 2 x 0.35 / (0.866368 + 0.35) = 0.575485;
     * confidence = 0.913139 x (1 - 0.575485) = 0.387642.
     */
    @Test
    void matchesBalancedConflictNumbers() {
        Belief b = BeliefCalculator.compute(Attribute.INVESTIGATIVE, prototypeSequence());
        assertEquals(0.387642, b.confidence(), 1e-6);
        assertTrue(b.contradictory());
    }

    @Test
    void noDirectionalEvidenceMeansNoConflict() {
        Belief b = BeliefCalculator.compute(Attribute.VARIETY, List.of(ev("1", Attribute.VARIETY, Direction.NEUTRAL, 0.6, 0.6)));
        assertEquals(0.36, b.confidence(), 1e-9, "confidence equals mass when there is no conflict");
        assertFalse(b.contradictory());
    }

    /** Two answers support an attribute, then the student disputes both, as ContestService does for a reason. */
    private static List<Evidence> disputedPair() {
        Evidence a = ev("1", Attribute.QUANTITATIVE, Direction.SUPPORTS, 0.8, 0.65);
        Evidence b = ev("2", Attribute.QUANTITATIVE, Direction.SUPPORTS, 0.7, 0.65);
        Supplier<String> ids = ids();
        return new ArrayList<>(List.of(a, b,
                EvidenceFactory.disputing(a, "Not me", ids, Instant.EPOCH),
                EvidenceFactory.disputing(b, "Not me", ids, Instant.EPOCH)));
    }

    @Test
    void disputeStillDropsConfidenceSharply() {
        List<Evidence> ev = disputedPair();
        Belief before = BeliefCalculator.compute(Attribute.QUANTITATIVE, ev.subList(0, 2));
        Belief after = BeliefCalculator.compute(Attribute.QUANTITATIVE, ev);
        assertTrue(after.confidence() < before.confidence() / 4,
                "confidence " + before.confidence() + " -> " + after.confidence());
        assertTrue(after.contradictory());
        assertEquals(before.support(), after.support(), 1e-12, "the disputed answers are still counted");
    }

    @Test
    void consistentFollowUpsRaiseConfidenceWithoutErasingTheDispute() {
        List<Evidence> ev = disputedPair();
        double beforeDispute = BeliefCalculator.compute(Attribute.QUANTITATIVE, ev.subList(0, 2)).confidence();
        Belief disputed = BeliefCalculator.compute(Attribute.QUANTITATIVE, ev);
        double previous = disputed.confidence();
        for (int i = 0; i < 3; i++) {
            ev.add(ev("f" + i, Attribute.QUANTITATIVE, Direction.SUPPORTS, 0.7, 0.65));
            Belief now = BeliefCalculator.compute(Attribute.QUANTITATIVE, ev);
            assertTrue(now.confidence() > previous, "follow-up " + (i + 1) + " did not raise confidence");
            assertTrue(now.confidence() < beforeDispute, "the dispute was fully erased");
            assertEquals(disputed.contradiction(), now.contradiction(), 1e-12, "the dispute's evidence must stay");
            assertTrue(now.contradictory());
            assertTrue(now.level() > 0.5);
            previous = now.confidence();
        }
    }

    @Test
    void computeAllCoversEveryAttribute() {
        Map<Attribute, Belief> all = BeliefCalculator.computeAll(List.of());
        assertEquals(Attribute.values().length, all.size());
    }

    @Test
    void evidenceRejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> sup("x", Attribute.SOCIAL, 1.5));
        assertThrows(IllegalArgumentException.class, () -> ev("x", Attribute.SOCIAL, Direction.SUPPORTS, 0.5, -0.1));
    }
}
