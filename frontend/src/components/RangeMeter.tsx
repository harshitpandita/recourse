import { certainty } from "../api";

interface Props {
  /** Estimate, 0..1 */
  level: number;
  /** How sure the system is, 0..1. Lower confidence draws a wider band. */
  confidence: number;
  contradictory?: boolean;
  /** Optional earlier state, drawn as an outline so the change is visible. */
  before?: { level: number; confidence: number };
  /** Words at each end of the scale. */
  ends?: [string, string];
  showWords?: boolean;
  size?: "sm" | "md";
  label?: string;
}

/** Half-width of the uncertainty band. At zero confidence it covers most of the scale. */
export function spread(confidence: number) {
  return 0.06 + 0.4 * (1 - Math.max(0, Math.min(1, confidence)));
}

function band(level: number, confidence: number) {
  const s = spread(confidence);
  const lo = Math.max(0, level - s);
  const hi = Math.min(1, level + s);
  return { left: `${lo * 100}%`, width: `${(hi - lo) * 100}%` };
}

/**
 * The signature element of Recourse: a belief is shown as a range, not a point.
 * The marker is the estimate; the shaded band is the uncertainty around it.
 */
export function RangeMeter({ level, confidence, contradictory, before, ends, showWords = true, size = "md", label }: Props) {
  const description = `${label ? label + ": " : ""}estimate ${Math.round(level * 100)} out of 100, ${certainty(confidence)}${contradictory ? ", mixed signals" : ""}`;
  return (
    <div className={`range range--${size}${contradictory ? " range--mixed" : ""}`}>
      <div className="range__track" role="img" aria-label={description}>
        <span className="range__mid" />
        {before && <span className="range__ghost" style={band(before.level, before.confidence)} />}
        <span className="range__band" style={band(level, confidence)} />
        <span className="range__marker" style={{ left: `${level * 100}%` }} />
      </div>
      {(ends || showWords) && (
        <div className="range__legend">
          {ends ? <span>{ends[0]}</span> : <span />}
          {showWords && (
            <span className="range__words">
              {contradictory ? "mixed signals" : certainty(confidence)}
            </span>
          )}
          {ends ? <span>{ends[1]}</span> : <span />}
        </div>
      )}
    </div>
  );
}
