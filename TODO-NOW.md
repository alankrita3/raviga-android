# Raviga Android: what's pending now

Kept current by the Android session (the founder's rule, 2026-10-28: nothing pending lives only inside a session).
The PM reads this file if the session has closed. Updated 2026-10-30.

## In flight

- **R-055 and R-056, code done; device check waits for the OnePlus on USB.**
  - R-055 (no name or address): a5f36c9. R-056 (consent at the first Type/Speak/Upload/Restore/Buy credits; only
    GET /config before Agree): 932b08f. Unit tests 69/69.
  - Left: install the debug build over the existing app (keep its data) and screenshot for the PM: the consent
    screen and Privacy and data (R-055); then a fresh flow (R-056: open on Welcome, Type gives the consent once,
    Agree, no second consent on Speak or Restore). The fresh flow needs a clean install. Do it on a second
    install of the release build (com.raviga.app, internal-test against dev), so the debug app's data stays.
- Last shipped: R-057 (2026-10-30), her final store copy in `docs/store/play-listing.md` (short description B,
  opening B plus "AI does the building; our team checks each project before it's handed over.", 1,853 characters;
  18+), and two cream feature graphics with icon A's r merged into the wordmark, no tile:
  `feature-graphic-cream-merged-two-squares.png` and `-one-square.png` in `~/Downloads/raviga-play-screenshots/`
  (fallback `feature-graphic-cream.png`). The PM shows her the drafts.

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
- **Founder via PM:** pick a feature graphic (the two cream merged drafts or the cream fallback). The copy (option B) and
  18+ are decided. The dev demo project RA-Q3FJ24 is rejected ("screenshot demo"); store reviewers get a prod demo
  account later.
- **Founder via PM (phone):** plug the OnePlus back in by USB and unlock it (it dropped off during R-055).
- **Founder via PM (Mac setting):** Wi-Fi adb to the OnePlus (192.168.1.4:5555) fails with "No route to host" because macOS
  Local Network privacy blocks adb under Terminal.app (this session's app since the 10-29 account move). The PM is asking her: System
  Settings → Privacy & Security → Local Network → Terminal on, then `adb kill-server` and `adb connect`. USB works meanwhile.
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
6. **Target audience:** 18+ (decided 2026-10-30).
7. **Account deletion URL:** prod delete-account page.
8. **Government, financial and health declarations:** none apply.

Store listing (public), drafted under R-052, copy final under R-057:
- short and full description, category (Business), contact email and website: `docs/store/play-listing.md`;
- 1024×500 feature graphic: cream, merged-r drafts in `~/Downloads/raviga-play-screenshots/` (her pick pending);
- 2 to 8 phone screenshots: six in the same folder, with captions.

Release:
- a prod bundle needs the prod URL and the RevenueCat key (`checkReleaseReadiness`);
- roll out internal testing first, read the pre-launch report, then closed or production. As an organization account,
  the 12-tester closed test isn't required.

## Next step (mine)

- When the PM says go: enter the App content answers from the drafts (anything public or legal goes to her first).
