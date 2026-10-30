# Raviga Android: what's pending now

Kept current by the Android session (the founder's rule, 2026-10-28: nothing pending lives only inside a session).
The PM reads this file if the session has closed. Updated 2026-10-30.

## In flight

- **R-066 (her review of the iOS builds, for both apps; via the PM 10-30): done, reported to the PM.** Welcome
  subline; onboarding navigation (Get started → step 1, Next and a graphite Skip, Start; Settings › How Raviga
  works replays the pages); the 13 style-D pictures (converted from iOS's PNGs, identical to iOS 6ac85eb); the
  Welcome hero cropped from the top (focus 0, not 0.2: on Android a higher focus lifts the brief under the status
  bar; the PM and iOS told); consent "Your rights" text and "Privacy policy · Terms of service · Your rights"
  (contract v0.9.3 `legal.rightsUrl`, also on Privacy and data); "What we don't keep" in her words on the
  consent screens and Privacy and data. Checked on the OnePlus (screenshots in
  `~/Downloads/raviga-device-checks/2026-10-30-r066/`). No Play upload until the PM says.
- **OnePlus stay-awake:** `svc power stayon false` is killed on the phone since 10-30 evening (exit 137), so
  Developer options › Stay awake is still on (setting 7) while it charges. She can switch it off there; the PM
  knows.
- **R-055, R-056 and R-060's colours: device checks done 2026-10-30** on the OnePlus (Wi-Fi adb works again).
  Screenshots in `~/Downloads/raviga-device-checks/2026-10-30-r055-r056-r060/`. The checks found two R-056 bugs,
  now fixed: release builds refused GET /config before Agree (R8 renamed the method the guard matched by name; it
  now matches the route), so Agree failed on a fresh install; and Home was blank before Agree instead of showing its
  empty state.
- **R-060 (her decisions 10-30, via the PM):**
  - 1, India prices: done. Play now sells credits_10 / 25 / 50 in India at ₹8,499 / ₹21,249 / ₹42,499 (were
    ₹11,200 / 28,100 / 56,200); credits_100 stays at ₹84,000. Read back with `tools/play_release.py regions`; the
    before/after diff (696 rows, and the full product JSON) changed only those three India rows. `FIXED_PRICES` keeps
    them on a re-run of `product`. All four /config hints match Play (backend c5f333b: credits_100 84000, live on dev);
    DemoApi mirrors it, pinned by a unit test. Verified by the PM.
  - 2, app colours C (R-049/R-059): code done with iOS's tokens (palette C: cream paper, navy ink, the logo's blue
    for marks, link blue #3B54C4 for text and controls, marigold unchanged); the wordmark on the launch screen, the
    loading screen and Welcome page 1. Unit tests 72/72 (PaletteTest pins the tokens to iOS and checks contrast).
    Device check done 2026-10-30 (splash, loading, Welcome, Home). It goes into the sprint build.
  - 3, registered address in `docs/HANDOFF.md` item 8: done (the Dwarka address; backend puts it in /config, the app
    shows no address).
- Last shipped: R-057, closed 2026-10-30. Her final store copy in `docs/store/play-listing.md` (short description B,
  opening B plus her sentence, 1,853 characters; 18+) and her feature graphic,
  `feature-graphic-cream-merged-two-squares.png` (alternates kept). Not uploaded to Play yet.

## Blocked, and on whom

- **Founder:** RevenueCat project, with the Android `goog_` key and Play credentials, then `REVENUECAT_API_KEY` in
  secrets.properties. Until then purchases can't be tested end to end, and neither can the analytics events
  `credits_purchased`, `project_submitted` and `delivery_accepted`.
- **Backend + founder:** the prod stage (`API_BASE_URL`). Until then only internal-test bundles against dev exist
  (`bundleRelease -Praviga.internalTest=true`).
- **Founder:** BillDesk KYC for sales outside India, due 15 Mar 2027 (Play Console banner; contact team@mirakee.com).
- Store listing: copy, 18+ and the feature graphic are decided (R-057). The dev demo project RA-Q3FJ24 is rejected
  ("screenshot demo"); store reviewers get a prod demo account later.
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
- 1024×500 feature graphic: her pick, `feature-graphic-cream-merged-two-squares.png` in `~/Downloads/raviga-play-screenshots/`;
- 2 to 8 phone screenshots: six in the same folder, with captions.

Release:
- a prod bundle needs the prod URL and the RevenueCat key (`checkReleaseReadiness`);
- roll out internal testing first, read the pre-launch report, then closed or production. As an organization account,
  the 12-tester closed test isn't required.

## Next step (mine)

- When the PM says go: enter the App content answers from the drafts (anything public or legal goes to her first).
