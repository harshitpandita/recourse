package com.recourse.evaluation;

import com.recourse.api.Views;
import com.recourse.data.CareerCatalog;
import com.recourse.data.QuestionBank;
import com.recourse.engine.Attribute;
import com.recourse.engine.Belief;
import com.recourse.engine.BeliefCalculator;
import com.recourse.engine.Evidence;
import com.recourse.engine.EvidenceFactory;
import com.recourse.engine.Matcher;
import com.recourse.engine.Question;
import com.recourse.engine.Pillar;
import com.recourse.engine.QuestionSelector;
import com.recourse.service.AssessmentService;
import com.recourse.service.ContestService;
import com.recourse.service.RecommendationService;
import com.recourse.service.StudentModel;
import com.recourse.service.StudentService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simulation-based evaluation of the whole engine, through the same services the app uses.
 * Not part of normal mvn test. Run it with:
 *
 *   mvn test -Pevaluation
 *
 * It writes CSVs and summary.md to docs/evaluation/. Everything is seeded, so a rerun gives the same numbers.
 */
@Tag("evaluation")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:recourse-evaluation;DB_CLOSE_DELAY=-1")
class SimulationEvaluationTest {

    static final int PERSONAS = 30;
    static final long SEED = 20261118L;
    static final double[] NOISE = {0.10, 0.20};
    static final int NOISE_RUNS = 5;
    static final double CLEAR_LEAN = 0.3;
    static final Path OUT = Path.of("..", "docs", "evaluation");
    static final Path BASELINE = OUT.resolve("baseline");
    static final int[] CHECKPOINTS = {8, 12, 16, 20};

    @Autowired StudentService students;
    @Autowired AssessmentService assessment;
    @Autowired RecommendationService recommendations;
    @Autowired ContestService contests;
    @Autowired StudentModel model;
    @Autowired QuestionBank bank;
    @Autowired CareerCatalog careers;

    /** One simulated assessment: the questions asked, how many answers were random, and the top 3. */
    record Run(String studentId, List<String> questions, int randomAnswers, List<String> top3) {}

    private int created = 0;

    private Run assess(Persona p, double noise, Random rng) {
        String id = students.create(p.id() + " run " + (++created), "CLASS_12").id();
        List<String> asked = new ArrayList<>();
        int random = 0;
        Views.Question q = assessment.start(id, Set.of());
        while (q != null) {
            Persona.Answer a = p.answer(bank.get(q.id()), noise, rng);
            if (a.random()) random++;
            asked.add(q.id());
            q = assessment.answer(q.sessionId(), q.id(), a.optionId()).next();
        }
        return new Run(id, asked, random, top3(id));
    }

    private List<String> top3(String studentId) {
        return recommendations.recommend(studentId).stream().map(Views.Recommendation::careerId).toList();
    }

    private char family(String careerId) {
        return careers.get(careerId).primaryType();
    }

    @Test
    void evaluate() throws IOException {
        Files.createDirectories(OUT);
        List<Persona> personas = Persona.generate(PERSONAS, SEED);
        Summary summary = new Summary();

        // Clean runs: every persona answers honestly
        Map<Persona, Run> clean = new LinkedHashMap<>();
        for (Persona p : personas) clean.put(p, assess(p, 0, new Random(SEED)));

        writePersonas(personas);
        recovery(clean, summary);
        top3(clean, summary);
        noise(personas, clean, summary);
        adaptivity(clean, summary);
        questionCurve(clean, summary);
        contest(clean, summary); // last: it changes the clean students' evidence

        write("metrics.csv", summary.metricsCsv());
        summary.comparison(BASELINE.resolve("metrics.csv"));
        write("summary.md", summary.render());
        assertEquals(PERSONAS, clean.size());
    }

    // 1. Recovery

