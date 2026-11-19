package com.recourse.service;

import com.recourse.api.Views;
import com.recourse.data.CareerCatalog;
import com.recourse.data.QuestionBank;
import com.recourse.engine.Attribute;
import com.recourse.engine.Belief;
import com.recourse.engine.Evidence;
import com.recourse.engine.BeliefCalculator;
import com.recourse.explain.OllamaClient;
import com.recourse.store.ContestRepository;
import com.recourse.store.StudentEntity;
import com.recourse.store.StudentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StudentService {

    static final Set<String> STAGES = Set.of("CLASS_10", "CLASS_12", "UNDERGRAD");

    private final StudentRepository students;
    private final ContestRepository contests;
    private final StudentModel model;
    private final AssessmentService assessment;
    private final ViewMapper views;
    private final QuestionBank bank;
    private final CareerCatalog careers;
    private final OllamaClient llm;

    public StudentService(StudentRepository students, ContestRepository contests, StudentModel model,
                          AssessmentService assessment, ViewMapper views, QuestionBank bank,
                          CareerCatalog careers, OllamaClient llm) {
        this.students = students;
        this.contests = contests;
        this.model = model;
        this.assessment = assessment;
        this.views = views;
        this.bank = bank;
        this.careers = careers;
        this.llm = llm;
    }

    /** Same message whichever part is wrong, so it never reveals whether a name exists. */
    static final String NO_MATCH = "We couldn't find that name and code together. Check both and try again.";

    /** Retries are only for the very rare case of a random code that is already taken. */
    private static final int CODE_ATTEMPTS = 20;

    public Views.Student create(String name, String stage) {
        return create(name, stage, newReturnCode());
    }

    /** For the demo seed, which gives its personas fixed, easy-to-remember codes. */
    Views.Student create(String name, String stage, String returnCode) {
        if (!STAGES.contains(stage)) throw new IllegalArgumentException("Stage must be one of " + STAGES);
        if (!ReturnCodes.isValid(returnCode)) throw new IllegalArgumentException("Not a valid return code");
        return view(students.save(new StudentEntity(Ids.next(), name.strip(), stage, Instant.now(), returnCode)));
    }

    /** A returning student: name (any case) and return code (any case, spaces or hyphens ignored) must match. */
    public Views.Student continueWith(String name, String typedCode) {
        String code = ReturnCodes.normalise(typedCode);
        String wanted = name == null ? "" : name.strip();
        return students.findByReturnCode(code)
                .filter(s -> s.getName().equalsIgnoreCase(wanted))
                .map(this::view)
                .orElseThrow(() -> new NotFoundException(NO_MATCH));
    }

    /** Gives a code to any student created before return codes existed. Returns how many were updated. */
    public int backfillReturnCodes() {
        List<StudentEntity> missing = students.findAllByReturnCodeIsNull();
        for (StudentEntity s : missing) {
            s.assignReturnCode(newReturnCode());
            students.save(s);
        }
        return missing.size();
    }

    String newReturnCode() {
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            String code = ReturnCodes.generate();
            if (!students.existsByReturnCode(code)) return code;
        }
        throw new IllegalStateException("Couldn't find a free return code");
    }

    public Views.Student get(String id) {
        return view(entity(id));
    }

    private Views.Student view(StudentEntity s) {
        return views.student(s, assessment.isComplete(s.getId()));
    }

    public Views.Profile profile(String id) {
        StudentEntity s = entity(id);
        List<Evidence> evidence = model.evidence(id);
        Map<Attribute, Belief> beliefs = BeliefCalculator.computeAll(evidence);
        List<Views.Belief> contradictions = beliefs.values().stream().filter(Belief::contradictory)
                .sorted(Comparator.comparingDouble(Belief::confidence)).map(views::belief).toList();
        return new Views.Profile(view(s), evidence.size(),
                contests.findByStudentIdOrderByCreatedAtDesc(id).size(),
                assessment.questionsAnswered(id), views.pillars(beliefs), contradictions);
    }

    public List<Views.Evidence> evidence(String id) {
        entity(id);
        List<Evidence> all = model.evidence(id);
        return all.stream().map(e -> views.evidence(e, all)).toList();
    }

    public Views.SystemStatus status() {
        int unverified = (int) careers.all().stream().filter(c -> !c.verified()).count();
        return new Views.SystemStatus(llm.enabled(), llm.reachable(), llm.model(),
                bank.all().size(), careers.all().size(), unverified);
    }

    private StudentEntity entity(String id) {
        return students.findById(id).orElseThrow(() -> new NotFoundException("No student " + id));
    }
}
