package com.recourse.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Turns an answered option, or an objection, into new evidence. Never edits existing evidence. */
public final class EvidenceFactory {

    /** Reliability given to a student's direct correction of the model. */
    public static final double CONTEST_RELIABILITY = 0.7;

    private EvidenceFactory() {}

    public static List<Evidence> fromAnswer(Question q, String optionId, Supplier<String> ids, Instant at) {
        Question.Option option = q.option(optionId);
        List<Evidence> out = new ArrayList<>();
        for (Question.EvidenceSpec spec : option.evidence()) {
            out.add(new Evidence(ids.get(), spec.attribute(), spec.direction(), spec.strength(),
                    q.reliability(), q.type(), q.id(), optionId, q.primaryContext(), null, at));
        }
        return out;
    }

    /**
     * "That answer doesn't describe me." Adds counter-evidence of equal strength
     * in the opposite direction. The original stays, so the belief becomes
     * visibly contradictory and its confidence drops, rather than silently flipping.
     */
    public static Evidence disputing(Evidence original, String studentReason, Supplier<String> ids, Instant at) {
        if (original.direction() == Direction.NEUTRAL) {
            throw new IllegalArgumentException("Neutral evidence cannot be disputed");
        }
        return new Evidence(ids.get(), original.attribute(), original.direction().opposite(),
                original.strength(), CONTEST_RELIABILITY, Source.CONTEST,
                original.questionId(), original.optionId(), studentReason, original.id(), at);
    }

    /** A direct statement from the student about one attribute, e.g. from a contest reason. */
    public static Evidence statement(Attribute attribute, Direction direction, double strength,
                                     String studentReason, Supplier<String> ids, Instant at) {
        return new Evidence(ids.get(), attribute, direction, strength, CONTEST_RELIABILITY,
                Source.CONTEST, null, null, studentReason, null, at);
    }
}
