package com.recourse.service;

import com.recourse.engine.Attribute;
import com.recourse.engine.Belief;
import com.recourse.engine.BeliefCalculator;
import com.recourse.engine.Evidence;
import com.recourse.store.EvidenceEntity;
import com.recourse.store.EvidenceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Reads a student's evidence and derives their beliefs. Beliefs are never stored, always recomputed. */
@Service
public class StudentModel {

    private final EvidenceRepository evidence;

    public StudentModel(EvidenceRepository evidence) {
        this.evidence = evidence;
    }

    public List<Evidence> evidence(String studentId) {
        return evidence.findByStudentIdOrderByRecordedAtAscIdAsc(studentId).stream()
                .map(EvidenceEntity::toEvidence).toList();
    }

    public Map<Attribute, Belief> beliefs(String studentId) {
        return BeliefCalculator.computeAll(evidence(studentId));
    }

    public void append(String studentId, String sessionId, List<Evidence> items) {
        evidence.saveAll(items.stream().map(e -> EvidenceEntity.of(studentId, sessionId, e)).toList());
    }
}
