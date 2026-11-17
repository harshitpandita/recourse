package com.recourse.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnswerRepository extends JpaRepository<AnswerEntity, String> {
    List<AnswerEntity> findBySessionIdOrderByAnsweredAtAsc(String sessionId);
    List<AnswerEntity> findByStudentId(String studentId);
}
