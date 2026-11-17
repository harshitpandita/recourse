package com.recourse.engine;

/** What kind of observation produced a piece of evidence. */
public enum Source {
    SELF_REPORT, SCENARIO, FORCED_CHOICE, LIKERT, RANKING, MINI_TASK, REFLECTION,
    /** Evidence created when the student disputes something (the Recourse mechanism). */
    CONTEST
}
