---
name: trace
description: >-
  Trace a requirements document to code and tests, and produce a traceability
  matrix
metadata:
  user-invocable: true
  disable-model-invocation: true
  argument-hint: <path-to-requirements-document>
---

Run the SpecTrace workflow on the requirements document at `$1` (use `docs/requirements.pdf` if no path was given).

If you are not already in the 🧭 SpecTrace mode (slug `spectrace`), switch to it first, then follow
`.bob/rules-spectrace/01-workflow.md` from Phase 0 to Phase 5. Trace the modules with parallel subagents.
