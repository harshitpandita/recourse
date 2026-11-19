# Manual testing checklist

Work through the sections in order. Tick each box when it behaves as described. If something is wrong, slow, confusing or ugly, write it in the issue log at the bottom (section and step number), then carry on.

Run everything in Command Prompt from `C:\recourse` unless a step says otherwise. Allow about 1.5 to 2 hours for the whole list. Sections 1 to 8 are the core. The rest can be split over two sittings.

**General things to watch for on every screen**
- Text is sentence case, plain and warm. No all-caps labels, arrows in buttons, emojis, typos or developer words (like "null", "undefined", "evidence id", "CLASS_12").
- Nothing overlaps, gets cut off or jumps around while loading.
- Every button does something visible, and there are no dead ends: you can always get back to your matches.
- Numbers make sense together (for example, a fit shown as 72% shouldn't sit next to a bar that looks half empty).

---

## 0. Fresh start

- [ ] 0.1 Stop any running backend or frontend (Ctrl+C in their windows).
- [ ] 0.2 Clear the real test database so you start clean (this deletes earlier real test students):
  `del backend\data\recourse.*`
- [ ] 0.3 Make sure Ollama is running (the llama icon in the taskbar tray, or open `http://localhost:11434`, which should say "Ollama is running").
- [ ] 0.4 Terminal 1: `cd backend` then `mvn spring-boot:run`
- [ ] 0.5 Terminal 2: `cd frontend` then `npm run dev`
- [ ] 0.6 Open `http://localhost:5173` in Edge or Chrome. Use a normal window, not a private one.

## 1. Startup and system check

- [ ] 1.1 Backend log ends with "Started RecourseApplication" and no red ERROR lines or stack traces.
- [ ] 1.2 Within about 30 seconds the log shows "Language model phi4-mini loaded in ... ms".
- [ ] 1.3 Open `http://localhost:8080/api/v1/system`. It shows `"llmReachable": true`.
- [ ] 1.4 The welcome page loads with no error message. Press F12, open the Console tab and check for red errors (keep DevTools open for the rest of the session if you can).

## 2. Welcome page and starting as a new student

- [ ] 2.1 Press "Start the questions" with everything empty. Look for: "Enter your name to start."
- [ ] 2.2 Type a name but don't pick a stage, then press start. Look for: "Pick where you are right now."
- [ ] 2.3 The three stage buttons read Class 10, Class 12 and College, and the chosen one is clearly highlighted.
- [ ] 2.4 Enter a name with spaces around it (for example `  Test One  `), pick Class 12, and press start.
- [ ] 2.5 The return code screen appears. Look for: a code shaped like `ABC-123`, with no 0, O, 1 or I in it, a clear "save this" message, and the note that it's also shown at the top of the page.
- [ ] 2.6 Press "Copy code". The button changes to "Copied". Paste somewhere (Notepad) to check it copied the code.
- [ ] 2.7 Write the code down. Press "I've saved it, start the questions".
- [ ] 2.8 The top bar shows your name (without the extra spaces) and "Code ABC-123".

## 3. The assessment

- [ ] 3.1 Each question shows "Question N. About 20 questions, around five minutes." and the progress bar fills a little with each answer.
- [ ] 3.2 Open "Why are we asking this?". The reason is a readable sentence that makes sense for the question. "Hide why we're asking" closes it.
- [ ] 3.3 After answering, "What your last answer changed" shows which traits moved. Check that the direction looks right (picking a creative option should raise something creative, not lower it).
- [ ] 3.4 Answer honestly as a made-up person (for example, someone who loves maths and dislikes routine). Keep going to the end. Note roughly how long it took.
- [ ] 3.5 It ends after about 20 questions with "That's enough for a first picture." and a "See your matches" button.
- [ ] 3.6 With a second student (you'll make one in section 9), reload the page halfway through the questions. You should still be signed in as that student and no answers should be lost. Note which page you land on and whether you can carry on with the questions.
- [ ] 3.7 Questions read naturally for an Indian student. Note any wording that feels odd (this also helps your q101 to q118 review).

## 4. Matches

- [ ] 4.1 Heading "Three directions worth a look", three matches, and the first one opened automatically.
- [ ] 4.2 The matches broadly fit the person you answered as. Note them down.
- [ ] 4.3 Each match shows a fit percentage and a range meter (a bar with a shaded band). A less certain match should have a wider band.
- [ ] 4.4 If one match shows a higher fit than the one above it, the note "Listed after a lower match so your three aren't all the same kind of work." appears. If none do, that's fine.
- [ ] 4.5 In the open match, "Why we think so" lists reasons, and "Worth thinking about" appears only if there are concerns.
- [ ] 4.6 "In plain words": "Writing an explanation…" shows briefly, then a short paragraph and the line "Written by a small model running on this computer, from the facts above only." Note how many seconds it took for the first match. (It started loading as soon as the page opened.)
- [ ] 4.7 Read the explanation. Look for: only facts from the reasons above, nothing invented (no salaries, colleges or statistics), and an invitation to disagree.
- [ ] 4.8 Open the second match. Its explanation loads then. Close it and reopen it: the explanation is shown straight away and isn't fetched again.
- [ ] 4.9 "What you told us" lists real answers you gave.
- [ ] 4.10 "How people usually get there", "Entrance exams" and "Sources" look right for India. Source links open the right pages in a new tab.
- [ ] 4.11 Unverified careers show "This entry hasn't been checked against its sources yet." (Expected for now, until you set `verified: true`.)

## 5. Pushing back on a match (the main feature)

- [ ] 5.1 On the top match press "This doesn't sound like me". The form "What did we get wrong?" lists the reasons (and concerns, if any).
- [ ] 5.2 Press "Send my objection" without ticking anything. Look for: "Pick at least one thing we got wrong."
- [ ] 5.3 Tick one reason, type a short note in "Anything else? (optional)", and send. The button shows "Recalculating…".
- [ ] 5.4 The outcome view shows "What changed" with Before and After rankings, and the disputed trait's level and confidence going down. Check that the numbers look consistent.
- [ ] 5.5 Below it, the feedback card "Before you go on: how did that feel?" appears with the privacy line "Your answers are saved without your name and may be used in a student project report."
- [ ] 5.6 Press "Answer 3 quick questions to settle this". You get a short follow-up with "You pushed back on something. These few questions check it again from a different angle." and "Check 1 of up to 3".
- [ ] 5.7 Answer the follow-ups. It ends with "Thanks. That settles it for now." and you can get back to the matches.
- [ ] 5.8 The matches have changed in a way that matches what you said. The disputed career has dropped or has a wider band.
- [ ] 5.9 Do a second dispute on a different match, but this time press "Show updated matches" instead of the follow-ups. Matches update. Look for: no feedback card this time.

## 6. Pilot feedback card

Use a fresh student for this (start one now, or come back after section 9).

- [ ] 6.1 After the first dispute, press "Send" on the feedback card without picking numbers. Look for: "Pick a number for each of the three."
- [ ] 6.2 Press "Not now". The card disappears.
- [ ] 6.3 Open "Pushback log". A small "Share feedback" link is at the top. Press it: the same card opens.
- [ ] 6.4 Pick numbers for all three (the chosen number is highlighted), add a comment with a comma and "quotes" in it, and press "Send". Look for: "Thank you. Your feedback is saved."
- [ ] 6.5 Reload the page and open the log again. The "Share feedback" link is gone.
- [ ] 6.6 Open `http://localhost:8080/api/v1/feedback/export`. A file `recourse-feedback.csv` downloads.
- [ ] 6.7 Open it in Excel. Look for: one row per student, no names, the comment intact in one cell (comma and quotes included), and readable dates.

## 7. "What we think" page

- [ ] 7.1 Heading "What we think about you". Traits are grouped under the four pillars: Interests, Work values, Work style, Abilities.
- [ ] 7.2 Traits with no evidence are greyed out and can't be opened.
- [ ] 7.3 If any trait has mixed evidence, a "Mixed signals" box appears near the top. After your disputes, the disputed trait should appear there.
- [ ] 7.4 Open a trait. You see the answers behind it, and disputed items are marked "You disputed this".
- [ ] 7.5 On an undisputed answer press "That's not me", optionally type in "Why this isn't you (optional)", and press "Dispute". You get an outcome view and the option of follow-up questions. "Done" closes it.
- [ ] 7.6 The same answer now shows "You disputed this" and can't be disputed again. Look for: no error, no duplicate.
- [ ] 7.7 Your own objection (the contest entry) can't be disputed.

## 8. Pushback log

- [ ] 8.1 Heading "Every time you pushed back". Every dispute from sections 5 and 7 is listed, newest first.
- [ ] 8.2 Each entry shows what was disputed, your note if you wrote one, and the before and after rankings.
- [ ] 8.3 Nothing was deleted: earlier entries are still there after later ones.

## 9. Return codes, signing out and coming back

- [ ] 9.1 Press "Sign out". You're back on the welcome page with no student list anywhere.
- [ ] 9.2 Under "Coming back?", press "Continue" with both fields empty. Look for: "Enter both your name and your return code."
- [ ] 9.3 Use the right name with a wrong code. Look for a clear "not found" message.
- [ ] 9.4 Use a wrong name with the right code. Look for: exactly the same message as 9.3 (so nobody can guess which part was wrong).
- [ ] 9.5 Use the name in a different case and the code in lower case with a space instead of the hyphen (for example `abc 123`). You get in, with all your earlier answers, matches and log.
- [ ] 9.6 Start a second new student. They get a different code. Sign out, continue as the first student, and check you see only the first student's data.

## 10. Demo students

- [ ] 10.1 Stop the backend. Reset the demo database: `rmdir /s /q backend\data\demo`
- [ ] 10.2 Start with the demo profile: `cd backend` then `mvn spring-boot:run -Dspring-boot.run.profiles=demo`
- [ ] 10.3 The log shows three "Demo seed: created ..." lines.
- [ ] 10.4 Continue as `Meera (demo)` / `DEM-MRA`. Her top match is Social worker. Her pushback log has one entry with before and after. "What we think" shows the disputed trait.
- [ ] 10.5 Sign out. Continue as `Kabir (demo)` / `DEM-KBR`. Top match is Graphic designer.
- [ ] 10.6 Sign out. Continue as `Aarav (demo)` / `DEM-AAR`. Top 3 is Data scientist 72, Software developer 71, Chartered accountant 66.
- [ ] 10.7 The live demo click: on Data scientist, press "This doesn't sound like me", tick the reason about organising data and records (conventional), and send. After: Research scientist 69, Data scientist 68, Mechanical engineer 62. The feedback card shows (first contest).
- [ ] 10.8 Rehearse what you'll say while this happens. Time it.
- [ ] 10.9 Reset again (10.1 and 10.2) and check Aarav is back to 10.6. Do this once more on the morning of the demo.
- [ ] 10.10 Your real students from sections 2 to 9 are still there afterwards. Restart without the demo profile and continue as one of them to check.

## 11. Without the language model

- [ ] 11.1 Quit Ollama (right-click the tray icon, Quit). Restart the backend. The log says the model was not loaded and that explanations will use the template.
- [ ] 11.2 `http://localhost:8080/api/v1/system` shows `"llmReachable": false`.
- [ ] 11.3 Open a match. The explanation appears quickly with "Written from a template using the facts above." It reads well and makes sense.
- [ ] 11.4 Disputes and everything else still work.
- [ ] 11.5 Start Ollama again and restart the backend before carrying on.

## 12. Errors and interruptions

- [ ] 12.1 With the app open, stop the backend. Click something that loads data. Look for: "Can't reach the Recourse server. Start the backend and try again." and a "Try again" button, not a blank page.
- [ ] 12.2 Start the backend again and press "Try again". It recovers.
- [ ] 12.3 Double-click buttons quickly (Send my objection, Dispute, an answer option). Look for: no duplicate entries in the log or evidence.
- [ ] 12.4 Use the browser's back button. Note what happens. (There's no router, so back may leave the page. Decide if that's acceptable.)
- [ ] 12.5 Paste a very long note (a few paragraphs) into a dispute note and the feedback comment. It's accepted or cut off cleanly, with no error.

## 13. Light theme and phone width

- [ ] 13.1 Press "Light" in the top bar. Every page is readable: check matches, the range meters, the outcome view, "Mixed signals", the feedback card and the code screen. Press "Dark" to switch back. The choice is remembered after a reload.
- [ ] 13.2 In DevTools, press Ctrl+Shift+M (device toolbar) and pick a phone around 390 px wide.
- [ ] 13.3 Go through: welcome, code screen, one question, matches (open one), a dispute form, the outcome view, the feedback card (the "1 means not at all, 5 means completely." line should show instead of the end labels), "What we think", and the log.
- [ ] 13.4 Look for: no sideways scrolling, buttons big enough to tap, nothing cut off, the top bar still usable.
- [ ] 13.5 If you can, open the app on your actual phone on the same Wi-Fi (optional; needs the laptop's IP and may need firewall permission, so skip if it's fiddly).

## 14. Keyboard and accessibility basics

- [ ] 14.1 Using only Tab, Shift+Tab, Enter and Space, start a student, answer a question, and open a match. Every control is reachable and you can always see where the focus is.
- [ ] 14.2 Zoom the browser to 200% (Ctrl and +). Pages still work and nothing important disappears.

## 15. Copy read-through

- [ ] 15.1 Read every visible sentence once on each page, out loud if possible. Mark anything that sounds stiff, salesy, slangy, unclear or wrong.
- [ ] 15.2 Check names of careers, exams and routes for spelling and Indian context.

## 16. Final checks

- [ ] 16.1 In `backend`: `mvn test` passes.
- [ ] 16.2 In `frontend`: `npm run build` passes.
- [ ] 16.3 `git status` is clean, and the latest commit is on GitHub.

---

## Issue log

Write one line per issue. Paste these to Claude Code as they are and it can work through them.

| # | Section.step | Student (demo or new) | What happened | What you expected | Severity (blocker / annoying / cosmetic) |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |
| 4 | | | | | |
| 5 | | | | | |
