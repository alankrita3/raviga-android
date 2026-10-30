"""Raviga's Google Play release tooling (Play Developer API), for com.raviga.app only.

Uses Raviga's own publisher service account (raviga-play-publisher@raviga-e6b92). Its key stays outside the repo,
at ~/.config/raviga/raviga-play-publisher.json (override with RAVIGA_PLAY_KEY); it is never printed or copied.
The account can release to testing tracks and manage in-app products for Raviga, and nothing for any other app.

    .venv/bin/python tools/play_release.py tracks
    .venv/bin/python tools/play_release.py upload app/build/outputs/bundle/release/app-release.aab [--track internal]
    .venv/bin/python tools/play_release.py products            # list one-time products
    .venv/bin/python tools/play_release.py product credits_10 "10 credits" 99.99
    .venv/bin/python tools/play_release.py add-region credits_10 FR     # add one country, priced at the USD conversion or its cap
    .venv/bin/python tools/play_release.py withdraw-region credits_50 KR  # stop selling one pack in one country
    .venv/bin/python tools/play_release.py set-price credits_10 IN 8499  # one pack's price in one country, local currency
    .venv/bin/python tools/play_release.py regions [IN]  # read back every pack's price per country (TSV, for diffs)

Releases are created as drafts: Play allows only drafts until the app is first published, and rolling out stays a
deliberate step in Play Console. Products: prices are converted from USD per region. Where the converted price is over
Play's maximum for that country, the pack is sold at that country's maximum (founder's rule, 2026-10-28): it sells
everywhere, slightly cheaper where the cap bites (India's ₹84,000 for the $999.99 pack, for example).
Exceptions are in NOT_SOLD and are never added back: Korea gets 10 and 25 only (her call 10-28; there the cap made the
50 and 100 packs cost the same, the 100 pack about 59% off). FIXED_PRICES holds her own local prices, which replace
Play's conversion: India's 10, 25 and 50 packs at the plan prices (R-060, 10-30); the 100 pack stays at India's cap.
"""
import argparse, json, os, re, sys

import google.auth.transport.requests as gtr
import requests
from google.oauth2 import service_account

PKG = "com.raviga.app"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PKG}"
UPLOAD = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PKG}"
KEY = os.environ.get("RAVIGA_PLAY_KEY", os.path.expanduser("~/.config/raviga/raviga-play-publisher.json"))
DESCRIPTION = "Credits to pay for building your Raviga projects."
# Founder's exclusions, per pack: countries where it is not sold at all.
NOT_SOLD = {"credits_50": {"KR"}, "credits_100": {"KR"}}  # Korea: 10 and 25 only (her call 10-28)
# Founder's fixed local prices, per pack (whole units of the local currency); `product` keeps them on a re-run.
FIXED_PRICES = {"credits_10": {"IN": 8499}, "credits_25": {"IN": 21249}, "credits_50": {"IN": 42499}}  # her plan prices, R-060 (10-30)


