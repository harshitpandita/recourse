# Recourse

A personalised career and education advisor for Indian students that keeps an evidence-based model of the student, explains every recommendation, and lets the student push back.

Most career tests give a result and stop there. Recourse treats a recommendation as a claim that the student can dispute. When a student says "that's not me", the system records the objection as new evidence, recalculates, shows exactly what changed, and asks a few follow-up questions to check again. It never deletes or edits what the student said before.

![Matches page](docs/screenshots/matches.png)

## Features

- **Adaptive assessment.** Each next question is the one expected to be most useful, given what the system is still unsure about. Every question comes with a plain-English reason for why it was asked. A session takes 12 to 20 questions.
- **A student model across four pillars.** Interests (Holland's RIASEC types), work values (O\*NET Work Importance Profiler), work style, and self-reported abilities.
- **Belief with uncertainty.** For every attribute the system keeps an estimate and a separate confidence. The UI draws both as an estimate with an uncertainty band. Contradictory answers lower confidence instead of being averaged away.
- **Explainable matching.** Careers are ranked by deterministic rules, with pillar-weighted scores and a small diversity penalty so the top three aren't all the same kind of job. Each match shows its reasons, its concerns, and the student's own answers behind them.
- **Recourse: contestable recommendations.**
  - The student can dispute a single piece of evidence or a whole recommendation.
  - Each objection becomes new evidence (source `CONTEST`), linked to what it disputes.
  - The system recalculates confidence, re-ranks, and logs the before and after.
  - It then opens a short follow-up session on the disputed areas.
  - A dispute is evidence, not an override: if the follow-up answers confirm the original reading, the match can stay.
- **Pushback log.** Every contest and its effect on the ranking is kept and visible to the student.
- **Local explanations.** A small language model running on the same machine (Phi-4-mini through Ollama) rewrites the computed facts as a short note. It only rephrases. It never ranks, scores or decides. If it is slow or unavailable, a deterministic template is used instead.
- **Indian context.** Each career lists Indian education routes and entrance exams, with sources.
- **Runs offline.** There are no paid APIs and no cloud services, and the database is an embedded file.

## How it works

```
answers ──> evidence ──> beliefs ──> matches ──> explanation
              ^           (estimate +   (rules,     (local LLM,
              |            confidence)   traceable)  or template)
              |                              |
              └──── contest (new evidence) <─┘
```

1. **Evidence.** Each answer option in `questions.json` maps to one or more pieces of evidence: an attribute, a direction (supports or contradicts) and a strength. Evidence is append-only.
2. **Belief.** For each attribute, supporting and contradicting evidence are combined separately with a noisy-OR. Each new item closes part of the remaining gap, so repeated similar answers have diminishing effect. Confidence grows with the amount of evidence and falls when the evidence disagrees with itself.
3. **Question selection.** Each question is scored by information value × importance × reliability × discrimination ÷ effort. After at least 12 questions, the assessment stops when 70% of attributes are known with reasonable confidence or no remaining question is worth asking. It never goes past 20.
4. **Matching.** Each career's interest weights come from its O\*NET interest code (one to three RIASEC letters, the first weighted most). Its work-value, work-style and ability weights are set by hand, informed by the O\*NET description (see [docs/career-verification.md](docs/career-verification.md)). A match combines the four pillars (interests 40%, abilities 25%, values 20%, work style 15%). Every score can be traced down to individual pieces of evidence.
5. **Contest.** An objection adds counter-evidence, beliefs and ranking are recomputed, the change is logged, and a follow-up session focuses on the disputed attributes.

## Tech stack

| Part | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3, Spring Data JPA, Maven |
| Database | H2, embedded, file-based (`backend/data/`) |
| Frontend | React 18, TypeScript, Vite, plain CSS with variables |
| Language model | Ollama with `phi4-mini`, called over local HTTP (optional) |
| Tests | JUnit 5, Spring Boot Test |

## Project structure

```
backend/                     Spring Boot application (package com.recourse)
  engine/                    Pure Java core with no framework code: evidence, beliefs,
                             question selection, matching, contests
  data/                      Loads questions.json (37 questions) and careers.json (25 careers)
  store/                     JPA entities and repositories (evidence is append-only)
  service/                   Assessment, recommendations, contests, student profile, demo seed
  explain/                   Ollama client and explanation service with template fallback
  api/                       REST controller under /api/v1
frontend/                    React app (welcome, questions, matches, profile, pushback log)
prototypes/                  Earlier Python prototypes the engine was ported from
```

## Running it on Windows

### Requirements

- Java 21 (JDK)
- Maven 3.9 or later
- Node.js 18 or later (with npm)
- Optional: [Ollama](https://ollama.com) for written explanations. Without it, the app uses its built-in template explanations.

### 1. Optional: start the language model

```
ollama pull phi4-mini
```

Ollama runs in the background after installation, on `http://localhost:11434`.

### 2. Start the backend

```
cd backend
mvn spring-boot:run
```

The API runs on `http://localhost:8080`. To check it, open `http://localhost:8080/api/v1/system`, which reports whether the language model is reachable.

### 3. Start the frontend

In a second terminal:

```
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. In development, the frontend forwards `/api` requests to the backend.

### Demo data

The `demo` profile creates three example students on startup. Each one answers the real adaptive assessment through the same services a real student uses, so their evidence is genuine. The profile does nothing if the students already exist.

Demo students are kept in their own database file (`backend/data/demo/`), separate from real students (`backend/data/recourse.*`). Resetting the demo never touches real data.

| Student | Stage | Leans towards |
|---|---|---|
| Aarav (demo) | Class 12 | Maths, analysis, research |
| Meera (demo) | College | People, communication, teamwork. Also has one contest on her top match, followed by a follow-up session that re-confirms it. |
| Kabir (demo) | Class 10 | Art, design, independence |

Command Prompt:

```
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

PowerShell (the argument needs quotes):

```
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=demo"
```

To reset the demo, stop the backend, delete the demo database, then start again with the demo profile:

- Command Prompt: `rmdir /s /q backend\data\demo`
- PowerShell: `Remove-Item -Recurse -Force backend\data\demo`

To clear real students instead (this deletes their answers for good), stop the backend and delete only the main database files:

- Command Prompt: `del backend\data\recourse.*`
- PowerShell: `Remove-Item backend\data\recourse.*`

## Tests

```
cd backend
mvn test
```

This runs 71 tests:

| Area | Tests |
|---|---|
| Engine: belief calculation, question selection, matching, evidence and contests | 45 |
| Data files: questions and careers load and are valid | 7 |
| End-to-end flow through the real services and an in-memory database | 8 |
| Demo seed: personas, idempotency, the contest used in the live demo, and its separate database | 8 |
| Explanation template: the fallback text used when the language model is unavailable | 3 |

The frontend is type-checked as part of its build:

```
cd frontend
npm run build
```

## Explanation benchmark

An opt-in benchmark times the app's real explanation prompt on the demo students' recommendations: 9 prompts, repeated 3 times, plus one cold-start call. It isn't part of `mvn test`. With Ollama running:

```
cd backend
mvn test -Pbenchmark -Dbenchmark.model=phi4-mini
```

Results are appended to `backend/benchmark-results.csv`, one row per call, including the full text.

Results from one laptop (27 timed calls per model):

| | phi4-mini | llama3.2:3b | qwen2.5:3b |
|---|---|---|---|
| Median response time | 7.7 s | 7.8 s | 7.7 s |
| 90th percentile | 9.3 s | 10.4 s | 8.7 s |
| Within the app's 20 s timeout | 27/27 | 27/27 | 27/27 |
| Invites the student to disagree, as the prompt asks | 25/27 | 5/27 | 9/27 |
| Kept to the requested 3 to 4 sentences | 16/27 | 23/27 | 14/27 |

All three models were equally fast. Phi-4-mini was kept because it followed the instruction most relevant to Recourse, inviting the student to disagree, far more reliably.

## API

All endpoints are under `/api/v1`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/system` | Question and career counts, language model status |
| POST, GET | `/students` | Create or list students |
| GET | `/students/{id}`, `/students/{id}/profile`, `/students/{id}/evidence` | Student, belief profile, full evidence trail |
| POST | `/students/{id}/sessions` | Start an assessment session |
| GET | `/sessions/{sessionId}/next` | Next question, with the reason it was chosen |
| POST | `/sessions/{sessionId}/answers` | Answer a question; returns how beliefs changed |
| GET | `/students/{id}/recommendations` | Top three matches with reasons, concerns and evidence |
| GET | `/students/{id}/recommendations/{careerId}/explanation` | Written explanation of one match |
| POST | `/students/{id}/contests/evidence` | Dispute a single piece of evidence |
| POST | `/students/{id}/contests/recommendation` | Dispute a recommendation |
| GET | `/students/{id}/contests` | Pushback log |

## Design principles

- **Decisions are rule-based and deterministic.** The same answers always give the same ranking. The language model writes explanations only.
- **Every recommendation carries its evidence trail.**
- **Nothing is deleted.** Disputes are added as new evidence that points at what it disputes, so the full history stays inspectable.
- **Career data is curated by hand.** Each career lists its sources and is marked as verified only after those sources have been checked. No career data is generated by the language model.

## Limitations and future work

- 25 careers and 37 questions. Phase 2 extends this to 40 to 60 careers and adds career adaptability and decision patterns.
- No user accounts yet. It is a single-machine prototype.
- Abilities are self-reported, not tested.
- Evaluation so far consists of automated tests, simulated personas and a model benchmark. A larger user study with real students is planned for phase 2.

## About

Final-year B.Tech project, The NorthCap University.
Harshit Pandita (23CSU119). Supervisor: Dr. Shilpa Mahajan.
