package com.recourse.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "answer", indexes = {@Index(columnList = "sessionId"), @Index(columnList = "studentId")})
public class AnswerEntity {

    @Id
    private String id;
    private String studentId;
    private String sessionId;
    private String questionId;
    private String optionId;
    private Instant answeredAt;

    protected AnswerEntity() {}

    public AnswerEntity(String id, String studentId, String sessionId, String questionId, String optionId, Instant answeredAt) {
        this.id = id;
        this.studentId = studentId;
        this.sessionId = sessionId;
        this.questionId = questionId;
        this.optionId = optionId;
        this.answeredAt = answeredAt;
    }

    public String getQuestionId() { return questionId; }
    public String getOptionId() { return optionId; }
    public String getSessionId() { return sessionId; }
    public Instant getAnsweredAt() { return answeredAt; }
}
