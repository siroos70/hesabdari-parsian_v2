#!/usr/bin/env python3
"""Enrich CHANGELOG.md with release notes.

Replaces the current version's placeholder entry (written by bump-version.sh)
with the generated notes, and backfills up to N previous placeholder entries
with the descriptions published on their GitHub release pages.

Usage: update-changelog.py <version> <notes_file> [prev_limit]
"""
import http.client
import json
import os
import re
import sys
import urllib.parse

VERSION = sys.argv[1]
NOTES_FILE = sys.argv[2]
PREV_LIMIT = int(sys.argv[3]) if len(sys.argv) > 3 else 5

with open(NOTES_FILE, encoding="utf-8") as fh:
    current_notes = fh.read().strip()

with open("CHANGELOG.md", encoding="utf-8") as fh:
    changelog = fh.read()

m = re.search(r"(?m)^## \[", changelog)
if not m:
    print("No changelog entries found", file=sys.stderr)
    sys.exit(1)
header = changelog[: m.start()]
body = changelog[m.start():]

# Split body into [heading, content, heading, content, ...]
parts = re.split(r"(?m)^(## \[[^\]]+\].*)$", body)
headings = []
contents = []
i = 1
while i < len(parts):
    headings.append(parts[i])
    contents.append(parts[i + 1] if i + 1 < len(parts) else "")
    i += 2


def strip_wrapper(text: str) -> str:
    text = re.sub(r"(?s)^<div dir=\"rtl\">\s*", "", text)
    text = re.sub(r"(?s)\s*</div>\s*$", "", text)
    # Drop any nested "previous releases" section to avoid recursion.
    text = re.sub(r"(?s)\n## نسخه‌های قبلی.*$", "", text)
    return text.strip()


def is_placeholder(content: str) -> bool:
    c = content.strip()
    if not re.search(r"- Release version \d+\.\d+\.\d+", c):
        return False
    # A placeholder is ONLY the version bullet (under any ### subsection).
    # Strip headers, bullets, and blockquotes; if nothing real remains it's a
    # placeholder. Real (backfilled) entries keep prose/extra bullets behind.
    cleaned = re.sub(r"(?m)^#{1,6} .*$", "", c)
    cleaned = re.sub(r"(?m)^[-*+] .*$", "", cleaned)
    cleaned = re.sub(r"(?m)^>.*$", "", cleaned)
    return not cleaned.strip()


def gh_body(tag: str):
    repo = os.environ.get("GITHUB_REPOSITORY")
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not repo:
        print("GITHUB_REPOSITORY not set; skipping backfill", file=sys.stderr)
        return None
    path = "/repos/%s/releases/tags/%s" % (repo, urllib.parse.quote(tag))
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "update-changelog",
    }
    if token:
        headers["Authorization"] = "Bearer %s" % token
    conn = None
    try:
        conn = http.client.HTTPSConnection("api.github.com", timeout=30)
        conn.request("GET", path, headers=headers)
        resp = conn.getresponse()
        if resp.status != 200:
            print("github api returned %s for %s" % (resp.status, tag), file=sys.stderr)
            return None
        data = json.loads(resp.read().decode("utf-8"))
        body = data.get("body") or ""
        if body.strip():
            return strip_wrapper(body)
    except Exception as exc:
        print("github api release fetch failed for %s: %s" % (tag, exc), file=sys.stderr)
    finally:
        if conn is not None:
            conn.close()
    return None


# Replace the current version's entry with the generated notes.
cur_idx = next(
    (idx for idx, h in enumerate(headings) if re.search(r"^## \[" + re.escape(VERSION) + r"\]", h)),
    None,
)
if cur_idx is None:
    headings.insert(0, f"## [{VERSION}]")
    contents.insert(0, "")
    cur_idx = 0
contents[cur_idx] = "\n" + current_notes + "\n"

# Backfill previous placeholder entries from their release pages.
backfilled = 0
for idx, h in enumerate(headings):
    if idx == cur_idx or backfilled >= PREV_LIMIT:
        continue
    if not is_placeholder(contents[idx]):
        continue
    mt = re.search(r"^## \[([^\]]+)\]", h)
    if not mt:
        continue
    ver = mt.group(1)
    tag = ver if ver.startswith("v") else "v" + ver
    body_notes = gh_body(tag)
    if body_notes:
        contents[idx] = "\n" + body_notes + "\n"
        backfilled += 1

new_body = "".join(h + c for h, c in zip(headings, contents))
with open("CHANGELOG.md", "w", encoding="utf-8") as fh:
    fh.write(header + new_body)

print(
    f"CHANGELOG.md updated (current + {backfilled} backfilled)",
    file=sys.stderr,
)
