# SpecTrace: from requirements PDF to verified test evidence, with IBM Bob

> **Workflow improved: testing and release readiness.** SpecTrace turns a business-requirements
> document into a verified traceability matrix. For every requirement it finds the code, finds or writes the tests,
> runs them, and reports what's covered, what's broken and what's missing. Parallel Bob subagents do
> the work, and deterministic tooling checks every claim.

## The problem

In regulated teams (banking, insurance, healthcare, government), every release needs **requirements
traceability**: proof that each numbered requirement is implemented and tested. Today a QA engineer
or developer builds this by hand:

1. Read a 10–50 page spec PDF and list every requirement.
2. For each one, search the codebase for the implementation and for tests that cover it.
3. Write the tests that are missing.
4. Keep a spreadsheet up to date, then redo it every time the spec changes.

This costs **hours per release** (we estimate ~30 min per requirement, so ~11 h for our 22-requirement spec).
It's also error-prone: requirements get skipped, "covered" tests don't actually check the acceptance
criterion, and when a spec change isn't reflected in the code, nobody notices.

## The solution

```
docs/requirements.pdf ──► 🧭 SpecTrace mode (orchestrator)
                             │ Phase 1: document understanding → requirements.json (22 reqs, verbatim ACs)
                             │ Phase 2: 3 parallel subagents, one per module
                             ▼
      ┌──────────────┬──────────────┬──────────────┐
      │ accounts     │ transfers    │ loans        │   each subagent:
      │ subagent     │ subagent     │ subagent     │   locate code → find @Tag'd tests →
      └──────┬───────┴──────┬───────┴──────┬───────┘   write missing JUnit tests → run in own
             ▼              ▼              ▼           build dir → triage failures → trace JSON
                             │ Phase 3: one clean full test run
                             │ Phase 4: tools/build_report.py recomputes every status from
                             │          Surefire XML + @Tag annotations (checks the agents' claims)
                             ▼
             spectrace-out/matrix.html · matrix.md · metrics.json · release-notes.md
```

### Bob features used

| Bob feature | How SpecTrace uses it |
|---|---|
| **Document understanding** | Extracts every `REQ-` ID, statement and acceptance criterion (including tables) verbatim from the PDF |
| **Custom modes** | `🧭 SpecTrace` orchestrator, `🔎 SpecTrace Tracer` (per-module), `🔧 SpecTrace Fixer` (fixes bugs). Each has `fileRegex` write guards. |
| **Parallel subagents** | One subagent per module, running at the same time, each in its own Maven build dir so they don't collide |
| **Agent mode + terminal** | Subagents write, compile and run tests themselves, and fix their own test mistakes (max 2 attempts) |
| **Rules** | `.bob/rules/` holds the team's traceability conventions (`@Tag("REQ-…")`, exact AC values, never edit `src/main`) |
| **Slash commands** | `/trace docs/requirements.pdf` runs the whole pipeline · `/fix` patches bugs and re-traces · `/release-notes` generates the release report |
| **Todo lists** | The orchestrator tracks the 5 phases so the audience can see progress |

### Why you can trust it

AI agents can hallucinate "all covered ✅". SpecTrace doesn't use agent claims for the final statuses:
`tools/build_report.py` recomputes each status from the actual Surefire results and the `@Tag`
annotations in source, compares against the git baseline to tell new tests from old, and **flags any
requirement where a subagent's claim doesn't match the evidence**.

The diff view in the HTML matrix highlights every requirement whose status changed since the previous
run, with a "was: X" badge — so incremental re-traces are immediately obvious.

## Commands

| Command | What it does |
|---|---|
| `/trace docs/requirements.pdf` | Full pipeline: extract → trace 3 modules in parallel → run tests → build matrix |
| `/trace docs/requirements-v2.pdf` | Incremental re-trace: only re-runs modules with changed requirements |
| `/fix` | Reads FAILING requirements from the matrix, fixes `src/main`, re-runs tests, verifies matrix goes green |
| `/fix REQ-TRF-03` | Fix a single named requirement |
| `/release-notes` | Generates `spectrace-out/release-notes.md` — a human-readable release readiness report |

## Demo target

`bank-app/` is a **Spring Boot 3 / Java 21** core-banking service (Accounts, Transfers, Loans) with a
realistic spec, `docs/requirements.pdf` (NBK-BRD-2026-014, 22 requirements). Like real codebases,
it contains:

| Planted state | Requirements | What SpecTrace should report |
|---|---|---|
| Implemented and tested | 12 | ✅ Covered |
| Implemented, never tested | 6 | 🆕 Tests written, passing |
| Implemented **wrong** | 2 (TRF-03 daily limit off-by-one, LN-03 instalment truncated instead of rounded) | 🐞 Bug found, with spec vs actual and `file:line` |
| Never implemented | 2 (ACC-07 PIN lockout, TRF-07 scheduled transfers) | ⛔ Not implemented |

`docs/requirements-v2.pdf` changes one requirement (LN-06 fee 1.5% → 2.0%) to demo **incremental
re-tracing**: only the Loans subagent runs again, and it catches that the code no longer matches the spec.

## Security layer (bank-app)

The banking API is production-ready secured:

| Feature | Implementation |
|---|---|
| Authentication | JWT (HS256, 1 h expiry) via `POST /api/auth/login` |
| PIN storage | BCrypt hash — raw PIN never stored or returned |
| Response safety | `@JsonIgnore` on `Account.pinHash` — PIN never leaks in JSON |
| Input validation | Bean Validation (`@Valid`, `@NotBlank`, `@Positive`) on all request bodies |
| Rate limiting | 5 failed logins per 15 min per account — sliding window, in-memory |
| Audit log | `AuditService` records every login, transfer, deposit, withdrawal, freeze and closure |
| Error responses | Validation errors return field-level detail; business errors return `422 Unprocessable Entity` |

All security requirements are fully traceable: `/trace` will find and verify them just like business requirements.

## Impact

| | Manual | SpecTrace |
|---|---|---|
| Build the traceability matrix for 22 requirements | ~11 h (30 min/req estimate) | _measured per run, in `metrics.json`_ |
| Missing tests | written by hand, often skipped | 15 tagged tests written automatically |
| Spec-vs-code bugs before release | found in UAT or production | 2 found, with root-cause `file:line` |
| Bug fix loop | find → fix → re-test → update spreadsheet | `/fix` → matrix auto-updates |
| Spec change | redo the spreadsheet | re-trace only changed modules |
| Audit evidence | a spreadsheet you have to trust | matrix built from actual test results + release-notes.md |

## Run it

Requirements: JDK 21+, Python 3, IBM Bob (IDE) with this folder open as the workspace.

```bash
cd bank-app && ./mvnw test        # baseline: 14 tests, all green
```

In Bob:
```
/trace docs/requirements.pdf      # full pipeline
/fix                              # fix the 2 bugs SpecTrace found
/trace docs/requirements.pdf      # re-run: matrix goes all-green
/release-notes v1.0.0             # generate the release report
```

Then open `spectrace-out/matrix.html` and `spectrace-out/release-notes.md`.

To try the tooling without Bob: `demo-kit/run-demo.sh`.
To reset to the pre-demo baseline: `demo-kit/reset.sh`.
