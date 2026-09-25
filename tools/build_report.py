#!/usr/bin/env python3
"""SpecTrace report builder.

Merges the per-module trace files written by Bob's subagents with the *actual*
evidence on disk (Surefire XML results + @Tag annotations in test sources) and
produces the traceability matrix. Statuses are recomputed from evidence, never
taken from the agents on trust; disagreements are flagged in the report.

Usage (from repo root):
    python3 tools/build_report.py [--out spectrace-out] [--app bank-app] [--baseline-ref HEAD]
"""
import argparse
import glob
import html
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

STATUSES = {
    "COVERED": ("Covered", "Existing tests prove the requirement"),
    "NEW_TEST_PASS": ("Newly tested", "Bob wrote the missing tests and they pass"),
    "FAILING": ("Bug found", "A test derived from the spec fails against the code"),
    "NOT_IMPLEMENTED": ("Not implemented", "No implementation found in the codebase"),
    "UNTESTED": ("Untested", "Implemented but no test traces to it"),
    "UNVERIFIED": ("Unverified", "Tests listed but no result found; re-run the suite"),
}
ORDER = ["FAILING", "NOT_IMPLEMENTED", "UNTESTED", "UNVERIFIED", "NEW_TEST_PASS", "COVERED"]

TAG_RE = re.compile(r'@Tag\(\s*"(REQ-[A-Z]+-\d+)"\s*\)')
METHOD_RE = re.compile(r'^\s*(?:public\s+|protected\s+|private\s+)?void\s+(\w+)\s*\(')
PACKAGE_RE = re.compile(r'^\s*package\s+([\w.]+)\s*;', re.M)


def scan_tags(source, fqcn_hint=None):
    """Return {req_id: [(class, method)]} for @Test methods carrying @Tag(REQ-...)."""
    pkg = PACKAGE_RE.search(source)
    found = {}
    pending_tags, is_test = [], False
    class_name = fqcn_hint
    for line in source.splitlines():
        cls = re.search(r'\bclass\s+(\w+)', line)
        if cls and class_name is None:
            class_name = (pkg.group(1) + "." if pkg else "") + cls.group(1)
        if "@Test" in line or "@ParameterizedTest" in line:
            is_test = True
        pending_tags += TAG_RE.findall(line)
        m = METHOD_RE.match(line)
        if m:
            if is_test:
                for tag in pending_tags:
                    found.setdefault(tag, []).append((class_name, m.group(1)))
            pending_tags, is_test = [], False
    return found


def scan_test_tree(app):
    result = {}
    for path in glob.glob(os.path.join(app, "src/test/java/**/*.java"), recursive=True):
        with open(path, encoding="utf-8") as f:
            for req, tests in scan_tags(f.read()).items():
                result.setdefault(req, []).extend(tests)
    return result


def scan_baseline(app, ref):
    """Tagged tests that already existed at `ref` (git), so we can tell new from old."""
    try:
        files = subprocess.run(["git", "ls-tree", "-r", "--name-only", ref, "--", "src/test/java"],
                               cwd=app, capture_output=True, text=True, check=True).stdout.split()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    result = set()
    for rel in files:
        if not rel.endswith(".java"):
            continue
        src = subprocess.run(["git", "show", f"{ref}:./{rel}"], cwd=app,
                             capture_output=True, text=True).stdout
        for req, tests in scan_tags(src).items():
            result.update((req, c, m) for c, m in tests)
    return result


def load_surefire(app, build_dir):
    results = {}
    for path in glob.glob(os.path.join(app, build_dir, "surefire-reports", "TEST-*.xml")):
        for case in ET.parse(path).getroot().iter("testcase"):
            key = (case.get("classname"), case.get("name"))
            failure = case.find("failure")
            if failure is None:
                failure = case.find("error")
            if failure is not None:
                msg = (failure.get("message") or failure.get("type") or "failed").strip()
                results[key] = ("FAIL", msg.splitlines()[0][:200] if msg else "failed")
            elif case.find("skipped") is not None:
                results[key] = ("SKIP", "")
            else:
                results[key] = ("PASS", "")
    return results


