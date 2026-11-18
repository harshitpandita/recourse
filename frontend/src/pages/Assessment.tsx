import { useEffect, useState } from "react";
import { api, BeliefChange, Question, Student } from "../api";
import { RangeMeter } from "../components/RangeMeter";
import { ErrorNote, Loading } from "../components/Feedback";

interface Props {
  student: Student;
  /** If given, continue this question (e.g. a follow-up after a contest) instead of starting a new session. */
  initial: Question | null;
  onFinished: () => void;
}

export function Assessment({ student, initial, onFinished }: Props) {
  const [question, setQuestion] = useState<Question | null>(initial);
  const [loading, setLoading] = useState(!initial);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [changes, setChanges] = useState<BeliefChange[]>([]);
  const [chosen, setChosen] = useState<string | null>(null);
  const [showWhy, setShowWhy] = useState(false);
  const followUp = question?.progress.followUp ?? initial?.progress.followUp ?? false;

  useEffect(() => {
    if (initial) return;
    let live = true;
    api.startSession(student.id)
      .then(q => { if (!live) return; q ? setQuestion(q) : setDone(true); })
      .catch(e => live && setError(e))
      .finally(() => live && setLoading(false));
    return () => { live = false; };
  }, [student.id, initial]);

  async function pick(optionId: string) {
    if (!question || chosen) return;
    setChosen(optionId);
    setError(null);
    try {
      const result = await api.answer(question.sessionId, question.id, optionId);
      setChanges(result.changes);
      setShowWhy(false);
      if (result.done || !result.next) {
        setQuestion(null);
        setDone(true);
      } else {
        setQuestion(result.next);
      }
    } catch (e) {
      setError(e);
    } finally {
      setChosen(null);
    }
  }

  if (loading) return <Loading what="your first question" />;

  return (
    <div className="assess">
      <section className="assess__main">
        {followUp && !done && (
          <p className="banner">You pushed back on something. These few questions check it again from a different angle.</p>
        )}

        {error != null && <ErrorNote error={error} />}

        {question && (
          <>
            <Progress q={question} />
            <h1 className="question">{question.text}</h1>
            <div className="options" role="group" aria-label="Answers">
              {question.options.map(o => (
                <button key={o.id} className={`option${chosen === o.id ? " option--chosen" : ""}`}
                        onClick={() => pick(o.id)} disabled={chosen !== null}>
                  {o.text}
                </button>
              ))}
            </div>
            <button className="link-button why-toggle" aria-expanded={showWhy} onClick={() => setShowWhy(s => !s)}>
              {showWhy ? "Hide why we're asking" : "Why are we asking this?"}
            </button>
            {showWhy && <p className="why">{question.reason}</p>}
          </>
        )}

        {done && (
          <div className="finished">
            <h1 className="question">
              {followUp ? "Thanks. That settles it for now." : "That's enough for a first picture."}
            </h1>
            <p className="lede">
              {followUp
                ? "Your matches have been recalculated with what you just told us."
                : "Nothing is final. You can disagree with any match, and we'll update."}
            </p>
            <button className="button button--primary" onClick={onFinished}>See your matches</button>
          </div>
        )}
      </section>

      <aside className="assess__side" aria-live="polite">
        <h2 className="h3">What your last answer changed</h2>
        {changes.length === 0 ? (
          <p className="quiet">Each answer is a piece of evidence. Here you'll see what it moved.</p>
        ) : (
          <ul className="changes">
            {changes.map(c => (
              <li key={c.attribute} className="changes__item">
                <span className="changes__label">{c.label}</span>
                <RangeMeter size="sm" level={c.levelAfter} confidence={c.confidenceAfter}
                            contradictory={c.contradictoryAfter} label={c.label}
                            before={{ level: c.levelBefore, confidence: c.confidenceBefore }} />
              </li>
            ))}
          </ul>
        )}
      </aside>
    </div>
  );
}

function Progress({ q }: { q: Question }) {
  const { asked, max, followUp } = q.progress;
  const n = asked + 1;
  return (
    <div className="progress">
      <span className="quiet">
        {followUp ? `Check ${n} of up to ${max}` : `Question ${n}`}
        {!followUp && <span className="progress__of">. About {max} questions, around five minutes.</span>}
      </span>
      <div className="progress__bar" aria-hidden="true"><span style={{ width: `${Math.min(100, (asked / max) * 100)}%` }} /></div>
    </div>
  );
}
