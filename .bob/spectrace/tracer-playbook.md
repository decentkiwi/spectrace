# SpecTrace module tracer playbook

You trace ONE module's requirements. Your inputs (MODULE, PACKAGE, REQUIREMENT_IDS, CHANGED_IDS)
are in your task prompt. All paths are relative to the workspace root. Also read
`.bob/rules/traceability-conventions.md`. It is binding.

### Critical disambiguation: ACC-07 vs LoginRateLimiter

**REQ-ACC-07** requires a PIN lockout: after N consecutive wrong PINs on an *account*, that specific
account must be automatically frozen (no further operations). The counter lives on the account and
is checked inside `AccountService.verifyPin`.

`LoginRateLimiter` (in `com.spectrace.bank.security`) is a *HTTP-level* rate limiter on the
`/api/auth/login` endpoint. It blocks further login *requests* from the same account number for a
time window, but it does NOT freeze the account in the core banking service and does NOT satisfy
REQ-ACC-07. If you find `LoginRateLimiter` while searching for ACC-07, note its existence in
`notes` but still record `status: NOT_IMPLEMENTED` — the account-level lockout is absent.

**You may only create or edit** `bank-app/src/test/java/**/<Module>RequirementsTest.java` and
`spectrace-out/trace-<module>.json`. Never touch `bank-app/src/main/**` or existing test classes.

### Known modules and packages

| MODULE    | PACKAGE                              | Test class                    |
|-----------|--------------------------------------|-------------------------------|
| accounts  | com.spectrace.bank.accounts          | AccountsRequirementsTest      |
| transfers | com.spectrace.bank.transfers         | TransfersRequirementsTest     |
| loans     | com.spectrace.bank.loans             | LoansRequirementsTest         |
| security  | com.spectrace.bank.security          | SecurityRequirementsTest      |

### Test setup note (security layer active)

`AccountService` now requires constructor injection of `PasswordEncoder` and `AuditService`.
`TransferService` requires `AccountService`, `Clock`, and `AuditService`.
In tests, use `NoOpPasswordEncoder.getInstance()` (from `spring-security-crypto`) and
`new AuditService()` to avoid BCrypt cost. See `AccountServiceTest.java` for the pattern.

## Step 1: Load requirements
Read `spectrace-out/requirements.json` and keep only your REQUIREMENT_IDS.

## Step 2: Locate the implementation
Search `bank-app/src/main/java/<package path>/` (and `common/`) for the code enforcing each
requirement. Record `{ "file", "symbol" (Class#method), "line" }` for the key line(s).
If the behaviour does not exist, the status is `NOT_IMPLEMENTED`. Partial scaffolding doesn't count
(for example, a PIN check that exists without any lockout means ACC-07 is not implemented). Say what exists in `notes`.

## Step 3: Find existing tests
Search `bank-app/src/test/java` for `@Tag("<ID>")`. For each hit, read the test and check that it
exercises the acceptance criteria. If existing tests cover the criteria, the candidate status is `COVERED`.
If they cover only some criteria, write tests for the rest in Step 4.

## Step 4: Write the missing tests
For every implemented requirement with no test, or with uncovered criteria (or listed in CHANGED_IDS
when CHANGED_IDS is not ALL), add tests to
`bank-app/src/test/java/com/spectrace/bank/<module>/<Module>RequirementsTest.java`
(`AccountsRequirementsTest`, `TransfersRequirementsTest`, `LoansRequirementsTest`). Create the
file if it doesn't exist. If the class already exists and a requirement is in CHANGED_IDS, update that
requirement's tests so they match the new acceptance criteria.
- One `@Test` per acceptance criterion where practical, each with `@Tag("<ID>")`.
- Method names describe the criterion (e.g. `transferOfExactlyRemainingDailyLimitIsAccepted`).
- Use the exact numbers from the acceptance criteria. Follow the style of the existing tests in the package.
- Do not write tests for NOT_IMPLEMENTED requirements.

## Step 5: Run your module's tests in your own build dir
From `bank-app/`:
```
./mvnw -q test -Dspectrace.buildDir=target-<module> -Dtest='com.spectrace.bank.<module>.**' -Dmaven.test.failure.ignore=true
```
Read results from `bank-app/target-<module>/surefire-reports/`.

## Step 6: Triage failures (the most important step)
For each failing test, decide who is wrong:
- **Test is wrong** (compile error, bad setup, misread API): fix the test and re-run. You get at most 2 fix attempts per test.
- **Code violates the spec** (the assertion is the literal acceptance criterion and the code gives something
  else): status `FAILING`. Do not weaken the assertion. Find the responsible line in `src/main`
  and explain in one sentence: spec expects X, code does Y because of Z (`file:line`).

## Step 7: Write `spectrace-out/trace-<module>.json`
```json
{
  "module": "<module>",
  "requirements": [
    {
      "id": "REQ-TRF-03",
      "status": "COVERED | NEW_TEST_PASS | FAILING | NOT_IMPLEMENTED",
      "implementation": [{ "file": "bank-app/src/main/java/...", "symbol": "TransferService#transfer", "line": 48 }],
      "tests": [{ "class": "com.spectrace.bank.transfers.TransfersRequirementsTest", "method": "...", "new": true }],
      "notes": "One or two sentences: evidence, or the bug diagnosis with file:line."
    }
  ]
}
```
Include every one of your REQUIREMENT_IDS. Use fully-qualified class names. `new` is true only for tests you wrote.

## Step 8: Reply
Reply with at most 5 lines: counts per status, then one line per FAILING requirement (spec vs actual, file:line).
