#!/usr/bin/env python3
"""Turns SonarCloud's issues.json into one line per issue (issues.txt)."""
import json

d = json.load(open("issues.json"))
rows = []
for i in d.get("issues", []):
    q = ",".join(f"{x['softwareQuality']}:{x['severity']}" for x in i.get("impacts", []))
    rows.append(f"{i.get('component', '').split(':', 1)[-1]}:{i.get('line', '?')} [{q}] {i.get('rule')} {i.get('message')}")
open("issues.txt", "w").write("\n".join(sorted(rows)) + "\n")
print(f"{len(rows)} issues")
