#!/usr/bin/env python3
"""
Download Inter font family for iTantra.
Fonts are placed in app/src/main/res/font/
"""
import os
import urllib.request

FONTS = {
    "inter_regular": "https://github.com/rsms/inter/raw/master/docs/font-files/Inter-Regular.ttf",
    "inter_medium": "https://github.com/rsms/inter/raw/master/docs/font-files/Inter-Medium.ttf",
    "inter_semibold": "https://github.com/rsms/inter/raw/master/docs/font-files/Inter-SemiBold.ttf",
    "inter_bold": "https://github.com/rsms/inter/raw/master/docs/font-files/Inter-Bold.ttf",
}

font_dir = os.path.join(os.path.dirname(__file__), "../app/src/main/res/font")
os.makedirs(font_dir, exist_ok=True)

for name, url in FONTS.items():
    dest = os.path.join(font_dir, f"{name}.ttf")
    if os.path.exists(dest):
        print(f"  Already exists: {name}.ttf")
        continue
    print(f"Downloading {name}...")
    try:
        urllib.request.urlretrieve(url, dest)
        size = os.path.getsize(dest) / 1024
        print(f"  ✅ {name}.ttf ({size:.0f}KB)")
    except Exception as e:
        print(f"  ❌ Failed: {e}")

print("\nDone. Fonts in:", font_dir)
