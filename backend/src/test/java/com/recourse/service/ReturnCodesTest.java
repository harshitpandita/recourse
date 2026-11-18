package com.recourse.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReturnCodesTest {

    @Test
    void alphabetLeavesOutCharactersThatAreEasyToMisread() {
        for (char c : "0O1I".toCharArray()) {
            assertEquals(-1, ReturnCodes.ALPHABET.indexOf(c), "alphabet contains " + c);
        }
        assertEquals(32, ReturnCodes.ALPHABET.length());
        assertEquals(32, ReturnCodes.ALPHABET.chars().distinct().count(), "alphabet repeats a character");
    }

    @Test
    void generatedCodesAreSixCharactersFromTheAlphabet() {
        Random random = new Random(42);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String code = ReturnCodes.generate(random);
            assertTrue(ReturnCodes.isValid(code), code);
            seen.add(code);
        }
        assertTrue(seen.size() > 1990, "codes repeat far too often: " + seen.size() + " distinct of 2000");
    }

    @Test
    void typedCodesAreNormalised() {
        assertEquals("K7P3QX", ReturnCodes.normalise("k7p-3qx"));
        assertEquals("K7P3QX", ReturnCodes.normalise(" k7p 3qx "));
        assertEquals("", ReturnCodes.normalise(null));
    }

    @Test
    void codesAreShownWithAHyphenInTheMiddle() {
        assertEquals("K7P-3QX", ReturnCodes.format("K7P3QX"));
        assertNull(ReturnCodes.format(null));
    }

    @Test
    void invalidCodesAreRejected() {
        assertFalse(ReturnCodes.isValid("K7P3Q"));    // too short
        assertFalse(ReturnCodes.isValid("K7P3QO"));   // contains O
        assertFalse(ReturnCodes.isValid("k7p3qx"));   // not normalised
        assertFalse(ReturnCodes.isValid(null));
    }
}
