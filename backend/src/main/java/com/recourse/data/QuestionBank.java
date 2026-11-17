package com.recourse.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recourse.engine.Attribute;
import com.recourse.engine.Direction;
import com.recourse.engine.Question;
import com.recourse.engine.Source;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Loads questions.json once at startup and validates it. */
@Component
public class QuestionBank {

    record JsonEvidence(String attribute, String direction, double strength) {}
    record JsonOption(String id, String text, List<JsonEvidence> evidence) {}
    record JsonQuestion(String id, String text, String type, List<String> contexts,
                        List<String> attributes_targeted, double reliability, int effort,
                        List<String> discriminates_between, List<JsonOption> options) {}

    private final List<Question> questions;
    private final Map<String, Question> byId;

    @Autowired
    public QuestionBank(ObjectMapper mapper) throws IOException {
        this(load(mapper, "questions.json"));
    }

    public QuestionBank(List<Question> questions) {
        this.questions = List.copyOf(questions);
        this.byId = questions.stream().collect(Collectors.toMap(Question::id, Function.identity()));
    }

    static List<Question> load(ObjectMapper mapper, String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            List<JsonQuestion> raw = mapper.readValue(in, new TypeReference<>() {});
            return raw.stream().map(QuestionBank::convert).toList();
        }
    }

    static Question convert(JsonQuestion q) {
        List<Question.Option> options = q.options().stream()
                .map(o -> new Question.Option(o.id(), o.text(), o.evidence().stream()
                        .map(e -> new Question.EvidenceSpec(Attribute.fromKey(e.attribute()),
                                Direction.valueOf(e.direction().toUpperCase(Locale.ROOT)), e.strength()))
                        .toList()))
                .toList();
        return new Question(q.id(), q.text(), Source.valueOf(q.type().toUpperCase(Locale.ROOT)),
                q.contexts() == null ? List.of() : q.contexts(), q.reliability(), Math.max(1, q.effort()),
                keys(q.attributes_targeted()), keys(q.discriminates_between()), options);
    }

    private static List<Attribute> keys(List<String> names) {
        return names == null ? List.of() : names.stream().map(Attribute::fromKey).toList();
    }

    public List<Question> all() { return questions; }

    public Question get(String id) {
        Question q = byId.get(id);
        if (q == null) throw new NoSuchElementException("Unknown question " + id);
        return q;
    }

    public boolean has(String id) { return byId.containsKey(id); }
}
