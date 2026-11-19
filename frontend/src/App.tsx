import { useCallback, useEffect, useState } from "react";
import { api, Question, Student } from "./api";
import { TopBar, View } from "./components/TopBar";
import { Loading } from "./components/Feedback";
import { Welcome } from "./pages/Welcome";
import { Assessment } from "./pages/Assessment";
import { Matches } from "./pages/Matches";
import { Model } from "./pages/Model";
import { Log } from "./pages/Log";

const STUDENT_KEY = "recourse.student";
const VIEW_KEY = "recourse.view";
const THEME_KEY = "recourse.theme";

const titles: Record<View, string> = {
  welcome: "Recourse",
  questions: "Questions · Recourse",
  matches: "Your matches · Recourse",
  model: "What we think · Recourse",
  log: "Pushback log · Recourse",
};

function read(key: string): string | null {
  try { return localStorage.getItem(key); } catch { return null; }
}
function write(key: string, value: string | null) {
  try { value === null ? localStorage.removeItem(key) : localStorage.setItem(key, value); } catch { /* storage unavailable */ }
}

/** Where a signed-in student should land: the questions until they're done, then their matches. */
const home = (s: Student): View => (s.assessmentComplete ? "matches" : "questions");

function savedView(s: Student): View {
  const v = read(VIEW_KEY);
  return v === "questions" || v === "matches" || v === "model" || v === "log" ? v : home(s);
}

export function App() {
  const [theme, setTheme] = useState<"dark" | "light">(read(THEME_KEY) === "light" ? "light" : "dark");
  const [student, setStudent] = useState<Student | null>(null);
  const [view, setView] = useState<View>("welcome");
  /** True while a remembered student is being loaded, so the welcome page doesn't flash. */
  const [restoring, setRestoring] = useState(() => read(STUDENT_KEY) !== null);
  /** A question handed over from elsewhere, e.g. the follow-up after a contest. */
  const [pending, setPending] = useState<Question | null>(null);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    write(THEME_KEY, theme);
  }, [theme]);

  // Each page starts at the top, and a reload comes back to the same page.
  useEffect(() => {
    window.scrollTo(0, 0);
    document.title = titles[view];
    if (view !== "welcome") write(VIEW_KEY, view);
  }, [view]);

  useEffect(() => {
    const id = read(STUDENT_KEY);
    if (!id) return;
    api.student(id)
      .then(s => { setStudent(s); setView(savedView(s)); })
      .catch(() => { write(STUDENT_KEY, null); write(VIEW_KEY, null); })
      .finally(() => setRestoring(false));
  }, []);

  const choose = useCallback((s: Student, fresh: boolean) => {
    setStudent(s);
    write(STUDENT_KEY, s.id);
    setPending(null);
    setView(fresh ? "questions" : home(s));
  }, []);

  const signOut = () => {
    write(STUDENT_KEY, null);
    write(VIEW_KEY, null);
    setStudent(null);
    setPending(null);
    setView("welcome");
  };

  const navigate = (v: View) => {
    setPending(null);
    setView(v);
  };

  const followUp = (q: Question | null) => {
    setPending(q);
    setView(q ? "questions" : "matches");
  };

  /** After the questions, refresh the student so the app knows the assessment is complete. */
  const finished = () => {
    setPending(null);
    setView("matches");
    if (student) api.student(student.id).then(setStudent).catch(() => { /* keep the old copy */ });
  };

  return (
    <div className="app">
      <TopBar
        student={student}
        view={view}
        onNavigate={navigate}
        theme={theme}
        onToggleTheme={() => setTheme(t => (t === "dark" ? "light" : "dark"))}
        onSignOut={signOut}
      />
      <main className="main">
        {restoring ? (
          <Loading what="your results" />
        ) : !student || view === "welcome" ? (
          <Welcome onChoose={choose} />
        ) : view === "questions" ? (
          <Assessment key={pending?.sessionId ?? "resume"} student={student} initial={pending} onFinished={finished} />
        ) : view === "matches" ? (
          <Matches student={student} onStart={() => navigate("questions")} onFollowUp={followUp} />
        ) : view === "model" ? (
          <Model student={student} onFollowUp={followUp} />
        ) : (
          <Log student={student} />
        )}
      </main>
    </div>
  );
}
