package com.recourse.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Small builders shared by the engine tests. */
final class Fixtures {
    private Fixtures() {}

    static Supplier<String> ids() {
        AtomicInteger n = new AtomicInteger();
        return () -> "e" + n.incrementAndGet();
    }

    static Evidence ev(String id, Attribute a, Direction d, double strength, double reliability) {
        return new Evidence(id, a, d, strength, reliability, Source.SCENARIO, "q", "a", null, null, null);
    }

    static Evidence sup(String id, Attribute a, double strength) {
        return ev(id, a, Direction.SUPPORTS, strength, 0.7);
    }

    static Evidence con(String id, Attribute a, double strength) {
        return ev(id, a, Direction.CONTRADICTS, strength, 0.7);
    }

    static Question.Option opt(String id, Attribute a, Direction d, double s) {
        return new Question.Option(id, "option " + id, List.of(new Question.EvidenceSpec(a, d, s)));
    }

    static Question question(String id, List<Attribute> targets, List<Attribute> discriminates, Question.Option... options) {
        return new Question(id, "Question " + id, Source.FORCED_CHOICE, List.of("personal"), 0.65, 1,
                targets, discriminates, List.of(options));
    }

    /** A bank of n simple questions, each targeting one attribute in turn. */
    static List<Question> bank(int n) {
        List<Question> out = new ArrayList<>();
        Attribute[] all = Attribute.values();
        for (int i = 0; i < n; i++) {
            Attribute a = all[i % all.length];
            out.add(question(String.format("q%03d", i), List.of(a), List.of(),
                    opt("a", a, Direction.SUPPORTS, 0.7), opt("b", a, Direction.CONTRADICTS, 0.7)));
        }
        return out;
    }

    static Career career(String id, String riasec, Map<Attribute, Double> extra) {
        Map<Attribute, Double> w = new java.util.EnumMap<>(Career.interestWeights(riasec));
        w.putAll(extra);
        return new Career(id, id, "", riasec, null, w, List.of(), List.of(), List.of(), false);
    }
}
