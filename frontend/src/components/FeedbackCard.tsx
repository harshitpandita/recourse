import { FormEvent, useEffect, useState } from "react";
import { api, ApiError } from "../api";
import { ErrorNote } from "./Feedback";

/**
 * Pilot feedback, asked once after a student's first contest. The server decides when to ask
 * (ContestResult.askForFeedback) and keeps one answer per student.
 *
 * If the student closes the card or moves on without sending, we remember that in this browser
 * so the pushback log can offer a small "Share feedback" link later.
 */
const shownKey = (studentId: string) => `recourse.feedbackShown.${studentId}`;

export function feedbackWasShown(studentId: string): boolean {
  try { return localStorage.getItem(shownKey(studentId)) === "1"; } catch { return false; }
}

function markShown(studentId: string) {
  try { localStorage.setItem(shownKey(studentId), "1"); } catch { /* storage unavailable */ }
}

const questions = [
  { key: "matchesMadeSense", label: "The matches made sense" },
  { key: "explanationHelped", label: "The explanation helped" },
  { key: "pushingBackWorked", label: "Pushing back felt like it worked" },
] as const;

type Key = typeof questions[number]["key"];

export function FeedbackCard({ studentId, onClose }: { studentId: string; onClose?: () => void }) {
  const [ratings, setRatings] = useState<Partial<Record<Key, number>>>({});
  const [comment, setComment] = useState("");
  const [state, setState] = useState<"asking" | "sent" | "closed">("asking");
  const [hint, setHint] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => markShown(studentId), [studentId]);

  if (state === "closed") return null;

  if (state === "sent") {
    return (
      <div className="feedback-card" role="status">
        <p className="feedback-card__thanks">Thank you. Your feedback is saved.</p>
      </div>
    );
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (questions.some(q => ratings[q.key] === undefined)) return setHint("Pick a number for each of the three.");
    setBusy(true);
    setError(null);
    try {
      await api.sendFeedback(studentId, {
        matchesMadeSense: ratings.matchesMadeSense!, explanationHelped: ratings.explanationHelped!,
        pushingBackWorked: ratings.pushingBackWorked!, comment: comment.trim() || null,
      });
      setState("sent");
    } catch (err) {
      // Already answered (for example in another tab): nothing more to ask
      if (err instanceof ApiError && err.status === 409) setState("sent");
      else setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="feedback-card" onSubmit={submit} noValidate>
      <h3 className="h3">Before you go on: how did that feel?</h3>
      <p className="quiet small feedback-card__note">
        Your answers are saved without your name and may be used in a student project report.
      </p>
      <p className="quiet small rating__key">1 means not at all, 5 means completely.</p>
      {questions.map(q => (
        <fieldset key={q.key} className="rating">
          <legend className="rating__label">{q.label}</legend>
          <div className="rating__scale">
            <span className="rating__end" aria-hidden="true">Not at all</span>
            {[1, 2, 3, 4, 5].map(n => (
              <button type="button" key={n} className={`rating__num${ratings[q.key] === n ? " rating__num--on" : ""}`}
                      aria-pressed={ratings[q.key] === n}
                      aria-label={`${n} out of 5${n === 1 ? ", not at all" : n === 5 ? ", completely" : ""}`}
                      onClick={() => { setRatings(r => ({ ...r, [q.key]: n })); setHint(null); }}>
                {n}
              </button>
            ))}
            <span className="rating__end" aria-hidden="true">Completely</span>
          </div>
        </fieldset>
      ))}
      <label className="field">
        <span className="field__label">Anything else you'd like to say? (optional)</span>
        <textarea rows={3} maxLength={1000} value={comment} onChange={e => setComment(e.target.value)} />
      </label>
      {hint && <p className="hint" role="alert">{hint}</p>}
      {error != null && <ErrorNote error={error} />}
      <div className="actions">
        <button className="button button--primary" disabled={busy}>{busy ? "Sending…" : "Send"}</button>
        <button type="button" className="button button--ghost" onClick={() => { setState("closed"); onClose?.(); }}>
          Not now
        </button>
      </div>
    </form>
  );
}
