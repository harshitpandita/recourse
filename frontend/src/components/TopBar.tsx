import { Student, stageLabel } from "../api";

export type View = "welcome" | "questions" | "matches" | "model" | "log";

interface Props {
  student: Student | null;
  view: View;
  onNavigate: (v: View) => void;
  theme: "dark" | "light";
  onToggleTheme: () => void;
  onSwitch: () => void;
}

const tabs: { view: View; label: string }[] = [
  { view: "questions", label: "Questions" },
  { view: "matches", label: "Matches" },
  { view: "model", label: "What we think" },
  { view: "log", label: "Pushback log" },
];

export function TopBar({ student, view, onNavigate, theme, onToggleTheme, onSwitch }: Props) {
  return (
    <header className="topbar">
      <div className="topbar__inner">
        <span className="wordmark" aria-label="Recourse">
          re<span className="wordmark__turn">course</span>
        </span>
        {student && (
          <nav className="tabs" aria-label="Sections">
            {tabs.map(t => (
              <button key={t.view} className={`tab${view === t.view ? " tab--active" : ""}`}
                      aria-current={view === t.view ? "page" : undefined}
                      onClick={() => onNavigate(t.view)}>
                {t.label}
              </button>
            ))}
          </nav>
        )}
        <div className="topbar__end">
          {student && (
            <span className="who">
              {student.name}
              <span className="who__stage">{stageLabel[student.stage]}</span>
            </span>
          )}
          {student && <button className="link-button" onClick={onSwitch}>Switch</button>}
          <button className="link-button" onClick={onToggleTheme}
                  aria-label={`Use ${theme === "dark" ? "light" : "dark"} theme`}>
            {theme === "dark" ? "Light" : "Dark"}
          </button>
        </div>
      </div>
    </header>
  );
}
