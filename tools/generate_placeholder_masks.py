#!/usr/bin/env python3
"""Genera las máscaras de ejemplo (robot y esqueleto) y los iconos.

Arte original geométrico, sin personajes con copyright. Los PNG y rig.json
quedan en el repositorio; este script solo hace falta para regenerarlos.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
MASKS = ROOT / "app" / "src" / "main" / "assets" / "masks"
RES = ROOT / "app" / "src" / "main" / "res"
FONT_BOLD = "/usr/share/fonts/truetype/macos/Inter-Bold.ttf"
FONT_REGULAR = "/usr/share/fonts/truetype/macos/Inter-Regular.ttf"

SLATE = (36, 48, 64, 255)
SLATE_DARK = (18, 24, 34, 255)
METAL = (78, 104, 128, 255)
METAL_LIGHT = (168, 196, 214, 255)
VISOR = (64, 214, 214, 255)
VISOR_DARK = (18, 92, 112, 255)
AMBER = (242, 164, 62, 255)
OUTLINE = (10, 14, 20, 255)
BONE = (236, 228, 210, 255)
BONE_SHADE = (186, 170, 146, 255)
BONE_LINE = (72, 54, 44, 255)


def new_canvas(width: int, height: int) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    return image, ImageDraw.Draw(image)


def punch(image: Image.Image, box: list[int]) -> Image.Image:
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).ellipse(box, fill=255)
    red, green, blue, alpha = image.split()
    alpha = ImageChops.multiply(alpha, ImageChops.invert(mask))
    return Image.merge("RGBA", (red, green, blue, alpha))


def save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG")


def capsule(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], fill, outline, width: int = 6) -> None:
    radius = (box[2] - box[0]) // 2
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def robot_head() -> tuple[Image.Image, float, float]:
    width, height = 400, 440
    image, draw = new_canvas(width, height)
    draw.line([(150, 78), (118, 18)], fill=OUTLINE, width=14)
    draw.line([(250, 78), (282, 18)], fill=OUTLINE, width=14)
    draw.ellipse((100, 2, 136, 38), fill=AMBER, outline=OUTLINE, width=4)
    draw.ellipse((264, 2, 300, 38), fill=AMBER, outline=OUTLINE, width=4)
    draw.rounded_rectangle((36, 70, 364, 410), radius=86, fill=SLATE, outline=OUTLINE, width=8)
    draw.ellipse((18, 150, 78, 250), fill=METAL, outline=OUTLINE, width=6)
    draw.ellipse((322, 150, 382, 250), fill=METAL, outline=OUTLINE, width=6)
    draw.rounded_rectangle((78, 168, 322, 268), radius=36, fill=VISOR_DARK, outline=OUTLINE, width=6)
    draw.rounded_rectangle((96, 184, 304, 248), radius=28, fill=VISOR)
    draw.polygon([(118, 196), (210, 196), (168, 230)], fill=(210, 255, 255, 180))
    draw.rectangle((150, 300, 250, 318), fill=METAL_LIGHT)
    draw.rectangle((164, 332, 188, 348), fill=AMBER)
    draw.rectangle((212, 332, 236, 348), fill=AMBER)
    # El visor queda sobre la nariz.
    return image, 0.5, 216 / height


def robot_torso() -> tuple[Image.Image, float, float]:
    width, height = 420, 480
    image, draw = new_canvas(width, height)
    draw.polygon([(40, 36), (380, 36), (340, 450), (80, 450)], fill=SLATE, outline=OUTLINE)
    draw.line([(40, 36), (380, 36), (340, 450), (80, 450), (40, 36)], fill=OUTLINE, width=8)
    draw.rounded_rectangle((110, 90, 310, 250), radius=18, fill=SLATE_DARK, outline=METAL, width=4)
    cx, cy, radius = 210, 168, 36
    hexagon = []
    for index in range(6):
        angle = math.radians(60 * index - 30)
        hexagon.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    draw.polygon(hexagon, fill=AMBER, outline=OUTLINE)
    draw.rectangle((150, 290, 270, 318), fill=VISOR)
    draw.rectangle((70, 24, 160, 52), fill=METAL, outline=OUTLINE, width=4)
    draw.rectangle((260, 24, 350, 52), fill=METAL, outline=OUTLINE, width=4)
    return image, 0.5, 40 / height


def robot_pelvis() -> tuple[Image.Image, float, float]:
    width, height = 360, 160
    image, draw = new_canvas(width, height)
    draw.polygon([(30, 24), (330, 24), (300, 140), (60, 140)], fill=SLATE, outline=OUTLINE)
    draw.line([(30, 24), (330, 24), (300, 140), (60, 140), (30, 24)], fill=OUTLINE, width=8)
    draw.ellipse((150, 48, 210, 108), fill=AMBER, outline=OUTLINE, width=4)
    return image, 0.5, 36 / height


def robot_limb(width: int, height: int, shaft: int, hand: bool) -> tuple[Image.Image, float, float]:
    image, draw = new_canvas(width, height)
    left = (width - shaft) // 2
    right = left + shaft
    top = 28
    bottom = height - (64 if hand else 28)
    capsule(draw, (left, top, right, bottom), METAL, OUTLINE, 6)
    joint = 34
    draw.ellipse((width // 2 - joint, 8, width // 2 + joint, 8 + joint * 2), fill=SLATE, outline=OUTLINE, width=6)
    draw.ellipse((width // 2 - 12, 28, width // 2 + 12, 52), fill=AMBER)
    if hand:
        palm_top = height - 78
        draw.rounded_rectangle((left - 6, palm_top, right + 6, height - 16), radius=16, fill=SLATE, outline=OUTLINE, width=5)
        finger_w = max(8, shaft // 5)
        gap = 6
        total = finger_w * 3 + gap * 2
        start = (width - total) // 2
        for index in range(3):
            x = start + index * (finger_w + gap)
            draw.rounded_rectangle((x, height - 48, x + finger_w, height - 8), radius=4, fill=METAL, outline=OUTLINE, width=3)
    else:
        end_r = shaft // 2 - 4
        draw.ellipse(
            (width // 2 - end_r, bottom - end_r, width // 2 + end_r, bottom + end_r // 2),
            fill=SLATE,
            outline=OUTLINE,
            width=5,
        )
    return image, 0.5, (8 + joint) / height


def robot_leg(width: int, height: int, shaft: int, boot: bool) -> tuple[Image.Image, float, float]:
    image, draw = new_canvas(width, height)
    left = (width - shaft) // 2
    bottom = height - (70 if boot else 24)
    capsule(draw, (left, 24, left + shaft, bottom), SLATE, OUTLINE, 6)
    joint = shaft // 2 + 4
    draw.ellipse((width // 2 - joint, 6, width // 2 + joint, 6 + joint * 2), fill=METAL, outline=OUTLINE, width=6)
    if boot:
        draw.rounded_rectangle((left - 18, height - 84, left + shaft + 22, height - 10), radius=16, fill=SLATE_DARK, outline=OUTLINE, width=6)
        draw.rectangle((left - 8, height - 36, left + shaft + 14, height - 18), fill=AMBER)
    return image, 0.5, (6 + joint) / height


def bone_limb(width: int, height: int) -> tuple[Image.Image, float, float]:
    image, draw = new_canvas(width, height)
    top_r = width // 2 - 8
    bot_r = width // 2 - 16
    cy_top = top_r + 6
    cy_bot = height - bot_r - 6
    draw.rounded_rectangle(
        (width // 2 - width // 5, cy_top, width // 2 + width // 5, cy_bot),
        radius=width // 5,
        fill=BONE,
        outline=BONE_LINE,
        width=5,
    )
    draw.ellipse((width // 2 - top_r, cy_top - top_r, width // 2 + top_r, cy_top + top_r), fill=BONE, outline=BONE_LINE, width=5)
    draw.ellipse((width // 2 - bot_r, cy_bot - bot_r, width // 2 + bot_r, cy_bot + bot_r), fill=BONE_SHADE, outline=BONE_LINE, width=5)
    return image, 0.5, cy_top / height


def skull() -> tuple[Image.Image, float, float]:
    width, height = 360, 400
    image, draw = new_canvas(width, height)
    draw.ellipse((40, 24, 320, 300), fill=BONE, outline=BONE_LINE, width=8)
    draw.rounded_rectangle((108, 230, 252, 360), radius=28, fill=BONE, outline=BONE_LINE, width=6)
    for x in range(128, 240, 22):
        draw.rectangle((x, 300, x + 10, 340), fill=BONE_SHADE)
    image = punch(image, [78, 120, 168, 210])
    image = punch(image, [192, 120, 282, 210])
    image = punch(image, [156, 200, 204, 260])
    # La nariz del esqueleto cae cerca del hueco central.
    return image, 0.5, 230 / height


def ribs() -> tuple[Image.Image, float, float]:
    width, height = 400, 460
    image, draw = new_canvas(width, height)
    draw.rounded_rectangle((184, 16, 216, 430), radius=16, fill=BONE, outline=BONE_LINE, width=5)
    for index, y in enumerate(range(70, 360, 48)):
        inset = 36 + index * 8
        draw.arc((inset, y, width - inset, y + 90), start=200, end=340, fill=BONE_LINE, width=10)
        draw.arc((inset + 8, y + 6, width - inset - 8, y + 84), start=200, end=340, fill=BONE, width=6)
    return image, 0.5, 24 / height


def part(
    part_id: str,
    src: str,
    z_index: int,
    anchor: str,
    aim_from: str,
    aim_to: str,
    size_from: str,
    size_to: str,
    pivot: tuple[float, float],
    length_scale: float,
    width_scale: float,
    min_visibility: float | None = None,
) -> dict:
    data = {
        "id": part_id,
        "src": src,
        "zIndex": z_index,
        "anchor": anchor,
        "aimFrom": aim_from,
        "aimTo": aim_to,
        "sizeFrom": size_from,
        "sizeTo": size_to,
        "pivot": {"x": round(pivot[0], 4), "y": round(pivot[1], 4)},
        "lengthScale": length_scale,
        "widthScale": width_scale,
    }
    if min_visibility is not None:
        data["minVisibility"] = min_visibility
    return data


def limb_pair(name: str, src: str, z_index: int, proximal: str, distal: str, other_prox: str, other_dist: str, pivot, length, width, vis) -> list[dict]:
    return [
        part(f"left_{name}", src, z_index, proximal, proximal, distal, proximal, distal, pivot, length, width, vis),
        part(f"right_{name}", src, z_index, other_prox, other_prox, other_dist, other_prox, other_dist, pivot, length, width, vis),
    ]


def write_rig(folder: str, payload: dict) -> None:
    path = MASKS / folder / "rig.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def build_robot() -> None:
    folder = MASKS / "robot"
    head, hx, hy = robot_head()
    torso, tx, ty = robot_torso()
    pelvis, px, py = robot_pelvis()
    upper, ux, uy = robot_limb(150, 360, 78, hand=False)
    forearm, fx, fy = robot_limb(140, 340, 64, hand=True)
    thigh, thx, thy = robot_leg(170, 380, 96, boot=False)
    shin, sx, sy = robot_leg(160, 360, 80, boot=True)
    save(head, folder / "head.png")
    save(torso, folder / "torso.png")
    save(pelvis, folder / "pelvis.png")
    save(upper, folder / "upper_arm.png")
    save(forearm, folder / "forearm.png")
    save(thigh, folder / "thigh.png")
    save(shin, folder / "shin.png")
    parts = []
    parts += limb_pair("thigh", "thigh.png", 10, "LEFT_HIP", "LEFT_KNEE", "RIGHT_HIP", "RIGHT_KNEE", (thx, thy), 1.12, 1.0, 0.5)
    parts += limb_pair("shin", "shin.png", 12, "LEFT_KNEE", "LEFT_ANKLE", "RIGHT_KNEE", "RIGHT_ANKLE", (sx, sy), 1.18, 1.0, 0.5)
    parts.append(
        part("pelvis", "pelvis.png", 16, "MID_HIPS", "MID_SHOULDERS", "MID_HIPS", "LEFT_HIP", "RIGHT_HIP", (px, py), 0.85, 1.15, 0.5)
    )
    parts.append(
        part("torso", "torso.png", 20, "MID_SHOULDERS", "NOSE", "MID_SHOULDERS", "LEFT_SHOULDER", "RIGHT_SHOULDER", (tx, ty), 1.5, 1.12, 0.4)
    )
    parts += limb_pair("upper_arm", "upper_arm.png", 30, "LEFT_SHOULDER", "LEFT_ELBOW", "RIGHT_SHOULDER", "RIGHT_ELBOW", (ux, uy), 1.16, 1.0, 0.4)
    parts += limb_pair("forearm", "forearm.png", 34, "LEFT_ELBOW", "LEFT_WRIST", "RIGHT_ELBOW", "RIGHT_WRIST", (fx, fy), 1.22, 1.0, 0.4)
    parts.append(
        part("head", "head.png", 40, "NOSE", "NOSE", "MID_SHOULDERS", "LEFT_SHOULDER", "RIGHT_SHOULDER", (hx, hy), 1.15, 1.08, 0.4)
    )
    write_rig(
        "robot",
        {
            "formatVersion": 1,
            "id": "robot",
            "name": "Robot",
            "description": "Traje robot original. La cabeza y el torso siguen nariz y hombros; brazos y piernas siguen sus articulaciones cuando se ven.",
            "defaults": {"minVisibility": 0.5},
            "parts": parts,
        },
    )


def build_skeleton() -> None:
    folder = MASKS / "esqueleto"
    head, hx, hy = skull()
    torso, tx, ty = ribs()
    limb, lx, ly = bone_limb(120, 320)
    save(head, folder / "skull.png")
    save(torso, folder / "ribs.png")
    save(limb, folder / "bone.png")
    parts = []
    parts += limb_pair("thigh", "bone.png", 10, "LEFT_HIP", "LEFT_KNEE", "RIGHT_HIP", "RIGHT_KNEE", (lx, ly), 1.08, 1.15, 0.5)
    parts += limb_pair("shin", "bone.png", 12, "LEFT_KNEE", "LEFT_ANKLE", "RIGHT_KNEE", "RIGHT_ANKLE", (lx, ly), 1.08, 0.95, 0.5)
    parts.append(
        part("torso", "ribs.png", 20, "MID_SHOULDERS", "NOSE", "MID_SHOULDERS", "LEFT_SHOULDER", "RIGHT_SHOULDER", (tx, ty), 1.55, 1.05, 0.4)
    )
    parts += limb_pair("upper_arm", "bone.png", 30, "LEFT_SHOULDER", "LEFT_ELBOW", "RIGHT_SHOULDER", "RIGHT_ELBOW", (lx, ly), 1.1, 0.85, 0.4)
    parts += limb_pair("forearm", "bone.png", 34, "LEFT_ELBOW", "LEFT_WRIST", "RIGHT_ELBOW", "RIGHT_WRIST", (lx, ly), 1.1, 0.72, 0.4)
    parts.append(
        part("head", "skull.png", 40, "NOSE", "NOSE", "MID_SHOULDERS", "LEFT_SHOULDER", "RIGHT_SHOULDER", (hx, hy), 1.05, 1.0, 0.4)
    )
    write_rig(
        "esqueleto",
        {
            "formatVersion": 1,
            "id": "esqueleto",
            "name": "Esqueleto",
            "description": "Disfraz de esqueleto geométrico, sin referencia a una obra concreta. Sirve para comprobar que el rig se puede cambiar.",
            "defaults": {"minVisibility": 0.5},
            "parts": parts,
        },
    )


def draw_helmet_icon(size: int) -> Image.Image:
    image = Image.new("RGBA", (size, size), (16, 22, 32, 255))
    draw = ImageDraw.Draw(image)
    margin = int(size * 0.08)
    draw.rounded_rectangle((margin, margin, size - margin, size - margin), radius=int(size * 0.22), fill=(24, 40, 54, 255))
    draw.rounded_rectangle(
        (int(size * 0.22), int(size * 0.24), int(size * 0.78), int(size * 0.8)),
        radius=int(size * 0.16),
        fill=SLATE,
        outline=OUTLINE,
        width=max(2, size // 48),
    )
    draw.rounded_rectangle(
        (int(size * 0.3), int(size * 0.42), int(size * 0.7), int(size * 0.58)),
        radius=int(size * 0.06),
        fill=VISOR,
    )
    r = max(3, size // 28)
    draw.ellipse((int(size * 0.34) - r, int(size * 0.16) - r, int(size * 0.34) + r, int(size * 0.16) + r), fill=AMBER)
    draw.ellipse((int(size * 0.66) - r, int(size * 0.16) - r, int(size * 0.66) + r, int(size * 0.16) + r), fill=AMBER)
    return image


def build_branding() -> None:
    master = draw_helmet_icon(512)
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        icon = master.resize((size, size), Image.Resampling.LANCZOS)
        save(icon, RES / folder / "ic_launcher.png")

    banner = Image.new("RGBA", (320, 180), (14, 20, 30, 255))
    draw = ImageDraw.Draw(banner)
    draw.rectangle((0, 0, 8, 180), fill=VISOR)
    helmet = master.resize((96, 96), Image.Resampling.LANCZOS)
    banner.paste(helmet, (16, 42), helmet)
    title = ImageFont.truetype(FONT_BOLD, 32)
    subtitle = ImageFont.truetype(FONT_REGULAR, 18)
    draw.text((124, 52), "Máscaras", font=title, fill=(244, 247, 251, 255))
    draw.text((124, 98), "Cuerpo en vivo", font=subtitle, fill=(180, 220, 214, 255))
    save(banner, RES / "drawable-nodpi" / "tv_banner.png")


def main() -> None:
    build_robot()
    build_skeleton()
    build_branding()
    print("Máscaras e iconos generados")


if __name__ == "__main__":
    main()
