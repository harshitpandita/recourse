package com.recourse.evaluation;

import com.recourse.engine.Attribute;
import com.recourse.engine.Direction;
import com.recourse.engine.Pillar;
import com.recourse.engine.Question;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A simulated student with a known "true lean" on every attribute, from -1 (clearly not)
 * to +1 (clearly yes).
 *
 * Interests: one primary RIASEC letter at +0.8, a different second letter at +0.5, the other
 * four between -0.6 and +0.2. Values, work style and abilities: anywhere between -0.8 and +0.8.
 */
record Persona(String id, char primary, char secondary, Map<Attribute, Double> lean) {

    static final String LETTERS = "RIASEC";
    static final double PRIMARY_LEAN = 0.8;
    static final double SECONDARY_LEAN = 0.5;

    /** {@code n} personas; primary letters cycle through R, I, A, S, E, C so every family is tested equally. */
    static List<Persona> generate(int n, long seed) {
        Random rng = new Random(seed);
        List<Persona> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            char primary = LETTERS.charAt(i % LETTERS.length());
            char secondary;
            do { secondary = LETTERS.charAt(rng.nextInt(LETTERS.length())); } while (secondary == primary);
            Map<Attribute, Double> lean = new EnumMap<>(Attribute.class);
            for (Attribute a : Attribute.values()) {
                if (a.pillar() == Pillar.INTERESTS) {
                    char letter = Character.toUpperCase(a.name().charAt(0));
                    lean.put(a, letter == primary ? PRIMARY_LEAN
                            : letter == secondary ? SECONDARY_LEAN
                            : round(-0.6 + 0.8 * rng.nextDouble()));
                } else {
                    lean.put(a, round(-0.8 + 1.6 * rng.nextDouble()));
                }
            }
            out.add(new Persona(String.format("P%02d", i + 1), primary, secondary, lean));
        }
        return out;
    }

    /** Rescaled to the model's 0..1 level scale, so it can be compared with Belief.level(). */
    double trueLevel(Attribute a) {
        return 0.5 + 0.5 * lean.get(a);
    }

    /**
     * How well an option describes this persona: for each piece of evidence the option would
     * create, strength x (+1 supports, -1 contradicts, 0 neutral) x the persona's true lean.
     */
    double agreement(Question.Option option) {
        double total = 0;
        for (Question.EvidenceSpec spec : option.evidence()) {
            int sign = spec.direction() == Direction.SUPPORTS ? 1 : spec.direction() == Direction.CONTRADICTS ? -1 : 0;
            total += spec.strength() * sign * lean.get(spec.attribute());
        }
        return total;
    }

    /** The honest answer: the option that best describes the persona. Ties go to the first option. */
    String honestAnswer(Question q) {
        Question.Option best = q.options().get(0);
        for (Question.Option o : q.options()) {
            if (agreement(o) > agreement(best)) best = o;
        }
        return best.id();
    }

    /** One answer, possibly replaced by a random option with probability {@code noise}. */
    Answer answer(Question q, double noise, Random rng) {
        if (noise > 0 && rng.nextDouble() < noise) {
            return new Answer(q.options().get(rng.nextInt(q.options().size())).id(), true);
        }
        return new Answer(honestAnswer(q), false);
    }

    record Answer(String optionId, boolean random) {}

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
