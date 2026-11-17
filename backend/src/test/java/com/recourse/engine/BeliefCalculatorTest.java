package com.recourse.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    @Test
    void matchesPrototypeV3Numbers() {
        // Same sequence was run through the Python prototype; values must agree.
        List<Evidence> ev = new ArrayList<>();
        ev.add(ev("1", Attribute.INVESTIGATIVE, Direction.SUPPORTS, 0.8, 0.65));
        ev.add(ev("2", Attribute.INVESTIGATIVE, Direction.SUPPORTS, 0.6, 0.7));
        ev.add(ev("3", Attribute.INVESTIGATIVE, Direction.SUPPORTS, 0.8, 0.65));
        Belief b = BeliefCalculator.compute(Attribute.INVESTIGATIVE, ev);
        assertEquals(0.866368, b.support(), 1e-6);
        assertEquals(0.866368, b.confidence(), 1e-6);
        ev.add(ev("4", Attribute.INVESTIGATIVE, Direction.CONTRADICTS, 0.7, 0.5));
        b = BeliefCalculator.compute(Attribute.INVESTIGATIVE, ev);
        assertEquals(0.35, b.contradiction(), 1e-6);
        assertEquals(0.273942, b.confidence(), 1e-6);
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
