package com.recourse.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentRepository extends JpaRepository<StudentEntity, String> {
    List<StudentEntity> findAllByOrderByCreatedAtDesc();
}
