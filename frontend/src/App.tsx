import { useCallback, useEffect, useState } from "react";
import { api, Question, Student } from "./api";
import { TopBar, View } from "./components/TopBar";
import { Welcome } from "./pages/Welcome";
import { Assessment } from "./pages/Assessment";
import { Matches } from "./pages/Matches";
import { Model } from "./pages/Model";
import { Log } from "./pages/Log";

const STUDENT_KEY = "recourse.student";
const THEME_KEY = "recourse.theme";

function read(key: string): string | null {
  try { return localStorage.getItem(key); } catch { return null; }
}
function write(key: string, value: string | null) {
  try { value === null ? localStorage.removeItem(key) : localStorage.setItem(key, value); } catch { /* storage unavailable */ }
}

export function App() {
  const [theme, setTheme] = useState<"dark" | "light">(read(THEME_KEY) === "light" ? "light" : "dark");
  const [student, setStudent] = useState<Student | null>(null);
  const [view, setView] = useState<View>("welcome");
  /** A question handed over from elsewhere, e.g. the follow-up after a contest. */
  const [pending, setPending] = useState<Question | null>(null);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    write(THEME_KEY, theme);
  }, [theme]);

  useEffect(() => {
    const id = read(STUDENT_KEY);
    if (!id) return;
    api.student(id)
      .then(s => { setStudent(s); setView("matches"); })
      .catch(() => write(STUDENT_KEY, null));
  }, []);

  const choose = useCallback((s: Student, fresh: boolean) => {
    setStudent(s);
    write(STUDENT_KEY, s.id);
    setPending(null);
    setView(fresh ? "questions" : "matches");
  }, []);

  const signOut = () => {
    write(STUDENT_KEY, null);
    setStudent(null);
    setView("welcome");
  };

  const followUp = (q: Question | null) => {
    setPending(q);
    setView(q ? "questions" : "matches");
  };

  return (
    <div className="app">
      <TopBar
        student={student}
        view={view}
        onNavigate={setView}
        theme={theme}
        onToggleTheme={() => setTheme(t => (t === "dark" ? "light" : "dark"))}
        onSwitch={signOut}
      />
      <main className="main">
        {!student || view === "welcome" ? (
          <Welcome onChoose={choose} />
        ) : view === "questions" ? (
          <Assessment key={pending?.sessionId ?? "fresh"} student={student} initial={pending}
                      onFinished={() => { setPending(null); setView("matches"); }} />
        ) : view === "matches" ? (
          <Matches student={student} onStart={() => setView("questions")} onFollowUp={followUp} />
        ) : view === "model" ? (
          <Model student={student} onFollowUp={followUp} />
        ) : (
          <Log student={student} />
        )}
      </main>
    </div>
  );
}
