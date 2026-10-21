#!/usr/bin/env python3
"""Drives QV Gallery on an emulator: seeds test media, walks through the main screens,
saves screenshots and logcat into ./out for review."""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

PKG = "com.jimmyyu.qvgallery"
OUT = os.path.abspath("out")
MEDIA = os.path.abspath("media")
os.makedirs(OUT, exist_ok=True)
os.makedirs(MEDIA, exist_ok=True)
step_no = 0
failures = []


def adb(*args, check=False, timeout=120):
    r = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {args} failed: {r.stderr}")
    return r.stdout


def sh(cmd, timeout=120):
    return adb("shell", cmd, timeout=timeout)


def log(msg):
    print(msg, flush=True)
    with open(os.path.join(OUT, "steps.txt"), "a") as f:
        f.write(msg + "\n")


def shot(name):
    global step_no
    step_no += 1
    path = os.path.join(OUT, f"{step_no:02d}_{name}.png")
    with open(path, "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, timeout=60)
    try:
        im = Image.open(path).convert("RGB")
        im.thumbnail((540, 1200))
        im.save(path.replace(".png", ".jpg"), quality=70)
        os.remove(path)
    except Exception as e:  # keep the png if conversion fails
        log(f"  screenshot convert failed: {e}")
    log(f"[{step_no:02d}] {name}")


def dump():
    sh("uiautomator dump /sdcard/ui.xml", timeout=60)
    xml = adb("shell", "cat /sdcard/ui.xml")
    try:
        return ET.fromstring(xml[xml.index("<"):])
    except Exception:
        return None


def find(label, exact=False):
    root = dump()
    if root is None:
        return None
    for node in root.iter("node"):
        for attr in ("text", "content-desc"):
            v = node.get(attr) or ""
            if (v == label) if exact else (label in v):
                m = re.findall(r"\d+", node.get("bounds", ""))
                if len(m) == 4:
                    x1, y1, x2, y2 = map(int, m)
                    return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap(label, exact=False, wait=1.5, required=True):
    for _ in range(3):
        pos = find(label, exact)
        if pos:
            sh(f"input tap {pos[0]} {pos[1]}")
            time.sleep(wait)
            return True
        time.sleep(1)
    msg = f"  !! could not find '{label}'"
    log(msg)
    if required:
        failures.append(msg)
    return False


def tap_xy(x, y, wait=1.0):
    sh(f"input tap {x} {y}")
    time.sleep(wait)


def back(wait=1.0):
    sh("input keyevent KEYCODE_BACK")
    time.sleep(wait)


def screen_size():
    m = re.search(r"(\d+)x(\d+)", sh("wm size"))
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def crashed():
    out = adb("logcat", "-d", "-b", "crash")
    return PKG in out


# ---------------- Media ----------------

def make_media():
    def picture(path, w, h, color, label, fmt="JPEG", **kw):
        im = Image.new("RGB", (w, h), color)
        d = ImageDraw.Draw(im)
        for i in range(0, max(w, h), max(w, h) // 12):
            d.line([(i, 0), (0, i)], fill=(255, 255, 255), width=max(2, w // 300))
        d.rectangle([w // 10, h // 10, w // 2, h // 3], outline=(0, 0, 0), width=max(3, w // 200))
        d.text((w // 8, h // 8), label, fill=(0, 0, 0))
        im.save(path, fmt, **kw)

    os.makedirs(f"{MEDIA}/Wallpapers", exist_ok=True)
    os.makedirs(f"{MEDIA}/Camera", exist_ok=True)
    os.makedirs(f"{MEDIA}/Big", exist_ok=True)
    os.makedirs(f"{MEDIA}/Formats", exist_ok=True)
    colors = [(220, 80, 80), (80, 160, 220), (90, 200, 120), (230, 200, 70), (170, 90, 210), (240, 140, 60)]
    for i, c in enumerate(colors):
        picture(f"{MEDIA}/Wallpapers/wall_{i + 1}.jpg", 1080, 1920, c, f"wall {i + 1}")
    for i in range(4):
        exif = Image.Exif()
        exif[0x010F] = "Google"
        exif[0x0110] = "Pixel 10 Pro"
        picture(f"{MEDIA}/Camera/IMG_2025010{i}_120000.jpg", 4000, 3000, colors[i], f"cam {i}", exif=exif.tobytes())
    picture(f"{MEDIA}/Big/huge_8000x12000.jpg", 8000, 12000, (60, 120, 180), "huge", quality=80)
    picture(f"{MEDIA}/Formats/sample.png", 1200, 900, (200, 100, 150), "png", fmt="PNG")
    picture(f"{MEDIA}/Formats/sample.webp", 1200, 900, (100, 200, 150), "webp", fmt="WEBP")
    frames = []
    for i in range(8):
        im = Image.new("RGB", (400, 400), (30 * i, 100, 255 - 30 * i))
        ImageDraw.Draw(im).ellipse([40 * i % 300, 100, 40 * i % 300 + 100, 200], fill=(255, 255, 0))
        frames.append(im)
    frames[0].save(f"{MEDIA}/Formats/anim.gif", save_all=True, append_images=frames[1:], duration=120, loop=0)
    try:
        import pillow_heif
        pillow_heif.register_heif_opener()
        picture(f"{MEDIA}/Formats/sample.heic", 1600, 1200, (120, 120, 220), "heic", fmt="HEIF")
    except Exception as e:
        log(f"heic skipped: {e}")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc=duration=5:size=640x360:rate=25",
                    "-pix_fmt", "yuv420p", f"{MEDIA}/Camera/VID_20250105_120000.mp4"])


def push_media():
    sh("mkdir -p /sdcard/Pictures /sdcard/DCIM")
    adb("push", f"{MEDIA}/Wallpapers", "/sdcard/Pictures/", timeout=300)
    adb("push", f"{MEDIA}/Big", "/sdcard/Pictures/", timeout=300)
    adb("push", f"{MEDIA}/Formats", "/sdcard/Pictures/", timeout=300)
    adb("push", f"{MEDIA}/Camera", "/sdcard/DCIM/", timeout=300)
    sh("content call --method scan_volume --uri content://media --arg external_primary", timeout=300)
    time.sleep(5)
    log("media count: " + sh("content query --uri content://media/external/file --projection _data | grep -c _data"))


# ---------------- Scenario ----------------

def main():
    apk = sys.argv[1]
    make_media()
    push_media()
    adb("install", "-r", "-g", apk, check=True, timeout=300)
    sh(f"appops set --uid {PKG} MANAGE_EXTERNAL_STORAGE allow")
    # The script looks for Traditional Chinese labels; the emulator itself runs in English.
    sh(f"cmd locale set-app-locales {PKG} --locales zh-TW")
    # Skip Android's one-time "Viewing full screen" hint, which would cover the app.
    sh("settings put secure immersive_mode_confirmations confirmed")
    adb("logcat", "-c")
    W, H = screen_size()
    log(f"screen {W}x{H}")

    sh(f"am start -W -n {PKG}/.MainActivity")
    time.sleep(6)
    shot("albums")

    # Album list view and back to grid
    tap("更多")
    shot("albums_menu")
    tap("清單檢視")
    shot("albums_list")
    tap("更多")
    tap("格狀檢視")

    # Sort menu
    tap("排序")
    shot("album_sort_menu")
    back()

    # Search
    tap("搜尋")
    sh("input text Wall")
    time.sleep(1.5)
    shot("album_search")
    back()
    back()

    # Open an album
    tap("Wallpapers")
    time.sleep(2)
    shot("album_grid")
    # Open first photo: first grid cell is just below the top bar.
    tap_xy(W // 6, int(H * 0.16), wait=3)
    shot("viewer")
    sh(f"input swipe {int(W * 0.8)} {H // 2} {int(W * 0.2)} {H // 2} 250")
    time.sleep(2)
    shot("viewer_swiped")
    tap("放大", wait=2)
    shot("viewer_zoom_in")
    sh(f"input swipe {W // 2} {H // 2} {W // 3} {H // 3} 300")
    time.sleep(1)
    shot("viewer_zoom_pan")
    tap("縮小", wait=2)
    sh("input keyevent KEYCODE_VOLUME_DOWN")
    time.sleep(2)
    shot("viewer_volume_next")
    # Toggle UI by tapping the image center
    tap_xy(W // 2, H // 2, wait=1.5)
    shot("viewer_fullscreen")
    tap_xy(W // 2, H // 2, wait=1.5)
    tap("更多")
    shot("viewer_menu")
    tap("詳細資訊")
    shot("viewer_details")
    tap("關閉")

    # Editor
    tap("編輯", exact=True, wait=3)
    shot("editor")
    tap("1:1")
    shot("editor_square")
    tap("向右旋轉")
    shot("editor_rotated")
    tap("儲存")
    shot("editor_save_dialog")
    tap("另存新檔", wait=5)
    shot("after_save")
    back()
    back()

    # Camera album: EXIF photos + video
    tap("Camera")
    time.sleep(2)
    shot("camera_grid")
    # Selection details: long-press two items, then More > Details
    sh(f"input swipe {W // 6} {int(H * 0.16)} {W // 6} {int(H * 0.16)} 900")
    time.sleep(1)
    tap_xy(W // 2, int(H * 0.16))
    tap("更多")
    tap("詳細資訊")
    shot("selection_details")
    tap("關閉")
    back()
    tap_xy(W // 6, int(H * 0.16), wait=3)
    shot("camera_viewer")
    tap("更多")
    tap("詳細資訊")
    shot("camera_details_exif")
    tap("關閉")
    for i in range(5):
        sh(f"input swipe {int(W * 0.85)} {H // 2} {int(W * 0.15)} {H // 2} 200")
        time.sleep(1.2)
    time.sleep(3)
    shot("video_page")
    back()
    back()

    # Formats album: GIF, WebP, PNG, HEIC
    tap("Formats")
    time.sleep(2)
    shot("formats_grid")
    tap_xy(W // 6, int(H * 0.16), wait=3)
    shot("formats_viewer_1")
    for i in range(3):
        sh(f"input swipe {int(W * 0.85)} {H // 2} {int(W * 0.15)} {H // 2} 200")
        time.sleep(2.5)
        shot(f"formats_viewer_{i + 2}")
    back()
    back()

    # Huge image
    tap("Big")
    time.sleep(2)
    tap_xy(W // 6, int(H * 0.16), wait=6)
    shot("huge_viewer")
    tap("放大", wait=2)
    tap("放大", wait=3)
    shot("huge_zoomed")
    back()
    back()

    # Slideshow from album menu
    tap("Wallpapers")
    time.sleep(1.5)
    tap("幻燈片")
    time.sleep(2)
    shot("slideshow_1")
    time.sleep(5)
    shot("slideshow_2")
    back()
    back()

    # Multi-select in album list: long press first album, hide it
    first = find("Big")
    if first:
        sh(f"input swipe {first[0]} {first[1]} {first[0]} {first[1]} 900")
        time.sleep(1.5)
        shot("album_selected")
        tap("更多")
        tap("詳細資訊")
        shot("album_details")
        tap("關閉")
        tap("隱藏")
        time.sleep(2)
        shot("after_hide")

    # Stress: switch drawer destinations and press back several times in a row.
    # A double back used to pop the root screen and leave a black, frozen app.
    for i, dest in enumerate(["所有媒體", "所有相片", "所有影片", "所有媒體", "設定", "隱藏的相簿"] * 2):
        tap("選單", wait=0.8)
        tap(dest, exact=True, wait=0.3, required=False)
        sh("input keyevent KEYCODE_BACK; input keyevent KEYCODE_BACK; input keyevent KEYCODE_BACK")
        time.sleep(1.2)
        sh(f"am start -n {PKG}/.MainActivity")  # back from the root may have left the app
        time.sleep(1.5)
        if not find("相簿", exact=True):
            failures.append(f"  !! albums screen missing after drawer stress round {i}")
            shot(f"stress_fail_{i}")
            break
    shot("after_drawer_stress")

    # Drawer
    tap("選單")
    shot("drawer")
    tap("隱藏的相簿")
    time.sleep(2)
    shot("hidden_albums")
    back()

    # Settings pages
    tap("選單")
    tap("設定", exact=True)
    shot("settings")
    for page in ["介面", "相簿", "檢視", "安全性", "幻燈片", "快取", "更新", "關於"]:
        if tap(page, exact=True, wait=1.5, required=False):
            shot(f"settings_{page}")
            if page == "關於":
                tap("QV Gallery", exact=True)
                shot("about_version_notes")
                tap("關閉")
            back()

    # Set a PIN, then hidden albums should require it
    tap("安全性", exact=True)
    tap("鎖定方式")
    tap("PIN 碼", exact=True)
    time.sleep(1)
    shot("pin_setup")
    for _ in range(2):
        for d in "1234":
            tap(d, exact=True, wait=0.3)
        tap("確定", exact=True, wait=1.5)
    shot("pin_set")
    back()
    back()
    sh(f"am force-stop {PKG}")
    sh(f"am start -W -n {PKG}/.MainActivity")
    time.sleep(4)
    tap("選單")
    tap("隱藏的相簿")
    time.sleep(1.5)
    shot("hidden_lock_screen")
    for d in "1234":
        tap(d, exact=True, wait=0.3)
    tap("確定", exact=True, wait=2)
    shot("hidden_unlocked")
    back()

    # Dark/light theme switch
    tap("選單")
    tap("設定", exact=True)
    tap("介面", exact=True)
    tap("主題模式")
    tap("淺色", exact=True)
    time.sleep(1)
    back()
    back()
    shot("light_theme_albums")

    # External VIEW intent (as from a file manager)
    out = sh("content query --uri content://media/external/images/media --projection _id:_data --where \"_data LIKE '%wall_3.jpg'\"")
    m = re.search(r"_id=(\d+)", out)
    if m:
        sh(f"am start -W -n {PKG}/.ViewActivity -a android.intent.action.VIEW -d content://media/external/images/media/{m.group(1)} -t image/jpeg")
        time.sleep(4)
        shot("external_view")
        sh(f"input swipe {int(W * 0.8)} {H // 2} {int(W * 0.2)} {H // 2} 250")
        time.sleep(2)
        shot("external_view_swiped")
        back()

    # Landscape
    sh("settings put system accelerometer_rotation 0")
    sh(f"am start -W -n {PKG}/.MainActivity")
    sh("settings put system user_rotation 1")
    time.sleep(3)
    shot("landscape_albums")
    sh("settings put system user_rotation 0")

    with open(os.path.join(OUT, "logcat.txt"), "w") as f:
        f.write(adb("logcat", "-d", "-v", "brief", "*:W"))
    with open(os.path.join(OUT, "crash.txt"), "w") as f:
        f.write(adb("logcat", "-d", "-b", "crash"))
    if crashed():
        failures.append("APP CRASHED (see crash.txt)")
    with open(os.path.join(OUT, "result.txt"), "w") as f:
        f.write("\n".join(failures) if failures else "OK")
    log("failures:\n" + "\n".join(failures))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        log(f"SCRIPT ERROR: {e!r}")
        with open(os.path.join(OUT, "crash.txt"), "w") as f:
            f.write(adb("logcat", "-d", "-b", "crash"))
        with open(os.path.join(OUT, "logcat.txt"), "w") as f:
            f.write(adb("logcat", "-d", "-v", "brief", "*:W"))
        raise
