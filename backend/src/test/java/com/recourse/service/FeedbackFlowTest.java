package com.recourse.service;

import com.recourse.api.Views;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pilot feedback: asked once after the first contest, one per student, exported without names. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:recourse-feedback-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class FeedbackFlowTest {

    @Autowired StudentService students;
    @Autowired AssessmentService assessment;
    @Autowired RecommendationService recommendations;
    @Autowired ContestService contests;
    @Autowired FeedbackService feedback;
    @Autowired MockMvc mvc;

    private String assessedStudent(String name) {
        String id = students.create(name, "CLASS_12").id();
        Views.Question q = assessment.start(id, Set.of());
        int guard = 0;
        while (q != null && guard++ < 50) {
            q = assessment.answer(q.sessionId(), q.id(), q.options().get(0).id()).next();
        }
        return id;
    }

    private Views.ContestResult contestTopMatch(String id) {
        Views.Recommendation top = recommendations.recommend(id).get(0);
        return contests.contestRecommendation(id, top.careerId(), List.of(top.reasons().get(0).attribute()), List.of(), null);
    }

    private static String ratings(Object a, Object b, Object c, String comment) {
        return "{\"matchesMadeSense\":" + a + ",\"explanationHelped\":" + b + ",\"pushingBackWorked\":" + c
                + (comment == null ? "" : ",\"comment\":\"" + comment + "\"") + "}";
    }

    @Test
    void askedAfterTheFirstContestOnly() {
        String id = assessedStudent("First contest");
        assertTrue(contestTopMatch(id).askForFeedback());
        assertFalse(contestTopMatch(id).askForFeedback(), "asked again on a second contest");
    }

    @Test
    void notAskedOnceFeedbackIsGiven() {
        String id = assessedStudent("Gives feedback");
        assertTrue(contestTopMatch(id).askForFeedback());
        feedback.submit(id, 4, 3, 5, "  Useful  ");
        assertTrue(feedback.given(id));
        assertFalse(feedback.shouldAsk(id));
    }

    @Test
    void ratingsMustBeOneToFiveAndAllPresent() throws Exception {
        String id = assessedStudent("Bad ratings");
        contestTopMatch(id);
        for (String body : List.of(ratings(0, 3, 3, null), ratings(3, 6, 3, null), ratings(3, 3, "null", null))) {
            mvc.perform(post("/api/v1/students/" + id + "/feedback").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertFalse(feedback.given(id));
    }

    @Test
    void onlyOneFeedbackPerStudent() throws Exception {
        String id = assessedStudent("Twice");
        contestTopMatch(id);
        String url = "/api/v1/students/" + id + "/feedback";
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(ratings(5, 5, 5, null)))
                .andExpect(status().isCreated());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(ratings(1, 1, 1, null)))
                .andExpect(status().isConflict());
        mvc.perform(get(url)).andExpect(jsonPath("$.given").value(true));
    }

    @Test
    void feedbackNeedsAContestFirst() throws Exception {
        String id = students.create("No contest", "CLASS_10").id();
        mvc.perform(post("/api/v1/students/" + id + "/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content(ratings(3, 3, 3, null)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/students/" + id + "/feedback")).andExpect(jsonPath("$.given").value(false));
    }

    @Test
    void exportIsCsvWithoutNamesAndEscapesComments() throws Exception {
        String id = assessedStudent("Priya Secretname");
        contestTopMatch(id);
        feedback.submit(id, 2, 4, 5, "=SUM(A1), said \"ok\"\nthen left");

        byte[] body = mvc.perform(get("/api/v1/feedback/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"recourse-feedback.csv\""))
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(body, StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("﻿" + FeedbackService.CSV_HEADER + "\r\n"), "missing BOM or header");
        assertFalse(csv.contains("Secretname"), "export contains a student's name");
        String row = csv.lines().filter(l -> l.startsWith("20") && l.contains(id.substring(0, 8))).findFirst().orElseThrow();
        assertTrue(row.contains(",CLASS_12,RECOMMENDATION,"), row);
        assertTrue(row.contains(",2,4,5,\"'=SUM(A1), said \"\"ok\"\""), row);
        assertTrue(csv.contains("then left\"\r\n"), "multi-line comment not kept inside quotes");
    }

    @Test
    void cellsAreEscapedForSpreadsheets() {
        assertEquals("plain", FeedbackService.cell("plain"));
        assertEquals("\"a, b\"", FeedbackService.cell("a, b"));
        assertEquals("\"say \"\"hi\"\"\"", FeedbackService.cell("say \"hi\""));
        assertEquals("'=1+1", FeedbackService.cell("=1+1"));
        assertEquals("'@cmd", FeedbackService.cell("@cmd"));
        assertEquals("\"line one\nline two\"", FeedbackService.cell("line one\nline two"));
        assertEquals("", FeedbackService.cell(""));
    }
}
