package com.recourse.engine;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Every dimension the engine can hold a belief about, grouped by pillar.
 * The JSON key (e.g. "investigative") is the lowercase enum name.
 */
public enum Attribute {
    // Interests: Holland's six RIASEC types
    REALISTIC(Pillar.INTERESTS, "Realistic", "Hands-on work with tools, machines, nature or physical things"),
    INVESTIGATIVE(Pillar.INTERESTS, "Investigative", "Figuring things out: research, analysis, ideas"),
    ARTISTIC(Pillar.INTERESTS, "Artistic", "Creating, designing and expressing"),
    SOCIAL(Pillar.INTERESTS, "Social", "Helping, teaching and working with people"),
    ENTERPRISING(Pillar.INTERESTS, "Enterprising", "Leading, persuading and building ventures"),
    CONVENTIONAL(Pillar.INTERESTS, "Conventional", "Organising information, records and systems"),

    // Work values: O*NET Work Importance Profiler categories
    ACHIEVEMENT(Pillar.VALUES, "Achievement", "Using your abilities and seeing results"),
    INDEPENDENCE(Pillar.VALUES, "Independence", "Making your own decisions and working on your own terms"),
    RECOGNITION(Pillar.VALUES, "Recognition", "Advancement, prestige and being seen as good at what you do"),
    RELATIONSHIPS(Pillar.VALUES, "Relationships", "Good colleagues and work that serves others"),
    SUPPORT(Pillar.VALUES, "Support", "Fair, supportive management and clear backing"),
    WORKING_CONDITIONS(Pillar.VALUES, "Working conditions", "Pay, security and a stable, comfortable work life"),

    // Work style preferences
    AUTONOMY(Pillar.WORK_STYLE, "Autonomy", "Preferring to plan and run your own work"),
    COLLABORATION(Pillar.WORK_STYLE, "Collaboration", "Preferring to work closely with others"),
    STRUCTURE(Pillar.WORK_STYLE, "Structure", "Preferring clear processes and defined expectations"),
    AMBIGUITY_TOLERANCE(Pillar.WORK_STYLE, "Comfort with ambiguity", "Being fine with open-ended, unclear problems"),
    PACE(Pillar.WORK_STYLE, "Fast pace", "Enjoying quick, high-tempo work"),
    VARIETY(Pillar.WORK_STYLE, "Variety", "Wanting work that changes often"),

    // Self-reported abilities
    QUANTITATIVE(Pillar.ABILITIES, "Quantitative reasoning", "Working with numbers and maths"),
    VERBAL(Pillar.ABILITIES, "Verbal reasoning", "Reading, writing and arguing clearly"),
    SPATIAL(Pillar.ABILITIES, "Spatial reasoning", "Picturing shapes, layouts and how things fit"),
    ANALYTICAL(Pillar.ABILITIES, "Analytical problem solving", "Breaking problems down logically"),
    CREATIVE(Pillar.ABILITIES, "Creative thinking", "Coming up with original ideas"),
    PATTERN(Pillar.ABILITIES, "Pattern recognition", "Spotting rules and regularities");

    private final Pillar pillar;
    private final String label;
    private final String description;

    Attribute(Pillar pillar, String label, String description) {
        this.pillar = pillar;
        this.label = label;
        this.description = description;
    }

    public Pillar pillar() { return pillar; }
    public String label() { return label; }
    public String description() { return description; }

    public String key() { return name().toLowerCase(Locale.ROOT); }

    public static Attribute fromKey(String key) {
        return valueOf(key.trim().toUpperCase(Locale.ROOT));
    }

    public static List<Attribute> of(Pillar pillar) {
        return Arrays.stream(values()).filter(a -> a.pillar == pillar).toList();
    }
}
