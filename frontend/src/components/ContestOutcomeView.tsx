import { ContestOutcome, pct } from "../api";
import { RangeMeter } from "./RangeMeter";

/** Shows exactly what an objection changed: beliefs before and after, and how the ranking moved. */
export function ContestOutcomeView({ outcome }: { outcome: ContestOutcome }) {
  const position = (id: string, list: { careerId: string }[]) => list.findIndex(r => r.careerId === id);
  return (
    <div className="outcome">
      <h3 className="h3">What changed</h3>
      <ul className="outcome__beliefs">
        {outcome.changes.map(c => {
          const delta = c.confidenceAfter - c.confidenceBefore;
          return (
            <li key={c.attribute}>
              <div className="outcome__head">
                <span className="outcome__label">{c.label}</span>
                <span className="quiet">
                  {delta < 0 ? `Now ${pct(c.confidenceAfter)} sure, down from ${pct(c.confidenceBefore)}`
                    : `Now ${pct(c.confidenceAfter)} sure, up from ${pct(c.confidenceBefore)}`}
                </span>
              </div>
              <RangeMeter level={c.levelAfter} confidence={c.confidenceAfter} contradictory={c.contradictoryAfter}
                          before={{ level: c.levelBefore, confidence: c.confidenceBefore }} label={c.label} />
            </li>
          );
        })}
      </ul>
      <p className="quiet small">The outline shows where we stood before. Nothing you said earlier was deleted: your objection is kept alongside it.</p>

      <div className="outcome__rank">
        <div>
          <h4 className="h4">Before</h4>
          <ol>{outcome.before.map(r => <li key={r.careerId}>{r.title}</li>)}</ol>
        </div>
        <div>
          <h4 className="h4">After</h4>
          <ol>
            {outcome.after.map((r, i) => {
              const was = position(r.careerId, outcome.before);
              const move = was === -1 ? "new" : was > i ? "up" : was < i ? "down" : "same";
              return (
                <li key={r.careerId}>
                  {r.title}
                  {move !== "same" && <span className={`move move--${move}`}>{move === "new" ? "new" : move === "up" ? `up ${was - i}` : `down ${i - was}`}</span>}
                </li>
              );
            })}
          </ol>
        </div>
      </div>
    </div>
  );
}