    private void recovery(Map<Persona, Run> clean, Summary summary) throws IOException {
        StringBuilder csv = new StringBuilder("persona,attribute,pillar,true_lean,true_level,belief_level,confidence,evidence_count\n");
        Map<String, List<Double>> truth = new LinkedHashMap<>(), belief = new LinkedHashMap<>();
        Map<String, int[]> covered = new LinkedHashMap<>(), direction = new LinkedHashMap<>(); // [hits, total]
        int flagged = 0;
        for (Map.Entry<Persona, Run> e : clean.entrySet()) {
            Persona p = e.getKey();
            Map<Attribute, Belief> beliefs = model.beliefs(e.getValue().studentId());
            for (Attribute a : Attribute.values()) {
                Belief b = beliefs.get(a);
                if (b.contradictory()) flagged++;
                double t = p.trueLevel(a), l = b.level();
                csv.append(String.join(",", p.id(), a.key(), a.pillar().name(), num(p.lean().get(a)), num(t), num(l),
                        num(b.confidence()), String.valueOf(b.evidenceCount()))).append('\n');
                for (String group : List.of("All", a.pillar().label())) {
                    truth.computeIfAbsent(group, k -> new ArrayList<>()).add(t);
                    belief.computeIfAbsent(group, k -> new ArrayList<>()).add(l);
                    int[] c = covered.computeIfAbsent(group, k -> new int[2]);
                    c[1]++;
                    if (b.evidenceCount() > 0) c[0]++;
                    // Direction: for clear true leans that got evidence, does the model lean the same way?
                    if (Math.abs(p.lean().get(a)) >= CLEAR_LEAN && b.evidenceCount() > 0) {
                        int[] d = direction.computeIfAbsent(group, k -> new int[2]);
                        d[1]++;
                        if (Math.signum(b.lean()) == Math.signum(p.lean().get(a))) d[0]++;
                    }
                }
            }
        }
        write("recovery.csv", csv.toString());

        List<String[]> rows = new ArrayList<>();
        List<String> groups = new ArrayList<>(List.of("All"));
        for (Pillar p : Pillar.values()) groups.add(p.label());
        for (String g : groups) {
            int[] c = covered.get(g), d = direction.getOrDefault(g, new int[2]);
            double r = Stats.pearson(truth.get(g), belief.get(g)), mae = Stats.mae(truth.get(g), belief.get(g));
            rows.add(new String[]{g, num(r), num(mae), pct(c[0], c[1]), pct(d[0], d[1])});
            summary.metric("Recovery", "Pearson r (" + g.toLowerCase(Locale.ROOT) + ")", r, Kind.NUM);
            summary.metric("Recovery", "Mean abs. error (" + g.toLowerCase(Locale.ROOT) + ")", mae, Kind.NUM);
        }
        int[] dAll = direction.get("All");
        summary.metric("Recovery", "Right direction (all)", (double) dAll[0] / dAll[1], Kind.PCT);
        double flaggedPerPersona = (double) flagged / clean.size();
        summary.metric("Recovery", "Attributes flagged contradictory per persona", flaggedPerPersona, Kind.NUM);
        summary.section("1. Recovery: does the model end up believing what is true?",
                """
                For each persona and attribute, the true lean (rescaled to 0..1) is compared with the model's final \
                belief level after the normal assessment. 30 personas x 24 attributes = 720 pairs.""",
                new String[]{"Attributes", "Pearson r", "Mean abs. error", "Got any evidence", "Right direction"},
                rows,
                List.of(
                        "**Pearson r**: how closely the model's levels rise and fall with the true levels (1 = perfectly in step, 0 = unrelated).",
                        "**Mean abs. error**: the average gap between true level and belief level on the 0 to 1 scale. "
                                + "The model pulls uncertain beliefs toward 0.5 on purpose, so this is larger than the raw disagreement.",
                        "**Got any evidence**: share of attributes that at least one asked question touched. Untouched ones stay at 0.5.",
                        "**Right direction**: for clear true leans (at least " + CLEAR_LEAN + " either way) that got evidence, "
                                + "how often the model leans the same way.",
                        "**Flagged contradictory**: on average " + num1(flaggedPerPersona) + " of a persona's 24 attributes are "
                                + "flagged as contradictory (shown to the student as mixed evidence) after the assessment."));
    }

    // 2. Top-3 accuracy

