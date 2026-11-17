import { useEffect, useState } from "react";
import { api, ContestResult, Explanation, pct, Question, Recommendation, Student } from "../api";
import { RangeMeter } from "../components/RangeMeter";
import { Quote } from "../components/Quote";
import { ContestOutcomeView } from "../components/ContestOutcomeView";
import { ErrorNote, Loading } from "../components/Feedback";

interface Props {
  student: Student;
  onStart: () => void;
  onFollowUp: (q: Question | null) => void;
}

export function Matches({ student, onStart, onFollowUp }: Props) {
  const [recs, setRecs] = useState<Recommendation[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [open, setOpen] = useState<string | null>(null);

  const load = () => {
    setError(null);
    api.recommendations(student.id)
      .then(r => { setRecs(r); setOpen(o => o ?? r[0]?.careerId ?? null); })
      .catch(setError);
  };
  useEffect(load, [student.id]);

  if (error) return <ErrorNote error={error} onRetry={load} />;
  if (!recs) return <Loading what="your matches" />;
  if (recs.length === 0) {
    return (
      <div className="empty">
        <h1 className="display display--sm">Answer a few questions first</h1>
        <p className="lede">Recourse needs some evidence before it can suggest anything. It takes about five minutes.</p>
        <button className="button button--primary" onClick={onStart}>Start the questions</button>
      </div>
    );
  }

  return (
    <div className="matches">
      <header className="page-head">
        <h1 className="display display--sm">Three directions worth a look</h1>
        <p className="lede">
          Picked to be different from each other, not three versions of the same job. The shaded band shows how sure we are:
          wider means less sure.
        </p>
      </header>
      <ol className="match-list">
        {recs.map((r, i) => (
          <MatchRow key={r.careerId} rank={i + 1} rec={r} student={student}
                    placedForVariety={recs.slice(0, i).some(above => shownFit(above) < shownFit(r))}
                    open={open === r.careerId}
                    onToggle={() => setOpen(o => (o === r.careerId ? null : r.careerId))}
                    onContested={load} onFollowUp={onFollowUp} />
        ))}
      </ol>
    </div>
  );
}

/** The fit as the student sees it (whole percent). */
const shownFit = (r: Recommendation) => Math.round(r.score * 100);

interface RowProps {
  rank: number;
  rec: Recommendation;
  /**
   * True when this match shows a higher fit than one listed above it. That only
   * happens through the diversity rule (a similar career already picked costs a
   * little), so we say so instead of leaving the order looking wrong.
   */
  placedForVariety: boolean;
  student: Student;
  open: boolean;
  onToggle: () => void;
  onContested: () => void;
  onFollowUp: (q: Question | null) => void;
}

function MatchRow({ rank, rec, student, placedForVariety, open, onToggle, onContested, onFollowUp }: RowProps) {
  const [explanation, setExplanation] = useState<Explanation | null>(null);
  const [explaining, setExplaining] = useState(false);
  const [contesting, setContesting] = useState(false);
  const [result, setResult] = useState<ContestResult | null>(null);

  useEffect(() => {
    if (!open || explanation || explaining) return;
    setExplaining(true);
    api.explanation(student.id, rec.careerId)
      .then(setExplanation)
      .catch(() => setExplanation(null))
      .finally(() => setExplaining(false));
  }, [open, explanation, explaining, student.id, rec.careerId]);

  return (
    <li className={`match${open ? " match--open" : ""}`}>
      <button className="match__summary" onClick={onToggle} aria-expanded={open}>
        <span className="match__rank" aria-label={`Match ${rank}`}>{rank}</span>
        <span className="match__title">
          <span className="match__name">{rec.title}</span>
          <span className="match__blurb">{rec.summary}</span>
          {placedForVariety && (
            <span className="match__note">Listed after a lower match so your three aren't all the same kind of work.</span>
          )}
        </span>
        <span className="match__fit">
          <span className="match__pct">{pct(rec.score)}</span>
          <span className="quiet small">fit</span>
        </span>
        <span className="match__meter">
          <RangeMeter level={rec.score} confidence={rec.confidence} label={`${rec.title} fit`} />
        </span>
      </button>

      {open && (
        <div className="match__body">
          <div className="match__cols">
            <section>
              <h3 className="h3">Why we think so</h3>
              <ul className="factors">
                {rec.reasons.map(f => <li key={f.attribute}><span className="dot dot--fit" />{f.label}</li>)}
              </ul>
              {rec.concerns.length > 0 && (
                <>
                  <h3 className="h3">Worth thinking about</h3>
                  <ul className="factors">
                    {rec.concerns.map(f => <li key={f.attribute}><span className="dot dot--concern" />{f.label} may not line up well</li>)}
                  </ul>
                </>
              )}
              <h3 className="h3">In plain words</h3>
              {explaining && <p className="quiet">Writing an explanation…</p>}
              {explanation && (
                <>
                  <p className="explanation">{explanation.text}</p>
                  <p className="quiet small">
                    {explanation.generatedBy.startsWith("llm")
                      ? "Written by a small model running on this computer, from the facts above only."
                      : "Written from a template using the facts above."}
                  </p>
                </>
              )}
            </section>
            <section>
              <h3 className="h3">What you told us</h3>
              <div className="quotes">{rec.evidence.slice(0, 4).map(e => <Quote key={e.id} e={e} />)}</div>
            </section>
          </div>

          <div className="match__facts">
            <div>
              <h4 className="h4">How people usually get there</h4>
              <ul className="plain">{rec.routes.map(r => <li key={r}>{r}</li>)}</ul>
            </div>
            {rec.entranceExams.length > 0 && (
              <div>
                <h4 className="h4">Entrance exams</h4>
                <p>{rec.entranceExams.join(", ")}</p>
              </div>
            )}
            <div>
              <h4 className="h4">Sources</h4>
              <ul className="plain">
                {rec.sources.map(s => <li key={s.url}><a href={s.url} target="_blank" rel="noreferrer">{s.label}</a></li>)}
              </ul>
              {!rec.verified && <p className="quiet small">This entry hasn't been checked against its sources yet.</p>}
            </div>
          </div>

          {result ? (
            <div className="contest-done">
              <ContestOutcomeView outcome={result.outcome} />
              <div className="actions">
                {result.followUp && (
                  <button className="button button--primary" onClick={() => onFollowUp(result.followUp)}>
                    Answer {result.followUp.progress.max} quick questions to settle this
                  </button>
                )}
                <button className="button button--ghost" onClick={() => { setResult(null); onContested(); }}>
                  Show updated matches
                </button>
              </div>
            </div>
          ) : contesting ? (
            <ContestForm rec={rec} student={student} onCancel={() => setContesting(false)}
                         onDone={r => { setContesting(false); setResult(r); }} />
          ) : (
            <div className="actions">
              <button className="button button--primary" onClick={() => setContesting(true)}>This doesn't sound like me</button>
            </div>
          )}
        </div>
      )}
    </li>
  );
}

function ContestForm({ rec, student, onCancel, onDone }: {
  rec: Recommendation; student: Student; onCancel: () => void; onDone: (r: ContestResult) => void;
}) {
  const [reasons, setReasons] = useState<string[]>([]);
  const [concerns, setConcerns] = useState<string[]>([]);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [hint, setHint] = useState<string | null>(null);

  const toggle = (list: string[], set: (v: string[]) => void, key: string) => {
    setHint(null);
    set(list.includes(key) ? list.filter(k => k !== key) : [...list, key]);
  };

  async function send() {
    if (reasons.length + concerns.length === 0) return setHint("Pick at least one thing we got wrong.");
    setBusy(true);
    setError(null);
    try {
      onDone(await api.contestRecommendation(student.id, rec.careerId, reasons, concerns, note));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="contest">
      <h3 className="h3">What did we get wrong?</h3>
      <p className="quiet">Pick anything that isn't true for you. We'll keep what you said before, record your objection next to it, and recalculate.</p>
      <div className="contest__choices">
        {rec.reasons.map(f => (
          <label key={f.attribute} className={`check${reasons.includes(f.attribute) ? " check--on" : ""}`}>
            <input type="checkbox" checked={reasons.includes(f.attribute)} onChange={() => toggle(reasons, setReasons, f.attribute)} />
            {f.prompt}
          </label>
        ))}
        {rec.concerns.map(f => (
          <label key={f.attribute} className={`check${concerns.includes(f.attribute) ? " check--on" : ""}`}>
            <input type="checkbox" checked={concerns.includes(f.attribute)} onChange={() => toggle(concerns, setConcerns, f.attribute)} />
            {f.prompt}
          </label>
        ))}
      </div>
      <label className="field">
        <span className="field__label">Anything else? (optional)</span>
        <textarea rows={2} maxLength={500} value={note} onChange={e => setNote(e.target.value)}
                  placeholder="I like the idea, but I can't see myself doing research all day" />
      </label>
      {hint && <p className="hint" role="alert">{hint}</p>}
      {error != null && <ErrorNote error={error} />}
      <div className="actions">
        <button className="button button--primary" onClick={send} disabled={busy}>{busy ? "Recalculating…" : "Send my objection"}</button>
        <button className="button button--ghost" onClick={onCancel}>Cancel</button>
      </div>
    </div>
  );
}
