#!/usr/bin/env bash
# Put the repo back to the pre-demo baseline: no Bob-written tests, no SpecTrace output.
set -euo pipefail
cd "$(dirname "$0")/.."
rm -f bank-app/src/test/java/com/spectrace/bank/*/*RequirementsTest.java
find spectrace-out -type f ! -name .gitkeep -delete
rm -rf bank-app/target bank-app/target-*
git checkout -- bank-app/src
echo "Reset to baseline. $(git -C . log --oneline -1)"
