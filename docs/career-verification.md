# Career dataset verification

Checked: 17 November 2026. Dataset: `backend/src/main/resources/careers.json` (25 careers).

## Method

1. **Occupation match.** Each career was matched to its O*NET-SOC occupation and the occupation title was confirmed on O*NET OnLine.
2. **Interest code.** The Holland (RIASEC) interest code was taken from the occupation's O*NET OnLine summary page ("Interest code"). O*NET lists between one and three letters, ordered by strength. The dataset now uses O*NET's code exactly, instead of the codes first assigned by hand.
3. **Indian routes and entrance exams.** Each route was checked against the official body listed in the career's sources. Routes most likely to have changed recently were re-checked against current (2026) admission notices.
4. **Other weights.** The work-value, work-style and ability weights are the developer's judgement. They are informed by each occupation's O*NET description, but they are not O*NET data. They are a stated limitation.

## Interest codes: original vs. O*NET

| Career | O*NET occupation | Code | First assigned | O*NET | Changed |
|---|---|---|---|---|---|
| Software developer | Software Developers | 15-1252.00 | ICR | IC | yes |
| Data scientist | Data Scientists | 15-2051.00 | ICE | IC | yes |
| Civil engineer | Civil Engineers | 17-2051.00 | RIC | RIC | no |
| Mechanical engineer | Mechanical Engineers | 17-2141.00 | RIC | RIC | no |
| Doctor (MBBS) | Family Medicine Physicians | 29-1215.00 | ISR | SI ¹ | yes |
| Nurse | Registered Nurses | 29-1141.00 | SIC | SCI | yes |
| Pharmacist | Pharmacists | 29-1051.00 | ICS | ISC | yes |
| Psychologist | Clinical and Counseling Psychologists | 19-3033.00 | ISA | SI | yes |
| School teacher | Secondary School Teachers | 25-2031.00 | SAE | S | yes |
| Lawyer | Lawyers | 23-1011.00 | ESI | ECI | yes |
| Chartered accountant | Accountants and Auditors | 13-2011.00 | CEI | CEI | no |
| Financial analyst | Financial and Investment Analysts | 13-2051.00 | CIE | CEI | yes |
| Economist / policy analyst | Economists | 19-3011.00 | ICE | ICE | no |
| Marketing manager | Marketing Managers | 11-2021.00 | EAC | EC | yes |
| Founder / entrepreneur | Chief Executives (closest equivalent) ² | 11-1011.00 | ECA | EC | yes |
| Management consultant | Management Analysts | 13-1111.00 | EIC | CIE | yes |
| Graphic designer | Graphic Designers | 27-1024.00 | AER | AC | yes |
| UX / product designer | Web and Digital Interface Designers | 15-1255.00 | AIS | IAC | yes |
| Architect | Architects, Except Landscape and Naval | 17-1011.00 | AIR | RCA | yes |
| Journalist | News Analysts, Reporters, and Journalists | 27-3023.00 | AEI | AIE | yes |
| Research scientist | Biochemists and Biophysicists | 19-1021.00 | IRA | IR | yes |
| Chef | Chefs and Head Cooks | 35-1011.00 | RAE | ERC | yes |
| Electrician | Electricians | 47-2111.00 | RCI | RC | yes |
| Social worker | Child, Family, and School Social Workers | 21-1021.00 | SEA | S | yes |
| Civil servant (IAS / IPS etc.) | none ³ | – | ESC | – | – |

20 of the 24 codes that could be checked differed from the first hand-assigned version.

¹ The summary page could not be read during the check. The code comes from the details page's interest scores (Social 87, Investigative 85, Realistic 46). Confirm it on the summary page.
² O*NET has no "entrepreneur" occupation. Chief Executives is used as the closest equivalent; the earlier mapping (General and Operations Managers) has the same code, EC.
³ O*NET is a US taxonomy with no equivalent of the Indian civil services. The code ESC remains the developer's judgement and is flagged as such.

## Route and exam corrections

| Career | Before | After | Source |
|---|---|---|---|
| Psychologist | RCI-recognised M.Phil for clinical practice | RCI-regulated MA Clinical Psychology (2 years), which replaces the M.Phil being phased out | RCI MA Clinical Psychology guidelines (2025-26); UGC notice discontinuing M.Phil |
| Social worker | "TISS entrance (for MSW)" | CUET-PG (TISS uses CUET-PG for MA Social Work; TISSNET was discontinued) | TISS admissions, selection procedure 2026 |
| Journalist | "IIMC entrance (PG diploma)" | CUET-PG (IIMC uses CUET-PG for its main PG Diplomas) | IIMC admission notice 2026-27 |
| Doctor | NEET-PG for MD/MS | unchanged: NEET-PG continues; NExT is deferred | NMC / NBEMS updates, 2026 |

## Still to do by the developer

- [ ] Spot-check each career's routes and exams against its source links
- [ ] Confirm the doctor's interest code on the O*NET summary page
- [ ] Then set `"verified": true` on each career checked
