package com.recourse.service;

import com.recourse.engine.Attribute;

import java.util.EnumMap;
import java.util.Map;

/** First-person objections the student can pick when disputing a recommendation. */
public final class ContestPrompts {
    private ContestPrompts() {}

    private static final Map<Attribute, String> NOT_ME = new EnumMap<>(Attribute.class);
    private static final Map<Attribute, String> CONCERN_WRONG = new EnumMap<>(Attribute.class);

    static {
        NOT_ME.put(Attribute.REALISTIC, "I don't really enjoy hands-on, practical work");
        NOT_ME.put(Attribute.INVESTIGATIVE, "Research and figuring things out isn't really my thing");
        NOT_ME.put(Attribute.ARTISTIC, "I'm not that drawn to creative or artistic work");
        NOT_ME.put(Attribute.SOCIAL, "I don't want my work to revolve around helping people");
        NOT_ME.put(Attribute.ENTERPRISING, "I don't actually enjoy leading or persuading people");
        NOT_ME.put(Attribute.CONVENTIONAL, "Organising data and records would bore me");
        NOT_ME.put(Attribute.ACHIEVEMENT, "Seeing results isn't what drives me most");
        NOT_ME.put(Attribute.INDEPENDENCE, "I don't need that much independence");
        NOT_ME.put(Attribute.RECOGNITION, "Status and recognition don't matter much to me");
        NOT_ME.put(Attribute.RELATIONSHIPS, "Working with and for people matters less to me than you think");
        NOT_ME.put(Attribute.SUPPORT, "I don't need much backing from a manager");
        NOT_ME.put(Attribute.WORKING_CONDITIONS, "Pay and stability aren't my top priority");
        NOT_ME.put(Attribute.AUTONOMY, "I'd rather not plan my own work all the time");
        NOT_ME.put(Attribute.COLLABORATION, "I prefer working on my own over working in a team");
        NOT_ME.put(Attribute.STRUCTURE, "I don't like rigid processes");
        NOT_ME.put(Attribute.AMBIGUITY_TOLERANCE, "Open-ended, unclear problems stress me out");
        NOT_ME.put(Attribute.PACE, "I don't enjoy fast-paced, high-pressure work");
        NOT_ME.put(Attribute.VARIETY, "I prefer consistency over constant change");
        NOT_ME.put(Attribute.QUANTITATIVE, "I'm not that strong with numbers");
        NOT_ME.put(Attribute.VERBAL, "Reading and writing aren't my strengths");
        NOT_ME.put(Attribute.SPATIAL, "I struggle to picture shapes and layouts");
        NOT_ME.put(Attribute.ANALYTICAL, "Breaking problems down logically isn't my strength");
        NOT_ME.put(Attribute.CREATIVE, "Coming up with original ideas doesn't come easily to me");
        NOT_ME.put(Attribute.PATTERN, "I don't usually spot patterns quickly");

        for (Attribute a : Attribute.values()) {
            CONCERN_WRONG.put(a, "You've got me wrong on " + a.label().toLowerCase() + ", it does suit me");
        }
    }

    /** Disputing a reason: "this isn't actually me". */
    public static String notMe(Attribute a) { return NOT_ME.get(a); }

    /** Disputing a concern: "that's not a problem for me". */
    public static String concernWrong(Attribute a) { return CONCERN_WRONG.get(a); }
}
