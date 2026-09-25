---
description: Fix the bugs SpecTrace found — reads the matrix, patches src/main, re-traces, verifies the matrix goes green
argument-hint: [REQ-ID ...] (optional — fixes all FAILING requirements if omitted)
---
Fix the bugs identified in the most recent SpecTrace run.

Switch to **spectrace-fixer** mode (slug `spectrace-fixer`) if not already in it, then follow
`.bob/rules-spectrace-fixer/01-playbook.md`.

If one or more REQ IDs are given as `$1`, fix only those. Otherwise fix all requirements
whose status is `FAILING` in `spectrace-out/matrix-curr.json` (or `spectrace-out/metrics.json`).
