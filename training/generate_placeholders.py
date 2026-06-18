"""Generate placeholder webp images for 111 preset recipes.

Hero images: 400×300, centered title text on colored background.
Thumbnail images: 120×90, smaller version.

Category color scheme:
  HOME  - warm orange (#FF9800)
  QUICK - green (#4CAF50)
  SOUP  - blue (#2196F3)
  DIET  - teal (#009688)
  COLD  - purple (#9C27B0)
"""

import json
import os
from PIL import Image, ImageDraw, ImageFont

# Paths
BASE_DIR = r"e:\freshscan"
JSON_PATH = os.path.join(BASE_DIR, "app", "src", "main", "assets", "recipes", "preset_recipes.json")
OUT_DIR = os.path.join(BASE_DIR, "app", "src", "main", "assets", "recipes")

# Category colors (Material Design 500)
CATEGORY_COLORS = {
    "HOME":  (255, 152, 0, 255),    # Orange
    "QUICK": (76, 175, 80, 255),    # Green
    "SOUP":  (33, 150, 243, 255),   # Blue
    "DIET":  (0, 150, 136, 255),    # Teal
    "COLD":  (156, 39, 176, 255),   # Purple
}

# Sizes
HERO_SIZE = (400, 300)
THUMB_SIZE = (120, 90)

# Font - try multiple Chinese fonts
FONT_SIZE_HERO = 28
FONT_SIZE_THUMB = 14

def find_chinese_font(size):
    """Try to find a Chinese-capable font at the given size."""
    font_names = [
        "C:/Windows/Fonts/msyh.ttc",       # Microsoft YaHei
        "C:/Windows/Fonts/msyhbd.ttc",     # Microsoft YaHei Bold
        "C:/Windows/Fonts/simhei.ttf",     # SimHei
        "C:/Windows/Fonts/simsun.ttc",     # SimSun
        "C:/Windows/Fonts/mingliu.ttc",    # MingLiU
        "C:/Windows/Fonts/msjh.ttc",       # Microsoft JhengHei
    ]
    for font_name in font_names:
        if os.path.exists(font_name):
            try:
                return ImageFont.truetype(font_name, size)
            except Exception:
                continue
    # Fallback to default
    print("WARNING: No Chinese font found, text will be missing")
    return ImageFont.load_default()

def create_placeholder(title: str, category: str, size: tuple, font_size: int) -> Image.Image:
    """Create a placeholder image with colored background and centered title."""
    bg_color = CATEGORY_COLORS.get(category, (158, 158, 158, 255))

    # Create image with RGBA
    img = Image.new("RGBA", size, bg_color)
    draw = ImageDraw.Draw(img)

    # Add a subtle pattern (rounded rectangle border)
    w, h = size
    margin = 8
    draw.rectangle(
        [margin, margin, w - margin - 1, h - margin - 1],
        outline=(255, 255, 255, 60),
        width=2
    )

    # Add a food emoji or icon circle at top
    circle_y = h // 3
    circle_r = min(w, h) // 6
    draw.ellipse(
        [w // 2 - circle_r, circle_y - circle_r, w // 2 + circle_r, circle_y + circle_r],
        fill=(255, 255, 255, 40)
    )

    # Draw food-related icon (simple leaf/fruit shape)
    icon_cx, icon_cy = w // 2, circle_y
    icon_s = circle_r // 2
    # Simple leaf shape
    draw.ellipse(
        [icon_cx - icon_s, icon_cy - icon_s, icon_cx + icon_s, icon_cy + icon_s],
        fill=(255, 255, 255, 180)
    )

    # Draw title text
    font = find_chinese_font(font_size)
    text_bbox = draw.textbbox((0, 0), title, font=font)
    text_w = text_bbox[2] - text_bbox[0]
    text_h = text_bbox[3] - text_bbox[1]

    text_x = (w - text_w) // 2
    text_y = h * 2 // 3 - text_h // 2

    # Text shadow
    draw.text((text_x + 1, text_y + 1), title, fill=(0, 0, 0, 100), font=font)
    # Text
    draw.text((text_x, text_y), title, fill=(255, 255, 255, 240), font=font)

    return img

def main():
    with open(JSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    recipes = data["recipes"]
    print(f"Generating placeholder images for {len(recipes)} recipes...")

    hero_count = 0
    thumb_count = 0

    for r in recipes:
        title = r["title"]
        category = r.get("category", "HOME")
        image_asset = r.get("imageAsset", "")
        thumb_asset = r.get("thumbnailAsset", "")

        # Generate hero image
        if image_asset:
            hero_path = os.path.join(OUT_DIR, image_asset)
            if not os.path.exists(hero_path):
                img = create_placeholder(title, category, HERO_SIZE, FONT_SIZE_HERO)
                img.save(hero_path, "WEBP", quality=75)
                hero_count += 1

        # Generate thumbnail
        if thumb_asset:
            thumb_path = os.path.join(OUT_DIR, thumb_asset)
            if not os.path.exists(thumb_path):
                img = create_placeholder(title, category, THUMB_SIZE, FONT_SIZE_THUMB)
                img.save(thumb_path, "WEBP", quality=60)
                thumb_count += 1

    print(f"Done! Generated {hero_count} hero images, {thumb_count} thumbnails.")

    # Print total size
    total_size = 0
    for f in os.listdir(OUT_DIR):
        if f.endswith(".webp"):
            total_size += os.path.getsize(os.path.join(OUT_DIR, f))
    print(f"Total webp size: {total_size / 1024:.1f} KB")

if __name__ == "__main__":
    main()
