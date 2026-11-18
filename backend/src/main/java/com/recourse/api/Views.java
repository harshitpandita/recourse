package com.recourse.api;

import java.time.Instant;
import java.util.List;

/** Everything the REST API returns. Kept separate from entities and engine types. */
public final class Views {
    private Views() {}

    /** returnCode is shown as "K7P-3QX". */
    public record Student(String id, String name, String stage, Instant createdAt, String returnCode) {}

    public record Belief(String attribute, String label, String description, String pillar,
                         double level, double confidence, double support, double contradiction,
                         boolean contradictory, int evidenceCount) {}

    public record Pillar(String key, String label, String basis, List<Belief> beliefs) {}

    public record Profile(Student student, int evidenceCount, int contestCount, int questionsAnswered,
                          List<Pillar> pillars, List<Belief> contradictions) {}

    public record Option(String id, String text) {}

    public record Progress(int asked, int min, int max, boolean followUp) {}

    public record Question(String sessionId, String id, String text, String type, String reason,
                           List<Option> options, Progress progress) {}

    public record BeliefChange(String attribute, String label, double levelBefore, double levelAfter,
                               double confidenceBefore, double confidenceAfter, boolean contradictoryAfter) {}

    public record AnswerResult(boolean done, Question next, List<BeliefChange> changes) {}

    public record Evidence(String id, String attribute, String label, String pillar, String direction,
                           double strength, double reliability, String source,
                           String questionId, String questionText, String optionText,
                           String note, String disputes, List<String> disputedBy, Instant recordedAt) {}

    public record PillarFit(String pillar, String label, double fit, double confidence) {}

    public record Factor(String attribute, String label, double fit, String prompt) {}

    public record Source(String label, String url) {}

    public record Recommendation(String careerId, String title, String summary, String riasec,
                                 double score, double confidence, List<PillarFit> pillars,
                                 List<Factor> reasons, List<Factor> concerns, List<Evidence> evidence,
                                 List<String> routes, List<String> entranceExams, List<Source> sources,
                                 boolean verified) {}

    public record Explanation(String careerId, String text, String generatedBy, long millis) {}

    public record RankEntry(String careerId, String title, double score) {}

    public record ContestOutcome(String contestId, String kind, String target, String reason,
                                 List<BeliefChange> changes, List<RankEntry> before, List<RankEntry> after,
                                 List<String> createdEvidence, Instant createdAt) {}

    public record ContestResult(ContestOutcome outcome, Question followUp) {}

    public record SystemStatus(boolean llmEnabled, boolean llmReachable, String model,
                               int questions, int careers, int unverifiedCareers) {}
}
