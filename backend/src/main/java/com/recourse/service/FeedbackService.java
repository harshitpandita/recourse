package com.recourse.service;

import com.recourse.store.ContestEntity;
import com.recourse.store.ContestRepository;
import com.recourse.store.FeedbackEntity;
import com.recourse.store.FeedbackRepository;
import com.recourse.store.StudentEntity;
import com.recourse.store.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pilot feedback: three 1 to 5 ratings and an optional comment, asked once,
 * after a student's first contest. Exported as CSV without names.
 */
@Service
public class FeedbackService {

    static final String CSV_HEADER = "submitted_at,student_ref,stage,contest_kind,contest_target,"
            + "matches_made_sense,explanation_helped,pushing_back_worked,comment";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FeedbackRepository feedback;
    private final ContestRepository contests;
    private final StudentRepository students;

    public FeedbackService(FeedbackRepository feedback, ContestRepository contests, StudentRepository students) {
        this.feedback = feedback;
        this.contests = contests;
        this.students = students;
    }

    public boolean given(String studentId) {
        return feedback.existsByStudentId(studentId);
    }

    /** Asked once: right after the student's first contest, if they haven't answered yet. */
    public boolean shouldAsk(String studentId) {
        return contests.findByStudentIdOrderByCreatedAtDesc(studentId).size() == 1 && !given(studentId);
    }

    @Transactional
    public void submit(String studentId, int matchesMadeSense, int explanationHelped, int pushingBackWorked,
                       String comment) {
        for (int r : new int[]{matchesMadeSense, explanationHelped, pushingBackWorked}) {
            if (r < 1 || r > 5) throw new IllegalArgumentException("Each rating must be from 1 to 5");
        }
        if (given(studentId)) throw new IllegalStateException("Feedback has already been given");
        ContestEntity first = contests.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .min(Comparator.comparing(ContestEntity::getCreatedAt))
                .orElseThrow(() -> new IllegalStateException("Feedback is asked for after your first pushback"));
        String text = comment == null || comment.isBlank() ? null : comment.strip();
        feedback.save(new FeedbackEntity(Ids.next(), studentId, first.getId(), matchesMadeSense,
                explanationHelped, pushingBackWorked, text, Instant.now()));
    }

    /** One row per student, oldest first. No names: students appear only as a short reference. */
    public String exportCsv() {
        Map<String, StudentEntity> byId = students.findAll().stream()
                .collect(Collectors.toMap(StudentEntity::getId, Function.identity()));
        Map<String, ContestEntity> contestById = contests.findAll().stream()
                .collect(Collectors.toMap(ContestEntity::getId, Function.identity()));
        StringBuilder csv = new StringBuilder(CSV_HEADER).append("\r\n");
        for (FeedbackEntity f : feedback.findAllByOrderByCreatedAtAsc()) {
            StudentEntity s = byId.get(f.getStudentId());
            ContestEntity c = contestById.get(f.getContestId());
            List<String> row = List.of(
                    WHEN.format(f.getCreatedAt().atZone(ZoneId.systemDefault())),
                    f.getStudentId().substring(0, Math.min(8, f.getStudentId().length())),
                    s == null ? "" : s.getStage(),
                    c == null ? "" : c.getKind(),
                    c == null ? "" : c.getTarget(),
                    String.valueOf(f.getMatchesMadeSense()),
                    String.valueOf(f.getExplanationHelped()),
                    String.valueOf(f.getPushingBackWorked()),
                    f.getComment() == null ? "" : f.getComment());
            csv.append(row.stream().map(FeedbackService::cell).collect(Collectors.joining(","))).append("\r\n");
        }
        return csv.toString();
    }

    /**
     * One CSV cell. Text that starts like a formula gets a leading apostrophe so a
     * spreadsheet shows it instead of running it. Cells with commas, quotes or line
     * breaks are quoted, with inner quotes doubled.
     */
    static String cell(String value) {
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
