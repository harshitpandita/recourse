package com.recourse.service;

import com.recourse.api.Views;
import com.recourse.data.CareerCatalog;
import com.recourse.engine.Attribute;
import com.recourse.engine.Belief;
import com.recourse.engine.BeliefCalculator;
import com.recourse.engine.Evidence;
import com.recourse.engine.Matcher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RecommendationService {

    /** How many meaningfully different directions to show. */
    public static final int COUNT = 3;

    private final CareerCatalog careers;
    private final StudentModel model;
    private final ViewMapper views;

    public RecommendationService(CareerCatalog careers, StudentModel model, ViewMapper views) {
        this.careers = careers;
        this.model = model;
        this.views = views;
    }

    public List<Views.Recommendation> recommend(String studentId) {
        List<Evidence> evidence = model.evidence(studentId);
        if (evidence.isEmpty()) return List.of();
        Map<Attribute, Belief> beliefs = BeliefCalculator.computeAll(evidence);
        return Matcher.recommend(careers.all(), beliefs, evidence, COUNT).stream()
                .map(m -> views.recommendation(m, evidence)).toList();
    }

    /** The full ranking, used to show how a contest moved things. */
    public List<Views.RankEntry> ranking(List<Evidence> evidence, int limit) {
        Map<Attribute, Belief> beliefs = BeliefCalculator.computeAll(evidence);
        return Matcher.recommend(careers.all(), beliefs, evidence, limit).stream()
                .map(m -> new Views.RankEntry(m.career().id(), m.career().title(), ViewMapper.round(m.score())))
                .toList();
    }

    public Matcher.Match matchFor(String studentId, String careerId) {
        List<Evidence> evidence = model.evidence(studentId);
        Map<Attribute, Belief> beliefs = BeliefCalculator.computeAll(evidence);
        Map<String, Evidence> byId = new java.util.HashMap<>();
        evidence.forEach(e -> byId.put(e.id(), e));
        return Matcher.match(careers.get(careerId), beliefs, byId);
    }

    public Views.Recommendation recommendation(String studentId, String careerId) {
        return views.recommendation(matchFor(studentId, careerId), model.evidence(studentId));
    }
}
