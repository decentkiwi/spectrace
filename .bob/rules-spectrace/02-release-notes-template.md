# Release notes template

Write `spectrace-out/release-notes.md` using the structure below.
Fill every `<placeholder>` from `spectrace-out/metrics.json`, `spectrace-out/requirements.json`,
and `spectrace-out/matrix-curr.json`. Use today's UTC date.

---

```markdown
# Release Readiness Report — <document_id> <version_tag>

**Generated:** <generated_at UTC>
**Spec document:** <document> v<document_version>
**Codebase:** bank-app

## Summary

| Metric | Value |
|---|---|
| Requirements traced | <requirements_total> |
| ✅ Covered | <COVERED> |
| 🆕 Newly tested | <NEW_TEST_PASS> |
| 🐞 Bugs found | <FAILING> |
| ⛔ Not implemented | <NOT_IMPLEMENTED + UNTESTED> |
| Tests added this run | <tests_added> |
| SpecTrace time | <spectrace_minutes> min |
| Manual estimate | ~<manual_estimate_minutes ÷ 60> h |
| **Time saved** | **~<time_saved_minutes ÷ 60> h** |

## Release Recommendation

<!-- Choose one of the three blocks below and delete the others -->

<!-- If FAILING == 0 and NOT_IMPLEMENTED == 0: -->
✅ **READY TO RELEASE** — All <requirements_total> requirements are implemented and verified by automated tests.

<!-- If FAILING > 0: -->
🚫 **NOT READY** — <FAILING> bug(s) found. The following requirements fail their acceptance criteria:
<list each FAILING req: id, title, notes>
These must be fixed before the release can be signed off.

<!-- If NOT_IMPLEMENTED > 0 and FAILING == 0: -->
⚠️ **CONDITIONAL** — All implemented requirements pass. However <NOT_IMPLEMENTED> requirement(s) have no
implementation: <list ids>. Confirm with the product owner whether these are in-scope for this release.

## Bugs Found

<!-- One section per FAILING requirement -->
### <REQ-ID> — <title>

- **Spec:** <acceptance criterion that fails>
- **Code:** <what the code actually does, from notes>
- **Location:** `<file:line>`

## Not Implemented

<!-- One line per NOT_IMPLEMENTED / UNTESTED requirement -->
- **<REQ-ID>** <title> — <notes>

## Newly Tested (Tests Written This Run)

<!-- One line per NEW_TEST_PASS requirement -->
- **<REQ-ID>** <title> — <number of new tests> test(s) added

## Evidence

- Traceability matrix: `spectrace-out/matrix.html`
- Raw metrics: `spectrace-out/metrics.json`
- Test results: `bank-app/target/surefire-reports/`
- Statuses are computed from actual Surefire XML results, not from AI agent claims.
```
