#!/usr/bin/env bash
# Save a successful live Bob /trace run as the answer key, so the stage fallback replays real Bob output
# (with its measured time) instead of hand-written reference files.
# Run right after `/trace docs/requirements.pdf` finishes and the matrix looks right.
set -euo pipefail
cd "$(dirname "$0")/.."
K=demo-kit/answer-key
T=bank-app/src/test/java/com/spectrace/bank

for f in spectrace-out/requirements.json spectrace-out/metrics.json spectrace-out/run.json \
         spectrace-out/trace-accounts.json spectrace-out/trace-transfers.json spectrace-out/trace-loans.json \
         spectrace-out/trace-security.json \
         "$T/accounts/AccountsRequirementsTest.java" "$T/transfers/TransfersRequirementsTest.java" \
         "$T/loans/LoansRequirementsTest.java" "$T/security/SecurityRequirementsTest.java"; do
  [ -f "$f" ] || { echo "missing $f. Run /trace in Bob first." >&2; exit 1; }
done

python3 - <<'EOF'
import json, sys
m = json.load(open("spectrace-out/metrics.json"))
if m.get("timing") != "live" or m.get("spectrace_minutes") is None:
    sys.exit(f"spectrace-out/metrics.json has timing={m.get('timing')!r}; only a live Bob run can be recorded")
if m.get("agent_disagreements"):
    print("warning: this run has agent disagreements:", ", ".join(m["agent_disagreements"]))
c = m["status_counts"]
print(f"recording: {m['requirements_total']} reqs, {c['COVERED']}/{c['NEW_TEST_PASS']}/{c['FAILING']}/"
      f"{c['NOT_IMPLEMENTED']} (covered/new/bug/missing), {m['spectrace_minutes']} min")
EOF

# Keep the hand-written reference once, for comparison
if [ ! -d "$K/reference" ]; then
  mkdir -p "$K/reference/tests"
  cp "$K"/requirements.json "$K"/trace-*.json "$K/reference/"
  cp "$K"/tests/*.java "$K/reference/tests/"
fi

cp spectrace-out/requirements.json spectrace-out/trace-*.json "$K/"
cp "$T/accounts/AccountsRequirementsTest.java" "$T/transfers/TransfersRequirementsTest.java" \
   "$T/loans/LoansRequirementsTest.java" "$T/security/SecurityRequirementsTest.java" "$K/tests/"
cp spectrace-out/matrix.html "$K/expected-matrix.html"
cp spectrace-out/matrix.md "$K/expected-matrix.md"
python3 - <<'EOF'
import json
run = json.load(open("spectrace-out/run.json"))
m = json.load(open("spectrace-out/metrics.json"))
run["elapsed_minutes"] = m["spectrace_minutes"]
run["recorded_at"] = m["generated_at"]
json.dump(run, open("demo-kit/answer-key/run.json", "w"), indent=2)
EOF
echo "Saved to $K/. Review with: git diff --stat -- $K"
