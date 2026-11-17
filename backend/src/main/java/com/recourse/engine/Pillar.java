package com.recourse.engine;

/** The four constructs the student model is built on (see synopsis, Background section). */
public enum Pillar {
    INTERESTS("Interests", "Holland's RIASEC types"),
    VALUES("Work values", "the O*NET Work Importance Profiler"),
    WORK_STYLE("Work style", "how you prefer to work"),
    ABILITIES("Abilities", "your own view of your strengths");

    private final String label;
    private final String basis;

    Pillar(String label, String basis) {
        this.label = label;
        this.basis = basis;
    }

    public String label() { return label; }
    public String basis() { return basis; }
}
