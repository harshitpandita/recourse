package com.recourse.service;

import com.recourse.api.Views;
import com.recourse.data.QuestionBank;
import com.recourse.engine.Attribute;
import com.recourse.engine.Belief;
import com.recourse.engine.BeliefCalculator;
import com.recourse.engine.Evidence;
import com.recourse.engine.EvidenceFactory;
import com.recourse.engine.Question;
import com.recourse.engine.QuestionSelector;
import com.recourse.store.AnswerEntity;
import com.recourse.store.AnswerRepository;
import com.recourse.store.SessionEntity;
import com.recourse.store.SessionRepository;
import com.recourse.store.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Runs the adaptive assessment: start a session, pick the next question, record answers. */
@Service
public class AssessmentService {

    private final QuestionBank bank;
    private final StudentModel model;
    private final StudentRepository students;
    private final SessionRepository sessions;
    private final AnswerRepository answers;
    private final ViewMapper views;

    public AssessmentService(QuestionBank bank, StudentModel model, StudentRepository students,
                             SessionRepository sessions, AnswerRepository answers, ViewMapper views) {
        this.bank = bank;
        this.model = model;
        this.students = students;
        this.sessions = sessions;
        this.answers = answers;
        this.views = views;
    }

    /**
     * The next question for this student.
     *
     * With a focus (after a contest), a new follow-up session starts, and any older follow-up the
     * student left unfinished is closed, since the new objection supersedes it.
     *
     * Without a focus, the student carries on where they left off, so leaving or reloading the page
     * never restarts the count: first an unfinished follow-up, then the unfinished main session with
     * the most answers. Empty duplicate main sessions (two requests arriving at once, or older
     * versions that opened one on every visit) are removed. If the main assessment is already done
     * and nothing is unfinished, there is nothing to ask (null).
     */
    @Transactional
    public Views.Question start(String studentId, Set<Attribute> focus) {
        if (!students.existsById(studentId)) throw new NotFoundException("No student " + studentId);
        List<SessionEntity> open = sessions.findByStudentIdAndCompletedAtIsNullOrderByStartedAtDesc(studentId);

        if (focus != null && !focus.isEmpty()) {
            Instant now = Instant.now();
            for (SessionEntity old : open) {
                if (isFollowUp(old)) {
                    old.complete(now);
                    sessions.save(old);
                }
            }
            String focusText = focus.stream().map(Attribute::key).sorted().collect(Collectors.joining(","));
            return next(sessions.save(new SessionEntity(Ids.next(), studentId, now, focusText)).getId());
        }

        for (SessionEntity s : open) {
            if (!isFollowUp(s)) continue;
            Views.Question q = next(s.getId()); // completes the session instead if nothing is left to ask
            if (q != null) return q;
        }

        boolean complete = isComplete(studentId);
        SessionEntity resume = null;
        int most = -1;
        for (SessionEntity s : open) {
            if (isFollowUp(s)) continue;
            int count = answers.findBySessionIdOrderByAnsweredAtAsc(s.getId()).size();
            if (count > most) {
                if (resume != null && most == 0) discard(resume);
                resume = s;
                most = count;
            } else if (count == 0) {
                discard(s);
            }
        }
        if (resume != null && most == 0 && complete) {
            discard(resume);
            resume = null;
        }
        if (resume != null) {
            Views.Question q = next(resume.getId());
            if (q != null) return q;
        }
        if (complete || isComplete(studentId)) return null;
        return next(sessions.save(new SessionEntity(Ids.next(), studentId, Instant.now(), null)).getId());
    }

    /**
     * Removes a main session with no answers. It holds no evidence, and marking it complete instead
     * would wrongly count as finishing the assessment.
     */
    private void discard(SessionEntity s) {
        sessions.deleteOpen(s.getId());
    }

    /** True once the student has finished the main assessment. */
    public boolean isComplete(String studentId) {
        return sessions.existsByStudentIdAndFocusIsNullAndCompletedAtIsNotNull(studentId);
    }

    private static boolean isFollowUp(SessionEntity s) {
        return s.getFocus() != null && !s.getFocus().isBlank();
    }

    /** The next question for this session, or null when the session is finished. */
    @Transactional
    public Views.Question next(String sessionId) {
        SessionEntity s = session(sessionId);
        if (s.isComplete()) return null;
        Map<Attribute, Belief> beliefs = model.beliefs(s.getStudentId());
        Set<String> askedEver = askedEver(s.getStudentId());
        int askedHere = answers.findBySessionIdOrderByAnsweredAtAsc(sessionId).size();
        Set<Attribute> focus = focus(s);
        boolean followUp = !focus.isEmpty();

        boolean stop = followUp
                ? QuestionSelector.shouldStopFollowUp(bank.all(), askedEver, askedHere, focus)
                : QuestionSelector.shouldStop(bank.all(), beliefs, askedEver, askedHere);
        if (stop) {
            s.complete(Instant.now());
            sessions.save(s);
            return null;
        }
        return QuestionSelector.next(bank.all(), beliefs, askedEver, focus)
                .map(scored -> views.question(sessionId, scored, askedHere, followUp))
                .orElse(null);
    }

    @Transactional
    public Views.AnswerResult answer(String sessionId, String questionId, String optionId) {
        SessionEntity s = session(sessionId);
        if (s.isComplete()) throw new IllegalStateException("This session is already finished");
        String studentId = s.getStudentId();
        if (askedEver(studentId).contains(questionId)) {
            throw new IllegalStateException("Question " + questionId + " was already answered");
        }
        Question q = bank.get(questionId);
        Instant now = Instant.now();

        List<Evidence> before = model.evidence(studentId);
        List<Evidence> created = EvidenceFactory.fromAnswer(q, optionId, Ids::next, now);
        answers.save(new AnswerEntity(Ids.next(), studentId, sessionId, questionId, optionId, now));
        model.append(studentId, sessionId, created);

        List<Evidence> after = new ArrayList<>(before);
        after.addAll(created);
        Set<Attribute> touched = created.stream().map(Evidence::attribute).collect(Collectors.toCollection(LinkedHashSet::new));
        var changes = views.changes(BeliefCalculator.computeAll(before), BeliefCalculator.computeAll(after), touched);

        Views.Question next = next(sessionId);
        return new Views.AnswerResult(next == null, next, changes);
    }

    public int questionsAnswered(String studentId) {
        return askedEver(studentId).size();
    }

    private Set<String> askedEver(String studentId) {
        return answers.findByStudentId(studentId).stream().map(AnswerEntity::getQuestionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private SessionEntity session(String id) {
        return sessions.findById(id).orElseThrow(() -> new NotFoundException("No session " + id));
    }

    private static Set<Attribute> focus(SessionEntity s) {
        if (s.getFocus() == null || s.getFocus().isBlank()) return Set.of();
        return Arrays.stream(s.getFocus().split(",")).map(Attribute::fromKey).collect(Collectors.toSet());
    }
}
