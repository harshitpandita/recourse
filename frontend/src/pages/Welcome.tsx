import { FormEvent, useEffect, useState } from "react";
import { api, Stage, Student, stageLabel } from "../api";
import { ErrorNote } from "../components/Feedback";

const stages: Stage[] = ["CLASS_10", "CLASS_12", "UNDERGRAD"];

export function Welcome({ onChoose }: { onChoose: (s: Student, fresh: boolean) => void }) {
  const [name, setName] = useState("");
  const [stage, setStage] = useState<Stage | null>(null);
  const [existing, setExisting] = useState<Student[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [hint, setHint] = useState<string | null>(null);

  useEffect(() => { api.students().then(setExisting).catch(() => setExisting([])); }, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) return setHint("Enter your name to start.");
    if (!stage) return setHint("Pick where you are right now.");
    setBusy(true);
    setError(null);
    try {
      onChoose(await api.createStudent(name.trim(), stage), true);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="welcome">
      <section className="welcome__intro">
        <h1 className="display">Career advice you can argue with.</h1>
        <p className="lede">
          Answer a few short questions. Recourse shows you which careers might fit, exactly why it thinks so,
          and how sure it is. If something sounds wrong, tell it. It will change its mind, and show you how.
        </p>
        <div className="welcome__demo" aria-hidden="true">
          <div className="demo-row">
            <span>Helping people</span>
            <div className="demo-track"><span className="demo-band demo-band--a" /><span className="demo-marker demo-marker--a" /></div>
          </div>
          <p className="demo-caption">The shaded band is how sure we are. Push back, and it widens.</p>
        </div>
      </section>

      <form className="welcome__form" onSubmit={submit} noValidate>
        <h2 className="h2">Start fresh</h2>
        <label className="field">
          <span className="field__label">Your name</span>
          <input value={name} onChange={e => { setName(e.target.value); setHint(null); }} placeholder="Aanya" maxLength={80} />
        </label>
        <fieldset className="field">
          <legend className="field__label">Where are you right now?</legend>
          <div className="choice-row">
            {stages.map(s => (
              <button type="button" key={s} className={`pill${stage === s ? " pill--on" : ""}`}
                      aria-pressed={stage === s} onClick={() => { setStage(s); setHint(null); }}>
                {stageLabel[s]}
              </button>
            ))}
          </div>
        </fieldset>
        {hint && <p className="hint" role="alert">{hint}</p>}
        {error != null && <ErrorNote error={error} />}
        <button className="button button--primary" disabled={busy}>{busy ? "Starting…" : "Start the questions"}</button>

        {existing.length > 0 && (
          <div className="returning">
            <h2 className="h3">Or pick up where you left off</h2>
            <ul className="returning__list">
              {existing.slice(0, 6).map(s => (
                <li key={s.id}>
                  <button type="button" className="returning__item" onClick={() => onChoose(s, false)}>
                    <span>{s.name}</span>
                    <span className="quiet">{stageLabel[s.stage]}</span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
      </form>
    </div>
  );
}
