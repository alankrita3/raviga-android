# Raviga Android: what's pending now

Kept current by the Android session (the founder's rule, 2026-10-28: nothing pending lives only inside a session).
The PM reads this file if the session has closed. Updated 2026-10-29.

## In flight

- **R-052, Play store listing (drafts only, nothing in Play Console).**
  - Done: `docs/store/play-listing.md` (name, short descriptions A and B, full descriptions with opening A or B,
    category, contact details, counts); feature graphic drafts `feature-graphic-navy.png` and `-cream.png` in
    `~/Downloads/raviga-play-screenshots/`.
  - Left: 4 to 6 phone screenshots on the OnePlus against dev (Welcome → brief → quote → project), with a team.py demo
    account; crop to 1080×2160 for Play's 2:1 rule; one-line captions in the same folder.

## Blocked, and on whom

- **Founder via PM:** India prices for the three smaller packs. Play's conversions (₹11,200 / ₹28,100 / ₹56,200) are
  above /config's hints (₹8,499 / ₹21,249 / ₹42,499). Changing them is one `tools/play_release.py` run once she decides (R-035
  follow-up).
- **Founder:** RevenueCat project, with the Android `goog_` key and Play credentials, then `REVENUECAT_API_KEY` in
  secrets.properties. Until then purchases can't be tested end to end, and neither can the analytics events
  `credits_purchased`, `project_submitted` and `delivery_accepted`.
- **Backend + founder:** the prod stage (`API_BASE_URL`). Until then only internal-test bundles against dev exist
  (`bundleRelease -Praviga.internalTest=true`).
- **Founder:** BillDesk KYC for sales outside India, due 15 Mar 2027 (Play Console banner; contact team@mirakee.com).
- **Founder via PM (phone):** the OnePlus is off Wi-Fi adb (192.168.1.4 unreachable). The PM is asking her to plug it
  in by USB; then `adb tcpip 5555` and the R-052 screenshots. No emulator.
- **Founder via PM:** approve the Play listing copy, pick short description A or B (pairs with the iOS subtitle) and
  a feature graphic (navy recommended).
- **Hardware:** the themed icon needs an Android 13+ phone; voice dictation needs a phone with an on-device recogniser.
  The OnePlus 7 runs Android 12 and has neither.
- **On hold (founder said "later"):** the queue screen and the suggested tech stack.

## Before a Play closed test or production

Done:
- organization account, developer "Raviga Apps";
- app "Raviga" (com.raviga.app);
- upload key, with Play App Signing on first upload;
- draft internal release, v1 (dev server);
- 4 credit packs (Korea: 10 and 25 only);
- publisher service account (R-051);
- icon, including the 512 px Play icon;
- 16 KB pages, target SDK and permissions.

Still to do in Play Console (App content). Drafts are in `docs/play-launch-readiness.md`:
1. **Privacy policy URL:** prod legal site on ravigaapps.com (domain pending; dev pages exist).
2. **Data safety form:** draft answers ready, including analytics. Enter them, founder to confirm.
3. **App access:** reviewer instructions with a team.py demo account and the verified restore taps (8 steps).
4. **Ads:** none.
5. **Content rating (IARC):** questionnaire.
6. **Target audience:** 18+.
7. **Account deletion URL:** prod delete-account page.
8. **Government, financial and health declarations:** none apply.

Store listing (public, founder approves the copy), drafted under R-052:
- short and full description, category (Business), contact email and website: `docs/store/play-listing.md`;
- 1024×500 feature graphic: two drafts in `~/Downloads/raviga-play-screenshots/`;
- 2 to 8 phone screenshots: waiting for the OnePlus.

Release:
- a prod bundle needs the prod URL and the RevenueCat key (`checkReleaseReadiness`);
- roll out internal testing first, read the pre-launch report, then closed or production. As an organization account,
  the 12-tester closed test isn't required.

## Next step (mine)

- When the PM says the OnePlus is plugged in: R-052 screenshots (demo account from backend's team.py, paid runs
  logged in the PM ledger first, mirakee-android told the phone is taken and free), then report to the PM.
- When the PM says go: enter the App content answers from the drafts (anything public or legal goes to her first).
