package com.recourse.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<EvidenceEntity, String> {
    List<EvidenceEntity> findByStudentIdOrderByRecordedAtAscIdAsc(String studentId);
}
