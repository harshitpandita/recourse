package com.recourse.service;

import com.recourse.api.Views;
import com.recourse.explain.ExplanationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end through the real services and an in-memory database. */
@SpringBootTest
class RecourseFlowTest {

    @Autowired StudentService students;
    @Autowired AssessmentService assessment;
    @Autowired RecommendationService recommendations;
    @Autowired ContestService contests;
    @Autowired ExplanationService explanations;

    private String assessedStudent() {
        String id = students.create("Test student", "CLASS_12").id();
        Views.Question q = assessment.start(id, Set.of());
        int guard = 0;
        while (q != null && guard++ < 50) {
            // Always pick the first option: deterministic, and enough to build a profile
            q = assessment.answer(q.sessionId(), q.id(), q.options().get(0).id()).next();
        }
        return id;
    }

    @Test
    void assessmentRunsToCompletionWithinLimits() {
        String id = assessedStudent();
        int answered = assessment.questionsAnswered(id);
        assertTrue(answered >= 12 && answered <= 20, "answered " + answered);
        assertTrue(students.profile(id).evidenceCount() > answered);
    }

    @Test
    void recommendationsAreThreeAndEachHasAnEvidenceTrail() {
        String id = assessedStudent();
        List<Views.Recommendation> recs = recommendations.recommend(id);
        assertEquals(3, recs.size());
        for (Views.Recommendation r : recs) {
            assertFalse(r.evidence().isEmpty(), r.title() + " has no evidence trail");
            assertFalse(r.reasons().isEmpty(), r.title() + " has no reasons");
            r.evidence().forEach(e -> assertNotNull(e.questionText()));
        }
    }

    @Test
    void newStudentHasNoRecommendations() {
        String id = students.create("Fresh", "UNDERGRAD").id();
        assertTrue(recommendations.recommend(id).isEmpty());
    }

    @Test
    void answeringTheSameQuestionTwiceIsRejected() {
        String id = students.create("Twice", "CLASS_10").id();
        Views.Question q = assessment.start(id, Set.of());
        assessment.answer(q.sessionId(), q.id(), q.options().get(0).id());
        assertThrows(IllegalStateException.class, () -> assessment.answer(q.sessionId(), q.id(), q.options().get(0).id()));
    }

    @Test
    void contestingARecommendationLowersConfidenceKeepsEvidenceAndOpensFollowUp() {
        String id = assessedStudent();
        Views.Recommendation top = recommendations.recommend(id).get(0);
        String disputed = top.reasons().get(0).attribute();
        int evidenceBefore = students.profile(id).evidenceCount();

        Views.ContestResult result = contests.contestRecommendation(id, top.careerId(), List.of(disputed), List.of(), null);

        Views.BeliefChange change = result.outcome().changes().stream()
                .filter(c -> c.attribute().equals(disputed)).findFirst().orElseThrow();
        assertTrue(change.confidenceAfter() < change.confidenceBefore(), "confidence should drop");
        assertTrue(students.profile(id).evidenceCount() > evidenceBefore, "nothing is deleted, evidence only grows");
        assertEquals(1, contests.history(id).size());
        assertFalse(result.outcome().before().isEmpty());
        assertFalse(result.outcome().after().isEmpty());
        if (result.followUp() != null) {
            assertTrue(result.followUp().progress().followUp());
        }
    }

    @Test
    void disputingEvidenceLinksTheCounterEvidence() {
        String id = assessedStudent();
        Views.Evidence first = students.evidence(id).stream()
                .filter(e -> !"neutral".equals(e.direction())).findFirst().orElseThrow();
        contests.contestEvidence(id, first.id(), "That was a guess");
        Views.Evidence reloaded = students.evidence(id).stream().filter(e -> e.id().equals(first.id())).findFirst().orElseThrow();
        assertEquals(1, reloaded.disputedBy().size());
        assertThrows(IllegalStateException.class, () -> contests.contestEvidence(id, first.id(), "again"));
    }

    @Test
    void explanationFallsBackToTemplateWhenModelIsOff() {
        String id = assessedStudent();
        Views.Recommendation top = recommendations.recommend(id).get(0);
        Views.Explanation e = explanations.explain(id, top.careerId());
        assertEquals("template", e.generatedBy());
        assertTrue(e.text().contains(top.title()));
    }

    @Test
    void invalidStageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> students.create("X", "PHD"));
    }
}
