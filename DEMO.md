# Demo script (~4 minutes)

## Before going on stage

1. `demo-kit/reset.sh` — clean baseline.
2. Open the `spectrace/` folder in Bob. Check that **🧭 SpecTrace**, **🔎 SpecTrace Tracer**, and **🔧 SpecTrace Fixer** appear in the mode picker, and `/trace`, `/fix`, `/release-notes` in the command menu.
3. Enable auto-approve for reads, writes to allowed files, and `./mvnw` / `python3` commands. Leave subagent-spawn approval on if you want the audience to see the fan-out.
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
| 2:00 | `matrix.html` opens | "We don't just take the agents' word for it. This script rebuilds every status from the actual test results. 12 covered, 6 newly tested, **2 real bugs**, 2 features never built. Notice the time-saved banner at the top." |
| 2:20 | Type `/fix` | "Now watch the close-the-loop moment. Bob reads the matrix, finds the two failing requirements, patches the production code…" |
| 2:35 | Fixer runs, re-traces, matrix reloads | "…re-runs the suite, and the matrix goes green. Before/after table right there in the chat." |
| 2:50 | Type `/release-notes v1.0.0` | "One more command generates the release-readiness report — a document you can hand to an auditor." |
| 3:05 | Replace the PDF with v2, `/trace docs/requirements-v2.pdf` | "Now the pricing committee changes the fee. SpecTrace re-traces only Loans — the matrix diff highlights the changed row." |
| 3:20 | Impact table | "About 11 hours of manual work down to minutes, with evidence you can hand to an auditor — and bugs caught before they ship." |

## Expected results (for checking a run)

Compare with `demo-kit/answer-key/expected-matrix.html`.

- **Covered (12):** ACC-01/02/03/04/08, TRF-01/02/05, LN-01/02/05/07
- **Newly tested (6):** ACC-05, ACC-06, TRF-04, TRF-06, LN-04, LN-06
- **Bug (2):** TRF-03 (`TransferService.java:50`, `>= 0` should be `> 0`); LN-03 (`LoanService.java:60`, `RoundingMode.DOWN` should be `HALF_UP`: 754.89 vs 754.90)
- **Not implemented (2):** ACC-07 PIN lockout, TRF-07 scheduled transfers
- **v2 re-run:** LN-06 becomes a bug (fee 246.91 expected, code gives 185.19); only the loans subagent runs; changed row is highlighted with "was: Newly tested" diff badge.
- **After `/fix`:** TRF-03 and LN-03 flip from Bug → Covered; time-saved banner updates.

## If the live run goes wrong

- **Run stalls or overruns** → `demo-kit/run-demo.sh` produces the same matrix in one command, and opens it automatically. A pre-recorded video is your ultimate backup.
- **A bug shows up as "Newly tested"** → a subagent weakened an assertion. Run `git diff` on the `*RequirementsTest` files and re-run that module in 🔎 SpecTrace Tracer mode.
- **`/fix` doesn't flip the status** → the fixer made a fix but the test still fails. Ask Bob in Agent mode to show the diff and check the assertion.

## API quick reference (for the security demo)

```bash
# Open an account
curl -s -X POST http://localhost:8080/api/accounts \
  -H 'Content-Type: application/json' \
  -d '{"ownerName":"Alice","initialDeposit":1000,"pin":"123456"}'

# Login → get JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"accountNumber":"SG00000001","pin":"123456"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# Authenticated request
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/accounts/SG00000001

# Rate limiting demo — 6th attempt gets 429
for i in $(seq 6); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/login \
    -H 'Content-Type: application/json' -d '{"accountNumber":"SG00000001","pin":"000000"}'
done
```
