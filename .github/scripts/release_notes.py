#!/usr/bin/env python3
"""Builds bilingual release notes from the commit messages since the previous release.

Commit message convention:
    English subject / 中文標題

    EN:
    - English details
    ZH:
    - 中文說明

Messages without the markers are used as-is in both languages."""
import re
import subprocess
import sys

rng, sha, prev = sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else ""
raw = subprocess.run(
    ["git", "log", "--no-merges", "--reverse", "--format=%x1e%B", rng, "--", ".", ":(exclude).github"],
    capture_output=True, text=True, check=True,
).stdout

en, zh = [], []
for msg in filter(None, (m.strip() for m in raw.split("\x1e"))):
    lines = [l for l in msg.splitlines() if not re.match(r"^(Co-Authored-By|Claude-Session):", l)]
    subject, body = lines[0], "\n".join(lines[1:]).strip()
    s_en, _, s_zh = subject.partition(" / ")
    s_zh = s_zh or s_en
    m = re.search(r"^EN:\s*\n(.*?)(?=^ZH:|\Z)", body, re.S | re.M)
    b_en = m.group(1).strip() if m else body
    m = re.search(r"^ZH:\s*\n(.*)", body, re.S | re.M)
    b_zh = m.group(1).strip() if m else body
    en.append(f"### {s_en.strip()}\n{b_en}".strip())
    zh.append(f"### {s_zh.strip()}\n{b_zh}".strip())

out = ["## What's new", "", "\n\n".join(en) or "- Maintenance", "", "## 更新內容", "", "\n\n".join(zh) or "- 維護更新", "", "---"]
out.append(f"Commit: {sha}" + (f" · Previous / 上一版: {prev}" if prev else ""))
print("\n".join(out))
