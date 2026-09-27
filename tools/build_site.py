#!/usr/bin/env python3
"""Build the GitHub Pages site from site/index.html.

site/index.html is a page fragment (its <title>, fonts and <style> come first, then the body
markup), which is also what the claude.ai page publisher expects. This wraps it in a full HTML
document and copies the results dashboard next to it.

Usage (from repo root):  python3 tools/build_site.py [--out _site]
"""
import argparse
import os
import shutil

HEAD_END_MARKER = '<div class="bar">'


def build(src, matrix, out):
    with open(src, encoding="utf-8") as f:
        page = f.read()
    split = page.index(HEAD_END_MARKER)
    head, body = page[:split].strip(), page[split:].strip()
    doc = ("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
           "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n"
           "<meta name=\"description\" content=\"SpecTrace traces a requirements PDF to code and tests with "
           "parallel IBM Bob subagents, and reports what's covered, broken or missing.\">\n"
           f"{head}\n</head>\n<body>\n{body}\n</body>\n</html>\n")
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "index.html"), "w", encoding="utf-8") as f:
        f.write(doc)
    shutil.copy(matrix, os.path.join(out, "matrix.html"))
    # Serve files as-is (no Jekyll processing).
    open(os.path.join(out, ".nojekyll"), "w").close()
    print(f"built {out}/index.html and {out}/matrix.html")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default="site/index.html")
    ap.add_argument("--matrix", default="demo-kit/answer-key/expected-matrix.html")
    ap.add_argument("--out", default="_site")
    args = ap.parse_args()
    build(args.src, args.matrix, args.out)


if __name__ == "__main__":
    main()
