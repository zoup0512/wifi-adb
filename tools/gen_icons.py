# -*- coding: utf-8 -*-
"""生成各密度的启动图标（mipmap PNG，供 Android 7.x 及以下使用）。"""
import os

from PIL import Image, ImageDraw

SIZE = 512
BG = (16, 27, 36, 255)          # 深蓝黑，与 ic_launcher_background 一致
GREEN = (78, 216, 143, 255)     # #4ED88F

BASE = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")

img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=int(SIZE * 0.22), fill=BG)

cx, cy = SIZE / 2, SIZE * 0.70
line_w = int(SIZE * 0.055)
for r in (SIZE * 0.14, SIZE * 0.27, SIZE * 0.40):
    d.arc([cx - r, cy - r, cx + r, cy + r], start=205, end=325, fill=GREEN, width=line_w)
dot_r = SIZE * 0.075
d.ellipse([cx - dot_r, cy - dot_r, cx + dot_r, cy + dot_r], fill=GREEN)

round_mask = Image.new("L", (SIZE, SIZE), 0)
ImageDraw.Draw(round_mask).ellipse([0, 0, SIZE - 1, SIZE - 1], fill=255)
round_img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
round_img.paste(img, (0, 0), round_mask)

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for dpi, px in DENSITIES.items():
    folder = os.path.join(BASE, "mipmap-%s" % dpi)
    os.makedirs(folder, exist_ok=True)
    img.resize((px, px), Image.Resampling.LANCZOS).save(os.path.join(folder, "ic_launcher.png"))
    round_img.resize((px, px), Image.Resampling.LANCZOS).save(
        os.path.join(folder, "ic_launcher_round.png"))
    print("done:", dpi, px)
