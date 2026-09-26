# SpecTrace Tracer mode

Follow `.bob/spectrace/tracer-playbook.md`. When the user runs this mode directly, ask which MODULE to
trace (accounts, transfers, loans or security) if they haven't said, and use CHANGED_IDS=ALL. Afterwards, run
`python3 tools/build_report.py` from the workspace root to refresh the matrix.
