package com.recourse.engine;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A career profile. {@code weights} says how much the career rewards each
 * attribute (0..1), or penalises it when negative (e.g. an entrepreneur's
 * work rarely suits someone who needs a lot of structure).
 */
public record Career(
        String id,
        String title,
        String summary,
        String riasec,
        String onetCode,
        Map<Attribute, Double> weights,
        List<String> routes,
        List<String> entranceExams,
        List<Source> sources,
        boolean verified) {

    public record Source(String label, String url) {}

    /** Holland code letters weighted by position: first letter matters most. */
    public static final double[] RIASEC_POSITION_WEIGHTS = {1.0, 0.7, 0.45};

    public static Map<Attribute, Double> interestWeights(String riasec) {
        Map<Attribute, Double> w = new EnumMap<>(Attribute.class);
        for (int i = 0; i < riasec.length() && i < RIASEC_POSITION_WEIGHTS.length; i++) {
            w.put(fromLetter(riasec.charAt(i)), RIASEC_POSITION_WEIGHTS[i]);
        }
        return w;
    }

    public static Attribute fromLetter(char letter) {
        return switch (Character.toUpperCase(letter)) {
            case 'R' -> Attribute.REALISTIC;
            case 'I' -> Attribute.INVESTIGATIVE;
            case 'A' -> Attribute.ARTISTIC;
            case 'S' -> Attribute.SOCIAL;
            case 'E' -> Attribute.ENTERPRISING;
            case 'C' -> Attribute.CONVENTIONAL;
            default -> throw new IllegalArgumentException("Not a RIASEC letter: " + letter);
        };
    }

    public char primaryType() {
        return riasec.charAt(0);
    }
}
