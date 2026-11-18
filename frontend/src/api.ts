// Types mirror backend api/Views.java. Keep them in sync.

export type Stage = "CLASS_10" | "CLASS_12" | "UNDERGRAD";

/** returnCode is shown as "K7P-3QX". */
export interface Student { id: string; name: string; stage: Stage; createdAt: string; returnCode: string }

export interface Belief {
  attribute: string; label: string; description: string; pillar: string;
  level: number; confidence: number; support: number; contradiction: number;
  contradictory: boolean; evidenceCount: number;
}

export interface Pillar { key: string; label: string; basis: string; beliefs: Belief[] }

export interface Profile {
  student: Student; evidenceCount: number; contestCount: number; questionsAnswered: number;
  pillars: Pillar[]; contradictions: Belief[];
}

export interface Option { id: string; text: string }
export interface Progress { asked: number; min: number; max: number; followUp: boolean }
export interface Question {
  sessionId: string; id: string; text: string; type: string; reason: string;
  options: Option[]; progress: Progress;
}

export interface BeliefChange {
  attribute: string; label: string; levelBefore: number; levelAfter: number;
  confidenceBefore: number; confidenceAfter: number; contradictoryAfter: boolean;
}

export interface AnswerResult { done: boolean; next: Question | null; changes: BeliefChange[] }

export interface Evidence {
  id: string; attribute: string; label: string; pillar: string; direction: string;
  strength: number; reliability: number; source: string;
  questionId: string | null; questionText: string | null; optionText: string | null;
  note: string | null; disputes: string | null; disputedBy: string[]; recordedAt: string;
}

export interface PillarFit { pillar: string; label: string; fit: number; confidence: number }
export interface Factor { attribute: string; label: string; fit: number; prompt: string }
export interface Source { label: string; url: string }

export interface Recommendation {
  careerId: string; title: string; summary: string; riasec: string;
  score: number; confidence: number; pillars: PillarFit[];
  reasons: Factor[]; concerns: Factor[]; evidence: Evidence[];
  routes: string[]; entranceExams: string[]; sources: Source[]; verified: boolean;
}

export interface Explanation { careerId: string; text: string; generatedBy: string; millis: number }
export interface RankEntry { careerId: string; title: string; score: number }

export interface ContestOutcome {
  contestId: string; kind: "EVIDENCE" | "RECOMMENDATION"; target: string; reason: string | null;
  changes: BeliefChange[]; before: RankEntry[]; after: RankEntry[];
  createdEvidence: string[]; createdAt: string;
}

/** askForFeedback is true only after the student's first contest, until they give feedback. */
export interface ContestResult { outcome: ContestOutcome; followUp: Question | null; askForFeedback: boolean }

export interface FeedbackStatus { given: boolean }

export interface FeedbackAnswers {
  matchesMadeSense: number; explanationHelped: number; pushingBackWorked: number; comment: string | null;
}

export interface SystemStatus {
  llmEnabled: boolean; llmReachable: boolean; model: string;
  questions: number; careers: number; unverifiedCareers: number;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

async function call<T>(method: "GET" | "POST", path: string, body?: unknown): Promise<T | null> {
  const res = await fetch(`/api/v1${path}`, {
    method,
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 204) return null;
  if (!res.ok) {
    let detail = `Request failed (${res.status})`;
    try {
      const problem = await res.json();
      if (problem?.detail) detail = problem.detail;
    } catch { /* not JSON */ }
    throw new ApiError(res.status, detail);
  }
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : null;
}

async function must<T>(p: Promise<T | null>): Promise<T> {
  const v = await p;
  if (v === null) throw new ApiError(204, "Empty response");
  return v;
}

export const api = {
  status: () => must(call<SystemStatus>("GET", "/system")),
  student: (id: string) => must(call<Student>("GET", `/students/${id}`)),
  createStudent: (name: string, stage: Stage) => must(call<Student>("POST", "/students", { name, stage })),
  continueStudent: (name: string, code: string) => must(call<Student>("POST", "/students/continue", { name, code })),
  profile: (id: string) => must(call<Profile>("GET", `/students/${id}/profile`)),
  evidence: (id: string) => must(call<Evidence[]>("GET", `/students/${id}/evidence`)),
  startSession: (id: string) => call<Question>("POST", `/students/${id}/sessions`),
  next: (sessionId: string) => call<Question>("GET", `/sessions/${sessionId}/next`),
  answer: (sessionId: string, questionId: string, optionId: string) =>
    must(call<AnswerResult>("POST", `/sessions/${sessionId}/answers`, { questionId, optionId })),
  recommendations: (id: string) => must(call<Recommendation[]>("GET", `/students/${id}/recommendations`)),
  explanation: (id: string, careerId: string) =>
    must(call<Explanation>("GET", `/students/${id}/recommendations/${careerId}/explanation`)),
  contestRecommendation: (id: string, careerId: string, reasons: string[], concerns: string[], note: string) =>
    must(call<ContestResult>("POST", `/students/${id}/contests/recommendation`, { careerId, reasons, concerns, note })),
  contestEvidence: (id: string, evidenceId: string, reason: string) =>
    must(call<ContestResult>("POST", `/students/${id}/contests/evidence`, { evidenceId, reason })),
  contests: (id: string) => must(call<ContestOutcome[]>("GET", `/students/${id}/contests`)),
  feedbackStatus: (id: string) => must(call<FeedbackStatus>("GET", `/students/${id}/feedback`)),
  sendFeedback: (id: string, answers: FeedbackAnswers) => call<void>("POST", `/students/${id}/feedback`, answers),
};

export const pct = (v: number) => `${Math.round(v * 100)}%`;

/** Plain words for a confidence value, used next to every range. */
export function certainty(c: number): string {
  if (c < 0.2) return "barely know yet";
  if (c < 0.45) return "still unsure";
  if (c < 0.7) return "fairly sure";
  return "confident";
}

export const stageLabel: Record<Stage, string> = {
  CLASS_10: "Class 10",
  CLASS_12: "Class 12",
  UNDERGRAD: "In college",
};
