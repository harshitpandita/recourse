package com.recourse.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SessionRepository extends JpaRepository<SessionEntity, String> {

    /** Sessions the student started but hasn't finished, newest first. */
    List<SessionEntity> findByStudentIdAndCompletedAtIsNullOrderByStartedAtDesc(String studentId);

    /** True once the student has finished the main assessment (a session with no follow-up focus). */
    boolean existsByStudentIdAndFocusIsNullAndCompletedAtIsNotNull(String studentId);

    /**
     * Removes an unfinished session. A plain query rather than delete(entity), so two requests
     * cleaning up the same session at once don't fail: the second simply removes nothing.
     */
    @Modifying
    @Query("delete from SessionEntity s where s.id = :id and s.completedAt is null")
    int deleteOpen(@Param("id") String id);
}
