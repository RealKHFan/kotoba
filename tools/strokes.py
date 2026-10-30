"""Builds app/src/main/assets/strokes.txt from KanjiVG for every kana and kanji the app uses.

Usage: python3 tools/strokes.py /path/to/kanjivg   (needs: pip install svgpathtools)

Each stroke is turned into a list of points about 1.5 units apart on KanjiVG's 109 x 109 grid,
stored as integers times 10. The app draws and checks strokes from these points.
"""
import os
import re
import sys

from svgpathtools import parse_path

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
KANA_KT = os.path.join(ROOT, "app", "src", "main", "java", "io", "github", "realkhfan", "kotoba", "data", "Kana.kt")


def characters():
    chars = []
    for line in open(os.path.join(ASSETS, "kanji.txt"), encoding="utf-8"):
        if line.strip() and not line.startswith("#"):
            chars.append(line.split("|")[0])
    spec = open(KANA_KT, encoding="utf-8").read().split('"""')[1]
    hira = set()
    for line in spec.strip().splitlines():
        for cell in line.split()[2:]:
            if cell != "-":
                hira.update(cell.split(":")[0])
    kata = {chr(ord(c) + 0x60) for c in hira}
    return chars + sorted(hira) + sorted(kata) + ["ー", "っ", "ッ"]


def resample(points, step=1.5):
    out = [points[0]]
    acc = 0.0
    for a, b in zip(points, points[1:]):
        d = abs(b - a)
        while acc + d >= step and d > 0:
            t = (step - acc) / d
            a = a + (b - a) * t
            out.append(a)
            d = abs(b - a)
            acc = 0.0
        acc += d
    if abs(points[-1] - out[-1]) > 0.3:
        out.append(points[-1])
    return out


def strokes(svg):
    paths = re.findall(r'<path id="kvg:[0-9a-f]+-s(\d+)"[^>]*? d="([^"]+)"', svg)
    result = []
    for _, d in sorted(paths, key=lambda p: int(p[0])):
        path = parse_path(d)
        pts = [path.point(i / 300) for i in range(301)]
        pts = resample(pts)
        result.append(" ".join(f"{round(p.real * 10)},{round(p.imag * 10)}" for p in pts))
    return result


def main(kanjivg):
    lines = [
        "# Stroke order from KanjiVG (https://kanjivg.tagaini.net), copyright Ulrich Apel and contributors,",
        "# CC BY-SA 3.0. Changed for Kotoba: each stroke is converted to points by tools/strokes.py.",
        "# Lines: character<TAB>stroke|stroke|...; a stroke is x,y points on a 1090 x 1090 grid.",
    ]
    missing = []
    for c in characters():
        f = os.path.join(kanjivg, "kanji", f"{ord(c):05x}.svg")
        if not os.path.exists(f):
            missing.append(c)
            continue
        lines.append(c + "\t" + "|".join(strokes(open(f, encoding="utf-8").read())))
    open(os.path.join(ASSETS, "strokes.txt"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print(len(lines) - 3, "characters written; missing:", "".join(missing) or "none")


if __name__ == "__main__":
    main(sys.argv[1])
