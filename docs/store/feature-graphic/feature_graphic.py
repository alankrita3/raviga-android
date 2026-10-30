"""Draft Play feature graphics (1024x500) from the R-045 kit: icon A tile plus the wordmark. Writes SVG sources."""
import re, sys
from pathlib import Path

KIT = Path.home() / "Downloads/raviga-logo-final"
out = Path(sys.argv[1])
NAVY, CREAM, BLUE = "#14183A", "#F6F3EE", "#6F8CFF"

def inner(svg: str) -> str:
    body = re.search(r"<svg[^>]*>(.*)</svg>", svg, re.S).group(1)
    return re.sub(r"^<rect[^>]*/>", "", body, count=1)   # drop the kit's background rect

icon = (KIT / "icons/A-r-and-square/A-r-and-square-1024.svg").read_text()
icon_body = re.search(r"<svg[^>]*>(.*)</svg>", icon, re.S).group(1)       # keeps its cream tile
marks = {"navy": inner((KIT / "wordmark/raviga-wordmark-on-navy.svg").read_text()),
         "cream": inner((KIT / "wordmark/raviga-wordmark-transparent.svg").read_text())}

W, H = 1024, 500
TILE, R = 232, 52          # icon tile and corner radius (about Play's icon mask)
S = 1.0                    # wordmark units to px (x-height 100 units -> 100 px)
GAP = 64
WM_X0, WM_X1 = 0.0, 522.66 # wordmark ink, left and right, in its units
total = TILE + GAP + (WM_X1 - WM_X0) * S
x0 = (W - total) / 2
ty = (H - TILE) / 2
wx = x0 + TILE + GAP - WM_X0 * S
wy = H / 2 - 150 * S        # x-height centre (150 units) on the tile centre

for name, bg in (("navy", NAVY), ("cream", CREAM)):
    border = "" if name == "navy" else f'<rect x="{x0+0.5}" y="{ty+0.5}" width="{TILE-1}" height="{TILE-1}" rx="{R}" fill="none" stroke="#D9D3C7" stroke-width="1"/>'
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">
<defs><clipPath id="t"><rect x="0" y="0" width="1024" height="1024" rx="{R*1024/TILE:.1f}"/></clipPath></defs>
<rect width="{W}" height="{H}" fill="{bg}"/>
<svg x="{x0:.1f}" y="{ty:.1f}" width="{TILE}" height="{TILE}" viewBox="0 0 1024 1024"><g clip-path="url(#t)">{icon_body}</g></svg>
{border}
<g transform="translate({wx:.1f},{wy:.1f}) scale({S})">{marks[name]}</g>
</svg>'''
    (out / f"feature-graphic-{name}.svg").write_text(svg)
    print(out / f"feature-graphic-{name}.svg")
