package com.recourse.engine;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Picks the next question by expected usefulness (ported from prototype v3):
 *
 *   score = information value x importance x reliability x discrimination / effort
 *
 * Information value is high when the targeted attributes are still uncertain.
 * Discrimination value is high when the question separates attributes we
 * cannot yet tell apart. Every pick comes with a plain-English reason.
 */
public final class QuestionSelector {

    static final double CONFIDENCE_SATURATION = 0.75;
    static final double AMBIGUITY_CONFIDENCE = 0.55;
    static final double AMBIGUITY_SEPARATION = 0.35;
    /** Attributes the student has just contested get looked at again first. */
    static final double FOCUS_BOOST = 1.6;

    public static final int MIN_QUESTIONS = 12;
    public static final int MAX_QUESTIONS = 20;
    static final double LOW_VALUE = 0.08;
    static final double USEFUL_CONFIDENCE = 0.6;

    public record Scored(Question question, double score, String reason) {}

    private QuestionSelector() {}

    private static double confidence(Map<Attribute, Belief> beliefs, Attribute a) {
        Belief b = beliefs.get(a);
        return b == null ? 0 : b.confidence();
    }

    static double informationValue(Map<Attribute, Belief> beliefs, List<Attribute> targets) {
        if (targets.isEmpty()) return 0;
        return targets.stream()
                .mapToDouble(a -> Math.max(0, 1 - Math.min(1, confidence(beliefs, a) / CONFIDENCE_SATURATION)))
                .average().orElse(0);
    }

    static boolean ambiguous(Map<Attribute, Belief> beliefs, Attribute a) {
        Belief b = beliefs.get(a);
        if (b == null || b.evidenceCount() == 0) return true;
        return b.confidence() < AMBIGUITY_CONFIDENCE && Math.abs(b.lean()) < AMBIGUITY_SEPARATION;
    }

    static double discriminationValue(Map<Attribute, Belief> beliefs, List<Attribute> between) {
        if (between.isEmpty()) return 0.5;
        long count = between.stream().filter(a -> ambiguous(beliefs, a)).count();
        return 0.5 + 0.5 * count / between.size();
    }

    public static Scored score(Question q, Map<Attribute, Belief> beliefs, Set<Attribute> focus) {
        double info = informationValue(beliefs, q.targets());
        double disc = discriminationValue(beliefs, q.discriminates());
        double boost = q.targets().stream().anyMatch(focus::contains) ? FOCUS_BOOST : 1;
        double score = info * q.reliability() * disc * boost / Math.max(1, q.effort());
        return new Scored(q, score, reason(q, beliefs, focus));
    }

    static String reason(Question q, Map<Attribute, Belief> beliefs, Set<Attribute> focus) {
        if (q.targets().isEmpty()) return "General question with no specific target.";
        Attribute weakest = q.targets().stream()
                .min(Comparator.comparingDouble(a -> confidence(beliefs, a))).orElseThrow();
        Attribute focused = q.targets().stream().filter(focus::contains).findFirst().orElse(null);
        if (focused != null) {
            return "You disputed something about " + focused.label().toLowerCase()
                    + ", so this checks it again from a different angle.";
        }
        String certainty = confidence(beliefs, weakest) < 0.4 ? "still unsure" : "only partly sure";
        if (q.discriminates().size() >= 2) {
            return "We're " + certainty + " about " + weakest.label().toLowerCase()
                    + ", and this helps tell " + q.discriminates().get(0).label().toLowerCase()
                    + " apart from " + q.discriminates().get(1).label().toLowerCase() + ".";
        }
        return "We're " + certainty + " about " + weakest.label().toLowerCase() + ".";
    }

    public static List<Scored> rank(List<Question> bank, Map<Attribute, Belief> beliefs,
                                    Set<String> asked, Set<Attribute> focus) {
        return bank.stream()
                .filter(q -> !asked.contains(q.id()))
                .map(q -> score(q, beliefs, focus))
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparing(s -> s.question().id()))
                .toList();
    }

    public static Optional<Scored> next(List<Question> bank, Map<Attribute, Belief> beliefs,
                                        Set<String> asked, Set<Attribute> focus) {
        return rank(bank, beliefs, asked, focus).stream().findFirst();
    }

    /** Questions asked in a follow-up session opened after a contest. */
    public static final int FOLLOW_UP_QUESTIONS = 3;

    /**
     * Stop at the cap, or (after the minimum) when nothing left is worth asking
     * or the profile is clear enough.
     *
     * @param askedEver       every question this student has answered, in any session (never repeated)
     * @param askedThisSession how many were answered in the current session
     */
    public static boolean shouldStop(List<Question> bank, Map<Attribute, Belief> beliefs,
                                     Set<String> askedEver, int askedThisSession) {
        if (askedThisSession >= MAX_QUESTIONS) return true;
        List<Scored> ranked = rank(bank, beliefs, askedEver, Set.of());
        if (ranked.isEmpty()) return true;
        if (askedThisSession < MIN_QUESTIONS) return false;
        if (ranked.get(0).score() < LOW_VALUE) return true;
        long clear = beliefs.values().stream().filter(b -> b.confidence() >= USEFUL_CONFIDENCE).count();
        return (double) clear / Attribute.values().length >= 0.7;
    }

    /** A follow-up session stops after a few questions, or when nothing left targets the contested attributes. */
    public static boolean shouldStopFollowUp(List<Question> bank, Set<String> askedEver,
                                             int askedThisSession, Set<Attribute> focus) {
        if (askedThisSession >= FOLLOW_UP_QUESTIONS) return true;
        return bank.stream().filter(q -> !askedEver.contains(q.id()))
                .noneMatch(q -> q.targets().stream().anyMatch(focus::contains));
    }
}
