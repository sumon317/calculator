#!/usr/bin/env python3
"""WCAG contrast checker for the Calu palette.

Every pair the app paints must clear its target:
  4.5:1 for body text, 3.0:1 for large text and key edges.
Run: python3 tools/contrast.py
"""
import sys

BG_TOP, BG_BOTTOM = "#1B1511", "#0E0A08"
SURFACE = "#33291F"
SCI_SURFACE = "#3B2E22"
CARAMEL = "#C98A4C"
GOLD = "#E7B451"
RUST = "#A44B36"
CREAM = "#F7EFE4"
MUTED = "#A78F76"
AMBER = "#E8B04B"
ON_ACCENT = "#17110C"
DISABLED_FILL = "#241C16"
DISABLED_TX = "#82705E"
ERROR = "#E08A63"
HAIRLINE = "#3A2C21"

PAIRS = [
    ("display cream on bg-top", CREAM, BG_TOP, 4.5),
    ("display cream on bg-bottom", CREAM, BG_BOTTOM, 4.5),
    ("history muted on bg-top", MUTED, BG_TOP, 4.5),
    ("history muted on bg-bottom", MUTED, BG_BOTTOM, 4.5),
    ("digit cream on surface", CREAM, SURFACE, 4.5),
    ("operator label on caramel", ON_ACCENT, CARAMEL, 4.5),
    ("equals label on gold", ON_ACCENT, GOLD, 4.5),
    ("clear label on rust", CREAM, RUST, 4.5),
    ("scientific label on roast", AMBER, SCI_SURFACE, 4.5),
    ("error text on bg-top", ERROR, BG_TOP, 4.5),
    ("disabled label on disabled fill", DISABLED_TX, DISABLED_FILL, 3.0),
    ("caramel key edge on bg-top", CARAMEL, BG_TOP, 3.0),
    ("gold key edge on bg-top", GOLD, BG_TOP, 3.0),
    ("rust key edge on bg-top", RUST, BG_TOP, 3.0),
    ("amber key edge on bg-top", AMBER, BG_TOP, 3.0),
]


def lin(c):
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def lum(h):
    h = h.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def ratio(a, b):
    la, lb = lum(a), lum(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


bad = 0
for name, fg, bg, need in PAIRS:
    r = ratio(fg, bg)
    ok = r >= need
    bad += 0 if ok else 1
    print(f"{'PASS' if ok else 'FAIL'}  {r:5.2f}:1 (need {need})  {name}")

print("\n" + ("ALL PASS" if bad == 0 else f"{bad} pair(s) below target"))
sys.exit(1 if bad else 0)
