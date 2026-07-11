#!/usr/bin/env python3
"""根据源图片生成 Android 应用图标资源。"""

import os
import shutil
from pathlib import Path
from PIL import Image, ImageDraw

SRC_IMAGE = r"C:\Users\angel\Pictures\blog\favicon.jpg"
RES_DIR = Path(__file__).parent.parent / "app" / "src" / "main" / "res"

# 各密度下 launcher 图标尺寸（dp 与 px 换算基于 1dp = density 个像素）
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# 自适应图标前景在 108dp 画布中的安全区域（66dp 直径的圆）
ADAPTIVE_SIZE = 108
# 我们将图片缩放到占满 72dp 的区域，留出边距
ADAPTIVE_FOREGROUND_CONTENT = 72


def make_square(src: Image.Image) -> Image.Image:
    """将图片居中裁剪为正方形。"""
    w, h = src.size
    size = min(w, h)
    left = (w - size) // 2
    top = (h - size) // 2
    return src.crop((left, top, left + size, top + size))


def make_round(src: Image.Image, size: int) -> Image.Image:
    """生成圆形图标。"""
    img = src.resize((size, size), Image.Resampling.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size, size), fill=255)
    out = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    out.paste(img.convert("RGBA"), (0, 0), mask)
    return out


def save_icon(img: Image.Image, path: Path):
    """保存为 PNG。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "PNG")


def generate_legacy_icons(src: Image.Image):
    """生成传统 mipmap 图标。"""
    square = make_square(src)
    for density, size in DENSITIES.items():
        mipmap_dir = RES_DIR / f"mipmap-{density}"
        icon = square.resize((size, size), Image.Resampling.LANCZOS)
        save_icon(icon.convert("RGBA"), mipmap_dir / "ic_launcher.png")
        round_icon = make_round(square, size)
        save_icon(round_icon, mipmap_dir / "ic_launcher_round.png")
        print(f"Generated mipmap-{density} icons ({size}x{size})")


def generate_adaptive_foreground(src: Image.Image):
    """生成自适应图标前景位图（居中缩放，留边距）。"""
    square = make_square(src)
    # 在 drawable-nodpi 中放置高密度版本（xxxhdpi 尺度）
    fg_size = DENSITIES["xxxhdpi"]  # 192px
    # 内容占 2/3，留出 1/6 边距
    content_size = fg_size * 2 // 3
    margin = (fg_size - content_size) // 2

    resized = square.resize((content_size, content_size), Image.Resampling.LANCZOS)
    foreground = Image.new("RGBA", (fg_size, fg_size), (255, 255, 255, 0))
    foreground.paste(resized, (margin, margin), resized)

    nodpi_dir = RES_DIR / "drawable-nodpi"
    nodpi_dir.mkdir(parents=True, exist_ok=True)
    save_icon(foreground, nodpi_dir / "ic_launcher_foreground.png")
    print(f"Generated drawable-nodpi/ic_launcher_foreground.png ({fg_size}x{fg_size})")


def generate_adaptive_xmls():
    """生成自适应图标 XML 文件。"""
    anydpi_dir = RES_DIR / "mipmap-anydpi-v26"
    anydpi_dir.mkdir(parents=True, exist_ok=True)

    launcher_xml = anydpi_dir / "ic_launcher.xml"
    launcher_xml.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_background"/>\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
        '</adaptive-icon>\n',
        encoding="utf-8",
    )
    print(f"Generated {launcher_xml}")

    bg_xml = RES_DIR / "drawable" / "ic_launcher_background.xml"
    bg_xml.parent.mkdir(parents=True, exist_ok=True)
    # 使用与图片主色调协调的浅紫/粉色
    bg_xml.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<shape xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:shape="rectangle">\n'
        '    <solid android:color="#FFF0F5"/>\n'
        '</shape>\n',
        encoding="utf-8",
    )
    print(f"Generated {bg_xml}")


def main():
    src_path = Path(SRC_IMAGE)
    if not src_path.exists():
        print(f"Source image not found: {src_path}")
        return 1

    src = Image.open(src_path).convert("RGBA")
    print(f"Loaded source image: {src.size}")

    generate_legacy_icons(src)
    generate_adaptive_foreground(src)
    generate_adaptive_xmls()

    print("\nApp icon resources generated successfully.")
    print("Please update AndroidManifest.xml to set android:icon and android:roundIcon.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