def load_json(path, default=None):
    if not os.path.exists(path):
        return default
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def build(out, app, baseline_ref, build_dir):
    reqs = load_json(os.path.join(out, "requirements.json"))
    if not reqs:
        sys.exit(f"missing {out}/requirements.json. Run the extractor first")
    req_list = reqs["requirements"] if isinstance(reqs, dict) else reqs

    claims = {}
    for path in sorted(glob.glob(os.path.join(out, "trace-*.json"))):
        for entry in load_json(path).get("requirements", []):
            claims[entry["id"]] = entry

    tagged = scan_test_tree(app)
    baseline = scan_baseline(app, baseline_ref)
    surefire = load_surefire(app, build_dir)

    rows = []
    for req in req_list:
        rid = req["id"]
        claim = claims.get(rid, {})
        tests = {(c, m) for c, m in tagged.get(rid, [])}
        tests |= {(t["class"], t["method"]) for t in claim.get("tests", []) if t.get("class") and t.get("method")}

        test_rows, any_fail, any_missing, any_new = [], False, False, False
        for cls, meth in sorted(tests):
            outcome, msg = surefire.get((cls, meth), ("MISSING", ""))
            if baseline is not None:
                is_new = (rid, cls, meth) not in baseline
            else:
                is_new = any(t.get("new") for t in claim.get("tests", []) if t.get("method") == meth)
            has_tag = (cls, meth) in set(tagged.get(rid, []))
            any_fail |= outcome == "FAIL"
            any_missing |= outcome == "MISSING"
            any_new |= is_new
            test_rows.append({"class": cls, "method": meth, "result": outcome, "message": msg,
                              "new": is_new, "tagged": has_tag})

        if not tests:
            status = "NOT_IMPLEMENTED" if claim.get("status") == "NOT_IMPLEMENTED" else "UNTESTED"
        elif any_fail:
            status = "FAILING"
        elif any_missing:
            status = "UNVERIFIED"
        elif any_new:
            status = "NEW_TEST_PASS"
        else:
            status = "COVERED"

        claimed = claim.get("status")
        rows.append({
            "id": rid,
            "title": req.get("title", ""),
            "module": req.get("module", rid.split("-")[1].lower()),
            "status": status,
            "agent_status": claimed,
            "agent_agrees": claimed in (None, status),
            "implementation": claim.get("implementation", []),
            "tests": test_rows,
            "notes": claim.get("notes", ""),
        })

    counts = {s: sum(1 for r in rows if r["status"] == s) for s in STATUSES}
    run = load_json(os.path.join(out, "run.json"), {})
    started = run.get("started_at")
    elapsed_min = None
    if started:
        t0 = datetime.fromisoformat(started.replace("Z", "+00:00"))
        elapsed_min = round((datetime.now(timezone.utc) - t0).total_seconds() / 60, 1)
    manual_min_per_req = run.get("manual_minutes_per_requirement", 30)
    metrics = {
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "requirements_total": len(rows),
        "status_counts": counts,
        "tests_total": sum(len(r["tests"]) for r in rows),
        "tests_added": sum(1 for r in rows for t in r["tests"] if t["new"]),
        "bugs_found": counts["FAILING"],
        "gaps_found": counts["NOT_IMPLEMENTED"] + counts["UNTESTED"],
        "agent_disagreements": [r["id"] for r in rows if not r["agent_agrees"]],
        "spectrace_minutes": elapsed_min,
        "manual_estimate_minutes": manual_min_per_req * len(rows),
        "manual_estimate_basis": f"{manual_min_per_req} min per requirement to locate code, find tests, "
                                 f"write missing tests and update the matrix by hand",
    }
    return rows, metrics


