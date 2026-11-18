package com.recourse.api;

import com.recourse.explain.ExplanationService;
import com.recourse.service.AssessmentService;
import com.recourse.service.ContestService;
import com.recourse.service.RecommendationService;
import com.recourse.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class RecourseController {

    private final StudentService students;
    private final AssessmentService assessment;
    private final RecommendationService recommendations;
    private final ContestService contests;
    private final ExplanationService explanations;

    public RecourseController(StudentService students, AssessmentService assessment,
                              RecommendationService recommendations, ContestService contests,
                              ExplanationService explanations) {
        this.students = students;
        this.assessment = assessment;
        this.recommendations = recommendations;
        this.contests = contests;
        this.explanations = explanations;
    }

    @GetMapping("/system")
    public Views.SystemStatus status() { return students.status(); }

    // Students and their model

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.Student create(@Valid @RequestBody Requests.CreateStudent body) {
        return students.create(body.name(), body.stage());
    }

    /** Returning students identify themselves with name and return code. There is no student list. */
    @PostMapping("/students/continue")
    public Views.Student continueStudent(@Valid @RequestBody Requests.ContinueStudent body) {
        return students.continueWith(body.name(), body.code());
    }

    @GetMapping("/students/{id}")
    public Views.Student get(@PathVariable String id) { return students.get(id); }

    @GetMapping("/students/{id}/profile")
    public Views.Profile profile(@PathVariable String id) { return students.profile(id); }

    @GetMapping("/students/{id}/evidence")
    public List<Views.Evidence> evidence(@PathVariable String id) { return students.evidence(id); }

    // Assessment

    @PostMapping("/students/{id}/sessions")
    public ResponseEntity<Views.Question> start(@PathVariable String id) {
        Views.Question q = assessment.start(id, Set.of());
        return q == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(q);
    }

    @GetMapping("/sessions/{sessionId}/next")
    public ResponseEntity<Views.Question> next(@PathVariable String sessionId) {
        Views.Question q = assessment.next(sessionId);
        return q == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(q);
    }

    @PostMapping("/sessions/{sessionId}/answers")
    public Views.AnswerResult answer(@PathVariable String sessionId, @Valid @RequestBody Requests.Answer body) {
        return assessment.answer(sessionId, body.questionId(), body.optionId());
    }

    // Recommendations

    @GetMapping("/students/{id}/recommendations")
    public List<Views.Recommendation> recommendations(@PathVariable String id) {
        students.get(id);
        return recommendations.recommend(id);
    }

    @GetMapping("/students/{id}/recommendations/{careerId}")
    public Views.Recommendation recommendation(@PathVariable String id, @PathVariable String careerId) {
        students.get(id);
        return recommendations.recommendation(id, careerId);
    }

    @GetMapping("/students/{id}/recommendations/{careerId}/explanation")
    public Views.Explanation explanation(@PathVariable String id, @PathVariable String careerId) {
        students.get(id);
        return explanations.explain(id, careerId);
    }

    // Recourse

    @PostMapping("/students/{id}/contests/evidence")
    public Views.ContestResult contestEvidence(@PathVariable String id, @Valid @RequestBody Requests.ContestEvidence body) {
        students.get(id);
        return contests.contestEvidence(id, body.evidenceId(), body.reason());
    }

    @PostMapping("/students/{id}/contests/recommendation")
    public Views.ContestResult contestRecommendation(@PathVariable String id,
                                                     @Valid @RequestBody Requests.ContestRecommendation body) {
        students.get(id);
        return contests.contestRecommendation(id, body.careerId(), body.reasons(), body.concerns(), body.note());
    }

    @GetMapping("/students/{id}/contests")
    public List<Views.ContestOutcome> contestHistory(@PathVariable String id) {
        students.get(id);
        return contests.history(id);
    }
}
