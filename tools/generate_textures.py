#!/usr/bin/env python3
"""
Generates every texture the mod ships, from scratch.

There is no reason for a mod like this to depend on art that has to be drawn by
hand and then updated every time a block changes. Everything here is a small piece
of code that draws a 16x16 image, so the whole set can be regenerated with one
command:

    python3 tools/generate_textures.py

The look is deliberately plain: dark stone, a little noise, and one or two marks
that mean something. Nothing here is trying to be frightening on its own.
"""
import os
import random
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "uncanny", "textures")
SIZE = 16


def png(path, pixels):
    """Writes an RGBA image. pixels is a list of rows of (r, g, b, a)."""
    raw = b""
    for row in pixels:
        raw += b"\x00"
        for (r, g, b, a) in row:
            # Clamp: the noise offsets can push a dark base below zero.
            raw += struct.pack("BBBB", max(0, min(255, r)), max(0, min(255, g)),
                               max(0, min(255, b)), max(0, min(255, a)))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    out = b"\x89PNG\r\n\x1a\n"
    out += chunk(b"IHDR", struct.pack(">IIBBBBB", len(pixels[0]), len(pixels), 8, 6, 0, 0, 0))
    out += chunk(b"IDAT", zlib.compress(raw, 9))
    out += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(out)


def canvas(base, noise=6, seed=1):
    rnd = random.Random(seed)
    return [[(base[0] + rnd.randint(-noise, noise),
              base[1] + rnd.randint(-noise, noise),
              base[2] + rnd.randint(-noise, noise), 255) for _ in range(SIZE)] for _ in range(SIZE)]


def transparent():
    return [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]


def starlight():
    img = transparent()
    centre = SIZE / 2 - 0.5
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - centre) ** 2 + (y - centre) ** 2) ** 0.5
            if d < 7:
                a = int(max(0, 255 - d * 34))
                v = int(max(60, 240 - d * 26))
                img[y][x] = (v, v, min(255, v + 20), a)
    return img


def seal_plate(opened=0):
    img = canvas((34, 34, 40), 5, seed=11)
    # A border, and seven marks. Marks that have gone are simply not lit.
    for i in range(SIZE):
        img[0][i] = (18, 18, 22, 255)
        img[SIZE - 1][i] = (18, 18, 22, 255)
        img[i][0] = (18, 18, 22, 255)
        img[i][SIZE - 1] = (18, 18, 22, 255)
    for n in range(7):
        x = 2 + n * 2
        lit = n < opened
        colour = (176, 65, 62, 255) if lit else (70, 70, 78, 255)
        for y in range(5, 11):
            img[y][x] = colour
    return img


def ledger():
    img = canvas((26, 22, 20), 4, seed=21)
    # Spine on the left, and a band that reads as a clasp.
    for y in range(SIZE):
        for x in range(3):
            img[y][x] = (16, 13, 12, 255)
    for x in range(3, SIZE):
        img[7][x] = (58, 50, 42, 255)
        img[8][x] = (58, 50, 42, 255)
    for y in range(4, 12):
        img[y][10] = (86, 74, 58, 255)
    return img


def eye_vent():
    img = canvas((40, 40, 46), 5, seed=31)
    centre = SIZE / 2 - 0.5
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - centre) ** 2 + (y - centre) ** 2) ** 0.5
            if d < 3.2:
                img[y][x] = (4, 4, 6, 255)
            elif d < 4.2:
                img[y][x] = (22, 22, 26, 255)
    return img


def observation_glass():
    img = transparent()
    for y in range(SIZE):
        for x in range(SIZE):
            edge = x == 0 or y == 0 or x == SIZE - 1 or y == SIZE - 1
            img[y][x] = (10, 10, 14, 235 if not edge else 255)
    return img


def survey_record():
    img = canvas((214, 208, 190), 8, seed=41)
    # Four lines of "writing", uneven, as if the writer was in a hurry.
    rnd = random.Random(42)
    for row, y in enumerate([3, 6, 9, 12]):
        width = rnd.randint(7, 13)
        for x in range(2, 2 + width):
            img[y][x] = (70, 66, 60, 255)
    return img


def ledger_page():
    img = canvas((22, 22, 26), 4, seed=51)
    for row, y in enumerate([4, 7, 10]):
        for x in range(3, 13):
            img[y][x] = (96, 96, 106, 255)
    # One line in red. It is the last one.
    for x in range(3, 12):
        img[12][x] = (150, 60, 58, 255)
    return img


def surveyors_compass():
    img = transparent()
    centre = SIZE / 2 - 0.5
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - centre) ** 2 + (y - centre) ** 2) ** 0.5
            if d < 6.5:
                img[y][x] = (46, 44, 42, 255)
            if 5.4 < d < 6.5:
                img[y][x] = (120, 112, 96, 255)
    # A needle that points somewhere the player is not.
    for i in range(-5, 6):
        x = int(centre + i)
        y = int(centre - i * 0.6)
        if 0 <= x < SIZE and 0 <= y < SIZE:
            img[y][x] = (176, 65, 62, 255)
    return img


def icon():
    """The mod icon, 128x128: a ring with too many openings in it."""
    size = 128
    img = [[(8, 8, 11, 255) for _ in range(size)] for _ in range(size)]
    centre = size / 2 - 0.5
    for y in range(size):
        for x in range(size):
            d = ((x - centre) ** 2 + (y - centre) ** 2) ** 0.5
            if 44 < d < 52:
                img[y][x] = (58, 58, 66, 255)
    import math
    for angle in range(0, 360, 15):
        radians = math.radians(angle)
        x = int(centre + math.cos(radians) * 48)
        y = int(centre + math.sin(radians) * 48)
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                if 0 <= x + dx < size and 0 <= y + dy < size:
                    img[y + dy][x + dx] = (4, 4, 6, 255)
    return img


def main():
    blocks = {
        "starlight": starlight(),
        "seal_plate": seal_plate(),
        "seal_plate_open": seal_plate(7),
        "ledger": ledger(),
        "eye_vent": eye_vent(),
        "observation_glass": observation_glass(),
        "survey_marker": transparent(),
    }
    items = {
        "survey_record": survey_record(),
        "ledger_page": ledger_page(),
        "surveyors_compass": surveyors_compass(),
    }
    for name, img in blocks.items():
        png(os.path.join(OUT, "block", name + ".png"), img)
    for name, img in items.items():
        png(os.path.join(OUT, "item", name + ".png"), img)
    png(os.path.join(OUT, "..", "icon.png"), icon())
    print("wrote", len(blocks), "block textures,", len(items), "item textures, 1 icon")


if __name__ == "__main__":
    main()