def render_md(rows, metrics):
    c = metrics["status_counts"]
    lines = ["# SpecTrace traceability matrix", "",
             f"_Generated {metrics['generated_at']}_", "",
             f"**{metrics['requirements_total']} requirements**: "
             f"{c['COVERED']} covered · {c['NEW_TEST_PASS']} newly tested · "
             f"{c['FAILING']} bugs found · {c['NOT_IMPLEMENTED']} not implemented · "
             f"{c['UNTESTED']} untested · {metrics['tests_added']} tests added", "",
             "| Requirement | Status | Implementation | Tests | Notes |",
             "|---|---|---|---|---|"]
    for r in sorted(rows, key=lambda r: r["id"]):
        impl = "<br>".join(f"`{i.get('symbol') or i.get('file')}`" for i in r["implementation"]) or "-"
        tests = "<br>".join(f"{'🆕 ' if t['new'] else ''}{t['method']} ({t['result']})" for t in r["tests"]) or "-"
        flag = "" if r["agent_agrees"] else f" ⚠ agent said {r['agent_status']}"
        lines.append(f"| **{r['id']}** {r['title']} | {STATUSES[r['status']][0]}{flag} | {impl} | {tests} | "
                     f"{r['notes'].replace('|', '/')} |")
    lines += ["", f"Time: SpecTrace {metrics['spectrace_minutes']} min vs manual estimate "
              f"{metrics['manual_estimate_minutes']} min ({metrics['manual_estimate_basis']})."]
    return "\n".join(lines) + "\n"


def render_html(rows, metrics):
    esc = html.escape
    c = metrics["status_counts"]
    cards = [("Requirements", metrics["requirements_total"], "neutral"),
             ("Covered", c["COVERED"], "covered"),
             ("Newly tested", c["NEW_TEST_PASS"], "new"),
             ("Bugs found", c["FAILING"], "failing"),
             ("Not implemented", c["NOT_IMPLEMENTED"] + c["UNTESTED"], "missing"),
             ("Tests added", metrics["tests_added"], "neutral")]
    card_html = "".join(f'<div class="card {k}"><div class="num">{v}</div><div class="lbl">{esc(l)}</div></div>'
                        for l, v, k in cards)
    klass = {"COVERED": "covered", "NEW_TEST_PASS": "new", "FAILING": "failing",
             "NOT_IMPLEMENTED": "missing", "UNTESTED": "missing", "UNVERIFIED": "missing"}
    body = []
    for r in sorted(rows, key=lambda r: (ORDER.index(r["status"]), r["id"])):
        impl = "".join(f"<div><code>{esc(i.get('symbol') or '')}</code> <span class='muted'>"
                       f"{esc(i.get('file', ''))}{':' + str(i['line']) if i.get('line') else ''}</span></div>"
                       for i in r["implementation"]) or "<span class='muted'>none found</span>"
        tests = "".join(
            f"<div class='t {t['result'].lower()}'>{'<b class=new-badge>NEW</b> ' if t['new'] else ''}"
            f"<code>{esc(t['method'])}</code> <span class='res'>{t['result']}</span>"
            f"{'' if t['tagged'] else ' <span class=warn>untagged</span>'}"
            f"{'<div class=msg>' + esc(t['message']) + '</div>' if t['message'] else ''}</div>"
            for t in r["tests"]) or "<span class='muted'>no tests</span>"
        flag = "" if r["agent_agrees"] else f"<div class='warn'>agent claimed {esc(str(r['agent_status']))}</div>"
        body.append(f"<tr><td><div class='rid'>{esc(r['id'])}</div><div>{esc(r['title'])}</div></td>"
                    f"<td><span class='pill {klass[r['status']]}'>{STATUSES[r['status']][0]}</span>{flag}</td>"
                    f"<td>{impl}</td><td>{tests}</td><td class='notes'>{esc(r['notes'])}</td></tr>")
    speed = ""
    if metrics["spectrace_minutes"] is not None:
        speed = (f"<p class='speed'><b>{metrics['spectrace_minutes']} min</b> with SpecTrace vs "
                 f"<b>~{metrics['manual_estimate_minutes'] // 60} h {metrics['manual_estimate_minutes'] % 60} min</b> "
                 f"manual estimate <span class='muted'>({esc(metrics['manual_estimate_basis'])})</span></p>")
    return f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>SpecTrace Matrix</title>
