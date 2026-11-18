package com.recourse.evaluation;

import com.recourse.engine.Attribute;
import com.recourse.engine.Direction;
import com.recourse.engine.Pillar;
import com.recourse.engine.Question;
import com.recourse.engine.Source;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the measuring code used by the simulation evaluation. Runs in normal mvn test. */
class EvaluationHelpersTest {

    @Test
    void statisticsMatchHandWorkedValues() {
        assertEquals(1.0, Stats.pearson(List.of(1.0, 2.0, 3.0), List.of(2.0, 4.0, 6.0)), 1e-9);
        assertEquals(-1.0, Stats.pearson(List.of(1.0, 2.0, 3.0), List.of(3.0, 2.0, 1.0)), 1e-9);
        assertTrue(Double.isNaN(Stats.pearson(List.of(1.0, 2.0), List.of(5.0, 5.0))));
        assertEquals(0.5, Stats.mae(List.of(0.0, 1.0), List.of(0.5, 0.5)), 1e-9);
        assertEquals(2.0 / 4, Stats.jaccard(Set.of("a", "b", "c"), Set.of("b", "c", "d")), 1e-9);
        assertEquals(1, Stats.firstDifference(List.of("x"), List.of("y")));
        assertEquals(3, Stats.firstDifference(List.of("a", "b", "c"), List.of("a", "b", "d")));
        assertEquals(2.0 / 3, Stats.overlap(List.of("a", "b", "c"), List.of("c", "a", "z")), 1e-9);
        // 3 random careers out of 25, 2 of them in the family: 1 - C(23,3)/C(25,3)
        assertEquals(1 - 1771.0 / 2300, Stats.chanceTop3(25, 2), 1e-9);
    }

    @Test
    void personasAreRepeatableAndCoverEveryFamily() {
        List<Persona> a = Persona.generate(30, 7), b = Persona.generate(30, 7);
        assertEquals(a, b);
        for (char letter : Persona.LETTERS.toCharArray()) {
            assertEquals(5, a.stream().filter(p -> p.primary() == letter).count());
        }
        for (Persona p : a) {
            Attribute top = Attribute.of(Pillar.INTERESTS).stream()
                    .max((x, y) -> Double.compare(p.lean().get(x), p.lean().get(y))).orElseThrow();
            assertEquals(p.primary(), top.name().charAt(0), p.id() + "'s primary letter isn't its strongest interest");
            assertNotEquals(p.primary(), p.secondary());
            p.lean().values().forEach(v -> assertTrue(v >= -0.8 && v <= 0.8));
        }
    }

    @Test
    void honestAnswerIsTheOptionThatBestDescribesThePersona() {
        Persona p = Persona.generate(1, 1).get(0); // primary R
        Question q = new Question("t1", "Pick one", Source.FORCED_CHOICE, List.of(), 0.8, 1,
                List.of(Attribute.REALISTIC), List.of(), List.of(
                new Question.Option("a", "Not hands-on", List.of(
                        new Question.EvidenceSpec(Attribute.REALISTIC, Direction.CONTRADICTS, 0.6))),
                new Question.Option("b", "Hands-on", List.of(
                        new Question.EvidenceSpec(Attribute.REALISTIC, Direction.SUPPORTS, 0.6)))));
        assertEquals("b", p.honestAnswer(q));
        assertEquals(new Persona.Answer("b", false), p.answer(q, 0, new Random(1)));
        long random = 0;
        Random rng = new Random(3);
        for (int i = 0; i < 1000; i++) if (p.answer(q, 0.2, rng).random()) random++;
        assertTrue(random > 150 && random < 250, "noise rate far from 20%: " + random);
    }
}
