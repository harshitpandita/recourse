package com.recourse.service;

import com.recourse.api.Views;
import com.recourse.engine.Attribute;
import com.recourse.engine.Direction;
import com.recourse.engine.Question;
import com.recourse.engine.Source;
import com.recourse.store.ContestRepository;
import com.recourse.store.EvidenceRepository;
import com.recourse.store.StudentEntity;
import com.recourse.store.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The demo profile seeds three students through the real services, exactly once. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:recourse-demo-test;DB_CLOSE_DELAY=-1")
@ActiveProfiles("demo")
class DemoSeederTest {

    @Autowired DemoSeeder seeder;
    @Autowired StudentRepository studentRepo;
    @Autowired EvidenceRepository evidenceRepo;
    @Autowired ContestRepository contestRepo;
    @Autowired AssessmentService assessment;
    @Autowired RecommendationService recommendations;

    private String idOf(String name) {
        return studentRepo.findAll().stream().filter(s -> s.getName().equals(name))
                .map(StudentEntity::getId).findFirst().orElseThrow(() -> new AssertionError(name + " not seeded"));
    }

    @Test
    void seedsThreeAssessedStudentsAndOnlyMeeraHasAContest() {
        assertEquals(3, studentRepo.count());
        for (DemoSeeder.Persona p : DemoSeeder.PERSONAS) {
            String id = idOf(p.name());
            assertTrue(assessment.questionsAnswered(id) >= 12, p.name() + " answered too few questions");
            assertEquals(3, recommendations.recommend(id).size());
            int expectedContests = p.contestTopMatch() ? 1 : 0;
            assertEquals(expectedContests, contestRepo.findByStudentIdOrderByCreatedAtDesc(id).size(), p.name());
        }
    }

    @Test
    void personasHaveTheFixedCodesListedInTheReadme() {
        assertEquals(idOf("Aarav (demo)"), students.continueWith("Aarav (demo)", "DEM-AAR").id());
        assertEquals(idOf("Meera (demo)"), students.continueWith("meera (demo)", "dem-mra").id());
        assertEquals(idOf("Kabir (demo)"), students.continueWith("Kabir (demo)", "DEMKBR").id());
    }

    @Autowired StudentService students;

    @Test
    void runningAgainCreatesNothing() {
        long students = studentRepo.count();
        long evidence = evidenceRepo.count();
        long contests = contestRepo.count();
        assertEquals(0, seeder.seed());
        assertEquals(students, studentRepo.count());
        assertEquals(evidence, evidenceRepo.count());
        assertEquals(contests, contestRepo.count());
    }

    @Test
    void personasGetMatchesThatFitTheirLean() {
        assertTopThreeIncludesType("Aarav (demo)", 'I');
        assertTopThreeIncludesType("Meera (demo)", 'S');
        assertTopThreeIncludesType("Kabir (demo)", 'A');
    }

    @Autowired ContestService contests;
    @Autowired PlatformTransactionManager txManager;

    /**
     * Guards the live-demo script: on Aarav's top match (Data scientist), disputing
     * "Organising data and records would bore me" (conventional) puts a different
     * career first. Rolled back so the seeded state is untouched.
     */
    @Test
    void liveDemoContestOnAaravChangesHisTopMatch() {
        String id = idOf("Aarav (demo)");
        Views.Recommendation top = recommendations.recommend(id).get(0);
        assertEquals("data-scientist", top.careerId());
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            contests.contestRecommendation(id, top.careerId(), List.of("conventional"), List.of(), null);
            assertNotEquals("data-scientist", recommendations.recommend(id).get(0).careerId());
            status.setRollbackOnly();
        });
    }

    private void assertTopThreeIncludesType(String name, char riasec) {
        List<Views.Recommendation> top = recommendations.recommend(idOf(name));
        assertTrue(top.stream().anyMatch(r -> r.riasec().charAt(0) == riasec),
                name + " top matches: " + top.stream().map(Views.Recommendation::riasec).toList());
    }

    // The answer rule on its own

    private static Question.Option option(String id, Attribute a, Direction d, double strength) {
        return new Question.Option(id, id, List.of(new Question.EvidenceSpec(a, d, strength)));
    }

    private static Question question(Question.Option... options) {
        return new Question("t", "t", Source.FORCED_CHOICE, List.of(), 0.6, 1, List.of(), List.of(), List.of(options));
    }

    @Test
    void choosesTheOptionThatFitsTheLean() {
        Question q = question(
                option("a", Attribute.REALISTIC, Direction.SUPPORTS, 0.8),
                option("b", Attribute.ARTISTIC, Direction.SUPPORTS, 0.7));
        assertEquals("b", DemoSeeder.choose(q, Map.of(Attribute.ARTISTIC, 1.0)));
    }

    @Test
    void unmentionedAttributesAreMildlyAvoidedNotEndorsedByDefault() {
        // Neither option is mentioned: the weaker claim costs less, so "b" wins instead of defaulting to "a"
        Question q = question(
                option("a", Attribute.REALISTIC, Direction.SUPPORTS, 0.8),
                option("b", Attribute.CONVENTIONAL, Direction.SUPPORTS, 0.4));
        assertEquals("b", DemoSeeder.choose(q, Map.of(Attribute.ARTISTIC, 1.0)));
    }

    @Test
    void disagreeingWithADislikedAttributeScoresPositive() {
        Question q = question(
                option("a", Attribute.SOCIAL, Direction.SUPPORTS, 0.5),
                option("b", Attribute.STRUCTURE, Direction.CONTRADICTS, 0.6));
        assertEquals("b", DemoSeeder.choose(q, Map.of(Attribute.STRUCTURE, -0.5)));
    }
}