<style>
:root {{ --bg:#fff; --fg:#161616; --muted:#6f6f6f; --line:#e0e0e0; --panel:#f4f4f4;
  --covered:#198038; --new:#0f62fe; --failing:#da1e28; --missing:#8a3ffc; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg:#161616; --fg:#f4f4f4; --muted:#a8a8a8; --line:#393939;
  --panel:#262626; --covered:#42be65; --new:#78a9ff; --failing:#ff8389; --missing:#be95ff; }} }}
* {{ box-sizing:border-box }}
body {{ margin:0; padding:24px 16px; background:var(--bg); color:var(--fg);
  font:14px/1.45 "IBM Plex Sans","Helvetica Neue",Arial,sans-serif }}
main {{ max-width:1200px; margin:0 auto }}
h1 {{ margin:0 0 4px; font-size:24px }} .muted {{ color:var(--muted) }}
.cards {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(140px,1fr)); gap:12px; margin:20px 0 }}
.card {{ background:var(--panel); border-left:4px solid var(--line); padding:12px 14px }}
.card .num {{ font-size:28px; font-weight:600 }} .card .lbl {{ color:var(--muted) }}
.card.covered {{ border-color:var(--covered) }} .card.new {{ border-color:var(--new) }}
.card.failing {{ border-color:var(--failing) }} .card.missing {{ border-color:var(--missing) }}
.speed {{ font-size:15px }}
.wrap {{ overflow-x:auto }}
table {{ width:100%; border-collapse:collapse; min-width:820px }}
th,td {{ border-bottom:1px solid var(--line); padding:10px 8px; vertical-align:top; text-align:left }}
th {{ font-size:12px; text-transform:uppercase; letter-spacing:.04em; color:var(--muted) }}
.rid {{ font-family:"IBM Plex Mono",Menlo,monospace; font-weight:600 }}
code {{ font-family:"IBM Plex Mono",Menlo,monospace; font-size:12px }}
.pill {{ display:inline-block; padding:2px 10px; border-radius:12px; font-size:12px; font-weight:600; color:#fff; white-space:nowrap }}
.pill.covered {{ background:var(--covered) }} .pill.new {{ background:var(--new) }}
.pill.failing {{ background:var(--failing) }} .pill.missing {{ background:var(--missing) }}
.t {{ margin-bottom:4px }} .t .res {{ font-size:11px; font-weight:600 }}
.t.pass .res {{ color:var(--covered) }} .t.fail .res {{ color:var(--failing) }} .t.missing .res {{ color:var(--missing) }}
.msg {{ font-size:12px; color:var(--failing) }}
.new-badge {{ font-size:10px; color:var(--new) }}
.warn {{ font-size:12px; color:var(--failing) }}
.notes {{ font-size:13px; max-width:320px }}
</style></head><body><main>
<h1>SpecTrace traceability matrix</h1>
<div class="muted">NBK-BRD-2026-014 → bank-app · generated {esc(metrics['generated_at'])} · statuses checked against Surefire results</div>
<div class="cards">{card_html}</div>
{speed}
<div class="wrap"><table><thead><tr><th>Requirement</th><th>Status</th><th>Implementation</th><th>Tests</th><th>Notes</th></tr></thead>
<tbody>{''.join(body)}</tbody></table></div>
</main></body></html>
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="spectrace-out")
    ap.add_argument("--app", default="bank-app")
    ap.add_argument("--build-dir", default="target")
    ap.add_argument("--baseline-ref", default="HEAD")
    args = ap.parse_args()

    rows, metrics = build(args.out, args.app, args.baseline_ref, args.build_dir)
    with open(os.path.join(args.out, "matrix.md"), "w", encoding="utf-8") as f:
        f.write(render_md(rows, metrics))
    with open(os.path.join(args.out, "matrix.html"), "w", encoding="utf-8") as f:
        f.write(render_html(rows, metrics))
    with open(os.path.join(args.out, "metrics.json"), "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2)
    c = metrics["status_counts"]
    print(f"{metrics['requirements_total']} requirements: {c['COVERED']} covered, {c['NEW_TEST_PASS']} newly tested, "
          f"{c['FAILING']} failing, {c['NOT_IMPLEMENTED']} not implemented, {c['UNTESTED']} untested, "
          f"{c['UNVERIFIED']} unverified; {metrics['tests_added']} tests added")
    if metrics["agent_disagreements"]:
        print("agent disagreements:", ", ".join(metrics["agent_disagreements"]))


if __name__ == "__main__":
    main()
