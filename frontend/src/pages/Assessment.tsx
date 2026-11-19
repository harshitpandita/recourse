import { useEffect, useState } from "react";
import { api, BeliefChange, Question, Student } from "../api";
import { RangeMeter } from "../components/RangeMeter";
import { ErrorNote, Loading } from "../components/Feedback";

/**
 * One "start" request per student at a time. React runs effects twice in development, and a
 * double request could open two sessions at once; both callers share the same answer instead.
 */
const starting = new Map<string, Promise<Question | null>>();
function startOnce(studentId: string): Promise<Question | null> {
  let p = starting.get(studentId);
  if (!p) {
    p = api.startSession(studentId).finally(() => starting.delete(studentId));
    starting.set(studentId, p);
  }
  return p;
}

interface Props {
  student: Student;
  /**
   * If given, show this question (e.g. the follow-up after a contest). Otherwise the server picks up
   * wherever the student left off, so a reload never restarts the count.
   */
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
  /** Answers given on this visit, to tell "just finished" apart from "finished earlier". */
  const [answered, setAnswered] = useState(0);
  /** Remembered after the last question is gone, so the closing words fit a follow-up resumed after a reload. */
  const [wasFollowUp, setWasFollowUp] = useState(initial?.progress.followUp ?? false);
  useEffect(() => { if (question) setWasFollowUp(question.progress.followUp); }, [question]);
  const followUp = question?.progress.followUp ?? wasFollowUp;

  useEffect(() => {
    if (initial) return;
    let live = true;
    startOnce(student.id)
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
      setAnswered(n => n + 1);
      // On a phone the options sit low on the page; bring the next question into view
      if (window.scrollY > 0) window.scrollTo({ top: 0, behavior: "smooth" });
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

  if (loading) return <Loading what="your questions" />;

  const alreadyDone = done && answered === 0 && !initial;

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
              {alreadyDone ? "You've answered all the questions."
                : followUp ? "Thanks. That settles it for now." : "That's enough for a first picture."}
            </h1>
            <p className="lede">
              {alreadyDone
                ? "Your matches are ready. If one doesn't sound like you, push back on it and we'll ask a few more questions to check."
                : followUp
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
