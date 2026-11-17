package com.recourse.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One sitting of the assessment. A student can come back for more sessions later. */
@Entity
@Table(name = "assessment_session")
public class SessionEntity {

    @Id
    private String id;
    private String studentId;
    private Instant startedAt;
    private Instant completedAt;
    /** Comma-separated attribute keys to revisit first (set after a contest). */
    private String focus;

    protected SessionEntity() {}

    public SessionEntity(String id, String studentId, Instant startedAt, String focus) {
        this.id = id;
        this.studentId = studentId;
        this.startedAt = startedAt;
        this.focus = focus;
    }

    public String getId() { return id; }
    public String getStudentId() { return studentId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getFocus() { return focus; }
    public boolean isComplete() { return completedAt != null; }
    public void complete(Instant at) { this.completedAt = at; }
}
