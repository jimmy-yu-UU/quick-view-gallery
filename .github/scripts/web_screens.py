#!/usr/bin/env python3
"""Logs into the transfer web page with headless Chromium and screenshots it. Usage: web_screens.py <port> <code> <out>"""
import sys
from playwright.sync_api import sync_playwright

port, code, out = sys.argv[1], sys.argv[2], sys.argv[3]
with sync_playwright() as p:
    b = p.chromium.launch()
    page = b.new_page(viewport={"width": 1280, "height": 800}, locale="zh-TW")
    page.goto(f"http://127.0.0.1:{port}/")
    page.screenshot(path=f"{out}/web_1_login.png")
    page.fill("#code", "000000")
    page.click("#loginBtn")
    page.wait_for_timeout(800)
    page.screenshot(path=f"{out}/web_2_wrong_code.png")
    page.fill("#code", code)
    page.click("#loginBtn")
    page.wait_for_selector("#grid .tile", timeout=15000)
    page.wait_for_timeout(1500)
    page.screenshot(path=f"{out}/web_3_albums.png")
    page.locator("#grid .tile", has_text="Wallpapers").click()
    page.wait_for_timeout(2000)
    page.locator("#grid .tile input").first.check()
    page.wait_for_timeout(300)
    page.screenshot(path=f"{out}/web_4_album.png")
    page.locator("#grid .tile img").nth(1).click()
    page.wait_for_timeout(1500)
    page.screenshot(path=f"{out}/web_5_viewer.png")
    b.close()
