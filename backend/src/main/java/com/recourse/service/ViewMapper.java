package com.recourse.service;

import com.recourse.api.Views;
import com.recourse.data.QuestionBank;
import com.recourse.engine.Attribute;
import com.recourse.engine.Belief;
import com.recourse.engine.Evidence;
import com.recourse.engine.Matcher;
import com.recourse.engine.Pillar;
import com.recourse.engine.Question;
import com.recourse.engine.QuestionSelector;
import com.recourse.store.StudentEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Converts engine and entity objects into API views. No logic lives here. */
@Component
public class ViewMapper {

    private final QuestionBank bank;

    public ViewMapper(QuestionBank bank) {
        this.bank = bank;
    }

    static String lower(Enum<?> e) { return e.name().toLowerCase(Locale.ROOT); }

    static double round(double v) { return Math.round(v * 1000) / 1000.0; }

    public Views.Student student(StudentEntity s) {
        return new Views.Student(s.getId(), s.getName(), s.getStage(), s.getCreatedAt());
    }

    public Views.Belief belief(Belief b) {
        Attribute a = b.attribute();
        return new Views.Belief(a.key(), a.label(), a.description(), lower(a.pillar()),
                round(b.level()), round(b.confidence()), round(b.support()), round(b.contradiction()),
                b.contradictory(), b.evidenceCount());
    }

    public List<Views.Pillar> pillars(Map<Attribute, Belief> beliefs) {
        return Arrays.stream(Pillar.values())
                .map(p -> new Views.Pillar(lower(p), p.label(), p.basis(),
                        Attribute.of(p).stream().map(a -> belief(beliefs.get(a))).toList()))
                .toList();
    }

    public Views.Question question(String sessionId, QuestionSelector.Scored scored, int asked, boolean followUp) {
        Question q = scored.question();
        int max = followUp ? QuestionSelector.FOLLOW_UP_QUESTIONS : QuestionSelector.MAX_QUESTIONS;
        int min = followUp ? 1 : QuestionSelector.MIN_QUESTIONS;
        return new Views.Question(sessionId, q.id(), q.text(), lower(q.type()), scored.reason(),
                q.options().stream().map(o -> new Views.Option(o.id(), o.text())).toList(),
                new Views.Progress(asked, min, max, followUp));
    }

    public Views.Evidence evidence(Evidence e, List<Evidence> all) {
        String qText = null, oText = null;
        if (e.questionId() != null && bank.has(e.questionId())) {
            Question q = bank.get(e.questionId());
            qText = q.text();
            if (e.optionId() != null) {
                oText = q.options().stream().filter(o -> o.id().equals(e.optionId()))
                        .map(Question.Option::text).findFirst().orElse(null);
            }
        }
        List<String> disputedBy = all.stream().filter(x -> e.id().equals(x.disputes())).map(Evidence::id).toList();
        return new Views.Evidence(e.id(), e.attribute().key(), e.attribute().label(), lower(e.attribute().pillar()),
                lower(e.direction()), e.strength(), e.reliability(), lower(e.source()),
                e.questionId(), qText, oText, e.context(), e.disputes(), disputedBy, e.recordedAt());
    }

    public List<Views.Evidence> evidence(List<String> ids, List<Evidence> all) {
        List<Views.Evidence> out = new ArrayList<>();
        for (String id : ids) {
            all.stream().filter(e -> e.id().equals(id)).findFirst().ifPresent(e -> out.add(evidence(e, all)));
        }
        return out;
    }

    public Views.Factor reason(Matcher.Contribution c) {
        String prompt = c.weight() >= 0 ? ContestPrompts.notMe(c.attribute())
                : "Actually, " + c.attribute().label().toLowerCase() + " does matter to me";
        return new Views.Factor(c.attribute().key(), c.attribute().label(), round(c.fit()), prompt);
    }

    public Views.Factor concern(Matcher.Contribution c) {
        return new Views.Factor(c.attribute().key(), c.attribute().label(), round(c.fit()),
                ContestPrompts.concernWrong(c.attribute()));
    }

    public Views.Recommendation recommendation(Matcher.Match m, List<Evidence> all) {
        var c = m.career();
        return new Views.Recommendation(c.id(), c.title(), c.summary(), c.riasec(),
                round(m.score()), round(m.confidence()),
                m.pillars().values().stream()
                        .map(p -> new Views.PillarFit(lower(p.pillar()), p.pillar().label(), round(p.fit()), round(p.confidence())))
                        .toList(),
                m.reasons().stream().map(this::reason).toList(),
                m.concerns().stream().map(this::concern).toList(),
                evidence(m.evidenceIds(), all),
                c.routes(), c.entranceExams(),
                c.sources().stream().map(s -> new Views.Source(s.label(), s.url())).toList(),
                c.verified());
    }

    public List<Views.BeliefChange> changes(Map<Attribute, Belief> before, Map<Attribute, Belief> after,
                                            Set<Attribute> touched) {
        return touched.stream().sorted().map(a -> {
            Belief b = before.get(a), x = after.get(a);
            return new Views.BeliefChange(a.key(), a.label(), round(b.level()), round(x.level()),
                    round(b.confidence()), round(x.confidence()), x.contradictory());
        }).toList();
    }
}
