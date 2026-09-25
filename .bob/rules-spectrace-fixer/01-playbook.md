# SpecTrace Fixer playbook

You fix bugs that SpecTrace identified as FAILING requirements. All paths are relative to the workspace root.
Read `.bob/rules/traceability-conventions.md` — it is binding.

**You may only edit** `bank-app/src/main/java/**/*.java` and `spectrace-out/`.
**Never** edit test files to weaken assertions. **Never** change an expected value to make a test pass.

## Step 1: Load the failing requirements

Read `spectrace-out/matrix-curr.json`. Collect all entries where `status == "FAILING"`.
If the `/fix` command was invoked with specific REQ IDs, restrict to those IDs only.

For each failing requirement, read its `notes` field in `spectrace-out/trace-<module>.json` —
this contains the bug diagnosis written by the tracer subagent (spec value vs actual value, file:line).

## Step 2: Show the plan

Before touching any file, list each bug as:
```
REQ-XXX-NN  <title>
  Spec:   <what the acceptance criterion says>
  Code:   <what the code does>
  Fix:    <one sentence describing the minimal change>
  File:   <file:line>
```
Confirm with the user if more than 3 bugs are queued, otherwise proceed immediately.

## Step 3: Apply fixes

For each failing requirement:
1. Read the responsible file to confirm the bug is still present.
2. Apply the **minimal** correct change. Do not refactor surrounding code.
3. Add a one-line comment above the fix: `// SpecTrace fix: REQ-XXX-NN — <what changed>`

## Step 4: Run the full test suite

From `bank-app/`: `./mvnw -q test -Dmaven.test.failure.ignore=true`

For any test that still fails after your fix:
- If the failure is in a `*RequirementsTest` file and the assertion matches the spec AC exactly, the fix is incomplete — go back to Step 3.
- If the failure is in an unrelated test, note it but do not touch it.

## Step 5: Rebuild the evidence report

From the workspace root: `python3 tools/build_report.py`

Verify that every requirement you fixed now shows `COVERED` or `NEW_TEST_PASS` in the output.
If any fixed requirement still shows `FAILING`, diagnose and fix again (max 2 attempts total per requirement).

## Step 6: Report

Reply with a table:

| Requirement | Before | After | Fix summary |
|---|---|---|---|
| REQ-TRF-03 | 🐞 Bug | ✅ Covered | Changed `>= 0` to `> 0` in `TransferService.java:47` |

Then print the updated headline from `spectrace-out/metrics.json` and the path to `spectrace-out/matrix.html`.
