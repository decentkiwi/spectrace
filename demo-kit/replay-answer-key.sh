#!/usr/bin/env bash
# Fallback / rehearsal: drop the reference output of a SpecTrace run into place and build the report,
# exactly as Bob's Phases 3-4 would. Use it to check the tooling, or if a live run fails on stage.
# If the answer key came from a real Bob run (demo-kit/record-run.sh), its measured timing is shown;
# otherwise the report says timing was not measured instead of showing a fake number.
set -euo pipefail
cd "$(dirname "$0")/.."
K=demo-kit/answer-key
T=bank-app/src/test/java/com/spectrace/bank
cp "$K/tests/AccountsRequirementsTest.java" "$T/accounts/"
cp "$K/tests/TransfersRequirementsTest.java" "$T/transfers/"
cp "$K/tests/LoansRequirementsTest.java" "$T/loans/"
cp "$K"/requirements.json "$K"/trace-*.json spectrace-out/
if [ -f "$K/run.json" ]; then
  cp "$K/run.json" spectrace-out/run.json
else
  echo "{\"mode\": \"replay\", \"spec\": \"docs/requirements.pdf\", \"manual_minutes_per_requirement\": 30}" > spectrace-out/run.json
fi
(cd bank-app && ./mvnw -q test -Dmaven.test.failure.ignore=true >/dev/null 2>&1 || true)
python3 tools/build_report.py
echo "Open spectrace-out/matrix.html"
