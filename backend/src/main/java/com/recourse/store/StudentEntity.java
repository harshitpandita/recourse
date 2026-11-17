package com.recourse.store;

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

    protected StudentEntity() {}

    public StudentEntity(String id, String name, String stage, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.stage = stage;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getStage() { return stage; }
    public Instant getCreatedAt() { return createdAt; }
}
