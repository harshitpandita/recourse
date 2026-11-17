package com.recourse.explain;

import com.recourse.api.Views;
import com.recourse.service.RecommendationService;
import com.recourse.store.StudentEntity;
import com.recourse.store.StudentRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Measures explanation latency and quality for one local model.
 *
 * It uses the app's own prompt and OllamaClient on the real recommendations of
 * the three demo students (3 students x top 3 careers = 9 prompts), each repeated
 * a few times. The first call loads the model into memory; it is recorded as the
 * cold start and kept out of the median and p90.
 *
 * Not part of the normal test run. Run it with:
 *   mvn test -Pbenchmark -Dbenchmark.model=phi4-mini
 * Results are appended to backend/benchmark-results.csv.
 */
@Tag("benchmark")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:recourse-benchmark;DB_CLOSE_DELAY=-1")
@ActiveProfiles("demo")
class ExplanationBenchmarkTest {

    /** Generous, so slow models are measured instead of cut off. */
    static final int BENCHMARK_TIMEOUT_SECONDS = 120;
    /** The app's own timeout (recourse.llm.timeout-seconds in application.properties). */
    static final int APP_TIMEOUT_SECONDS = 20;

    static final List<String> DEMO_STUDENTS = List.of("Aarav (demo)", "Meera (demo)", "Kabir (demo)");
    static final List<String> INVITES_DISAGREEMENT = List.of(
            "disagree", "doesn't sound like", "does not sound like", "doesn't feel like",
            "tell us", "let us know", "push back");
    static final Pattern NUMBER = Pattern.compile("\\d+");

    static final String HEADER = "started_at,model,student,career,run,cold_start,latency_ms,within_app_timeout,"
            + "usable,chars,words,sentences,invites_disagreement,unlisted_numbers,text";

    @Autowired StudentRepository students;
    @Autowired RecommendationService recommendations;
    @Value("${recourse.llm.base-url}") String baseUrl;

    /** One thing to explain: a demo student's recommendation. */
    record Item(String student, Views.Recommendation rec) {}

    @Test
    void benchmark() throws IOException {
        String model = System.getProperty("benchmark.model", "phi4-mini");
        int runs = Integer.parseInt(System.getProperty("benchmark.runs", "3"));
        Path out = Path.of(System.getProperty("benchmark.out", "benchmark-results.csv"));
        OllamaClient llm = new OllamaClient(new LlmProperties(true, baseUrl, model, BENCHMARK_TIMEOUT_SECONDS));
        if (!llm.reachable()) fail("Ollama isn't running at " + baseUrl + ". Start it and run the benchmark again.");

        List<Item> items = items();
        String startedAt = Instant.now().toString();
        List<String> rows = new ArrayList<>();

        // Cold start: the first call also loads the model
        String cold = call(llm, items.get(0), startedAt, model, 0, true, rows);
        if (cold.isEmpty()) fail("No answer from '" + model + "'. Is it pulled? Try: ollama pull " + model);

        List<Long> times = new ArrayList<>();
        int usable = 0;
        for (int run = 1; run <= runs; run++) {
            for (Item item : items) {
                long start = System.nanoTime();
                String text = call(llm, item, startedAt, model, run, false, rows);
                times.add((System.nanoTime() - start) / 1_000_000);
                if (isUsable(text)) usable++;
            }
        }
        write(out, rows);

        times.sort(Long::compare);
        System.out.printf(Locale.ROOT, "BENCH summary %s: %d calls, median %d ms, p90 %d ms, max %d ms, usable %d/%d. Saved to %s%n",
                model, times.size(), percentile(times, 0.5), percentile(times, 0.9), times.get(times.size() - 1),
                usable, times.size(), out.toAbsolutePath());
    }

    /** Asks the model once, adds a CSV row, and returns the text ("" if it failed). */
    private String call(OllamaClient llm, Item item, String startedAt, String model, int run, boolean coldStart,
                        List<String> rows) {
        String facts = ExplanationService.facts(item.rec());
        long start = System.nanoTime();
        String text = llm.generate(ExplanationService.prompt(item.rec(), facts)).orElse("");
        long ms = (System.nanoTime() - start) / 1_000_000;
        String lower = text.toLowerCase(Locale.ROOT);

        rows.add(String.join(",",
                startedAt, quote(model), quote(item.student()), item.rec().careerId(),
                Integer.toString(run), Boolean.toString(coldStart), Long.toString(ms),
                Boolean.toString(ms <= APP_TIMEOUT_SECONDS * 1000L),
                Boolean.toString(isUsable(text)),
                Integer.toString(text.length()), Integer.toString(words(text)), Integer.toString(sentences(text)),
                Boolean.toString(INVITES_DISAGREEMENT.stream().anyMatch(lower::contains)),
                Integer.toString(unlistedNumbers(text, facts)),
                quote(text)));
        System.out.printf(Locale.ROOT, "BENCH %s run %d %-14s %-30s %6d ms%n",
                model, run, item.student(), item.rec().title(), ms);
        return text;
    }

    private List<Item> items() {
        List<Item> items = new ArrayList<>();
        for (String name : DEMO_STUDENTS) {
            String id = students.findAll().stream().filter(s -> s.getName().equals(name))
                    .map(StudentEntity::getId).findFirst().orElseThrow();
            recommendations.recommend(id).forEach(r -> items.add(new Item(name, r)));
        }
        return items;
    }

    private static void write(Path out, List<String> rows) throws IOException {
        boolean fresh = Files.notExists(out);
        try (var w = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (fresh) {
                w.write(HEADER);
                w.newLine();
            }
            for (String row : rows) {
                w.write(row);
                w.newLine();
            }
        }
    }

    /** The same check the app uses to decide between the model's text and the template. */
    static boolean isUsable(String text) {
        return !text.isEmpty() && ExplanationService.usable(text);
    }

    static long percentile(List<Long> sorted, double p) {
        return sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * p) - 1));
    }

    static int words(String text) {
        return text.isBlank() ? 0 : text.strip().split("\\s+").length;
    }

    static int sentences(String text) {
        return text.isBlank() ? 0 : text.strip().split("(?<=[.!?])\\s+").length;
    }

    /** Numbers in the text that aren't in the facts: a rough sign of made-up figures. */
    static int unlistedNumbers(String text, String facts) {
        int n = 0;
        Matcher m = NUMBER.matcher(text);
        while (m.find()) if (!facts.contains(m.group())) n++;
        return n;
    }

    private static String quote(String s) {
        return "\"" + s.replace("\"", "\"\"").replace('\r', ' ').replace('\n', ' ') + "\"";
    }
}