def session() -> requests.Session:
    cred = service_account.Credentials.from_service_account_file(KEY, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    cred.refresh(gtr.Request())
    s = requests.Session()
    s.headers["Authorization"] = f"Bearer {cred.token}"
    return s


def check(r: requests.Response) -> dict:
    if not r.ok:
        sys.exit(f"{r.request.method} {r.url.split('?')[0]} -> {r.status_code}: {r.text[:500]}")
    return r.json() if r.content else {}


def tracks(s):
    edit = check(s.post(f"{API}/edits", json={}))["id"]
    try:
        for t in check(s.get(f"{API}/edits/{edit}/tracks")).get("tracks", []):
            print(t["track"], [(r.get("name"), r.get("status"), r.get("versionCodes")) for r in t.get("releases", [])])
    finally:
        s.delete(f"{API}/edits/{edit}")


def upload(s, aab, track):
    edit = check(s.post(f"{API}/edits", json={}))["id"]
    with open(aab, "rb") as f:
        b = check(s.post(f"{UPLOAD}/edits/{edit}/bundles", params={"uploadType": "media"},
                         headers={"Content-Type": "application/octet-stream"}, data=f, timeout=900))
    vc = str(b["versionCode"])
    check(s.put(f"{API}/edits/{edit}/tracks/{track}", json={"track": track, "releases": [
        {"name": f"{vc} {track} test", "versionCodes": [vc], "status": "draft",
         "releaseNotes": [{"language": "en-US", "text": "Test build."}]}]}))
    check(s.post(f"{API}/edits/{edit}:commit"))
    print(f"uploaded versionCode {vc} (sha256 {b['sha256'][:12]}…) as a draft {track} release")


def products(s):
    for p in check(s.get(f"{API}/oneTimeProducts")).get("oneTimeProducts", []):
        po = p["purchaseOptions"][0]
        cfg = po.get("regionalPricingAndAvailabilityConfigs", [])
        us = next((c["price"] for c in cfg if c["regionCode"] == "US"), {})
        print(p["productId"], repr(p["listings"][0]["title"]), po.get("state"),
              f"US ${us.get('units')}.{str(us.get('nanos', 0))[:2]}", f"{sum(c.get('availability') == 'AVAILABLE' for c in cfg)} regions")


def money(display: str, currency: str) -> dict:
    """'₹84,000.00', 'CA$1,380.00', 'BDT 110,000.00' or '¥155,000' (from Play's error text) as Money."""
    digits = re.sub(r"[^0-9.,]", "", display)
    if digits.count(".") > 1:
        digits = digits.replace(".", "").replace(",", ".")
    amount = float(digits.replace(",", ""))
    return {"currencyCode": currency, "units": str(int(amount)), "nanos": int(round((amount - int(amount)) * 1e9))}


CAP = re.compile(r"Price for ([A-Z]{2}) must be between .*? and (.*?), found")


def product(s, pid, title, usd):
    units, _, cents = f"{float(usd):.2f}".partition(".")
    conv = check(s.post(f"{API}/pricing:convertRegionPrices",
                        json={"price": {"currencyCode": "USD", "units": units, "nanos": int(cents) * 10_000_000}}))
    regions = {code: v["price"] for code, v in conv["convertedRegionPrices"].items() if code not in NOT_SOLD.get(pid, set())}
    for code, amount in FIXED_PRICES.get(pid, {}).items():
        regions[code] = {"currencyCode": regions[code]["currencyCode"], "units": str(amount), "nanos": 0}
    capped = []
    for _ in range(len(regions) + 1):
        body = {"packageName": PKG, "productId": pid,
                "listings": [{"languageCode": "en-US", "title": title, "description": DESCRIPTION}],
                "purchaseOptions": [{"purchaseOptionId": "default",
                                     "buyOption": {"legacyCompatible": True, "multiQuantityEnabled": False},
                                     "regionalPricingAndAvailabilityConfigs": [
                                         {"regionCode": c, "price": p, "availability": "AVAILABLE"} for c, p in sorted(regions.items())],
                                     "newRegionsConfig": {**conv["convertedOtherRegionsPrice"], "availability": "AVAILABLE"}}]}
        r = s.patch(f"{API}/onetimeproducts/{pid}", json=body, params={
            "allowMissing": "true", "regionsVersion.version": conv["regionVersion"]["version"], "updateMask": "listings,purchaseOptions"})
        m = CAP.search(r.text) if r.status_code == 400 else None
        if m and m.group(1) in regions and m.group(1) not in capped:
            regions[m.group(1)] = money(m.group(2), regions[m.group(1)]["currencyCode"]); capped.append(m.group(1)); continue
        check(r)
        break
    for code in NOT_SOLD.get(pid, set()):
        withdraw_region(s, pid, code, quiet=True)
    check(s.post(f"{API}/oneTimeProducts/{pid}/purchaseOptions:batchUpdateStates", json={"requests": [
        {"activatePurchaseOptionRequest": {"packageName": PKG, "productId": pid, "purchaseOptionId": "default"}}]}))
    print(f"{pid} {title!r} ${usd}: active in {len(regions)} regions; sold at the local maximum in {len(capped)}: {' '.join(sorted(capped)) or 'none'}")


def withdraw_region(s, pid, code, quiet=False):
    """Stops selling an existing product in one country (NO_LONGER_AVAILABLE); every other region is sent back as is."""
    prod = check(s.get(f"{API}/oneTimeProducts/{pid}"))
    cfg = prod["purchaseOptions"][0]["regionalPricingAndAvailabilityConfigs"]
    hit = [c for c in cfg if c["regionCode"] == code]
    if not hit or hit[0].get("availability") == "NO_LONGER_AVAILABLE":
        if not quiet:
            print(f"{pid}: not sold in {code} already")
        return
    hit[0]["availability"] = "NO_LONGER_AVAILABLE"
    check(s.patch(f"{API}/onetimeproducts/{pid}", json=prod,
                  params={"regionsVersion.version": prod["regionsVersion"]["version"], "updateMask": "purchaseOptions"}))
    print(f"{pid}: withdrawn from {code}")


def add_region(s, pid, code):
    """Adds one country to an existing product, priced from its US price (or at that country's cap); others untouched."""
    if code in NOT_SOLD.get(pid, set()):
        sys.exit(f"{pid} is not sold in {code} (NOT_SOLD, the founder's call)")
    prod = check(s.get(f"{API}/oneTimeProducts/{pid}"))
    po = prod["purchaseOptions"][0]
    cfg = po["regionalPricingAndAvailabilityConfigs"]
    if any(c["regionCode"] == code for c in cfg):
        sys.exit(f"{pid} already has {code}")
    us = next(c["price"] for c in cfg if c["regionCode"] == "US")
    conv = check(s.post(f"{API}/pricing:convertRegionPrices", json={"price": us}))
    price = conv["convertedRegionPrices"][code]["price"]
    for _ in range(2):
        po["regionalPricingAndAvailabilityConfigs"] = cfg + [{"regionCode": code, "price": price, "availability": "AVAILABLE"}]
        r = s.patch(f"{API}/onetimeproducts/{pid}", json=prod,
                    params={"regionsVersion.version": prod["regionsVersion"]["version"], "updateMask": "purchaseOptions"})
        m = CAP.search(r.text) if r.status_code == 400 else None
        if m and m.group(1) == code:
            price = money(m.group(2), price["currencyCode"]); continue
        check(r); break
    print(f"{pid}: added {code} at {price['currencyCode']} {price['units']}.{str(price.get('nanos', 0)).zfill(9)[:2]}")


def regions(s, only=None):
    """Every product's price and availability per country, one TSV row each, sorted (diff two runs to see changes)."""
    rows = []
    for p in check(s.get(f"{API}/oneTimeProducts")).get("oneTimeProducts", []):
        for c in p["purchaseOptions"][0].get("regionalPricingAndAvailabilityConfigs", []):
            if only and c["regionCode"] != only:
                continue
            pr = c.get("price", {})
            rows.append((p["productId"], c["regionCode"], pr.get("currencyCode", ""),
                         f"{pr.get('units', '0')}.{str(pr.get('nanos', 0)).zfill(9)[:2]}", c.get("availability", "")))
    for r in sorted(rows):
        print("\t".join(r))


def set_price(s, pid, code, amount):
    """Sets one product's price in one country, in that country's currency; every other region is sent back as is."""
    prod = check(s.get(f"{API}/oneTimeProducts/{pid}"))
    cfg = prod["purchaseOptions"][0]["regionalPricingAndAvailabilityConfigs"]
    hit = [c for c in cfg if c["regionCode"] == code]
    if not hit:
        sys.exit(f"{pid} has no {code}; add it with add-region first")
    old = hit[0]["price"]
    units, _, frac = f"{float(amount):.2f}".partition(".")
    hit[0]["price"] = {"currencyCode": old["currencyCode"], "units": units, "nanos": int(frac) * 10_000_000}
    check(s.patch(f"{API}/onetimeproducts/{pid}", json=prod,
                  params={"regionsVersion.version": prod["regionsVersion"]["version"], "updateMask": "purchaseOptions"}))
    print(f"{pid} {code}: {old['currencyCode']} {old.get('units')}.{str(old.get('nanos', 0)).zfill(9)[:2]} -> {units}.{frac}")


def main():
    a = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = a.add_subparsers(dest="cmd", required=True)
    sub.add_parser("tracks")
    u = sub.add_parser("upload"); u.add_argument("aab"); u.add_argument("--track", default="internal")
    sub.add_parser("products")
    p = sub.add_parser("product"); p.add_argument("id"); p.add_argument("title"); p.add_argument("usd")
    r = sub.add_parser("add-region"); r.add_argument("id"); r.add_argument("region")
    w = sub.add_parser("withdraw-region"); w.add_argument("id"); w.add_argument("region")
    sp = sub.add_parser("set-price"); sp.add_argument("id"); sp.add_argument("region"); sp.add_argument("amount")
    rg = sub.add_parser("regions"); rg.add_argument("region", nargs="?")
    args = a.parse_args()
    s = session()
    {"tracks": lambda: tracks(s), "upload": lambda: upload(s, args.aab, args.track), "products": lambda: products(s),
     "product": lambda: product(s, args.id, args.title, args.usd),
     "add-region": lambda: add_region(s, args.id, args.region),
     "withdraw-region": lambda: withdraw_region(s, args.id, args.region),
     "set-price": lambda: set_price(s, args.id, args.region, args.amount),
     "regions": lambda: regions(s, args.region)}[args.cmd]()


if __name__ == "__main__":
    main()
