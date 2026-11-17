package com.recourse.store;

import com.recourse.engine.Attribute;
import com.recourse.engine.Direction;
import com.recourse.engine.Evidence;
import com.recourse.engine.Source;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only. There is deliberately no setter and no delete path anywhere in the app. */
@Entity
@Table(name = "evidence", indexes = @Index(columnList = "studentId"))
public class EvidenceEntity {

    @Id
    private String id;
    private String studentId;
    private String sessionId;
    @Enumerated(EnumType.STRING)
    private Attribute attribute;
    @Enumerated(EnumType.STRING)
    private Direction direction;
    private double strength;
    private double reliability;
    @Enumerated(EnumType.STRING)
    private Source source;
    private String questionId;
    private String optionId;
    @Column(length = 1000)
    private String context;
    private String disputes;
    private Instant recordedAt;

    protected EvidenceEntity() {}

    public static EvidenceEntity of(String studentId, String sessionId, Evidence e) {
        EvidenceEntity x = new EvidenceEntity();
        x.id = e.id();
        x.studentId = studentId;
        x.sessionId = sessionId;
        x.attribute = e.attribute();
        x.direction = e.direction();
        x.strength = e.strength();
        x.reliability = e.reliability();
        x.source = e.source();
        x.questionId = e.questionId();
        x.optionId = e.optionId();
        x.context = e.context();
        x.disputes = e.disputes();
        x.recordedAt = e.recordedAt();
        return x;
    }

    public Evidence toEvidence() {
        return new Evidence(id, attribute, direction, strength, reliability, source,
                questionId, optionId, context, disputes, recordedAt);
    }

    public String getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getSessionId() { return sessionId; }
    public Instant getRecordedAt() { return recordedAt; }
}
