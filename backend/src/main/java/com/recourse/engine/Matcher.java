package com.recourse.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rule-based matching of a student model against career profiles. Deterministic
 * and fully traceable: every score breaks down into pillar fits, every pillar
 * fit into attribute contributions, and every contribution points at evidence.
 * The language model is never involved here.
 */
public final class Matcher {

    /** How much each pillar counts toward the overall match. */
    public static final Map<Pillar, Double> PILLAR_WEIGHTS = Map.of(
            Pillar.INTERESTS, 0.40,
            Pillar.ABILITIES, 0.25,
            Pillar.VALUES, 0.20,
            Pillar.WORK_STYLE, 0.15);

    /** Contributions smaller than this are not worth mentioning to the student. */
    static final double NOTABLE = 0.04;

    /** Score cost for each already-recommended career sharing the same primary interest type. */
    static final double SAME_TYPE_PENALTY = 0.03;

    public record Contribution(Attribute attribute, double weight, double fit, double effect) {}

    public record PillarFit(Pillar pillar, double fit, double confidence) {}

    public record Match(
            Career career,
            double score,
            double confidence,
            Map<Pillar, PillarFit> pillars,
            List<Contribution> reasons,
            List<Contribution> concerns,
            List<String> evidenceIds) {}

    private Matcher() {}

    /** How well one attribute suits the career: the belief level, flipped if the career penalises it. */
    static double fit(double weight, Belief belief) {
        return weight >= 0 ? belief.level() : 1 - belief.level();
    }

    public static Match match(Career career, Map<Attribute, Belief> beliefs, Map<String, Evidence> evidenceById) {
        Map<Pillar, double[]> sums = new EnumMap<>(Pillar.class); // [weighted fit, weighted confidence, total weight]
        List<Contribution> contributions = new ArrayList<>();

        for (Map.Entry<Attribute, Double> e : career.weights().entrySet()) {
            Attribute a = e.getKey();
            double w = e.getValue();
            Belief b = beliefs.getOrDefault(a, Belief.empty(a));
            double f = fit(w, b);
            double[] s = sums.computeIfAbsent(a.pillar(), p -> new double[3]);
            s[0] += Math.abs(w) * f;
            s[1] += Math.abs(w) * b.confidence();
            s[2] += Math.abs(w);
            contributions.add(new Contribution(a, w, f, Math.abs(w) * (f - 0.5) * PILLAR_WEIGHTS.get(a.pillar())));
        }

        // Every career is scored on all four pillars. A pillar the career has no
        // data for counts as a neutral 0.5, so listing fewer attributes can never
        // make a career look like a better match.
        Map<Pillar, PillarFit> pillars = new EnumMap<>(Pillar.class);
        double score = 0, confidence = 0, confidenceWeight = 0;
        for (Pillar p : Pillar.values()) {
            double pw = PILLAR_WEIGHTS.get(p);
            double[] s = sums.get(p);
            if (s == null) {
                score += pw * 0.5;
                continue;
            }
            PillarFit pf = new PillarFit(p, s[0] / s[2], s[1] / s[2]);
            pillars.put(p, pf);
            score += pw * pf.fit();
            confidence += pw * pf.confidence();
            confidenceWeight += pw;
        }
        confidence = confidenceWeight == 0 ? 0 : confidence / confidenceWeight;

        List<Contribution> reasons = contributions.stream()
                .filter(c -> c.effect() > NOTABLE / 4 && c.fit() - 0.5 > NOTABLE)
                .sorted(Comparator.comparingDouble(Contribution::effect).reversed()).limit(3).toList();
        List<Contribution> concerns = contributions.stream()
                .filter(c -> c.fit() - 0.5 < -NOTABLE)
                .sorted(Comparator.comparingDouble(Contribution::effect)).limit(2).toList();

        return new Match(career, score, confidence, pillars, reasons, concerns,
                trail(reasons, concerns, beliefs, evidenceById));
    }

    /** Evidence behind the reasons (agreeing items) and the concerns (disagreeing items). */
    static List<String> trail(List<Contribution> reasons, List<Contribution> concerns,
                              Map<Attribute, Belief> beliefs, Map<String, Evidence> evidenceById) {
        Set<String> ids = new LinkedHashSet<>();
        for (Contribution c : reasons) ids.addAll(agreeing(c, true, beliefs, evidenceById));
        for (Contribution c : concerns) ids.addAll(agreeing(c, false, beliefs, evidenceById));
        return List.copyOf(ids);
    }

    private static List<String> agreeing(Contribution c, boolean forReason, Map<Attribute, Belief> beliefs,
                                         Map<String, Evidence> evidenceById) {
        // For a reason on a positively weighted attribute we want SUPPORTS evidence, and so on.
        boolean wantSupport = (c.weight() >= 0) == forReason;
        Direction wanted = wantSupport ? Direction.SUPPORTS : Direction.CONTRADICTS;
        Belief b = beliefs.get(c.attribute());
        if (b == null) return List.of();
        return b.evidenceIds().stream()
                .map(evidenceById::get)
                .filter(ev -> ev != null && ev.direction() == wanted)
                .sorted(Comparator.comparingDouble(Evidence::weight).reversed())
                .limit(3).map(Evidence::id).toList();
    }

    public static List<Match> rankAll(List<Career> careers, Map<Attribute, Belief> beliefs, List<Evidence> evidence) {
        Map<String, Evidence> byId = new HashMap<>();
        evidence.forEach(e -> byId.put(e.id(), e));
        return careers.stream().map(c -> match(c, beliefs, byId))
                .sorted(Comparator.comparingDouble(Match::score).reversed()
                        .thenComparing(m -> m.career().id()))
                .toList();
    }

    /**
     * Picks {@code n} recommendations that are meaningfully different. Picks are
     * made one at a time; each candidate loses {@link #SAME_TYPE_PENALTY} for every
     * already-picked career with the same primary interest type. So a second
     * "Enterprising" career only gets in if it is clearly better than the
     * best alternative of a different type.
     */
    public static List<Match> recommend(List<Career> careers, Map<Attribute, Belief> beliefs,
                                        List<Evidence> evidence, int n) {
        List<Match> remaining = new ArrayList<>(rankAll(careers, beliefs, evidence));
        List<Match> picked = new ArrayList<>();
        while (picked.size() < n && !remaining.isEmpty()) {
            Match best = remaining.stream()
                    .max(Comparator.comparingDouble((Match m) -> adjusted(m, picked))
                            .thenComparing(m -> m.career().id(), Comparator.reverseOrder()))
                    .orElseThrow();
            picked.add(best);
            remaining.remove(best);
        }
        return picked;
    }

    static double adjusted(Match m, List<Match> picked) {
        long sameType = picked.stream()
                .filter(p -> p.career().primaryType() == m.career().primaryType()).count();
        return m.score() - SAME_TYPE_PENALTY * sameType;
    }
}
