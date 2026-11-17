package com.recourse.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionRepository extends JpaRepository<SessionEntity, String> {
    Optional<SessionEntity> findFirstByStudentIdOrderByStartedAtDesc(String studentId);
}
