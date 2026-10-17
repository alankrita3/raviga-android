"""Converts the iOS clay pictures into res/drawable-nodpi/illo_*.webp.

Same libwebp settings as the old `cwebp -q 88 -alpha_q 100 -m 6`, through Pillow, so it runs natively
without Homebrew:

    .venv/bin/python tools/to_webp.py "../Raviga iOS/Raviga/Resources/Assets.xcassets" app/src/main/res/drawable-nodpi [Name ...]

Each `IlloName.imageset` becomes `illo_name.webp` (IlloAIBuild -> illo_ai_build). Pass names
(e.g. Welcome HomeEmpty) to convert only those.
"""
import pathlib, re, sys
from PIL import Image

catalog, out_dir = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
only = set(sys.argv[3:])

def snake(name):
    return re.sub(r"(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", "_", name).lower()

for imageset in sorted(catalog.glob("Illo*.imageset")):
    name = imageset.stem[len("Illo"):]
    if only and name not in only:
        continue
    src = next(p for p in imageset.iterdir() if p.suffix.lower() in (".png", ".jpg", ".jpeg"))
    img = Image.open(src)
    img = img.convert("RGBA" if img.mode in ("RGBA", "LA", "P") else "RGB")
    dst = out_dir / f"illo_{snake(name)}.webp"
    img.save(dst, "WEBP", quality=88, alpha_quality=100, method=6)
    print(f"{src.name} -> {dst.name} ({img.size[0]}x{img.size[1]}, {img.mode}, {dst.stat().st_size // 1024} KB)")
