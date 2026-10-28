"""Raviga's Google Play release tooling (Play Developer API), for com.raviga.app only.

Uses Raviga's own publisher service account (raviga-play-publisher@raviga-e6b92). Its key stays outside the repo,
at ~/.config/raviga/raviga-play-publisher.json (override with RAVIGA_PLAY_KEY); it is never printed or copied.
The account can release to testing tracks and manage in-app products for Raviga, and nothing for any other app.

    .venv/bin/python tools/play_release.py tracks
    .venv/bin/python tools/play_release.py upload app/build/outputs/bundle/release/app-release.aab [--track internal]
    .venv/bin/python tools/play_release.py products            # list one-time products
    .venv/bin/python tools/play_release.py product credits_10 "10 credits" 99.99

Releases are created as drafts: Play allows only drafts until the app is first published, and rolling out stays a
deliberate step in Play Console. Products: prices are converted from USD per region; a country whose cap is below the
converted price is left out (never sold cheaper), as Play requires.
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


def product(s, pid, title, usd):
    units, _, cents = f"{float(usd):.2f}".partition(".")
    conv = check(s.post(f"{API}/pricing:convertRegionPrices",
                        json={"price": {"currencyCode": "USD", "units": units, "nanos": int(cents) * 10_000_000}}))
    regions = {code: v["price"] for code, v in conv["convertedRegionPrices"].items()}
    left_out = []
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
        m = re.search(r"Price for ([A-Z]{2}) must be between", r.text) if r.status_code == 400 else None
        if m and m.group(1) in regions:
            regions.pop(m.group(1)); left_out.append(m.group(1)); continue
        check(r)
        break
    check(s.post(f"{API}/oneTimeProducts/{pid}/purchaseOptions:batchUpdateStates", json={"requests": [
        {"activatePurchaseOptionRequest": {"packageName": PKG, "productId": pid, "purchaseOptionId": "default"}}]}))
    print(f"{pid} {title!r} ${usd}: active in {len(regions)} regions; left out (over the local cap): {left_out or 'none'}")


def main():
    a = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = a.add_subparsers(dest="cmd", required=True)
    sub.add_parser("tracks")
    u = sub.add_parser("upload"); u.add_argument("aab"); u.add_argument("--track", default="internal")
    sub.add_parser("products")
    p = sub.add_parser("product"); p.add_argument("id"); p.add_argument("title"); p.add_argument("usd")
    args = a.parse_args()
    s = session()
    {"tracks": lambda: tracks(s), "upload": lambda: upload(s, args.aab, args.track), "products": lambda: products(s),
     "product": lambda: product(s, args.id, args.title, args.usd)}[args.cmd]()


if __name__ == "__main__":
    main()
