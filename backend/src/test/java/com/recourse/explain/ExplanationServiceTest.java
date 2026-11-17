package com.recourse.explain;

import com.recourse.api.Views;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The deterministic template used when the language model is unavailable. */
class ExplanationServiceTest {

    private static Views.Recommendation rec(String optionText) {
        Views.Evidence said = new Views.Evidence("e1", "investigative", "Investigative", "interests", "supports",
                0.8, 0.65, "forced_choice", "q001", "Which pulls you in most?", optionText,
                null, null, List.of(), null);
        return new Views.Recommendation("data-scientist", "Data scientist", "Finds patterns in data.", "ICE",
                0.7, 0.6, List.of(),
                List.of(new Views.Factor("investigative", "Investigative", 0.8, "Research isn't my thing")),
                List.of(new Views.Factor("ambiguity_tolerance", "Comfort with ambiguity", 0.4, "")),
                List.of(said), List.of(), List.of(), List.of(), false);
    }

    @Test
    void quotedAnswerEndingInAFullStopDoesNotGiveADoubleFullStop() {
        String text = ExplanationService.template(rec("Reading about how the universe works."));
        assertTrue(text.contains("you chose \"Reading about how the universe works\". "), text);
        assertFalse(text.contains(".\"."), text);
    }

    @Test
    void quotedAnswerWithoutAFullStopIsUnchanged() {
        String text = ExplanationService.template(rec("80 rupees"));
        assertTrue(text.contains("you chose \"80 rupees\". "), text);
    }

    @Test
    void templateMentionsTheConcernTheConfidenceAndInvitesDisagreement() {
        String text = ExplanationService.template(rec("Anything"));
        assertTrue(text.contains("comfort with ambiguity"), text);
        assertTrue(text.contains("60% sure"), text);
        assertTrue(text.contains("tell us what we got wrong"), text);
        assertTrue(ExplanationService.usable(text), "the template should pass the same check as the model");
    }
}
