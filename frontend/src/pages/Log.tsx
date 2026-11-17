import { useEffect, useState } from "react";
import { api, ContestOutcome, Student } from "../api";
import { ContestOutcomeView } from "../components/ContestOutcomeView";
import { ErrorNote, Loading } from "../components/Feedback";

const when = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });

export function Log({ student }: { student: Student }) {
  const [items, setItems] = useState<ContestOutcome[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [open, setOpen] = useState<string | null>(null);

  const load = () => { setError(null); api.contests(student.id).then(setItems).catch(setError); };
  useEffect(load, [student.id]);

  if (error) return <ErrorNote error={error} onRetry={load} />;
  if (!items) return <Loading what="your pushback log" />;

  return (
    <div className="log">
      <header className="page-head">
        <h1 className="display display--sm">Every time you pushed back</h1>
        <p className="lede">A permanent record of your objections and what each one changed. Nothing here is ever deleted.</p>
      </header>
      {items.length === 0 ? (
        <p className="quiet">No objections yet. Open a match or a line in "What we think" and tell us where we're wrong.</p>
      ) : (
        <ol className="log-list">
          {items.map(c => (
            <li key={c.contestId} className="log-item">
              <button className="log-item__summary" onClick={() => setOpen(o => (o === c.contestId ? null : c.contestId))}
                      aria-expanded={open === c.contestId}>
                <span className="log-item__when">{when.format(new Date(c.createdAt))}</span>
                <span className="log-item__what">
                  {c.kind === "RECOMMENDATION" ? `Disputed a match: ${titleOf(c)}` : `Disputed an answer about ${c.changes[0]?.label.toLowerCase() ?? "you"}`}
                  {c.reason && <span className="log-item__reason">"{c.reason}"</span>}
                </span>
              </button>
              {open === c.contestId && <ContestOutcomeView outcome={c} />}
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

function titleOf(c: ContestOutcome) {
  return c.before.find(r => r.careerId === c.target)?.title ?? c.after.find(r => r.careerId === c.target)?.title ?? c.target;
}
