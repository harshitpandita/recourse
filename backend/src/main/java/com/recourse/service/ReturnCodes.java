package com.recourse.service;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Return codes let a student come back to their results with their name and a short code,
 * without the app ever listing other students.
 *
 * A code is 6 characters from a 32-character alphabet (about a billion possibilities).
 * The alphabet leaves out 0, O, 1 and I, which are easy to misread or mistype.
 * Codes are stored without separators and shown as "K7P-3QX" for readability.
 */
public final class ReturnCodes {
    private ReturnCodes() {}

    public static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    public static final int LENGTH = 6;

    private static final Random RANDOM = new SecureRandom();

    public static String generate() {
        return generate(RANDOM);
    }

    static String generate(Random random) {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    /** What the student typed, made comparable: upper case, spaces and hyphens removed. */
    public static String normalise(String typed) {
        return typed == null ? "" : typed.toUpperCase().replaceAll("[\\s-]", "");
    }

    /** "K7P3QX" becomes "K7P-3QX". */
    public static String format(String code) {
        if (code == null || code.length() != LENGTH) return code;
        return code.substring(0, 3) + "-" + code.substring(3);
    }

    public static boolean isValid(String code) {
        if (code == null || code.length() != LENGTH) return false;
        return code.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0);
    }
}
