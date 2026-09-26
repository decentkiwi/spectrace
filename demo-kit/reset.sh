#!/usr/bin/env bash
# Put the repo back to the pre-demo baseline: no Bob-written tests, no SpecTrace output, no /fix patches.
# Uncommitted edits under bank-app/src are stashed, never discarded (recover with `git stash list`).
set -euo pipefail
cd "$(dirname "$0")/.."
rm -f bank-app/src/test/java/com/spectrace/bank/*/*RequirementsTest.java
find spectrace-out -type f ! -name .gitkeep -delete
rm -rf bank-app/target bank-app/target-*
if ! git diff --quiet HEAD -- bank-app/src; then
  git stash push -q -m "spectrace reset $(date +%Y-%m-%dT%H:%M:%S)" -- bank-app/src
  echo "Stashed uncommitted changes in bank-app/src (git stash list / git stash pop to recover)."
fi
if ! git rev-parse -q --verify refs/tags/spectrace-baseline >/dev/null; then
  git tag spectrace-baseline
  echo "Tagged HEAD as spectrace-baseline (the test suite Bob's new tests are compared against)."
fi
echo "Reset to baseline. $(git log --oneline -1)"
