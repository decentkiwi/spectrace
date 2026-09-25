# Demo script (~3 minutes)

## Before going on stage
1. `demo-kit/reset.sh`: clean baseline.
2. Open the `spectrace/` folder in Bob. Check that **🧭 SpecTrace** appears in the mode picker and `/trace` in the command menu.
3. Enable auto-approve for reads, writes to allowed files, and `./mvnw` / `python3` commands, so the run isn't held up by approval prompts. Leave subagent-spawn approval on if you want the audience to see the fan-out.
4. Warm the Maven cache: `cd bank-app && ./mvnw -q test`.
5. Have `docs/requirements.pdf` open in a tab.

## Script
| Time | Show | Say |
|---|---|---|
| 0:00 | The PDF spec | "A bank has 22 requirements for this release. The regulator wants proof that every one is implemented and tested. Today that's a QA engineer and a spreadsheet for most of a day." |
| 0:20 | `bank-app` tests green | "The tests are all green. So is the release safe? Green only tells you the existing tests pass. It doesn't tell you the spec is covered." |
| 0:35 | Type `/trace docs/requirements.pdf` | "One command." |
| 0:45 | Bob extracts the requirements; todo list appears | "Bob reads the PDF, including tables and acceptance criteria, and finds all 22 requirements." |
| 1:00 | **Parallel subagents panel**: 3 running | "It sends out one subagent per module, in parallel. Each one finds the code, checks the existing tests, writes the missing ones, and runs them in its own build dir." |
| 1:45 | Subagent summaries | "Transfers found something: the spec says the daily limit is inclusive, but the code uses `>=`." |
| 2:00 | Report script runs, then `matrix.html` | "We don't just take the agents' word for it. This script rebuilds every status from the actual test results. 12 covered, 6 newly tested, **2 real bugs**, 2 features never built." |
| 2:30 | Replace the PDF with v2, `/trace docs/requirements-v2.pdf` | "Now the pricing committee changes the fee. SpecTrace re-traces only Loans, and flags that the code still charges 1.5%." |
| 2:50 | Impact table | "About 11 hours of manual work down to minutes, with evidence you can hand to an auditor." |

## Expected results (for checking a run)
Compare with `demo-kit/answer-key/expected-matrix.html`.
- **Covered (12):** ACC-01/02/03/04/08, TRF-01/02/05, LN-01/02/05/07
- **Newly tested (6):** ACC-05, ACC-06, TRF-04, TRF-06, LN-04, LN-06
- **Bug (2):** TRF-03 (`TransferService.java:47`, `>= 0` should be `> 0`); LN-03 (`LoanService.java:60`, `RoundingMode.DOWN` should be `HALF_UP`: 754.89 vs 754.90)
- **Not implemented (2):** ACC-07 PIN lockout, TRF-07 scheduled transfers
- **v2 re-run:** LN-06 becomes a bug (fee 246.91 expected, code gives 185.19); only the loans subagent runs.

## If the live run goes wrong
- A bug shows up as "Newly tested" → a subagent probably weakened an assertion (for example asserting 754.89). The report can't catch this, since it only checks tags and results, so check `git diff` on the `*RequirementsTest` files and re-run that module in 🔎 SpecTrace Tracer mode. Worth rehearsing a few times beforehand.
- The run stalls or overruns → `demo-kit/replay-answer-key.sh` produces the same matrix, and a pre-recorded video of a real run is your backup.

## Optional finale: close the loop
Ask Bob (Code mode): "Fix the two bugs SpecTrace found." Then run `/trace` again, and TRF-03 and LN-03 turn from red to green.
