package com.recourse.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, String> {
    boolean existsByStudentId(String studentId);

    List<FeedbackEntity> findAllByOrderByCreatedAtAsc();
}
