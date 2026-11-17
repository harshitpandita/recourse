export function Loading({ what }: { what: string }) {
  return <p className="quiet" role="status">Loading {what}…</p>;
}

export function ErrorNote({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof Error ? error.message : "Something went wrong.";
  const offline = message.includes("Failed to fetch") || message.includes("NetworkError");
  return (
    <div className="error-note" role="alert">
      <p>{offline ? "Can't reach the Recourse server. Start the backend and try again." : message}</p>
      {onRetry && <button className="button button--ghost" onClick={onRetry}>Try again</button>}
    </div>
  );
}
