import { Evidence } from "../api";

/** Something the student said, set in the "student's voice" serif. */
export function Quote({ e }: { e: Evidence }) {
  const disputed = e.disputedBy.length > 0;
  if (e.source === "contest") {
    return (
      <figure className="quote quote--contest">
        <blockquote>{e.note ?? "You disagreed with this."}</blockquote>
        <figcaption>Your correction, about {e.label.toLowerCase()}</figcaption>
      </figure>
    );
  }
  return (
    <figure className={`quote${disputed ? " quote--disputed" : ""}`}>
      <blockquote>{e.optionText}</blockquote>
      <figcaption>
        Your answer to: {e.questionText}
        {disputed && <span className="tag tag--mixed">You disputed this</span>}
      </figcaption>
    </figure>
  );
}
