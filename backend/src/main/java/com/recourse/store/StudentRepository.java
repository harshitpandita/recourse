package com.recourse.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<StudentEntity, String> {
    boolean existsByName(String name);

    boolean existsByReturnCode(String returnCode);

    Optional<StudentEntity> findByReturnCode(String returnCode);

    List<StudentEntity> findAllByReturnCodeIsNull();
}
