package com.recourse.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** On startup, gives a return code to any student created before return codes existed. */
@Component
@Order(0)
public class ReturnCodeBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReturnCodeBackfill.class);

    private final StudentService students;

    public ReturnCodeBackfill(StudentService students) {
        this.students = students;
    }

    @Override
    public void run(ApplicationArguments args) {
        int updated = students.backfillReturnCodes();
        if (updated > 0) log.info("Gave return codes to {} existing student(s)", updated);
    }
}
