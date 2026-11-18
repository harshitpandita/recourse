package com.recourse.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "student")
public class StudentEntity {

    @Id
    private String id;
    private String name;
    /** CLASS_10, CLASS_12 or UNDERGRAD */
    private String stage;
    private Instant createdAt;
    /** Short code the student uses with their name to come back. Stored without the hyphen. */
    @Column(unique = true, length = 6)
    private String returnCode;

    protected StudentEntity() {}

    public StudentEntity(String id, String name, String stage, Instant createdAt, String returnCode) {
        this.id = id;
        this.name = name;
        this.stage = stage;
        this.createdAt = createdAt;
        this.returnCode = returnCode;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getStage() { return stage; }
    public Instant getCreatedAt() { return createdAt; }
    public String getReturnCode() { return returnCode; }

    /** Only for students created before return codes existed. */
    public void assignReturnCode(String code) {
        if (returnCode != null) throw new IllegalStateException("Student already has a return code");
        this.returnCode = code;
    }
}
