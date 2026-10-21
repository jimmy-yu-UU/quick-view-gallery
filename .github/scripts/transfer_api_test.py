#!/usr/bin/env python3
"""Exercises the phone's transfer server through `adb forward` from the CI machine.
Usage: transfer_api_test.py <port> <pairing code> <out dir>. Exit code 1 on failure."""
import io
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile

port, code, out = sys.argv[1], sys.argv[2], sys.argv[3]
base = f"http://127.0.0.1:{port}"
results, failed = [], False


def req(path, method="GET", body=None, headers=None):
    r = urllib.request.Request(base + path, data=body, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            return resp.status, resp.read(), dict(resp.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read(), dict(e.headers)


def check(name, cond, detail=""):
    global failed
    results.append(("PASS" if cond else "FAIL") + f"  {name}  {detail}")
    failed |= not cond


s, b, _ = req("/api/info")
check("info", s == 200 and json.loads(b)["app"] == "QV Gallery", b[:100])
s, b, _ = req("/")
check("web page", s == 200 and b"QV Gallery" in b, f"{len(b)} bytes")
s, _, _ = req("/api/albums")
check("albums without token -> 401", s == 401, str(s))
s, _, _ = req("/api/login", "POST", json.dumps({"code": "000000" if code != "000000" else "111111"}).encode(), {"Content-Type": "application/json"})
check("wrong code -> 403", s == 403, str(s))
s, b, h = req("/api/login", "POST", json.dumps({"code": code}).encode(), {"Content-Type": "application/json"})
check("login", s == 200 and "qvt=" in h.get("Set-Cookie", ""), str(s))
token = json.loads(b)["token"] if s == 200 else ""
H = {"X-QV-Token": token}

s, b, _ = req("/api/albums", headers=H)
albums = json.loads(b) if s == 200 else []
check("albums", s == 200 and len(albums) > 0, ", ".join(a["name"] for a in albums))
check("hidden album not shared", all(a["name"] != "Big" for a in albums))
album = next((a for a in albums if a["name"] == "Wallpapers"), albums[0] if albums else None)
s, b, _ = req("/api/media?album=" + urllib.parse.quote(album["path"]), headers=H)
media = json.loads(b) if s == 200 else []
check("media", s == 200 and len(media) == album["count"], f"{len(media)} items")
m = media[0]
s, b, h = req("/api/thumb?path=" + urllib.parse.quote(m["path"]), headers=H)
check("thumbnail jpeg", s == 200 and b[:2] == b"\xff\xd8", f"{len(b)} bytes")
s, b, h = req("/api/file?path=" + urllib.parse.quote(m["path"]), headers=H)
check("file download", s == 200 and len(b) == m["size"], f"{len(b)} / {m['size']}")
full = b
s, b, h = req("/api/file?path=" + urllib.parse.quote(m["path"]), headers=dict(H, Range="bytes=100-199"))
check("range request", s == 206 and b == full[100:200] and h.get("Content-Range", "").startswith("bytes 100-199/"), h.get("Content-Range"))
s, _, _ = req("/api/file?path=" + urllib.parse.quote("/system/etc/hosts"), headers=H)
check("arbitrary path refused", s == 404, str(s))
s, b, _ = req("/api/zip?album=" + urllib.parse.quote(album["path"]), headers=H)
names = zipfile.ZipFile(io.BytesIO(b)).namelist() if s == 200 else []
check("album zip", s == 200 and len(names) == len(media), f"{len(names)} entries, {len(b)} bytes")
sel = json.dumps([x["path"] for x in media[:2]])
s, b, _ = req("/api/zip?album=x", "POST", ("paths=" + urllib.parse.quote(sel)).encode(), dict(H, **{"Content-Type": "application/x-www-form-urlencoded"}))
names = zipfile.ZipFile(io.BytesIO(b)).namelist() if s == 200 else []
check("selection zip (form)", s == 200 and len(names) == 2, str(names))
s, b, _ = req("/api/upload?name=" + urllib.parse.quote("from_pc.jpg"), "PUT", full, dict(H, **{"Content-Type": "application/octet-stream"}))
check("upload", s == 200, b[:80])
s, b, _ = req("/api/upload?name=evil.sh", "PUT", b"#!/bin/sh", H)
check("non-media upload refused", s == 415, str(s))

open(f"{out}/transfer_api.txt", "w").write("\n".join(results) + "\n")
print("\n".join(results))
sys.exit(1 if failed else 0)
