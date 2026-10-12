import os
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont

BASE = 512
GOLD_L = (255, 235, 175)
GOLD = (255, 210, 111)
GOLD_D = (196, 160, 92)
NAVY_T = (52, 48, 78)
NAVY_B = (17, 15, 26)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def background(S):
    """深蓝紫纵向渐变 + 中心金色辉光"""
    y = np.linspace(0, 1, S)[:, None]
    top = np.array(NAVY_T, dtype=float)
    bot = np.array(NAVY_B, dtype=float)
    grad = top[None, None, :] * (1 - y[:, :, None]) + bot[None, None, :] * y[:, :, None]
    grad = np.repeat(grad, S, axis=1)

    yy, xx = np.mgrid[0:S, 0:S]
    dist = np.hypot(xx - S / 2.0, yy - S / 2.0) / (S * 0.62)
    glow = np.clip(1.0 - dist, 0, 1) ** 2 * 0.42
    gold = np.array(GOLD, dtype=float)
    out = grad * (1 - glow[:, :, None]) + gold[None, None, :] * glow[:, :, None]
    return Image.fromarray(out.astype("uint8"), "RGB").convert("RGBA")


def diamond(cx, cy, r, ratio=1.0):
    """菱形四顶点（ratio<1 变扁）"""
    return [(cx, cy - r * ratio), (cx + r, cy), (cx, cy + r * ratio), (cx - r, cy)]


def small_diamond(cx, cy, r):
    return [(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)]


def find_font():
    for fp in [
        r"C:\Windows\Fonts\georgiab.ttf",
        r"C:\Windows\Fonts\timesbd.ttf",
        r"C:\Windows\Fonts\georgia.ttf",
        r"C:\Windows\Fonts\arialbd.ttf",
    ]:
        if os.path.exists(fp):
            return fp
    return None


def make_icon(S=BASE):
    img = background(S)
    d = ImageDraw.Draw(img)

    cx = cy = S / 2.0
    w_thick = max(2, int(S * 0.020))
    w_thin = max(1, int(S * 0.008))

    # ---------- 中央大菱形（双层金边）----------
    R = S * 0.335
    outer = diamond(cx, cy, R)
    # 外发光
    glow_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow_layer)
    for k in range(10, 0, -1):
        gd.polygon(diamond(cx, cy, R + k * S * 0.009),
                   outline=(255, 210, 111, int(90 / k)))
    img = Image.alpha_composite(img, glow_layer)
    d = ImageDraw.Draw(img)

    # 菱形内部：深色填充
    d.polygon(outer, fill=(26, 22, 40, 235))
    # 内层渐变描边（金→暗金）
    d.polygon(outer, outline=GOLD_D, width=w_thick + 2)
    d.polygon(outer, outline=GOLD, width=w_thick)
    # 细内边
    inner = diamond(cx, cy, R * 0.86)
    d.polygon(inner, outline=(255, 226, 150, 170), width=w_thin)

    # ---------- 中央文字 AI ----------
    fp = find_font()
    fs = int(S * 0.252)
    font = ImageFont.truetype(fp, fs) if fp else ImageFont.load_default()
    text = "AI"
    bbox = d.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    tx = cx - tw / 2 - bbox[0]
    ty = cy - th / 2 - bbox[1]
    # 文字外发光
    glow2 = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    g2 = ImageDraw.Draw(glow2)
    for dx in range(-4, 5, 2):
        for dy in range(-4, 5, 2):
            g2.text((tx + dx, ty + dy), text, font=font, fill=(255, 210, 111, 55))
    img = Image.alpha_composite(img, glow2)
    d = ImageDraw.Draw(img)

    # 描边 + 金色主体
    for dx, dy in [(-2, 0), (2, 0), (0, -2), (0, 2)]:
        d.text((tx + dx, ty + dy), text, font=font, fill=(90, 70, 30, 255))
    d.text((tx, ty), text, font=font, fill=GOLD_L)

    # ---------- 菱形上下的小装饰点 ----------
    for sign in (-1, 1):
        yy = cy + sign * R * 1.30
        if 0 < yy < S:
            d.polygon(small_diamond(cx, yy, S * 0.021), fill=GOLD)
            d.polygon(small_diamond(cx, yy, S * 0.010), fill=GOLD_L)

    # ---------- 四角宝石 ----------
    off = S * 0.115
    for (px, py) in [(off, off), (S - off, off), (off, S - off), (S - off, S - off)]:
        r = S * 0.030
        d.polygon(small_diamond(px, py, r + 2), fill=(60, 50, 26, 220))
        d.polygon(small_diamond(px, py, r), fill=GOLD)
        d.polygon(small_diamond(px, py, r * 0.45), fill=GOLD_L)

    # ---------- 外框金边 ----------
    m = S * 0.045
    d.rounded_rectangle([m, m, S - m, S - m], radius=S * 0.20,
                        outline=(211, 188, 142, 200), width=max(2, int(S * 0.012)))

    # ---------- 圆角遮罩 ----------
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1],
                                           radius=int(S * 0.22), fill=255)
    img.putalpha(mask)
    return img


def main():
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "res")
    base = make_icon(BASE)
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, px in densities.items():
        out = os.path.join(root, folder)
        os.makedirs(out, exist_ok=True)
        base.resize((px, px), Image.LANCZOS).save(os.path.join(out, "ic_launcher.png"))
        print("written:", os.path.join(out, "ic_launcher.png"), px)
    # 预览大图（放在 res/ 之外，避免被当作资源编译）
    prev = os.path.join(os.path.dirname(os.path.abspath(__file__)), "icon-preview-512.png")
    base.save(prev)
    print("preview:", prev)


if __name__ == "__main__":
    main()
