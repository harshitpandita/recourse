package com.recourse.explain;

import com.recourse.api.Views;
import com.recourse.engine.Evidence;
import com.recourse.service.RecommendationService;
import com.recourse.service.StudentModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns an already-computed match into a short, readable explanation.
 * The language model only rephrases facts it is given; it never decides
 * anything. If it is slow, unavailable or produces something unusable,
 * a deterministic template is used instead.
 */
@Service
public class ExplanationService {

    private final RecommendationService recommendations;
    private final StudentModel model;
    private final OllamaClient llm;
    private final Map<String, Views.Explanation> cache = new ConcurrentHashMap<>();

    public ExplanationService(RecommendationService recommendations, StudentModel model, OllamaClient llm) {
        this.recommendations = recommendations;
        this.model = model;
        this.llm = llm;
    }

    public Views.Explanation explain(String studentId, String careerId) {
        List<Evidence> evidence = model.evidence(studentId);
        String key = studentId + "|" + careerId + "|" + evidence.size();
        return cache.computeIfAbsent(key, k -> build(studentId, careerId));
    }

    private Views.Explanation build(String studentId, String careerId) {
        Views.Recommendation r = recommendations.recommendation(studentId, careerId);
        long start = System.currentTimeMillis();
        String facts = facts(r);
        return llm.generate(prompt(r, facts))
                .map(ExplanationService::tidy)
                .filter(ExplanationService::usable)
                .map(text -> new Views.Explanation(careerId, text, "llm:" + llm.model(), System.currentTimeMillis() - start))
                .orElseGet(() -> new Views.Explanation(careerId, template(r), "template", System.currentTimeMillis() - start));
    }

    /** The only things the model is allowed to talk about. */
    static String facts(Views.Recommendation r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Career: ").append(r.title()).append(" - ").append(r.summary()).append('\n');
        sb.append("How sure the system is about this match: ").append(pct(r.confidence())).append('\n');
        if (!r.reasons().isEmpty()) {
            sb.append("Why it fits:\n");
            r.reasons().forEach(f -> sb.append("- ").append(f.label()).append('\n'));
        }
        if (!r.concerns().isEmpty()) {
            sb.append("Possible concerns:\n");
            r.concerns().forEach(f -> sb.append("- ").append(f.label()).append('\n'));
        }
        List<Views.Evidence> shown = r.evidence().stream().filter(e -> e.optionText() != null).limit(3).toList();
        if (!shown.isEmpty()) {
            sb.append("Things the student actually said:\n");
            shown.forEach(e -> sb.append("- \"").append(e.optionText()).append("\" (relates to ").append(e.label()).append(")\n"));
        }
        return sb.toString();
    }

    static String prompt(Views.Recommendation r, String facts) {
        return """
                You are writing a short note for an Indian student explaining why a career was suggested to them.
                Use ONLY the facts below. Do not add salaries, statistics, colleges or claims that are not listed.
                Write 3 to 4 sentences in second person ("you"). Be warm but plain, not salesy.
                Refer to one or two things the student said. If there is a concern, mention it honestly.
                End by reminding them they can disagree if this doesn't sound like them.
                Do not use headings, bullet points or emojis.
                It is not a letter: no greeting such as "Dear student" and no sign-off. Start directly with the career.

                FACTS:
                %s
                """.formatted(facts);
    }

    /** "Dear Student," or "Hi there!" at the very start. */
    private static final Pattern GREETING = Pattern.compile(
            "^\\s*(dear|hi|hello|hey|greetings)\\b[^,!.\\n]{0,40}[,!]\\s*", Pattern.CASE_INSENSITIVE);
    /** "I hope you're doing well." right after it. */
    private static final Pattern PLEASANTRY = Pattern.compile(
            "^\\s*I hope (you're|you are|this (message|note) finds you)[^.!]*[.!]\\s*", Pattern.CASE_INSENSITIVE);
    /** "Best regards, ..." on its own line at the end, and anything after it. */
    private static final Pattern SIGN_OFF = Pattern.compile(
            "\\n\\s*(best|warm|kind)?\\s*(regards|wishes|sincerely|cheers|yours)[\\s\\S]*$", Pattern.CASE_INSENSITIVE);

    /**
     * Small models sometimes write the note as a letter despite the prompt. Removes an opening
     * greeting ("Dear Student,"), a stock pleasantry ("I hope you're doing well.") and a closing
     * sign-off, leaving the content untouched.
     */
    static String tidy(String text) {
        String t = GREETING.matcher(text).replaceFirst("");
        t = PLEASANTRY.matcher(t).replaceFirst("");
        t = SIGN_OFF.matcher(t).replaceFirst("");
        t = t.strip();
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    static boolean usable(String text) {
        String t = text.toLowerCase();
        return text.length() >= 80 && text.length() <= 1200 && !t.contains("as an ai") && !t.contains("```");
    }

    /** Deterministic fallback built from the same facts. */
    static String template(Views.Recommendation r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.title()).append(" came up because ");
        if (r.reasons().isEmpty()) {
            sb.append("it is a reasonable overall fit across your answers so far");
        } else {
            sb.append("your answers point toward ")
              .append(r.reasons().stream().map(f -> f.label().toLowerCase()).collect(Collectors.joining(", ")));
        }
        sb.append(". ");
        // Drop the option's own full stop so the sentence doesn't end in ".".
        r.evidence().stream().filter(e -> e.optionText() != null).findFirst().ifPresent(e ->
                sb.append("For example, you chose \"").append(e.optionText().strip().replaceAll("\\.+$", ""))
                  .append("\". "));
        if (!r.concerns().isEmpty()) {
            sb.append("One thing to think about: your answers suggest ")
              .append(r.concerns().get(0).label().toLowerCase())
              .append(" may not line up well with this path. ");
        }
        sb.append("We're about ").append(pct(r.confidence()))
          .append(" sure about this match. If it doesn't sound like you, tell us what we got wrong.");
        return sb.toString();
    }

    static String pct(double v) { return Math.round(v * 100) + "%"; }
}
