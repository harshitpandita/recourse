package com.recourse.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/** The audit log of every objection a student raised and what it changed. */
@Entity
@Table(name = "contest", indexes = @Index(columnList = "studentId"))
public class ContestEntity {

    @Id
    private String id;
    private String studentId;
    /** EVIDENCE or RECOMMENDATION */
    private String kind;
    /** Evidence id or career id */
    private String target;
    @Column(length = 1000)
    private String reason;
    /** Comma-separated ids of the evidence this contest created. */
    @Column(length = 2000)
    private String createdEvidence;
    /** JSON snapshot of the outcome (before/after), shown in the history view. */
    @Lob
    private String outcomeJson;
    private Instant createdAt;

    protected ContestEntity() {}

    public ContestEntity(String id, String studentId, String kind, String target, String reason,
                         String createdEvidence, String outcomeJson, Instant createdAt) {
        this.id = id;
        this.studentId = studentId;
        this.kind = kind;
        this.target = target;
        this.reason = reason;
        this.createdEvidence = createdEvidence;
        this.outcomeJson = outcomeJson;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getKind() { return kind; }
    public String getTarget() { return target; }
    public String getReason() { return reason; }
    public String getCreatedEvidence() { return createdEvidence; }
    public String getOutcomeJson() { return outcomeJson; }
    public Instant getCreatedAt() { return createdAt; }
}
