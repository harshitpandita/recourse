package com.recourse.engine;

import java.util.List;

/** A question from the bank, as loaded from questions.json. */
public record Question(
        String id,
        String text,
        Source type,
        List<String> contexts,
        double reliability,
        int effort,
        List<Attribute> targets,
        List<Attribute> discriminates,
        List<Option> options) {

    public record Option(String id, String text, List<EvidenceSpec> evidence) {}

    public record EvidenceSpec(Attribute attribute, Direction direction, double strength) {}

    public Option option(String optionId) {
        return options.stream().filter(o -> o.id().equals(optionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No option " + optionId + " on question " + id));
    }

    public String primaryContext() {
        return contexts == null || contexts.isEmpty() ? null : contexts.get(0);
    }
}
