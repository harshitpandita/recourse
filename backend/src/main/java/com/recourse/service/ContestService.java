package com.recourse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recourse.api.Views;
import com.recourse.engine.Attribute;
import com.recourse.engine.BeliefCalculator;
import com.recourse.engine.Direction;
import com.recourse.engine.Evidence;
import com.recourse.engine.EvidenceFactory;
import com.recourse.engine.Matcher;
import com.recourse.store.ContestEntity;
import com.recourse.store.ContestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Recourse mechanism. A student can dispute a single piece of evidence or a
 * whole recommendation. Either way the system:
 *   1. never deletes or edits what it already knows,
 *   2. records the objection as new evidence (source = CONTEST),
 *   3. recomputes beliefs and ranking, and logs the before/after,
 *   4. opens a short follow-up session to check the disputed areas again.
 */
@Service
public class ContestService {

    /** Strength of a direct statement when there is no specific evidence to dispute. */
    static final double STATEMENT_STRENGTH = 0.6;

    private final StudentModel model;
    private final RecommendationService recommendations;
    private final AssessmentService assessment;
    private final ContestRepository contests;
    private final ViewMapper views;
    private final ObjectMapper json;

    public ContestService(StudentModel model, RecommendationService recommendations, AssessmentService assessment,
                          ContestRepository contests, ViewMapper views, ObjectMapper json) {
        this.model = model;
        this.recommendations = recommendations;
        this.assessment = assessment;
        this.contests = contests;
        this.views = views;
        this.json = json;
    }

    /** "That answer doesn't describe me." */
    @Transactional
    public Views.ContestResult contestEvidence(String studentId, String evidenceId, String reason) {
        List<Evidence> before = model.evidence(studentId);
        Evidence target = before.stream().filter(e -> e.id().equals(evidenceId)).findFirst()
                .orElseThrow(() -> new NotFoundException("No evidence " + evidenceId));
        if (target.isContest()) throw new IllegalArgumentException("You can't dispute your own correction");
        if (before.stream().anyMatch(e -> evidenceId.equals(e.disputes()))) {
            throw new IllegalStateException("You've already disputed this");
        }
        Evidence counter = EvidenceFactory.disputing(target, clean(reason), Ids::next, Instant.now());
        return apply(studentId, "EVIDENCE", evidenceId, reason, before, List.of(counter));
    }

    /**
     * "This recommendation doesn't fit me, because..." The student picks which
     * reasons are wrong ({@code disputedReasons}) and which concerns are wrong
     * ({@code disputedConcerns}).
     */
    @Transactional
    public Views.ContestResult contestRecommendation(String studentId, String careerId,
                                                     List<String> disputedReasons, List<String> disputedConcerns,
                                                     String note) {
        List<Evidence> before = model.evidence(studentId);
        Matcher.Match match = recommendations.matchFor(studentId, careerId);
        Instant now = Instant.now();
        String why = clean(note);
        List<Evidence> created = new ArrayList<>();

        for (String key : safe(disputedReasons)) {
            Attribute a = Attribute.fromKey(key);
            Matcher.Contribution c = find(match.reasons(), a);
            // The career leaned on this attribute; the student says it's wrong.
            // Dispute the strongest evidence behind it, or state the opposite directly.
            Direction claimed = c.weight() >= 0 ? Direction.SUPPORTS : Direction.CONTRADICTS;
            List<Evidence> behind = before.stream()
                    .filter(e -> e.attribute() == a && e.direction() == claimed && !e.isContest())
                    .filter(e -> before.stream().noneMatch(x -> e.id().equals(x.disputes())))
                    .sorted((x, y) -> Double.compare(y.weight(), x.weight()))
                    .limit(2).toList();
            String text = why != null ? why : ContestPrompts.notMe(a);
            if (behind.isEmpty()) {
                created.add(EvidenceFactory.statement(a, claimed.opposite(), STATEMENT_STRENGTH, text, Ids::next, now));
            } else {
                behind.forEach(e -> created.add(EvidenceFactory.disputing(e, text, Ids::next, now)));
            }
        }
        for (String key : safe(disputedConcerns)) {
            Attribute a = Attribute.fromKey(key);
            Matcher.Contribution c = find(match.concerns(), a);
            // The concern said the student lacks (or has too much of) this. They disagree.
            Direction claimedNow = c.weight() >= 0 ? Direction.SUPPORTS : Direction.CONTRADICTS;
            String text = why != null ? why : ContestPrompts.concernWrong(a);
            created.add(EvidenceFactory.statement(a, claimedNow, STATEMENT_STRENGTH, text, Ids::next, now));
        }
        if (created.isEmpty()) throw new IllegalArgumentException("Pick at least one thing that's wrong");
        return apply(studentId, "RECOMMENDATION", careerId, note, before, created);
    }

    public List<Views.ContestOutcome> history(String studentId) {
        return contests.findByStudentIdOrderByCreatedAtDesc(studentId).stream().map(this::read).toList();
    }

    private Views.ContestResult apply(String studentId, String kind, String target, String reason,
                                      List<Evidence> before, List<Evidence> created) {
        model.append(studentId, null, created);
        List<Evidence> after = new ArrayList<>(before);
        after.addAll(created);

        Set<Attribute> touched = new LinkedHashSet<>();
        created.forEach(e -> touched.add(e.attribute()));

        String contestId = Ids.next();
        Instant now = Instant.now();
        Views.ContestOutcome outcome = new Views.ContestOutcome(contestId, kind, target, clean(reason),
                views.changes(BeliefCalculator.computeAll(before), BeliefCalculator.computeAll(after), touched),
                recommendations.ranking(before, 5), recommendations.ranking(after, 5),
                created.stream().map(Evidence::id).toList(), now);

        contests.save(new ContestEntity(contestId, studentId, kind, target, clean(reason),
                String.join(",", outcome.createdEvidence()), write(outcome), now));

        Views.Question followUp = assessment.start(studentId, touched);
        return new Views.ContestResult(outcome, followUp);
    }

    private static Matcher.Contribution find(List<Matcher.Contribution> list, Attribute a) {
        return list.stream().filter(c -> c.attribute() == a).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(a.label() + " isn't part of this recommendation"));
    }

    private static List<String> safe(List<String> l) { return l == null ? List.of() : l; }

    private static String clean(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.strip();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }

    private String write(Views.ContestOutcome o) {
        try { return json.writeValueAsString(o); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private Views.ContestOutcome read(ContestEntity c) {
        try { return json.readValue(c.getOutcomeJson(), Views.ContestOutcome.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
