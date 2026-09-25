#!/usr/bin/env bash
# Fallback / rehearsal: drop the reference output of a SpecTrace run into place and build the report,
# exactly as Bob's Phases 3-4 would. Use it to check the tooling, or if a live run fails on stage.
set -euo pipefail
cd "$(dirname "$0")/.."
K=demo-kit/answer-key
T=bank-app/src/test/java/com/spectrace/bank
cp "$K/tests/AccountsRequirementsTest.java" "$T/accounts/"
cp "$K/tests/TransfersRequirementsTest.java" "$T/transfers/"
cp "$K/tests/LoansRequirementsTest.java" "$T/loans/"
cp "$K"/requirements.json "$K"/trace-*.json spectrace-out/
[ -f spectrace-out/run.json ] || echo "{\"started_at\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\", \"spec\": \"docs/requirements.pdf\", \"manual_minutes_per_requirement\": 30}" > spectrace-out/run.json
(cd bank-app && ./mvnw -q test -Dmaven.test.failure.ignore=true >/dev/null 2>&1 || true)
python3 tools/build_report.py
echo "Open spectrace-out/matrix.html"
