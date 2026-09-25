#!/usr/bin/env bash
# demo-kit/run-demo.sh — one command that resets, replays the answer key, and opens the matrix.
# Use this for rehearsal or as a stage fallback if the live Bob run fails.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== SpecTrace Demo Runner ==="
echo ""

# 1. Reset to clean baseline
echo "→ Resetting to baseline..."
bash demo-kit/reset.sh
echo ""

# 2. Warm Maven cache silently
echo "→ Warming Maven cache (baseline test run)..."
cd bank-app && ./mvnw -q test
cd ..
echo "   Baseline: all tests green ✓"
echo ""

# 3. Replay answer-key (equivalent to a full Bob /trace run)
echo "→ Replaying SpecTrace answer key..."
bash demo-kit/replay-answer-key.sh
echo ""

# 4. Show summary
echo "=== Results ==="
python3 -c "
import json, sys
m = json.load(open('spectrace-out/metrics.json'))
c = m['status_counts']
print(f\"  {m['requirements_total']} requirements traced\")
print(f\"  ✅  Covered:         {c['COVERED']}\")
print(f\"  🆕  Newly tested:    {c['NEW_TEST_PASS']}\")
print(f\"  🐞  Bugs found:      {c['FAILING']}\")
print(f\"  ⛔  Not implemented: {c['NOT_IMPLEMENTED']}\")
print(f\"  Tests added: {m['tests_added']}\")
if m.get('time_saved_minutes'):
    saved = int(m['time_saved_minutes'])
    print(f\"  ⏱  Manual estimate: ~{m['manual_estimate_minutes']//60}h {m['manual_estimate_minutes']%60}m  |  SpecTrace: ~{m['spectrace_minutes']} min  |  Saved: ~{saved//60}h {saved%60}m\")
"

echo ""
echo "→ Open spectrace-out/matrix.html in your browser."

# Auto-open on macOS / Linux
if command -v open &>/dev/null; then
    open spectrace-out/matrix.html
elif command -v xdg-open &>/dev/null; then
    xdg-open spectrace-out/matrix.html
fi
