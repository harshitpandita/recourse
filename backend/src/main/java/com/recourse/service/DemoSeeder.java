package com.recourse.service;

import com.recourse.api.Views;
import com.recourse.data.QuestionBank;
import com.recourse.engine.Attribute;
import com.recourse.engine.Direction;
import com.recourse.engine.Question;
import com.recourse.store.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.recourse.engine.Attribute.*;

/**
 * Demo data, active only with the "demo" Spring profile.
 *
 * Creates three simulated students and runs each one through the real adaptive
 * assessment, so their evidence is produced exactly as it would be for a real
 * student. Because the assessment is adaptive, we can't script a fixed list of
 * answers; instead each persona has a stated lean, and {@link #choose} picks the
 * option that best fits that lean for whatever question the selector asks.
 * Selector and rule are both deterministic, so every run produces the same students.
 *
 * Each persona is skipped if a student with its name already exists.
 */
@Component
@Profile("demo")
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    /**
     * Weight for attributes a persona doesn't mention. Slightly negative so that,
     * between options the persona has no view on, it doesn't endorse one by default.
     */
    static final double UNMENTIONED_WEIGHT = -0.15;

    /** Safety net: the selector stops well before this. */
    private static final int MAX_QUESTIONS = 50;

    record Persona(String name, String stage, Map<Attribute, Double> leans, boolean contestTopMatch) {}

    static final List<Persona> PERSONAS = List.of(
            new Persona("Aarav (demo)", "CLASS_12", Map.of(
                    INVESTIGATIVE, 1.0, QUANTITATIVE, 1.0, ANALYTICAL, 0.9, PATTERN, 0.8,
                    ACHIEVEMENT, 0.7, STRUCTURE, 0.5, AUTONOMY, 0.5), false),
            new Persona("Meera (demo)", "UNDERGRAD", Map.of(
                    SOCIAL, 1.0, RELATIONSHIPS, 0.9, COLLABORATION, 0.9, VERBAL, 0.8,
                    ENTERPRISING, 0.5, AUTONOMY, -0.4), true),
            new Persona("Kabir (demo)", "CLASS_10", Map.of(
                    ARTISTIC, 1.0, CREATIVE, 1.0, SPATIAL, 0.7, INDEPENDENCE, 0.7,
                    VARIETY, 0.6, AMBIGUITY_TOLERANCE, 0.5, STRUCTURE, -0.5, CONVENTIONAL, -0.6), false));

    private final StudentRepository studentRepo;
    private final StudentService students;
    private final AssessmentService assessment;
    private final RecommendationService recommendations;
    private final ContestService contests;
    private final QuestionBank bank;
    private final TransactionTemplate tx;

    public DemoSeeder(StudentRepository studentRepo, StudentService students, AssessmentService assessment,
                      RecommendationService recommendations, ContestService contests, QuestionBank bank,
                      PlatformTransactionManager txManager) {
        this.studentRepo = studentRepo;
        this.students = students;
        this.assessment = assessment;
        this.recommendations = recommendations;
        this.contests = contests;
        this.bank = bank;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Creates any persona that doesn't exist yet. Returns how many were created. */
    public int seed() {
        int created = 0;
        for (Persona p : PERSONAS) {
            if (studentRepo.existsByName(p.name())) {
                log.info("Demo seed: {} already exists, skipping", p.name());
                continue;
            }
            // One transaction per persona: a failure never leaves a half-seeded student behind
            tx.executeWithoutResult(status -> seed(p));
            created++;
        }
        return created;
    }

    private void seed(Persona p) {
        String id = students.create(p.name(), p.stage()).id();
        answerAll(assessment.start(id, Set.of()), p);

        String contested = "";
        // Meera disputes the first reason on her top match, then answers her follow-up
        // with the same lean. Her answers re-confirm the disputed attribute, so the
        // match stays on top. That is intentional: a dispute is evidence, not an
        // override, and the system checks again before changing its mind.
        if (p.contestTopMatch()) {
            Views.Recommendation top = recommendations.recommend(id).get(0);
            String reason = top.reasons().get(0).attribute();
            Views.ContestResult result = contests.contestRecommendation(id, top.careerId(), List.of(reason), List.of(), null);
            answerAll(result.followUp(), p);
            contested = ", contested " + top.title() + " on " + reason;
        }

        String matches = recommendations.recommend(id).stream().map(Views.Recommendation::title)
                .collect(Collectors.joining(", "));
        log.info("Demo seed: created {} ({} questions answered{}); top matches: {}",
                p.name(), assessment.questionsAnswered(id), contested, matches);
    }

    private void answerAll(Views.Question q, Persona p) {
        int guard = 0;
        while (q != null && guard++ < MAX_QUESTIONS) {
            String option = choose(bank.get(q.id()), p.leans());
            q = assessment.answer(q.sessionId(), q.id(), option).next();
        }
    }

    /**
     * The answer rule: score each option by how well its evidence fits the persona's
     * lean (strength times weight, negated for "contradicts") and pick the highest.
     * Ties go to the earlier option.
     */
    static String choose(Question q, Map<Attribute, Double> leans) {
        Question.Option best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Question.Option o : q.options()) {
            double score = 0;
            for (Question.EvidenceSpec e : o.evidence()) {
                double sign = e.direction() == Direction.SUPPORTS ? 1 : -1;
                score += sign * e.strength() * leans.getOrDefault(e.attribute(), UNMENTIONED_WEIGHT);
            }
            if (score > bestScore) {
                best = o;
                bestScore = score;
            }
        }
        return best.id();
    }
}
