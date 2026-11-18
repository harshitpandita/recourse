import { useEffect, useState } from "react";
import { api, Belief, ContestResult, Evidence, Profile, Question, Student } from "../api";
import { RangeMeter } from "../components/RangeMeter";
import { Quote } from "../components/Quote";
import { ContestOutcomeView } from "../components/ContestOutcomeView";
import { FeedbackCard } from "../components/FeedbackCard";
import { ErrorNote, Loading } from "../components/Feedback";

/** Plain words for each end of the scale, per pillar. */
const ends: Record<string, [string, string]> = {
  interests: ["not for me", "draws me in"],
  values: ["matters little", "matters a lot"],
  work_style: ["not my style", "my style"],
  abilities: ["not a strength", "a strength"],
};

export function Model({ student, onFollowUp }: { student: Student; onFollowUp: (q: Question | null) => void }) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [evidence, setEvidence] = useState<Evidence[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [result, setResult] = useState<ContestResult | null>(null);

  const load = () => {
    setError(null);
    Promise.all([api.profile(student.id), api.evidence(student.id)])
      .then(([p, e]) => { setProfile(p); setEvidence(e); })
      .catch(setError);
  };
  useEffect(load, [student.id]);

  if (error) return <ErrorNote error={error} onRetry={load} />;
  if (!profile) return <Loading what="your profile" />;

  return (
    <div className="model">
      <header className="page-head">
        <h1 className="display display--sm">What we think about you</h1>
        <p className="lede">
          Built from {profile.evidenceCount} pieces of evidence across {profile.questionsAnswered} {profile.questionsAnswered === 1 ? "answer" : "answers"}
          {profile.contestCount > 0 ? ` and ${profile.contestCount} ${profile.contestCount === 1 ? "objection" : "objections"}` : ""}. Open any line to see the answers behind it,
          and dispute any you disagree with.
        </p>
      </header>

      {profile.contradictions.length > 0 && (
        <div className="mixed">
          <h2 className="h3">Mixed signals</h2>
          <p>
            Your answers pointed both ways on {listOf(profile.contradictions.map(b => b.label.toLowerCase()))}.
            We're keeping both sides instead of picking one.
          </p>
        </div>
      )}

      {result && (
        <div className="contest-done">
          <ContestOutcomeView outcome={result.outcome} />
          <div className="actions">
            {result.followUp && (
              <button className="button button--primary" onClick={() => onFollowUp(result.followUp)}>
                Answer {result.followUp.progress.max} quick questions to settle this
              </button>
            )}
            <button className="button button--ghost" onClick={() => setResult(null)}>Done</button>
          </div>
          {result.askForFeedback && <FeedbackCard studentId={student.id} />}
        </div>
      )}

      {profile.pillars.map(p => (
        <section key={p.key} className="pillar">
          <div className="pillar__head">
            <h2 className="h2">{p.label}</h2>
            <span className="quiet small">Based on {p.basis}</span>
          </div>
          <ul className="beliefs">
            {p.beliefs.map(b => (
              <BeliefRow key={b.attribute} b={b} ends={ends[p.key]}
                         open={open === b.attribute}
                         onToggle={() => setOpen(o => (o === b.attribute ? null : b.attribute))}
                         evidence={evidence.filter(e => e.attribute === b.attribute)}
                         student={student}
                         onContested={r => { setResult(r); setOpen(null); load(); window.scrollTo({ top: 0, behavior: "smooth" }); }} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

function listOf(items: string[]) {
  if (items.length <= 1) return items.join("");
  return items.slice(0, -1).join(", ") + " and " + items[items.length - 1];
}

function BeliefRow({ b, ends, open, onToggle, evidence, student, onContested }: {
  b: Belief; ends: [string, string]; open: boolean; onToggle: () => void;
  evidence: Evidence[]; student: Student; onContested: (r: ContestResult) => void;
}) {
  const unknown = b.evidenceCount === 0;
  return (
    <li className={`belief${open ? " belief--open" : ""}${unknown ? " belief--unknown" : ""}`}>
      <button className="belief__summary" onClick={onToggle} aria-expanded={open} disabled={unknown}>
        <span className="belief__name">
          {b.label}
          <span className="belief__desc">{b.description}</span>
        </span>
        <span className="belief__meter">
          {unknown ? <span className="quiet small">No evidence yet</span>
            : <RangeMeter level={b.level} confidence={b.confidence} contradictory={b.contradictory} ends={ends} label={b.label} />}
        </span>
      </button>
      {open && (
        <div className="belief__evidence">
          {evidence.map(e => <EvidenceItem key={e.id} e={e} student={student} onContested={onContested} />)}
        </div>
      )}
    </li>
  );
}

function EvidenceItem({ e, student, onContested }: { e: Evidence; student: Student; onContested: (r: ContestResult) => void }) {
  const [asking, setAsking] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const canDispute = e.source !== "contest" && e.direction !== "neutral" && e.disputedBy.length === 0;

  async function dispute() {
    setBusy(true);
    setError(null);
    try {
      onContested(await api.contestEvidence(student.id, e.id, reason));
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="evidence">
      <Quote e={e} />
      <div className="evidence__side">
        <span className={`tag tag--${e.direction}`}>{e.direction === "supports" ? "points toward" : e.direction === "contradicts" ? "points away" : "neutral"}</span>
        {canDispute && !asking && <button className="link-button" onClick={() => setAsking(true)}>That's not me</button>}
      </div>
      {asking && (
        <div className="evidence__dispute">
          <input value={reason} onChange={x => setReason(x.target.value)} maxLength={500}
                 placeholder="I picked that, but it doesn't really describe me" aria-label="Why this isn't you (optional)" />
          <button className="button button--primary button--sm" onClick={dispute} disabled={busy}>{busy ? "Saving…" : "Dispute"}</button>
          <button className="button button--ghost button--sm" onClick={() => setAsking(false)}>Cancel</button>
          {error != null && <ErrorNote error={error} />}
        </div>
      )}
    </div>
  );
}
