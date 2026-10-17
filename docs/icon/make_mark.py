"""Interim Raviga mark (until the founder picks a logo): Bricolage Grotesque Bold "R" in ink
with a teal full stop.

Writes the Android vector path data (r108.txt and dot108.txt for the 108 launcher/splash
viewport, r24.txt for the 24 notification viewport) and renders AppIcon.png, a 1024 opaque
PNG for iOS, from the same geometry. Paste the paths into res/drawable/ic_launcher_foreground.xml,
ic_splash_mark.xml and ic_notification.xml.

    .venv/bin/python docs/icon/make_mark.py app/src/main/res/font/bricolage_bold.ttf <out dir>

(.venv is the native helper venv: see tools/requirements.txt.)
"""
import math, sys
from fontTools.ttLib import TTFont
from fontTools.pens.recordingPen import RecordingPen
from PIL import Image, ImageDraw

font_path, out_dir = sys.argv[1], sys.argv[2]
font = TTFont(font_path)
gs = font.getGlyphSet()
gname = font.getBestCmap()[ord("R")]
rec = RecordingPen(); gs[gname].draw(rec)
glyf = font["glyf"][gname]
xmin, xmax, ymax = glyf.xMin, glyf.xMax, glyf.yMax  # baseline is y = 0

def contours(transform, steps=12):
    """Flattened contours (lists of points) after transform."""
    out, cur, start = [], [], None
    for op, args in rec.value:
        if op == "moveTo":
            cur = [transform(*args[0])]; start = args[0]; last = args[0]
        elif op == "lineTo":
            cur.append(transform(*args[0])); last = args[0]
        elif op == "qCurveTo":
            pts = list(args)
            if pts[-1] is None:  # implied on-curve contour
                pts = pts[:-1]
            p0 = last
            # expand implied on-curve points between consecutive off-curve points
            offs, end = pts[:-1], pts[-1]
            segs = []
            for i, c in enumerate(offs):
                nxt = end if i == len(offs) - 1 else ((c[0] + offs[i + 1][0]) / 2, (c[1] + offs[i + 1][1]) / 2)
                segs.append((c, nxt))
            for c, e in segs:
                for k in range(1, steps + 1):
                    t = k / steps
                    x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * e[0]
                    y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * e[1]
                    cur.append(transform(x, y))
                p0 = e
            last = end
        elif op in ("closePath", "endPath"):
            out.append(cur); cur = []
    return out

def path_data(transform):
    def f(v): return ("%.2f" % v).rstrip("0").rstrip(".")
    d, last = [], None
    for op, args in rec.value:
        if op == "moveTo":
            x, y = transform(*args[0]); d.append(f"M{f(x)},{f(y)}"); last = args[0]
        elif op == "lineTo":
            x, y = transform(*args[0]); d.append(f"L{f(x)},{f(y)}"); last = args[0]
        elif op == "qCurveTo":
            pts = [p for p in args if p is not None]
            offs, end = pts[:-1], pts[-1]
            for i, c in enumerate(offs):
                nxt = end if i == len(offs) - 1 else ((c[0] + offs[i + 1][0]) / 2, (c[1] + offs[i + 1][1]) / 2)
                cx, cy = transform(*c); ex, ey = transform(*nxt)
                d.append(f"Q{f(cx)},{f(cy)} {f(ex)},{f(ey)}")
            last = end
        elif op in ("closePath", "endPath"):
            d.append("Z")
    return " ".join(d)

def layout(cap, center=(54.0, 54.0), dot=True):
    s = cap / ymax
    w = (xmax - xmin) * s
    r = cap * 5 / 44 if dot else 0
    gap = cap * 0.07 if dot else 0
    total = w + (gap + 2 * r if dot else 0)
    left = center[0] - total / 2
    base = center[1] + cap / 2
    t = lambda x, y: (left + (x - xmin) * s, base - y * s)
    dotc = (left + w + gap + r, base - r)
    return t, dotc, r

# Largest cap height (up to the old D's 44) that keeps everything in the 66dp safe circle.
cap = 44.0
while True:
    t, dotc, r = layout(cap)
    far = max(math.hypot(p[0] - 54, p[1] - 54) for c in contours(t) for p in c)
    far = max(far, math.hypot(dotc[0] - 54, dotc[1] - 54) + r)
    if far <= 32.5: break
    cap -= 0.25
print("cap", cap, "farthest", round(far, 2), file=sys.stderr)

t, dotc, r = layout(cap)
def f(v): return ("%.2f" % v).rstrip("0").rstrip(".")
open(f"{out_dir}/r108.txt", "w").write(path_data(t))
open(f"{out_dir}/dot108.txt", "w").write(f"M{f(dotc[0])},{f(dotc[1])} m-{f(r)},0 a{f(r)},{f(r)} 0 1,1 {f(2*r)},0 a{f(r)},{f(r)} 0 1,1 -{f(2*r)},0")

# Notification: R alone, 18 tall in a 24 viewport, white.
tn, _, _ = layout(18.0, center=(12.0, 12.0), dot=False)
open(f"{out_dir}/r24.txt", "w").write(path_data(tn))

# iOS: the adaptive icon's visible 72dp (18..90) becomes the 1024 square. Draw at 4x, then downsample.
S = 4096; k = S / 72.0
paper, ink, teal = (0xFB, 0xFA, 0xF7), (0x17, 0x18, 0x1C), (0x0F, 0x5B, 0x57)
img = Image.new("RGB", (S, S), paper); dr = ImageDraw.Draw(img)
ti = lambda x, y: ((t(x, y)[0] - 18) * k, (t(x, y)[1] - 18) * k)
cs = contours(ti, steps=48)
# Outer contour in ink, counters (wound the other way) in paper.
def area(c): return sum(c[i][0] * c[i - 1][1] - c[i - 1][0] * c[i][1] for i in range(len(c))) / 2
outer_sign = area(max(cs, key=lambda c: abs(area(c)))) > 0
for c in sorted(cs, key=lambda c: -abs(area(c))):
    dr.polygon(c, fill=ink if (area(c) > 0) == outer_sign else paper)
cx, cy = (dotc[0] - 18) * k, (dotc[1] - 18) * k; rr = r * k
dr.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=teal)
img.resize((1024, 1024), Image.LANCZOS).save(f"{out_dir}/AppIcon.png")
print("areas", [round(area(c)) for c in cs], file=sys.stderr)
