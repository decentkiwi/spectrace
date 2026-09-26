# SpecTrace orchestrator workflow

The workspace root contains `docs/` (requirement documents), `spectrace.yaml` (project config),
the application codebase, `tools/build_report.py` and `spectrace-out/` (your output).
Create a todo list with the 5 phases below and tick them off as you go.

## Phase 0: Start the clock

Run `date -u +%Y-%m-%dT%H:%M:%SZ` and write `spectrace-out/run.json`:
```json
{ "started_at": "<timestamp>", "spec": "<path to the document>", "manual_minutes_per_requirement": 30 }
```

Then pin the test baseline, so tests you write can never be mistaken for pre-existing ones:
`git rev-parse -q --verify refs/tags/spectrace-baseline || git tag spectrace-baseline`
(Only create the tag if it is missing. Never move or delete it.)

**Read `spectrace.yaml`** (if it exists at the workspace root). Note:
- `app_dir` — the directory containing the buildable project
- `stack` — determines which language supplement subagents must read
- `modules` — the list of modules, their `pkg`, `src_glob`, and `req_prefix`
- `test_cmd` — the full-suite test command
- `manual_minutes_per_requirement` — use this value in `run.json` if present

If `spectrace.yaml` is absent, assume `java-maven` defaults (`app_dir: bank-app`, etc.).

## Phase 1: Extract requirements (document understanding)

Read the requirements document the user named (default `docs/requirements.pdf`).
If `spectrace-out/requirements.json` already exists, first copy it to `spectrace-out/requirements.prev.json`.
Write `spectrace-out/requirements.json`:
```json
{
  "document": "NBK-BRD-2026-014", "version": "1.0",
  "requirements": [
    { "id": "REQ-ACC-01", "module": "accounts", "title": "Account opening",
      "text": "<requirement statement, verbatim>",
      "acceptance_criteria": ["<criterion verbatim, keep every number>", "..."] }
  ]
}
```
Rules: capture **every** `REQ-` ID in the document and nothing else. Copy statements and
acceptance criteria verbatim, since the numbers in them become test assertions. Tables (such as
interest tiers) become acceptance criteria, one row each.
Map each requirement to a module using the `req_prefix` in `spectrace.yaml modules[]`
(e.g. `REQ-ACC-*` → `accounts`). If no config, use `accounts | transfers | loans | security`.
Tell the user how many requirements you found per module.

**Incremental mode:** if `requirements.prev.json` exists, compare each requirement's `text` and
`acceptance_criteria`. List the added, changed and removed IDs. Only modules containing added or
changed IDs are re-traced in Phase 2; untouched modules keep their existing `trace-<module>.json`.
If nothing changed, skip to Phase 3.

## Phase 2: Trace modules in parallel (subagents)

Spawn **one `general` subagent per module that needs tracing, all at once in parallel**. Do not
trace modules yourself or one after another. Subagents do not see this conversation, so give each
one this self-contained prompt with the placeholders filled in:

```
You are a SpecTrace module tracer. Read .bob/spectrace/tracer-playbook.md and follow it exactly.
MODULE=<module name>
PACKAGE=<pkg from spectrace.yaml modules[], e.g. com.spectrace.bank.accounts>
REQUIREMENT_IDS=<comma-separated IDs for this module>
CHANGED_IDS=<IDs changed since last run, or ALL>
Requirements are in spectrace-out/requirements.json.
spectrace.yaml is at the workspace root — read it in Step 0 of the playbook.
Write spectrace-out/trace-<module>.json and reply with a 5-line summary:
counts per status, and one line per bug found.
```

When they return, relay each subagent's summary to the user in one line.

## Phase 3: Verify with one clean full run

From `{app_dir}` (from `spectrace.yaml`, default `bank-app`):
```
{test_cmd from spectrace.yaml, e.g.: ./mvnw -q test -Dmaven.test.failure.ignore=true}
```
(failures are expected when bugs exist; don't try to fix them).

## Phase 4: Build the evidence report

From the workspace root: `python3 tools/build_report.py`
This recomputes every status from test results and tag annotations. It reads `spectrace.yaml`
automatically. If it reports "agent disagreements" or "bad file:line references", tell the user
which IDs were affected and trust the script, not the subagent.

## Phase 5: Brief the user

Reply with:
1. A one-line headline: `N requirements · X covered · Y newly tested · Z bugs · W not implemented · T tests added · M min`.
2. **Bugs found**: for each FAILING requirement, the spec value vs the actual value, and the suspect code as `file:line`.
3. **Gaps**: each NOT_IMPLEMENTED requirement, with where it would be implemented.
4. The paths `spectrace-out/matrix.html` and `spectrace-out/matrix.md`.
Do not fix bugs. Offer to fix them as a separate follow-up.