    private void top3(Map<Persona, Run> clean, Summary summary) throws IOException {
        StringBuilder csv = new StringBuilder("persona,primary,secondary,top1,top2,top3,hit_primary,hit_top_two_letters\n");
        Map<Character, int[]> byFamily = new LinkedHashMap<>(); // [hits, personas]
        int hits = 0, lenient = 0;
        double chance = 0;
        int total = careers.all().size();
        for (Map.Entry<Persona, Run> e : clean.entrySet()) {
            Persona p = e.getKey();
            List<String> top = e.getValue().top3();
            boolean hit = top.stream().anyMatch(c -> family(c) == p.primary());
            boolean soft = top.stream().anyMatch(c -> family(c) == p.primary() || family(c) == p.secondary());
            if (hit) hits++;
            if (soft) lenient++;
            chance += Stats.chanceTop3(total, familySize(p.primary()));
            int[] f = byFamily.computeIfAbsent(p.primary(), k -> new int[2]);
            f[1]++;
            if (hit) f[0]++;
            csv.append(String.join(",", p.id(), String.valueOf(p.primary()), String.valueOf(p.secondary()),
                    String.join(",", padded(top)), String.valueOf(hit), String.valueOf(soft))).append('\n');
        }
        write("top3.csv", csv.toString());
        summary.metric("Top-3 accuracy", "Top 3 has a career from the family", (double) hits / clean.size(), Kind.PCT);
        summary.metric("Top-3 accuracy", "Lenient (either of two strongest letters)", (double) lenient / clean.size(), Kind.PCT);

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"All", String.valueOf(total), pct(hits, clean.size()), pct(chance / clean.size())});
        for (char letter : Persona.LETTERS.toCharArray()) {
            int[] f = byFamily.get(letter);
            rows.add(new String[]{familyName(letter), String.valueOf(familySize(letter)), pct(f[0], f[1]),
                    pct(Stats.chanceTop3(total, familySize(letter)))});
        }
        summary.section("2. Top-3 accuracy: does a career from the right family make the top 3?",
                """
                A persona's target family is every career whose O*NET interest code starts with the persona's strongest \
                RIASEC letter. Five personas per letter.""",
                new String[]{"Family (first letter)", "Careers in family", "Top 3 has one", "By chance"},
                rows,
                List.of(
                        "**Top 3 has one**: share of personas whose top 3 includes at least one career from their family.",
                        "**By chance**: the same share if the top 3 were three careers picked at random from the " + total + ".",
                        "Lenient version (a top-3 career starts with either of the persona's two strongest letters): "
                                + pct(lenient, clean.size()) + "."));
    }

    private int familySize(char letter) {
        return (int) careers.all().stream().filter(c -> c.primaryType() == letter).count();
    }

    // 3. Noise robustness

    private void noise(List<Persona> personas, Map<Persona, Run> clean, Summary summary) throws IOException {
        StringBuilder csv = new StringBuilder("persona,noise,run,questions,random_answers,top1,top2,top3,same_set,overlap\n");
        List<String[]> rows = new ArrayList<>();
        for (int n = 0; n < NOISE.length; n++) {
            int same = 0, sameTop = 0, runs = 0, randomAnswers = 0, answers = 0;
            List<Double> overlaps = new ArrayList<>();
            for (int i = 0; i < personas.size(); i++) {
                Persona p = personas.get(i);
                List<String> base = clean.get(p).top3();
                for (int r = 0; r < NOISE_RUNS; r++) {
                    Run run = assess(p, NOISE[n], new Random(SEED + 1000L * i + 100L * n + r));
                    boolean sameSet = new HashSet<>(run.top3()).equals(new HashSet<>(base));
                    double overlap = Stats.overlap(run.top3(), base);
                    if (sameSet) same++;
                    if (!run.top3().isEmpty() && run.top3().get(0).equals(base.get(0))) sameTop++;
                    runs++;
                    overlaps.add(overlap);
                    randomAnswers += run.randomAnswers();
                    answers += run.questions().size();
                    csv.append(String.join(",", p.id(), num(NOISE[n]), String.valueOf(r + 1),
                            String.valueOf(run.questions().size()), String.valueOf(run.randomAnswers()),
                            String.join(",", padded(run.top3())), String.valueOf(sameSet), num(overlap))).append('\n');
                }
            }
            rows.add(new String[]{pct(NOISE[n]), String.valueOf(runs), pct(randomAnswers, answers), pct(sameTop, runs), pct(same, runs),
                    num(Stats.mean(overlaps)) + " (" + num(Stats.mean(overlaps) * 3) + " of 3)"});
            String level = "(" + pct(NOISE[n]) + " noise)";
            summary.metric("Noise", "Same top match " + level, (double) sameTop / runs, Kind.PCT);
            summary.metric("Noise", "Same top-3 set " + level, (double) same / runs, Kind.PCT);
            summary.metric("Noise", "Average overlap " + level, Stats.mean(overlaps), Kind.NUM);
        }
        write("noise.csv", csv.toString());
        summary.section("3. Noise robustness: do a few careless answers change the result?",
                """
                Each persona is run again with each answer independently replaced by a random option, with a 10% or 20% \
                chance. Five runs per persona and noise level, each with its own fixed seed. Results are compared with the \
                persona's clean run.""",
                new String[]{"Noise", "Runs", "Answers actually replaced", "Same top match", "Same top-3 set", "Average overlap"},
                rows,
                List.of(
                        "**Same top match**: share of noisy runs whose first recommendation is the clean run's first.",
                        "**Same top-3 set**: share of noisy runs whose three careers are the same as the clean run's (order ignored).",
                        "**Average overlap**: careers shared with the clean top 3, as a fraction of 3."));
    }

    // 4. Adaptivity

    private void adaptivity(Map<Persona, Run> clean, Summary summary) throws IOException {
        StringBuilder csv = new StringBuilder("persona,questions,sequence\n");
        List<Run> runs = new ArrayList<>(clean.values());
        List<Persona> ps = new ArrayList<>(clean.keySet());
        for (int i = 0; i < runs.size(); i++) {
            csv.append(ps.get(i).id()).append(',').append(runs.get(i).questions().size()).append(',')
                    .append(String.join(" ", runs.get(i).questions())).append('\n');
        }
        write("adaptivity.csv", csv.toString());

        List<Double> counts = runs.stream().map(r -> (double) r.questions().size()).toList();
        List<Double> jaccard = new ArrayList<>(), divergence = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            for (int j = i + 1; j < runs.size(); j++) {
                jaccard.add(Stats.jaccard(new HashSet<>(runs.get(i).questions()), new HashSet<>(runs.get(j).questions())));
                divergence.add((double) Stats.firstDifference(runs.get(i).questions(), runs.get(j).questions()));
            }
        }
        long distinct = runs.stream().map(Run::questions).distinct().count();
        Set<String> used = runs.stream().flatMap(r -> r.questions().stream()).collect(Collectors.toSet());
        int min = counts.stream().mapToInt(Double::intValue).min().orElse(0);
        int max = counts.stream().mapToInt(Double::intValue).max().orElse(0);
        summary.metric("Adaptivity", "Questions asked (mean)", Stats.mean(counts), Kind.NUM);
        summary.metric("Adaptivity", "Distinct question sequences (of 30)", distinct, Kind.COUNT);
        summary.metric("Adaptivity", "Question-set overlap between two personas", Stats.mean(jaccard), Kind.NUM);
        summary.metric("Adaptivity", "Position where two sequences first differ", Stats.mean(divergence), Kind.NUM);

        summary.section("4. Adaptivity: do different students get different questions?",
                """
                Taken from the 30 clean runs. The first question is always the same, because nothing is known about \
                the student yet; after that the next question depends on the answers so far.""",
                new String[]{"Measure", "Value"},
                List.of(
                        new String[]{"Questions asked (mean)", num1(Stats.mean(counts))},
                        new String[]{"Questions asked (min to max)", min + " to " + max},
                        new String[]{"Distinct question sequences", distinct + " of " + runs.size()},
                        new String[]{"Questions in the bank used by anyone", used.size() + " of " + bank.all().size()},
                        new String[]{"Average question-set overlap between two personas (Jaccard)", num(Stats.mean(jaccard))},
                        new String[]{"Average position where two sequences first differ", num1(Stats.mean(divergence))}),
                List.of(
                        "**Questions asked**: the assessment asks 12 to 20 questions; after 12 it stops early once nothing left is worth asking or the profile is clear enough."
                                + (min == max && max == QuestionSelector.MAX_QUESTIONS
                                        ? " Every persona reached the " + max + "-question cap, so the early stop never triggered for these personas." : ""),
                        "**Jaccard overlap**: questions both personas saw, divided by questions either saw (1 = same set, 0 = nothing shared).",
                        "**First difference**: the question number at which two personas' sequences stop being identical."));
    }

    // 5. Contest behaviour

    private void contest(Map<Persona, Run> clean, Summary summary) throws IOException {
        StringBuilder csv = new StringBuilder("persona,top_match,disputed,true_lean_of_disputed,belief_level_before,belief_level_after,belief_level_final,belief_conf_before,belief_conf_after,belief_conf_final,"
                + "match_conf_before,match_conf_after,before_1,before_2,before_3,after_1,after_2,after_3,"
                + "follow_up_questions,final_1,final_2,final_3,top_changed,set_changed,top_restored\n");
        int contested = 0, skipped = 0, topChanged = 0, setChanged = 0, restored = 0, restoredAfterChange = 0;
        List<Double> levelBefore = new ArrayList<>(), levelAfter = new ArrayList<>(), levelFinal = new ArrayList<>(), confFinal = new ArrayList<>();
        int stillTop3 = 0;
        List<Double> ceiling = new ArrayList<>(), contradictionAfter = new ArrayList<>();
        List<Double> beliefDrop = new ArrayList<>(), matchDrop = new ArrayList<>(), followUps = new ArrayList<>(), trueLeans = new ArrayList<>();
        for (Map.Entry<Persona, Run> e : clean.entrySet()) {
            Persona p = e.getKey();
            String id = e.getValue().studentId();
            List<Views.Recommendation> before = recommendations.recommend(id);
            Views.Recommendation top = before.get(0);
            if (top.reasons().isEmpty()) { skipped++; continue; }
            Attribute disputed = Attribute.fromKey(top.reasons().get(0).attribute());
            Belief bBefore = model.beliefs(id).get(disputed);
            double beliefBefore = bBefore.confidence();

            Views.ContestResult result = contests.contestRecommendation(id, top.careerId(),
                    List.of(disputed.key()), List.of(), null);
            List<String> after = top3(id);
            Belief bAfter = model.beliefs(id).get(disputed);
            double beliefAfter = bAfter.confidence();
            double matchAfter = recommendations.recommendation(id, top.careerId()).confidence();

            // The persona answers the follow-up questions honestly
            int asked = 0;
            Views.Question q = result.followUp();
            while (q != null) {
                q = assessment.answer(q.sessionId(), q.id(), p.honestAnswer(bank.get(q.id()))).next();
                asked++;
            }
            List<String> fin = top3(id);
            Belief bFinal = model.beliefs(id).get(disputed);

            List<String> beforeIds = before.stream().map(Views.Recommendation::careerId).toList();
            boolean changed = !after.get(0).equals(top.careerId());
            boolean setMoved = !new HashSet<>(after).equals(new HashSet<>(beforeIds));
            boolean back = fin.get(0).equals(top.careerId());
            contested++;
            if (changed) topChanged++;
            if (setMoved) setChanged++;
            if (back) restored++;
            if (changed && back) restoredAfterChange++;
            if (fin.contains(top.careerId())) stillTop3++;
            levelBefore.add(bBefore.level());
            levelAfter.add(bAfter.level());
            levelFinal.add(bFinal.level());
            confFinal.add(bFinal.confidence());
            // Even with support at its maximum of 1, conflict can't fall below 2c / (1 + c), so (1 - c) / (1 + c)
            // is the most of its evidence mass the disputed attribute can ever turn back into confidence.
            contradictionAfter.add(bAfter.contradiction());
            ceiling.add((1 - bAfter.contradiction()) / (1 + bAfter.contradiction()));
            beliefDrop.add(beliefBefore - beliefAfter);
            matchDrop.add(top.confidence() - matchAfter);
            followUps.add((double) asked);
            trueLeans.add(p.lean().get(disputed));

            csv.append(String.join(",", p.id(), top.careerId(), disputed.key(), num(p.lean().get(disputed)),
                    num(bBefore.level()), num(bAfter.level()), num(bFinal.level()),
                    num(beliefBefore), num(beliefAfter), num(bFinal.confidence()), num(top.confidence()), num(matchAfter),
                    String.join(",", padded(beforeIds)), String.join(",", padded(after)), String.valueOf(asked),
                    String.join(",", padded(fin)), String.valueOf(changed), String.valueOf(setMoved),
                    String.valueOf(back))).append('\n');
        }
        write("contest.csv", csv.toString());
        summary.metric("Contest", "Top match changed by the dispute", (double) topChanged / contested, Kind.PCT);
        summary.metric("Contest", "Top-3 set changed by the dispute", (double) setChanged / contested, Kind.PCT);
        summary.metric("Contest", "Drop in the disputed attribute's confidence", Stats.mean(beliefDrop), Kind.NUM);
        summary.metric("Contest", "Drop in the match's confidence", Stats.mean(matchDrop), Kind.NUM);
        summary.metric("Contest", "Follow-up questions asked (mean)", Stats.mean(followUps), Kind.NUM);
        summary.metric("Contest", "Original top match back on top after follow-ups", (double) restored / contested, Kind.PCT);
        summary.metric("Contest", "Original top match still in top 3 after follow-ups", (double) stillTop3 / contested, Kind.PCT);
        summary.metric("Contest", "Disputed attribute's level after follow-ups", Stats.mean(levelFinal), Kind.NUM);
        summary.metric("Contest", "Disputed attribute's confidence after follow-ups", Stats.mean(confFinal), Kind.NUM);

        List<String[]> rows = new ArrayList<>(List.of(
                new String[]{"Personas contested", contested + (skipped > 0 ? " (" + skipped + " skipped: top match had no reason)" : "")},
                new String[]{"Top match changed after the contest", pct(topChanged, contested)},
                new String[]{"Top-3 set changed after the contest", pct(setChanged, contested)},
                new String[]{"Average drop in confidence of the disputed attribute", num(Stats.mean(beliefDrop))},
                new String[]{"Average drop in the match's confidence", num(Stats.mean(matchDrop))},
                new String[]{"Follow-up questions asked (mean)", num1(Stats.mean(followUps))},
                new String[]{"Original top match back on top after the follow-ups", pct(restored, contested)},
                new String[]{"...of those where the contest had changed the top match", pct(restoredAfterChange, topChanged)},
                new String[]{"Original top match still in the top 3 after the follow-ups", pct(stillTop3, contested)},
                new String[]{"Disputed attribute level: before, after contest, after follow-ups", num(Stats.mean(levelBefore)) + ", " + num(Stats.mean(levelAfter)) + ", " + num(Stats.mean(levelFinal))},
                new String[]{"Disputed attribute confidence after follow-ups", num(Stats.mean(confFinal))},
                new String[]{"Average true lean of the disputed attribute", num(Stats.mean(trueLeans))}));
        List<String> notes = new ArrayList<>(List.of(
                "**Changed after the contest**: the dispute alone, before any follow-up answers.",
                "**Drop in confidence**: confidence before minus after, on the 0 to 1 scale. Evidence is never deleted, "
                        + "so a dispute makes the belief contradictory and less certain rather than flipping it.",
                "**Back on top**: the top match after the follow-up answers is the original one."));
        if ((double) restored / contested < 0.2) {
            notes.add("Why honest follow-ups rarely restore it: a dispute adds counter-evidence as strong as the evidence it "
                    + "disputes, so contradiction ends up high (on average " + num(Stats.mean(contradictionAfter)) + "). Confidence is "
                    + "evidence mass x (1 - conflict), with conflict = 2 x min(support, contradiction) / (support + contradiction). "
                    + "Support can never exceed 1, so conflict can never fall below 2c / (1 + c): even unlimited agreeing answers "
                    + "could lift the disputed attribute to at most " + num(Stats.mean(ceiling)) + " of its evidence mass, "
                    + "and a few follow-up questions get only part of the way there.");
        }
        summary.section("5. Contest behaviour: does pushing back work, and can honest answers undo a mistaken dispute?",
                """
                For each persona, the first reason given for its top match is disputed, exactly as a student would do it \
                in the app. The persona then answers the follow-up questions honestly. Because these disputes are \
                deliberately mistaken (the persona really does have that trait, see the last row), a good system should \
                both react to the objection and let honest follow-up answers correct it.""",
                new String[]{"Measure", "Value"},
                rows,
                notes);
    }

    // 6. Question curve

    /**
     * Recovery and top-3 accuracy after 8, 12, 16 and 20 questions, from one run per persona. It uses the
     * app's own pieces (QuestionSelector.next, EvidenceFactory.fromAnswer, BeliefCalculator, Matcher.recommend)
     * but never stops early, so every persona reaches every checkpoint.
     */
    private void questionCurve(Map<Persona, Run> clean, Summary summary) throws IOException {
        StringBuilder csv = new StringBuilder("persona,questions,pearson_r,mean_abs_error,top1,top2,top3,hit\n");
        Map<Integer, List<Double>> truth = new LinkedHashMap<>(), belief = new LinkedHashMap<>();
        Map<Integer, int[]> hits = new LinkedHashMap<>(); // [hits, personas]
        int last = CHECKPOINTS[CHECKPOINTS.length - 1];
        for (Map.Entry<Persona, Run> e : clean.entrySet()) {
            Persona p = e.getKey();
            Run normal = e.getValue();
            List<Evidence> evidence = new ArrayList<>();
            Set<String> asked = new LinkedHashSet<>();
            int[] ids = {0};
            for (int k = 1; k <= last; k++) {
                var next = QuestionSelector.next(bank.all(), BeliefCalculator.computeAll(evidence), asked, Set.of());
                if (next.isEmpty()) break;
                Question q = next.get().question();
                evidence.addAll(EvidenceFactory.fromAnswer(q, p.honestAnswer(q), () -> "e" + ids[0]++, Instant.EPOCH));
                asked.add(q.id());
                Map<Attribute, Belief> beliefs = BeliefCalculator.computeAll(evidence);
                List<String> top = Matcher.recommend(careers.all(), beliefs, evidence, RecommendationService.COUNT).stream()
                        .map(m -> m.career().id()).toList();

                if (k == normal.questions().size()) {
                    // Proof that this is the app's logic: where the app stopped, same questions and same top 3
                    assertEquals(normal.questions(), new ArrayList<>(asked), p.id() + ": question curve asked different questions");
                    assertEquals(normal.top3(), top, p.id() + ": question curve gave a different top 3");
                }
                if (!isCheckpoint(k)) continue;
                List<Double> t = new ArrayList<>(), l = new ArrayList<>();
                for (Attribute a : Attribute.values()) {
                    t.add(p.trueLevel(a));
                    l.add(beliefs.get(a).level());
                }
                truth.computeIfAbsent(k, x -> new ArrayList<>()).addAll(t);
                belief.computeIfAbsent(k, x -> new ArrayList<>()).addAll(l);
                boolean hit = top.stream().anyMatch(c -> family(c) == p.primary());
                int[] h = hits.computeIfAbsent(k, x -> new int[2]);
                h[1]++;
                if (hit) h[0]++;
                csv.append(String.join(",", p.id(), String.valueOf(k), num(Stats.pearson(t, l)), num(Stats.mae(t, l)),
                        String.join(",", padded(top)), String.valueOf(hit))).append('\n');
            }
        }
        write("question-curve.csv", csv.toString());
        assertTrue(hits.containsKey(last), "no persona reached " + last + " questions");

        List<String[]> rows = new ArrayList<>();
        for (int k : CHECKPOINTS) {
            double r = Stats.pearson(truth.get(k), belief.get(k)), mae = Stats.mae(truth.get(k), belief.get(k));
            int[] h = hits.get(k);
            rows.add(new String[]{String.valueOf(k), num(r), num(mae), pct(h[0], h[1])});
            summary.metric("Question curve", "Pearson r after " + k + " questions", r, Kind.NUM);
            summary.metric("Question curve", "Top-3 accuracy after " + k + " questions", (double) h[0] / h[1], Kind.PCT);
        }
        summary.section("6. Question curve: how much do more questions help?",
                """
                One run per persona, answering honestly, with the beliefs and top 3 recorded after 8, 12, 16 and 20 \
                questions. It uses the app's own question choice but never stops early, so every persona reaches every \
                checkpoint. (The app itself asks at least 12 and at most 20.) Per-persona values are in question-curve.csv.""",
                new String[]{"Questions answered", "Pearson r", "Mean abs. error", "Top 3 has a career from the family"},
                rows,
                List.of(
                        "Same measures as sections 1 and 2, pooled over all 30 personas at each point.",
                        "Where a persona's normal run stopped, the curve run is checked to have asked the same questions and "
                                + "given the same top 3, so it follows the app exactly."));
    }

    private static boolean isCheckpoint(int k) {
        for (int c : CHECKPOINTS) if (c == k) return true;
        return false;
    }

    // Output helpers

    private void writePersonas(List<Persona> personas) throws IOException {
        StringBuilder csv = new StringBuilder("persona,primary,secondary,target_family");
        for (Attribute a : Attribute.values()) csv.append(',').append(a.key());
        csv.append('\n');
        for (Persona p : personas) {
            csv.append(p.id()).append(',').append(p.primary()).append(',').append(p.secondary()).append(',')
                    .append(familyName(p.primary()));
            for (Attribute a : Attribute.values()) csv.append(',').append(num(p.lean().get(a)));
            csv.append('\n');
        }
        write("personas.csv", csv.toString());
    }

    private static String familyName(char letter) {
        return Attribute.values()[Persona.LETTERS.indexOf(letter)].label();
    }

    private static List<String> padded(List<String> top) {
        List<String> out = new ArrayList<>(top);
        while (out.size() < 3) out.add("");
        return out.subList(0, 3);
    }

    private static void write(String name, String content) throws IOException {
        Files.writeString(OUT.resolve(name), content, StandardCharsets.UTF_8);
    }

    static String num(double v) { return Double.isNaN(v) ? "n/a" : String.format(Locale.ROOT, "%.2f", v); }
    static String num1(double v) { return String.format(Locale.ROOT, "%.1f", v); }
    static String pct(double share) { return String.format(Locale.ROOT, "%.0f%%", share * 100); }
    static String pct(int part, int whole) { return whole == 0 ? "n/a" : pct((double) part / whole) + " (" + part + "/" + whole + ")"; }

    enum Kind { NUM, PCT, COUNT }

    /** Builds summary.md and metrics.csv. */
    static final class Summary {
        private final StringBuilder head = new StringBuilder();
        private final StringBuilder comparison = new StringBuilder();
        private final StringBuilder md = new StringBuilder();
        private final List<Metric> metrics = new ArrayList<>();

        record Metric(String area, String name, double value, Kind kind) {
            String key() { return area + "|" + name; }
        }

        /** Areas in the order the before/after table shows them: contests first. */
        static final List<String> AREAS = List.of("Contest", "Recovery", "Top-3 accuracy", "Noise", "Adaptivity", "Question curve");

        Summary() {
            head.append("# Simulation evaluation\n\n")
              .append("Generated by `SimulationEvaluationTest` (`mvn test -Pevaluation` in `backend/`). Do not edit by hand.\n\n")
              .append("## Method\n\n")
              .append(PERSONAS).append(" simulated students (personas) each have a known true lean, from -1 to +1, on all 24 ")
              .append("attributes. Each RIASEC letter is the strongest interest for 5 personas (+0.8), with a different second letter ")
              .append("(+0.5); everything else is random from a fixed seed (").append(SEED).append("). A persona answers each question ")
              .append("by picking the option whose evidence best agrees with its true leans (strength x direction x lean, added up). ")
              .append("The personas go through the real assessment, recommendation and contest services with an in-memory database, ")
              .append("so the questions asked, the stopping rule and the contest logic are exactly the app's. Per-persona results ")
              .append("are in the CSV files next to this one.\n");
        }

        void section(String title, String intro, String[] header, List<String[]> rows, List<String> notes) {
            md.append("\n## ").append(title).append("\n\n").append(intro).append("\n\n");
            md.append("| ").append(String.join(" | ", header)).append(" |\n");
            md.append("|").append("---|".repeat(header.length)).append('\n');
            for (String[] r : rows) md.append("| ").append(String.join(" | ", r)).append(" |\n");
            md.append('\n');
            for (String n : notes) md.append("- ").append(n).append('\n');
        }

        void metric(String area, String name, double value, Kind kind) {
            if (!AREAS.contains(area) || name.contains(",")) throw new IllegalArgumentException(area + " / " + name);
            metrics.add(new Metric(area, name, value, kind));
        }

        /** Every headline number, machine-readable, so a later run can compare itself with this one. */
        String metricsCsv() {
            StringBuilder csv = new StringBuilder("area,metric,value,kind\n");
            for (Metric m : metrics) {
                csv.append(m.area()).append(',').append(m.name()).append(',')
                        .append(String.format(Locale.ROOT, "%.6f", m.value())).append(',').append(m.kind()).append('\n');
            }
            return csv.toString();
        }

        /** Adds the before/after section if a baseline run's metrics.csv exists. */
        void comparison(Path baselineCsv) throws IOException {
            if (!Files.exists(baselineCsv)) return;
            Map<String, Double> before = new LinkedHashMap<>();
            List<String> lines = Files.readAllLines(baselineCsv, StandardCharsets.UTF_8);
            for (String line : lines.subList(1, lines.size())) {
                String[] f = line.split(",");
                before.put(f[0] + "|" + f[1], Double.parseDouble(f[2]));
            }
            Map<String, Metric> now = new LinkedHashMap<>();
            metrics.forEach(m -> now.put(m.key(), m));

            comparison.append("\n## Before and after the conflict fix\n\n")
                    .append("The baseline is the same evaluation with the prototype's conflict formula, ")
                    .append("conflict = min(1, 2 x min(support, contradiction)). The current results use ")
                    .append("conflict = 2 x min(support, contradiction) / (support + contradiction), which depends on the balance ")
                    .append("of evidence rather than its amount. Everything else, including the seed, is identical. ")
                    .append("The full baseline results are in `baseline/`.\n\n")
                    .append("| Area | Measure | Before | After | Change |\n|---|---|---|---|---|\n");
            for (String area : AREAS) {
                for (Metric m : metrics) {
                    if (!m.area().equals(area)) continue;
                    Double b = before.get(m.key());
                    comparison.append("| ").append(area).append(" | ").append(m.name()).append(" | ")
                            .append(b == null ? "n/a" : show(b, m.kind())).append(" | ").append(show(m.value(), m.kind()))
                            .append(" | ").append(b == null ? "" : change(b, m.value(), m.kind())).append(" |\n");
                }
            }
            comparison.append('\n');
            String restored = "Contest|Original top match back on top after follow-ups", changed = "Contest|Top match changed by the dispute";
            String r = "Recovery|Pearson r (all)", flags = "Recovery|Attributes flagged contradictory per persona";
            if (before.containsKey(restored) && now.containsKey(restored)) {
                comparison.append("- After a mistaken dispute, honest follow-up answers now bring the original top match back in ")
                        .append(show(now.get(restored).value(), Kind.PCT)).append(" of cases (before: ")
                        .append(show(before.get(restored), Kind.PCT)).append(").\n");
            }
            String conf = "Contest|Disputed attribute's confidence after follow-ups";
            if (before.containsKey(conf) && now.containsKey(conf)) {
                comparison.append("- The disputed attribute's confidence after the follow-ups went from ").append(num(before.get(conf)))
                        .append(" to ").append(num(now.get(conf).value()))
                        .append(": honest answers now count for something, but see section 5 for why the effect is small.\n");
            }
            if (before.containsKey(changed) && now.containsKey(changed)) {
                comparison.append("- A dispute on its own changes the top match in ").append(show(now.get(changed).value(), Kind.PCT))
                        .append(" of cases (before: ").append(show(before.get(changed), Kind.PCT)).append("), so pushing back still has a visible effect.\n");
            }
            if (before.containsKey(r) && before.containsKey(flags)) {
                comparison.append("- In the normal assessment, recovery r went from ").append(num(before.get(r))).append(" to ")
                        .append(num(now.get(r).value())).append(", and attributes flagged contradictory per persona from ")
                        .append(num1(before.get(flags))).append(" to ").append(num1(now.get(flags).value())).append(".\n");
            }
        }

        private static String show(double v, Kind k) {
            return switch (k) {
                case PCT -> pct(v);
                case COUNT -> String.valueOf(Math.round(v));
                case NUM -> num(v);
            };
        }

        private static String change(double before, double after, Kind k) {
            double d = after - before;
            return switch (k) {
                case PCT -> String.format(Locale.ROOT, "%+.0f points", d * 100);
                case COUNT -> String.format(Locale.ROOT, "%+d", Math.round(d));
                case NUM -> String.format(Locale.ROOT, "%+.2f", d);
            };
        }

        String render() { return head.toString() + comparison + md; }
    }
}
