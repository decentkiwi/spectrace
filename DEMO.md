# Demo script (~4 minutes)

## Before going on stage

1. `demo-kit/reset.sh` — clean baseline.
2. Open the `spectrace/` folder in Bob. Check that **🧭 SpecTrace**, **🔎 SpecTrace Tracer**, and **🔧 SpecTrace Fixer** appear in the mode picker, and `/trace`, `/fix`, `/release-notes` in the command menu.
3. Enable auto-approve for reads, writes to allowed files, and `./mvnw` / `python3` commands. Leave subagent-spawn approval on if you want the audience to see the fan-out.
4. Warm the Maven cache: `cd bank-app && ./mvnw -q test`.
5. Have `docs/requirements.pdf` open in a tab.
6. After your first clean rehearsal run in Bob, run `demo-kit/record-run.sh`. The fallback then replays
   **real Bob output with its measured time**. Until you do, the fallback matrix says "timing not measured"
   (it replays hand-written reference files and won't invent a number).

## Script

| Time | Show | Say |
|---|---|---|
| 0:00 | The PDF spec | "A bank has 27 requirements for this release. The regulator wants proof that every one is implemented and tested. Today that's a QA engineer and a spreadsheet for most of a day." |
| 0:20 | `bank-app` tests green | "The tests are all green. So is the release safe? Green only tells you the existing tests pass. It doesn't tell you the spec is covered." |
| 0:35 | Type `/trace docs/requirements.pdf` | "One command." |
| 0:45 | Bob extracts the requirements; todo list appears | "Bob reads the PDF, including tables and acceptance criteria, and finds all 27 requirements." |
| 1:00 | **Parallel subagents panel**: 4 running | "It sends out one subagent per module, in parallel. Each one finds the code, checks the existing tests, writes the missing ones, and runs them in its own build dir." |
| 1:45 | Subagent summaries | "Transfers found something: the spec says the daily limit is inclusive, but the code uses `>=`. And look at security: with Alice's login, it just moved money out of Bob's account." |
| 2:00 | `matrix.html` opens | "We don't just take the agents' word for it. This script rebuilds every status from the actual test results. 11 covered, 11 newly tested, **3 real bugs**, 2 features never built. And one 'covered' requirement wasn't fully covered: Bob noticed no test checked that a zero opening deposit is allowed, and wrote one. We planted two of those bugs. We didn't plant the security one: it came in with our own login code, and SpecTrace caught it. Notice the time-saved banner at the top." |
| 2:20 | Type `/fix` | "Now watch the close-the-loop moment. Bob reads the matrix, finds the three failing requirements, patches the production code, including an ownership check on every account endpoint…" |
| 2:35 | Fixer runs, re-traces, matrix reloads | "…re-runs the suite, and the matrix goes green. Before/after table right there in the chat." |
| 2:50 | Type `/release-notes v1.0.0` | "One more command generates the release-readiness report — a document you can hand to an auditor." |
| 3:05 | Replace the PDF with v2, `/trace docs/requirements-v2.pdf` | "Now the pricing committee changes the fee. SpecTrace re-traces only Loans — the matrix diff highlights the changed row." |
| 3:20 | Impact table | "About 13 and a half hours of manual work down to minutes, with evidence you can hand to an auditor — and bugs caught before they ship." |

## Expected results (for checking a run)

Compare with `demo-kit/answer-key/expected-matrix.html`.

Recorded live Bob run (26.2 min). Bob writes its own tests, so a new run can differ slightly; what matters is 3 bugs, 2 gaps, 0 disagreements, 0 bad references.

- **Covered (11):** ACC-02/03/04/08, TRF-01/02/05, LN-01/02/05/07
- **Newly tested (11):** ACC-01 (existing tests never checked the SGD 0.00 opening deposit), ACC-05, ACC-06, TRF-04, TRF-06, LN-04, LN-06, SEC-01, SEC-02, SEC-03, SEC-04
- **Bug (3):** TRF-03 (`TransferService.java:50`, `>= 0` should be `> 0`); LN-03 (`LoanService.java:60`, `RoundingMode.DOWN` should be `HALF_UP`: 754.89 vs 754.90); SEC-05 (no ownership check: with Alice's token, `GET /api/accounts/{bob}` and a transfer from Bob's account both return 200 instead of 403; see `AccountController.java:44`, `TransferController.java:35`)
- **Tests added:** 39
- **Not implemented (2):** ACC-07 PIN lockout, TRF-07 scheduled transfers
- **v2 re-run:** LN-06 becomes a bug (fee 246.91 expected, code gives 185.19); only the loans subagent runs; changed row is highlighted with "was: Newly tested" diff badge.
- **After `/fix`:** TRF-03, LN-03 and SEC-05 flip from Bug → Newly tested (their tests are new this run), each with a "was: Bug found" badge; time-saved banner updates.

## If the live run goes wrong

- **Run stalls or overruns** → `demo-kit/run-demo.sh` produces the same matrix in one command, and opens it automatically. Say it's a replay of an earlier run. A pre-recorded video is your ultimate backup.
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

### Show the SEC-05 hole live (before `/fix`)

```bash
# Two customers; account numbers are random, so capture them
ALICE=$(curl -s -X POST http://localhost:8080/api/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName":"Alice","initialDeposit":100,"pin":"111111"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['accountNumber'])")
BOB=$(curl -s -X POST http://localhost:8080/api/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName":"Bob","initialDeposit":1000,"pin":"222222"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['accountNumber'])")

# Alice logs in with HER OWN PIN...
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"accountNumber\":\"$ALICE\",\"pin\":\"111111\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# ...and moves 500 out of BOB's account. Before /fix: 200 OK. After /fix: 403.
curl -s -w "\nHTTP %{http_code}\n" -X POST http://localhost:8080/api/transfers \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"fromAccount\":\"$BOB\",\"toAccount\":\"$ALICE\",\"amount\":500}"
```
