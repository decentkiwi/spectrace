# SpecTrace module tracer playbook

You trace ONE module's requirements. Your inputs (MODULE, PACKAGE, REQUIREMENT_IDS, CHANGED_IDS)
are in your task prompt. All paths are relative to the workspace root. Also read
`.bob/rules/traceability-conventions.md` — it is binding.

---

## Step 0: Load project config

Read `spectrace.yaml` at the workspace root. The fields you need are:

| Field | What it controls | Fallback if absent |
|---|---|---|
| `stack` | Language/framework | `java-maven` |
| `app_dir` | Directory containing the buildable project | `bank-app` |
| `test_cmd` | Full test suite command (run from `app_dir`) | see stack default |
| `module_test_cmd` | Per-module isolated test command | see stack default |
| `tag_pattern` | Regex to find REQ-* IDs in test source | `@Tag\("(REQ-[A-Z]+-\d+)"\)` |
| `test_source_glob` | Glob for test source files | `src/test/java/**/*.java` |
| `modules[name=MODULE]` | `pkg`, `src_glob`, `test_class` for your module | — |

**Then load the language supplement** for your stack:

| `stack` | Language supplement |
|---|---|
| `java-maven` or `java-gradle` | (no supplement — this playbook covers Java fully) |
| `python-pytest` | `.bob/spectrace/lang/python-pytest.md` |
| `node-jest` | `.bob/spectrace/lang/node-jest.md` |
| `go-testing` | `.bob/spectrace/lang/go-testing.md` |

The language supplement overrides Steps 4, 5 and 6 of this playbook. Read it now before continuing.

---

### Critical disambiguation: ACC-07 vs LoginRateLimiter (bank-app only)

**REQ-ACC-07** requires a PIN lockout at the *account service* level: after N wrong PINs, the
account is frozen. `LoginRateLimiter` (in `com.spectrace.bank.security`) is a *HTTP-level*
rate limiter — it does **not** satisfy REQ-ACC-07. Record `status: NOT_IMPLEMENTED` for ACC-07.

---

**You may only create or edit:**
- The `*RequirementsTest` file for your module (path from `spectrace.yaml modules[].test_class`)
- `spectrace-out/trace-<module>.json`

Never touch production source or existing test classes.

---

## Step 1: Load requirements

Read `spectrace-out/requirements.json`. Keep only your REQUIREMENT_IDS.

## Step 2: Locate the implementation

Search `{app_dir}/{modules[this].src_glob}` (and the common/shared package if one exists) for the
code enforcing each requirement. Record `{ "file", "symbol" (Class#method or module.function),
"line" }` for the key line(s). If the behaviour does not exist, the status is `NOT_IMPLEMENTED`.
Partial scaffolding doesn't count — say what exists in `notes`.

## Step 3: Find existing tests

Search `{app_dir}` using the pattern `{tag_pattern}` for `{REQ_ID}`. For each hit, read the test
and check it exercises the acceptance criteria. If existing tests fully cover the criteria,
the candidate status is `COVERED`.

## Step 4: Write the missing tests

For every implemented requirement with no test, or with uncovered criteria, or listed in
CHANGED_IDS (when CHANGED_IDS ≠ ALL), add tests to the file named `test_class` in the module
config. Create it if it doesn't exist.

**Java (java-maven / java-gradle):**
```java
@Test
@Tag("REQ-MOD-01")          // tag_pattern for Java
void descriptiveMethodName() {
    // assert the exact acceptance criterion value
    assertThat(result).isEqualByComparingTo("754.90");
}
```
Place in: `{app_dir}/src/test/java/{pkg.replace('.','/')}/{test_class}.java`

**For other stacks:** follow the language supplement loaded in Step 0.

Rules for all languages:
- One test per acceptance criterion where practical.
- Method/function names describe the criterion.
- Use the **exact numbers** from the acceptance criteria — never round or adjust.
- Do not write tests for NOT_IMPLEMENTED requirements.
- If CHANGED_IDS is not ALL, update only the tests for those IDs.

## Step 5: Run your module's tests

Substitute `{module}` and `{pkg}` from `spectrace.yaml modules[]` into `module_test_cmd`:

**Java (default):**
```bash
cd {app_dir} && ./mvnw -q test \
  -Dspectrace.buildDir=target-{module} \
  -Dtest='{pkg}.**' \
  -Dmaven.test.failure.ignore=true
```

For other stacks, the command comes from `module_test_cmd` in `spectrace.yaml` with `{module}`
and `{pkg}` substituted. Follow the language supplement if present.

Result files are at: `{app_dir}/{result_dir}` (substituting `{build_dir}` → `target-{module}`).

## Step 6: Triage failures

For each failing test, decide who is wrong:

- **Test is wrong** (compile/import error, wrong setup, misread API): fix the test and re-run.
  You get at most **2 fix attempts per test**.
- **Code violates the spec** (the assertion is the literal acceptance criterion value and the
  code returns something else): status `FAILING`. Do **not** weaken the assertion.
  Find the responsible line in production source and explain in one sentence:
  > spec expects X, code does Y because of Z (`file:line`)

## Step 7: Write `spectrace-out/trace-<module>.json`

```json
{
  "module": "<module>",
  "requirements": [
    {
      "id": "REQ-TRF-03",
      "status": "COVERED | NEW_TEST_PASS | FAILING | NOT_IMPLEMENTED",
      "implementation": [
        { "file": "<app_dir>/src/main/...", "symbol": "ClassName#methodName", "line": 50 }
      ],
      "tests": [
        { "class": "com.example.TransfersRequirementsTest", "method": "methodName", "new": true }
      ],
      "notes": "One or two sentences: evidence summary, or the bug diagnosis with file:line."
    }
  ]
}
```

Include every one of your REQUIREMENT_IDS. Use fully-qualified class/module names.
`new` is `true` only for tests you wrote this run.

## Step 8: Reply

Reply with at most 5 lines:
1. Counts per status.
2. One line per FAILING requirement: spec vs actual, file:line.
