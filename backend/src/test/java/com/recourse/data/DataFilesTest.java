package com.recourse.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recourse.engine.Attribute;
import com.recourse.engine.Career;
import com.recourse.engine.Pillar;
import com.recourse.engine.Question;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the hand-written data files against mistakes. */
class DataFilesTest {

    static QuestionBank bank;
    static CareerCatalog careers;

    @BeforeAll
    static void load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        bank = new QuestionBank(mapper);
        careers = new CareerCatalog(mapper);
    }

    @Test
    void questionIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        bank.all().forEach(q -> assertTrue(ids.add(q.id()), "duplicate question " + q.id()));
    }

    @Test
    void everyQuestionHasTargetsAndAtLeastTwoOptions() {
        for (Question q : bank.all()) {
            assertFalse(q.targets().isEmpty(), q.id() + " has no targets");
            assertTrue(q.options().size() >= 2, q.id() + " needs at least two options");
            assertTrue(q.reliability() > 0 && q.reliability() <= 1, q.id() + " reliability out of range");
        }
    }

    @Test
    void everyOptionProducesEvidence() {
        for (Question q : bank.all()) {
            for (Question.Option o : q.options()) {
                assertFalse(o.evidence().isEmpty(), q.id() + "/" + o.id() + " produces no evidence");
                o.evidence().forEach(e -> assertTrue(e.strength() > 0 && e.strength() <= 1));
            }
        }
    }

    @Test
    void everyAttributeIsMeasuredByAtLeastThreeOptions() {
        Map<Attribute, Integer> coverage = new EnumMap<>(Attribute.class);
        bank.all().forEach(q -> q.options().forEach(o -> o.evidence()
                .forEach(e -> coverage.merge(e.attribute(), 1, Integer::sum))));
        for (Attribute a : Attribute.values()) {
            assertTrue(coverage.getOrDefault(a, 0) >= 3, a + " is only measured " + coverage.getOrDefault(a, 0) + " times");
        }
    }

    @Test
    void careerIdsAreUniqueAndEveryCareerHasASource() {
        Set<String> ids = new HashSet<>();
        for (Career c : careers.all()) {
            assertTrue(ids.add(c.id()), "duplicate career " + c.id());
            assertFalse(c.sources().isEmpty(), c.id() + " has no source");
            assertEquals(3, c.riasec().length(), c.id() + " needs a three-letter Holland code");
        }
    }

    @Test
    void everyCareerCoversAllFourPillars() {
        for (Career c : careers.all()) {
            Set<Pillar> covered = new HashSet<>();
            c.weights().keySet().forEach(a -> covered.add(a.pillar()));
            assertEquals(Set.of(Pillar.values()), covered, c.id() + " is missing a pillar");
        }
    }

    @Test
    void everyRiasecTypeHasAtLeastOneCareer() {
        Set<Character> types = new HashSet<>();
        careers.all().forEach(c -> types.add(c.primaryType()));
        for (char t : "RIASEC".toCharArray()) assertTrue(types.contains(t), "no career with primary type " + t);
    }
}
