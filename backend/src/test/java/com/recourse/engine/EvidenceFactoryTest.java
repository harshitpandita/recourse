package com.recourse.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.recourse.engine.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceFactoryTest {

    private final Question q = new Question("q1", "Pick one", Source.SCENARIO, List.of("work"), 0.6, 1,
            List.of(Attribute.SOCIAL), List.of(),
            List.of(new Question.Option("a", "Help people", List.of(
                            new Question.EvidenceSpec(Attribute.SOCIAL, Direction.SUPPORTS, 0.8),
                            new Question.EvidenceSpec(Attribute.RELATIONSHIPS, Direction.SUPPORTS, 0.4))),
                    new Question.Option("b", "Work alone", List.of(
                            new Question.EvidenceSpec(Attribute.SOCIAL, Direction.CONTRADICTS, 0.5)))));

    @Test
    void answerProducesOneItemPerEvidenceSpec() {
        List<Evidence> ev = EvidenceFactory.fromAnswer(q, "a", ids(), Instant.EPOCH);
        assertEquals(2, ev.size());
        assertEquals(Attribute.SOCIAL, ev.get(0).attribute());
        assertEquals(0.6, ev.get(0).reliability(), 1e-9, "reliability comes from the question");
        assertEquals("q1", ev.get(0).questionId());
        assertEquals("a", ev.get(0).optionId());
        assertEquals("work", ev.get(0).context());
        assertEquals(Source.SCENARIO, ev.get(0).source());
    }

    @Test
    void unknownOptionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EvidenceFactory.fromAnswer(q, "z", ids(), Instant.EPOCH));
    }

    @Test
    void disputingCreatesOppositeEvidenceLinkedToTheOriginal() {
        Evidence original = sup("orig", Attribute.SOCIAL, 0.8);
        Evidence counter = EvidenceFactory.disputing(original, "Not me", ids(), Instant.EPOCH);
        assertEquals(Direction.CONTRADICTS, counter.direction());
        assertEquals(original.attribute(), counter.attribute());
        assertEquals(original.strength(), counter.strength(), 1e-9);
        assertEquals("orig", counter.disputes());
        assertEquals(Source.CONTEST, counter.source());
        assertEquals("Not me", counter.context());
        assertTrue(counter.isContest());
    }

    @Test
    void neutralEvidenceCannotBeDisputed() {
        Evidence neutral = ev("n", Attribute.SOCIAL, Direction.NEUTRAL, 0.5, 0.5);
        assertThrows(IllegalArgumentException.class, () -> EvidenceFactory.disputing(neutral, null, ids(), Instant.EPOCH));
    }

    @Test
    void disputeMakesBeliefContradictoryInsteadOfFlippingIt() {
        Evidence original = sup("orig", Attribute.ENTERPRISING, 0.8);
        Evidence counter = EvidenceFactory.disputing(original, null, ids(), Instant.EPOCH);
        Belief before = BeliefCalculator.compute(Attribute.ENTERPRISING, List.of(original));
        Belief after = BeliefCalculator.compute(Attribute.ENTERPRISING, List.of(original, counter));
        assertEquals(before.support(), after.support(), 1e-9);
        assertTrue(after.confidence() < before.confidence());
        assertTrue(after.contradictory());
    }

    @Test
    void statementIsAContestWithNoQuestion() {
        Evidence s = EvidenceFactory.statement(Attribute.PACE, Direction.CONTRADICTS, 0.6, "Too stressful", ids(), Instant.EPOCH);
        assertNull(s.questionId());
        assertNull(s.disputes());
        assertEquals(Source.CONTEST, s.source());
        assertEquals(EvidenceFactory.CONTEST_RELIABILITY, s.reliability(), 1e-9);
    }
}
