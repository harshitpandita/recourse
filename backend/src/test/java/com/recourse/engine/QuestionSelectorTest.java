package com.recourse.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.recourse.engine.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestionSelectorTest {

    private static Map<Attribute, Belief> none() {
        return BeliefCalculator.computeAll(List.of());
    }

    @Test
    void prefersQuestionsAboutUncertainAttributes() {
        Question aboutSocial = question("q1", List.of(Attribute.SOCIAL), List.of(), opt("a", Attribute.SOCIAL, Direction.SUPPORTS, 0.7));
        Question aboutArtistic = question("q2", List.of(Attribute.ARTISTIC), List.of(), opt("a", Attribute.ARTISTIC, Direction.SUPPORTS, 0.7));
        List<Evidence> ev = List.of(sup("1", Attribute.SOCIAL, 0.9), sup("2", Attribute.SOCIAL, 0.9), sup("3", Attribute.SOCIAL, 0.9));
        var next = QuestionSelector.next(List.of(aboutSocial, aboutArtistic), BeliefCalculator.computeAll(ev), Set.of(), Set.of());
        assertEquals("q2", next.orElseThrow().question().id());
    }

    @Test
    void neverRepeatsAQuestion() {
        List<Question> bank = bank(5);
        var ranked = QuestionSelector.rank(bank, none(), Set.of("q000", "q001"), Set.of());
        assertEquals(3, ranked.size());
        assertTrue(ranked.stream().noneMatch(s -> s.question().id().equals("q000")));
    }

    @Test
    void returnsEmptyWhenBankIsExhausted() {
        List<Question> bank = bank(2);
        assertTrue(QuestionSelector.next(bank, none(), Set.of("q000", "q001"), Set.of()).isEmpty());
    }

    @Test
    void discriminatingQuestionsWinWhenAttributesAreAmbiguous() {
        Question plain = question("q1", List.of(Attribute.SOCIAL), List.of(), opt("a", Attribute.SOCIAL, Direction.SUPPORTS, 0.7));
        Question discriminating = question("q2", List.of(Attribute.SOCIAL), List.of(Attribute.SOCIAL, Attribute.ENTERPRISING),
                opt("a", Attribute.SOCIAL, Direction.SUPPORTS, 0.7));
        var next = QuestionSelector.next(List.of(plain, discriminating), none(), Set.of(), Set.of());
        assertEquals("q2", next.orElseThrow().question().id());
    }

    @Test
    void higherEffortLowersScore() {
        Question easy = question("q1", List.of(Attribute.SOCIAL), List.of(), opt("a", Attribute.SOCIAL, Direction.SUPPORTS, 0.7));
        Question hard = new Question("q2", "Hard", Source.MINI_TASK, List.of(), 0.65, 3, List.of(Attribute.SOCIAL), List.of(),
                easy.options());
        assertTrue(QuestionSelector.score(easy, none(), Set.of()).score() > QuestionSelector.score(hard, none(), Set.of()).score());
    }

    @Test
    void contestedAttributesAreBoosted() {
        Question aboutSocial = question("q1", List.of(Attribute.SOCIAL), List.of(), opt("a", Attribute.SOCIAL, Direction.SUPPORTS, 0.7));
        Question aboutPace = question("q2", List.of(Attribute.PACE), List.of(), opt("a", Attribute.PACE, Direction.SUPPORTS, 0.7));
        var next = QuestionSelector.next(List.of(aboutSocial, aboutPace), none(), Set.of(), Set.of(Attribute.PACE));
        assertEquals("q2", next.orElseThrow().question().id());
        assertTrue(next.get().reason().contains("disputed"));
    }

    @Test
    void everyPickHasAPlainEnglishReason() {
        for (var s : QuestionSelector.rank(bank(10), none(), Set.of(), Set.of())) {
            assertNotNull(s.reason());
            assertFalse(s.reason().isBlank());
        }
    }

    @Test
    void selectionIsDeterministic() {
        var a = QuestionSelector.rank(bank(24), none(), Set.of(), Set.of());
        var b = QuestionSelector.rank(bank(24), none(), Set.of(), Set.of());
        assertEquals(a.stream().map(s -> s.question().id()).toList(), b.stream().map(s -> s.question().id()).toList());
    }

    @Test
    void doesNotStopBeforeMinimum() {
        List<Question> bank = bank(40);
        Set<String> asked = new HashSet<>();
        for (int i = 0; i < QuestionSelector.MIN_QUESTIONS - 1; i++) asked.add(bank.get(i).id());
        assertFalse(QuestionSelector.shouldStop(bank, none(), asked, asked.size()));
    }

    @Test
    void stopsAtMaximum() {
        List<Question> bank = bank(40);
        Set<String> asked = new HashSet<>();
        for (int i = 0; i < QuestionSelector.MAX_QUESTIONS; i++) asked.add(bank.get(i).id());
        assertTrue(QuestionSelector.shouldStop(bank, none(), asked, asked.size()));
    }

    @Test
    void stopsWhenNothingIsLeftToAsk() {
        List<Question> bank = bank(3);
        assertTrue(QuestionSelector.shouldStop(bank, none(), Set.of("q000", "q001", "q002"), 3));
    }

    @Test
    void differentAnswersLeadToDifferentNextQuestions() {
        // q0 asks about Social. A clear "yes" settles it; a weak "no" leaves it ambiguous.
        // q1 helps tell Social apart from Artistic, so it is only worth asking while Social is unclear.
        Question q0 = question("q0", List.of(Attribute.SOCIAL), List.of(Attribute.SOCIAL),
                opt("a", Attribute.SOCIAL, Direction.SUPPORTS, 0.8), opt("b", Attribute.SOCIAL, Direction.CONTRADICTS, 0.4));
        Question q1 = question("q1", List.of(Attribute.ARTISTIC), List.of(Attribute.SOCIAL, Attribute.ARTISTIC),
                opt("a", Attribute.ARTISTIC, Direction.SUPPORTS, 0.7));
        Question q2 = question("q2", List.of(Attribute.PACE), List.of(Attribute.PACE, Attribute.VARIETY),
                opt("a", Attribute.PACE, Direction.SUPPORTS, 0.7));
        List<Question> bank = List.of(q0, q1, q2);
        assertEquals("q0", QuestionSelector.next(bank, none(), Set.of(), Set.of()).orElseThrow().question().id());

        var yes = BeliefCalculator.computeAll(EvidenceFactory.fromAnswer(q0, "a", ids(), Instant.EPOCH));
        var no = BeliefCalculator.computeAll(EvidenceFactory.fromAnswer(q0, "b", ids(), Instant.EPOCH));
        String afterYes = QuestionSelector.next(bank, yes, Set.of("q0"), Set.of()).orElseThrow().question().id();
        String afterNo = QuestionSelector.next(bank, no, Set.of("q0"), Set.of()).orElseThrow().question().id();
        assertEquals("q2", afterYes);
        assertEquals("q1", afterNo);
    }

    @Test
    void fullRunAsksBetweenMinimumAndMaximum() {
        List<String> asked = run(bank(48), "a");
        assertTrue(asked.size() >= QuestionSelector.MIN_QUESTIONS);
        assertTrue(asked.size() <= QuestionSelector.MAX_QUESTIONS);
        assertEquals(asked.size(), new HashSet<>(asked).size(), "no question is asked twice");
    }

    private static List<String> run(List<Question> bank, String alwaysPick) {
        List<Evidence> ev = new ArrayList<>();
        Set<String> asked = new java.util.LinkedHashSet<>();
        var ids = ids();
        while (!QuestionSelector.shouldStop(bank, BeliefCalculator.computeAll(ev), asked, asked.size())) {
            Question q = QuestionSelector.next(bank, BeliefCalculator.computeAll(ev), asked, Set.of()).orElseThrow().question();
            asked.add(q.id());
            ev.addAll(EvidenceFactory.fromAnswer(q, alwaysPick, ids, Instant.EPOCH));
        }
        return new ArrayList<>(asked);
    }

    @Test
    void followUpStopsAfterAFewQuestions() {
        List<Question> bank = bank(48);
        assertFalse(QuestionSelector.shouldStopFollowUp(bank, Set.of(), 0, Set.of(Attribute.SOCIAL)));
        assertTrue(QuestionSelector.shouldStopFollowUp(bank, Set.of(), QuestionSelector.FOLLOW_UP_QUESTIONS, Set.of(Attribute.SOCIAL)));
    }

    @Test
    void followUpStopsWhenNoQuestionTargetsTheFocus() {
        List<Question> bank = List.of(question("q1", List.of(Attribute.SOCIAL), List.of(), opt("a", Attribute.SOCIAL, Direction.SUPPORTS, 0.7)));
        assertTrue(QuestionSelector.shouldStopFollowUp(bank, Set.of(), 0, Set.of(Attribute.PACE)));
    }
}
