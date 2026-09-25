---
description: Generate a release-readiness report from the latest SpecTrace matrix
argument-hint: [version-tag] (optional, e.g. v1.2.0)
---
Generate a release-readiness summary from the current SpecTrace results.

Switch to **🧭 SpecTrace** mode (slug `spectrace`) if not already in it, then:

1. Read `spectrace-out/metrics.json` and `spectrace-out/matrix-curr.json`.
2. Read `spectrace-out/requirements.json` for document metadata.
3. If `$1` is provided, use it as the release version tag; otherwise use the document version.
4. Write `spectrace-out/release-notes.md` following the template in
   `.bob/rules-spectrace/02-release-notes-template.md`.
5. Report the path and print the one-line headline to the user.
