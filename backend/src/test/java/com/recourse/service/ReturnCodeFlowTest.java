package com.recourse.service;

import com.recourse.api.Views;
import com.recourse.store.StudentEntity;
import com.recourse.store.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Students come back with name and return code; other students are never listed. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:recourse-codes-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ReturnCodeFlowTest {

    @Autowired StudentService students;
    @Autowired StudentRepository studentRepo;
    @Autowired MockMvc mvc;

    @Test
    void newStudentsGetAValidUniqueCode() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            Views.Student s = students.create("Student " + i, "CLASS_12");
            assertTrue(s.returnCode().matches("[2-9A-HJ-NP-Z]{3}-[2-9A-HJ-NP-Z]{3}"), s.returnCode());
            assertTrue(codes.add(s.returnCode()), "duplicate code " + s.returnCode());
        }
    }

    @Test
    void continuingIsForgivingAboutCaseSpacesAndHyphens() {
        Views.Student s = students.create("Aanya Sharma", "CLASS_10");
        String typed = " " + s.returnCode().replace("-", " ").toLowerCase() + " ";
        assertEquals(s.id(), students.continueWith("  aanya sharma ", typed).id());
        assertEquals(s.id(), students.continueWith("Aanya Sharma", s.returnCode()).id());
    }

    @Test
    void wrongCodeAndWrongNameGiveTheSameAnswer() {
        Views.Student s = students.create("Rohan", "UNDERGRAD");
        String wrongCode = s.returnCode().startsWith("2") ? "333-333" : "222-222";
        NotFoundException badCode = assertThrows(NotFoundException.class,
                () -> students.continueWith("Rohan", wrongCode));
        NotFoundException badName = assertThrows(NotFoundException.class,
                () -> students.continueWith("Rahul", s.returnCode()));
        assertEquals(badCode.getMessage(), badName.getMessage());
        assertFalse(badName.getMessage().contains("Rohan"));
    }

    @Test
    void studentsCreatedBeforeReturnCodesGetOne() {
        StudentEntity old = studentRepo.save(new StudentEntity("old-student", "Old", "CLASS_12", Instant.now(), null));
        assertNull(old.getReturnCode());
        assertTrue(students.backfillReturnCodes() >= 1);
        String code = studentRepo.findById("old-student").orElseThrow().getReturnCode();
        assertTrue(ReturnCodes.isValid(code), code);
        assertEquals(0, students.backfillReturnCodes());
    }

    @Test
    void theApiNeverListsStudents() throws Exception {
        students.create("Hidden", "CLASS_12");
        mvc.perform(get("/api/v1/students")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void theApiContinuesWithNameAndCode() throws Exception {
        Views.Student s = students.create("Ishaan", "CLASS_12");
        mvc.perform(post("/api/v1/students/continue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ishaan\",\"code\":\"" + s.returnCode().toLowerCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(s.id()))
                .andExpect(jsonPath("$.returnCode").value(s.returnCode()));
        mvc.perform(post("/api/v1/students/continue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ishaan\",\"code\":\"ZZZ-ZZZ\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(StudentService.NO_MATCH));
    }
}
