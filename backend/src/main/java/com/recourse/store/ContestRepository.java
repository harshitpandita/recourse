package com.recourse.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContestRepository extends JpaRepository<ContestEntity, String> {
    List<ContestEntity> findByStudentIdOrderByCreatedAtDesc(String studentId);
}
