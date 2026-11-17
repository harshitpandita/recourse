package com.recourse.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.recourse.engine.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class MatcherTest {

    private final Career scientist = career("scientist", "IRC", Map.of(Attribute.ANALYTICAL, 0.9, Attribute.AUTONOMY, 0.5));
    private final Career teacher = career("teacher", "SAE", Map.of(Attribute.VERBAL, 0.8, Attribute.COLLABORATION, 0.5));
    private final Career founder = career("founder", "ECA", Map.of(Attribute.INDEPENDENCE, 0.9, Attribute.STRUCTURE, -0.5));
    private final Career marketer = career("marketer", "EAC", Map.of(Attribute.VERBAL, 0.6));
    private final Career consultant = career("consultant", "EIC", Map.of(Attribute.ANALYTICAL, 0.8));
    private final List<Career> all = List.of(scientist, teacher, founder, marketer, consultant);

    private static List<Evidence> student(Object... pairs) {
        List<Evidence> ev = new ArrayList<>();
        int n = 0;
        for (int i = 0; i < pairs.length; i += 2) {
            Attribute a = (Attribute) pairs[i];
            double lean = (double) pairs[i + 1];
            for (int k = 0; k < 3; k++) {
                ev.add(lean >= 0 ? sup("e" + (n++), a, Math.abs(lean)) : con("e" + (n++), a, Math.abs(lean)));
            }
        }
        return ev;
    }

    @Test
    void investigativeStudentGetsScientistFirst() {
        List<Evidence> ev = student(Attribute.INVESTIGATIVE, 0.9, Attribute.ANALYTICAL, 0.9, Attribute.SOCIAL, -0.6);
        var ranked = Matcher.rankAll(all, BeliefCalculator.computeAll(ev), ev);
        assertEquals("scientist", ranked.get(0).career().id());
    }

    @Test
    void socialStudentGetsTeacherFirst() {
        List<Evidence> ev = student(Attribute.SOCIAL, 0.9, Attribute.VERBAL, 0.8, Attribute.INVESTIGATIVE, -0.5);
        var ranked = Matcher.rankAll(all, BeliefCalculator.computeAll(ev), ev);
        assertEquals("teacher", ranked.get(0).career().id());
    }

    @Test
    void scoresStayBetweenZeroAndOne() {
        List<Evidence> ev = student(Attribute.ENTERPRISING, 0.9, Attribute.STRUCTURE, 0.9);
        for (var m : Matcher.rankAll(all, BeliefCalculator.computeAll(ev), ev)) {
            assertTrue(m.score() >= 0 && m.score() <= 1);
            assertTrue(m.confidence() >= 0 && m.confidence() <= 1);
        }
    }

    @Test
    void noEvidenceGivesNeutralScoresAndZeroConfidence() {
        var ranked = Matcher.rankAll(all, BeliefCalculator.computeAll(List.of()), List.of());
        for (var m : ranked) {
            assertEquals(0.5, m.score(), 1e-9);
            assertEquals(0, m.confidence(), 1e-9);
            assertTrue(m.reasons().isEmpty());
        }
    }

    @Test
    void negativeWeightPenalisesTheAttribute() {
        List<Evidence> likesStructure = student(Attribute.ENTERPRISING, 0.8, Attribute.STRUCTURE, 0.9);
        List<Evidence> dislikesStructure = student(Attribute.ENTERPRISING, 0.8, Attribute.STRUCTURE, -0.9);
        double a = Matcher.match(founder, BeliefCalculator.computeAll(likesStructure), Map.of()).score();
        double b = Matcher.match(founder, BeliefCalculator.computeAll(dislikesStructure), Map.of()).score();
        assertTrue(b > a);
    }

    @Test
    void concernsAppearWhenTheStudentLacksWhatTheCareerNeeds() {
        List<Evidence> ev = student(Attribute.INVESTIGATIVE, 0.9, Attribute.ANALYTICAL, -0.9);
        var m = Matcher.match(scientist, BeliefCalculator.computeAll(ev), Map.of());
        assertTrue(m.concerns().stream().anyMatch(c -> c.attribute() == Attribute.ANALYTICAL));
    }

    @Test
    void evidenceTrailOnlyContainsAgreeingEvidence() {
        List<Evidence> ev = new ArrayList<>(student(Attribute.INVESTIGATIVE, 0.9, Attribute.ANALYTICAL, 0.8));
        ev.add(con("against", Attribute.INVESTIGATIVE, 0.2));
        var byId = new java.util.HashMap<String, Evidence>();
        ev.forEach(e -> byId.put(e.id(), e));
        var m = Matcher.match(scientist, BeliefCalculator.computeAll(ev), byId);
        assertFalse(m.evidenceIds().isEmpty());
        assertFalse(m.evidenceIds().contains("against"));
        for (String id : m.evidenceIds()) assertEquals(Direction.SUPPORTS, byId.get(id).direction());
    }

    @Test
    void recommendationsAreMeaningfullyDifferent() {
        // An enterprising student: three E careers rank high, but the picks should not all be E-type
        // unless they clearly beat the alternatives.
        List<Evidence> ev = student(Attribute.ENTERPRISING, 0.5, Attribute.ANALYTICAL, 0.7, Attribute.INVESTIGATIVE, 0.4);
        var picks = Matcher.recommend(all, BeliefCalculator.computeAll(ev), ev, 3);
        assertEquals(3, picks.size());
        long types = picks.stream().map(p -> p.career().primaryType()).distinct().count();
        assertTrue(types >= 2);
    }

    @Test
    void recommendReturnsNoDuplicates() {
        List<Evidence> ev = student(Attribute.SOCIAL, 0.5);
        var picks = Matcher.recommend(all, BeliefCalculator.computeAll(ev), ev, 5);
        assertEquals(5, picks.stream().map(p -> p.career().id()).distinct().count());
    }

    @Test
    void contestingTheMainReasonLowersThatCareer() {
        List<Evidence> ev = new ArrayList<>(student(Attribute.ENTERPRISING, 0.9, Attribute.INDEPENDENCE, 0.8));
        double before = Matcher.match(founder, BeliefCalculator.computeAll(ev), Map.of()).score();
        var ids = ids();
        for (Evidence e : List.copyOf(ev)) {
            if (e.attribute() == Attribute.ENTERPRISING) ev.add(EvidenceFactory.disputing(e, "not me", ids, Instant.EPOCH));
        }
        double after = Matcher.match(founder, BeliefCalculator.computeAll(ev), Map.of()).score();
        assertTrue(after < before);
    }

    @Test
    void riasecLettersAreWeightedByPosition() {
        Map<Attribute, Double> w = Career.interestWeights("ISA");
        assertEquals(1.0, w.get(Attribute.INVESTIGATIVE));
        assertEquals(0.7, w.get(Attribute.SOCIAL));
        assertEquals(0.45, w.get(Attribute.ARTISTIC));
        assertThrows(IllegalArgumentException.class, () -> Career.fromLetter('X'));
    }
}
