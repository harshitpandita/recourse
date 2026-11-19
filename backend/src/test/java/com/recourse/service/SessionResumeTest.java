package com.recourse.service;

import com.recourse.api.Views;
import com.recourse.engine.QuestionSelector;
import com.recourse.store.SessionEntity;
import com.recourse.store.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Leaving or reloading the page must never restart the questions or lose a follow-up. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:recourse-resume-test;DB_CLOSE_DELAY=-1")
class SessionResumeTest {

    @Autowired StudentService students;
    @Autowired AssessmentService assessment;
    @Autowired RecommendationService recommendations;
    @Autowired ContestService contests;
    @Autowired SessionRepository sessions;

    private static Views.Question answer(AssessmentService assessment, Views.Question q) {
        return assessment.answer(q.sessionId(), q.id(), q.options().get(0).id()).next();
    }

    private String finishedStudent(String name) {
        String id = students.create(name, "CLASS_12").id();
        Views.Question q = assessment.start(id, Set.of());
        while (q != null) q = answer(assessment, q);
        return id;
    }

    @Test
    void startingAgainMidwayContinuesTheSameSessionAndCount() {
        String id = students.create("Halfway", "CLASS_10").id();
        Views.Question q = assessment.start(id, Set.of());
        for (int i = 0; i < 5; i++) q = answer(assessment, q);
        assertEquals(5, q.progress().asked());

        Views.Question resumed = assessment.start(id, Set.of());
        assertEquals(q.sessionId(), resumed.sessionId(), "a reload started a new session");
        assertEquals(q.id(), resumed.id(), "the same question should be waiting");
        assertEquals(5, resumed.progress().asked(), "the count went back to the start");
        assertFalse(students.get(id).assessmentComplete());
    }

    /** Two "start" requests at once (React runs effects twice in development) leave an empty twin session. */
    @Test
    void anEmptyDuplicateSessionIsDroppedAndNeverCountsAsFinishing() {
        String id = students.create("Twin", "CLASS_12").id();
        Views.Question q = assessment.start(id, Set.of());
        for (int i = 0; i < 3; i++) q = answer(assessment, q);
        SessionEntity twin = sessions.save(new SessionEntity("twin-" + id, id, Instant.now().plusSeconds(1), null));

        Views.Question resumed = assessment.start(id, Set.of());
        assertEquals(q.sessionId(), resumed.sessionId(), "resumed the empty twin instead of the real session");
        assertEquals(3, resumed.progress().asked());
        assertFalse(sessions.existsById(twin.getId()), "the empty twin should be removed");
        assertFalse(students.get(id).assessmentComplete(), "removing the twin must not mark the assessment finished");
    }

    @Test
    void theMainAssessmentStillStopsAtTheCapAcrossReloads() {
        String id = students.create("Reloader", "CLASS_12").id();
        Views.Question q = assessment.start(id, Set.of());
        int answered = 0;
        while (q != null) {
            q = answer(assessment, q);
            answered++;
            if (q != null && answered % 4 == 0) q = assessment.start(id, Set.of()); // reload every few answers
        }
        assertTrue(answered <= QuestionSelector.MAX_QUESTIONS, "asked " + answered);
        assertEquals(answered, assessment.questionsAnswered(id));
    }

    @Test
    void nothingIsAskedOnceTheAssessmentIsFinished() {
        String id = finishedStudent("Finished");
        assertTrue(students.get(id).assessmentComplete());
        assertNull(assessment.start(id, Set.of()), "a finished student was asked to start again");
        assertNull(assessment.start(id, Set.of()), "visiting the questions page twice must not open new sessions");
    }

    @Test
    void anUnfinishedFollowUpIsPickedUpAfterAReload() {
        String id = finishedStudent("Follow-up");
        Views.Recommendation top = recommendations.recommend(id).get(0);
        Views.ContestResult result = contests.contestRecommendation(id, top.careerId(),
                List.of(top.reasons().get(0).attribute()), List.of(), null);
        assertNotNull(result.followUp());

        Views.Question resumed = assessment.start(id, Set.of());
        assertEquals(result.followUp().sessionId(), resumed.sessionId());
        assertTrue(resumed.progress().followUp(), "should still be shown as a follow-up");
    }

    @Test
    void aNewObjectionReplacesAnOlderUnfinishedFollowUp() {
        String id = finishedStudent("Two objections");
        List<Views.Recommendation> recs = recommendations.recommend(id);
        Views.ContestResult first = contests.contestRecommendation(id, recs.get(0).careerId(),
                List.of(recs.get(0).reasons().get(0).attribute()), List.of(), null);
        Views.Recommendation other = recommendations.recommend(id).get(1);
        Views.ContestResult second = contests.contestRecommendation(id, other.careerId(),
                List.of(other.reasons().get(0).attribute()), List.of(), null);
        assertNotNull(first.followUp());
        assertNotNull(second.followUp());

        Views.Question q = assessment.start(id, Set.of());
        assertEquals(second.followUp().sessionId(), q.sessionId());
        while (q != null) q = answer(assessment, q);
        assertNull(assessment.start(id, Set.of()), "the older, replaced follow-up came back");
    }
}
