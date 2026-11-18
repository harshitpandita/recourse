import { FormEvent, useState } from "react";
import { api, ApiError, Stage, Student, stageLabel } from "../api";
import { ErrorNote } from "../components/Feedback";

const stages: Stage[] = ["CLASS_10", "CLASS_12", "UNDERGRAD"];

export function Welcome({ onChoose }: { onChoose: (s: Student, fresh: boolean) => void }) {
  /** Set right after signup, so the student sees their return code before the questions. */
  const [created, setCreated] = useState<Student | null>(null);

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

      <div className="welcome__form">
        {created
          ? <ReturnCode student={created} onDone={() => onChoose(created, true)} />
          : <>
              <StartForm onCreated={setCreated} />
              <ContinueForm onFound={s => onChoose(s, false)} />
            </>}
      </div>
    </div>
  );
}

function StartForm({ onCreated }: { onCreated: (s: Student) => void }) {
  const [name, setName] = useState("");
  const [stage, setStage] = useState<Stage | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [hint, setHint] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) return setHint("Enter your name to start.");
    if (!stage) return setHint("Pick where you are right now.");
    setBusy(true);
    setError(null);
    try {
      onCreated(await api.createStudent(name.trim(), stage));
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate>
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
    </form>
  );
}

function ReturnCode({ student, onDone }: { student: Student; onDone: () => void }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(student.returnCode);
      setCopied(true);
    } catch { /* clipboard unavailable: the code is still on screen */ }
  }

  return (
    <div className="code-step">
      <h2 className="h2">Your return code</h2>
      <p className="code-step__code" aria-label={`Your return code is ${student.returnCode.split("").join(" ")}`}>
        {student.returnCode}
      </p>
      <p>
        Save this code somewhere safe, for example in your phone's notes. You'll need it with your name
        to come back to your results.
      </p>
      <p className="quiet small">While you're signed in, you can also find it at the top of the page.</p>
      <div className="actions">
        <button type="button" className="button button--primary" onClick={onDone}>I've saved it, start the questions</button>
        <button type="button" className="button button--ghost" onClick={copy}>{copied ? "Copied" : "Copy code"}</button>
      </div>
    </div>
  );
}

function ContinueForm({ onFound }: { onFound: (s: Student) => void }) {
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [hint, setHint] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim() || !code.trim()) return setHint("Enter both your name and your return code.");
    setBusy(true);
    setError(null);
    try {
      onFound(await api.continueStudent(name.trim(), code.trim()));
    } catch (err) {
      // A 404 is an ordinary "no match", shown as a hint rather than an error box
      if (err instanceof ApiError && err.status === 404) setHint(err.message);
      else setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="returning" onSubmit={submit} noValidate>
      <h2 className="h3">Coming back?</h2>
      <p className="quiet small">Use the name you started with and your return code.</p>
      <label className="field">
        <span className="field__label">Your name</span>
        <input value={name} onChange={e => { setName(e.target.value); setHint(null); }} maxLength={80} autoComplete="off" />
      </label>
      <label className="field">
        <span className="field__label">Return code</span>
        <input className="code-input" value={code} onChange={e => { setCode(e.target.value); setHint(null); }}
               placeholder="K7P-3QX" maxLength={20} autoComplete="off" autoCapitalize="characters" spellCheck={false} />
      </label>
      {hint && <p className="hint" role="alert">{hint}</p>}
      {error != null && <ErrorNote error={error} />}
      <button className="button button--ghost" disabled={busy}>{busy ? "Checking…" : "Continue"}</button>
    </form>
  );
}
