package com.recourse.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Pilot feedback: one row per student, given after their first contest. Holds no name. */
@Entity
@Table(name = "feedback")
public class FeedbackEntity {

    @Id
    private String id;
    @Column(unique = true, nullable = false)
    private String studentId;
    /** The student's first contest, which the ratings are about. */
    private String contestId;
    private int matchesMadeSense;
    private int explanationHelped;
    private int pushingBackWorked;
    @Column(length = 1000)
    private String comment;
    private Instant createdAt;

    protected FeedbackEntity() {}

    public FeedbackEntity(String id, String studentId, String contestId, int matchesMadeSense,
                          int explanationHelped, int pushingBackWorked, String comment, Instant createdAt) {
        this.id = id;
        this.studentId = studentId;
        this.contestId = contestId;
        this.matchesMadeSense = matchesMadeSense;
        this.explanationHelped = explanationHelped;
        this.pushingBackWorked = pushingBackWorked;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getContestId() { return contestId; }
    public int getMatchesMadeSense() { return matchesMadeSense; }
    public int getExplanationHelped() { return explanationHelped; }
    public int getPushingBackWorked() { return pushingBackWorked; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}
