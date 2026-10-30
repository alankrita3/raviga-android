"""R-057: cream Play feature graphics (1024x500) with icon A's r merged into the wordmark, no separate tile.

The leading r carries icon A's floating square (r's right edge + 6, the i dot's height), and the letters after it
move right 22 units so the square has room without reading as an apostrophe. Writes SVG sources:
  feature-graphic-cream-merged-two-squares.svg: both squares blue (the r's and the i's dot, the wordmark's signature)
  feature-graphic-cream-merged-one-square.svg:  only the r's square blue; the i's dot navy, as in the monochrome kit
"""
import re, sys
from pathlib import Path

KIT = Path.home() / "Downloads/raviga-logo-final"
out = Path(sys.argv[1])
NAVY, CREAM, BLUE = "#14183A", "#F6F3EE", "#6F8CFF"

body = re.search(r"<svg[^>]*>(.*)</svg>", (KIT / "wordmark/raviga-wordmark-transparent.svg").read_text(), re.S).group(1)
r_path, rest = re.match(r"(<path[^>]*/>)(.*)", body, re.S).groups()    # the kit's r, then "aviga"
i_dot = f'y="64" width="24" height="24" fill="{BLUE}"'
assert rest.count(i_dot) == 1
r_square = f'<rect x="61" y="64" width="24" height="24" fill="{BLUE}"/>'  # icon A: x = 55 + 6, y = 100 - 12 - 24

W, H = 1024, 500
SHIFT = 22                 # extra space after the r, in wordmark units
S = 1.25                   # wordmark units to px (x-height 125 px)
X0, X1 = 0.0, 522.66 + SHIFT
Y0, Y1 = 64, 258           # square top to the g's descender
tx = (W - (X1 - X0) * S) / 2 - X0 * S
ty = H / 2 - (Y0 + Y1) / 2 * S

for name, letters in (("two-squares", rest), ("one-square", rest.replace(i_dot, i_dot.replace(BLUE, NAVY)))):
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">
<rect width="{W}" height="{H}" fill="{CREAM}"/>
<g transform="translate({tx:.1f},{ty:.1f}) scale({S})">{r_path}{r_square}<g transform="translate({SHIFT},0)">{letters}</g></g>
</svg>'''
    (out / f"feature-graphic-cream-merged-{name}.svg").write_text(svg)
    print(out / f"feature-graphic-cream-merged-{name}.svg")
