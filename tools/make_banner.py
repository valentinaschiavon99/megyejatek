#!/usr/bin/env python3
"""Builds docs/banner.png (1280×640, GitHub's social preview size) from the README screenshots.

Usage: python3 tools/make_banner.py   (needs Pillow; run make_icon.py --preview first for the icon)
"""
import pathlib
import subprocess
import tempfile

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
SHOTS = ROOT / "docs/screenshots"
FONTS = ROOT / "app/src/main/res/font"
OUT = ROOT / "docs/banner.png"

INK, INK2, PAPER, AMBER = (27, 42, 68), (44, 65, 102), (247, 241, 228), (224, 154, 45)
W, H = 1280, 640


def font(name, size, weight):
    f = ImageFont.truetype(str(FONTS / name), size)
    try:
        axes = f.get_variation_axes()
        f.set_variation_by_axes([weight if a["name"] in (b"Weight", "Weight") else a["default"] for a in axes])
    except OSError:
        pass
    return f


def gradient():
    img = Image.new("RGB", (W, H), INK)
    px = img.load()
    for y in range(H):
        for x in range(W):
            t = (x / W + y / H) / 2
            px[x, y] = tuple(int(INK2[i] * (1 - t) + INK[i] * t) for i in range(3))
    return img


def phone(shot: Image.Image, height: int) -> Image.Image:
    """The screenshot with rounded corners, a thin dark bezel and a soft shadow."""
    w = int(shot.width * height / shot.height)
    shot = shot.resize((w, height), Image.LANCZOS)
    r = int(w * 0.09)
    bezel = 10
    body = Image.new("RGBA", (w + 2 * bezel, height + 2 * bezel), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle([0, 0, body.width - 1, body.height - 1], r + bezel, fill=(14, 18, 26, 255))
    mask = Image.new("L", (w, height), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, height - 1], r, fill=255)
    body.paste(shot, (bezel, bezel), mask)
    pad = 40
    out = Image.new("RGBA", (body.width + 2 * pad, body.height + 2 * pad), (0, 0, 0, 0))
    shadow = Image.new("RGBA", body.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([0, 0, body.width - 1, body.height - 1], r + bezel, fill=(0, 0, 0, 150))
    out.paste(shadow, (pad + 6, pad + 14), shadow)
    out = out.filter(ImageFilter.GaussianBlur(14))
    out.alpha_composite(body, (pad, pad))
    return out


def main():
    img = gradient().convert("RGBA")
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([700, -120, 1400, 760], fill=(255, 255, 255, 18))
    img.alpha_composite(glow)

    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["python3", str(ROOT / "tools/make_icon.py"), "--preview", tmp], check=True)
        icon = Image.open(pathlib.Path(tmp) / "icon.png").convert("RGBA").resize((112, 112), Image.LANCZOS)
    img.alpha_composite(icon, (80, 92))

    d = ImageDraw.Draw(img)
    d.text((76, 220), "Megyejáték", font=font("fraunces.ttf", 104, 700), fill=PAPER)
    d.text((82, 352), "Hungary's 19 counties and Budapest", font=font("manrope.ttf", 34, 600), fill=PAPER)
    d.text((82, 398), "on a blind map. Tap, learn, beat your record.", font=font("manrope.ttf", 34, 500), fill=(200, 210, 228))
    tag = font("manrope.ttf", 24, 700)
    chips = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    c = ImageDraw.Draw(chips)
    x = 82
    for label in ["Android", "Kotlin", "Compose", "Open data"]:
        tw = c.textlength(label, font=tag)
        first = label == "Android"
        c.rounded_rectangle([x, 480, x + tw + 32, 524], 22, fill=AMBER if first else (255, 255, 255, 34))
        c.text((x + 16, 487), label, font=tag, fill=INK if first else PAPER)
        x += tw + 44
    img.alpha_composite(chips)

    back = phone(Image.open(SHOTS / "02-counties.png").convert("RGB"), 520)
    front = phone(Image.open(SHOTS / "06-result.png").convert("RGB"), 470)
    img.alpha_composite(back, (W - back.width - 150, 40))
    img.alpha_composite(front, (W - front.width + 10, 120))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(OUT, optimize=True)
    print(OUT)


if __name__ == "__main__":
    main()
