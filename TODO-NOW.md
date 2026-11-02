# Raviga Android: what's pending now

Kept current by the Android session (the founder's rule, 2026-10-28: nothing pending lives only inside a session).
The PM reads this file if the session has closed. Updated 2026-11-03 00:15.

## In flight

- **Build 8 gate (her order 11-02, `~/Documents/Raviga PM/qa/BUILD-8-GATE.md`): in progress.**
  - Done: compiler warnings 0, Lint 0 errors (lint.xml justified), OSV 0 advisories, deps current for AGP 8 (AGP 9 and
    OkHttp 5/Retrofit 3 waived by the PM), RevenueCat 10, session store off EncryptedSharedPreferences, no `!!`, secret
    scan clean, no client text in logs/analytics. Gate file: `qa/results/2026-11-02-android-gate.md`.
  - QA suite P0 done (`qa/results/2026-11-02-android.md`): 7 FAILs fixed (AI consent on every AI call, D4 note limit,
    H9 brief size, F5 10-note limit, G2 save on kill, G7 swipe spring-back), voice blocked (no recogniser).
  - R-079 done (d202008, pushed): recovery email, link codes, phones with access, sign out this phone, Protect your
    credits; checked on the OnePlus (dev and demo). Lint warnings in code: 0 (d23f6cd).
  - R-082 done (f5f5645, pushed): drafts named from the first note with iOS's rules; Gemini Nano (ML Kit Prompt API
    beta5) where the phone already has it; client renames never replaced. OnePlus checked (fallback; no Nano there).
  - Suite P1 in progress (results in `qa/results/2026-11-02-android.md`): N1-N8, M4, O8 PASS (fixes: O8 removed-phone
    notice f1ed6da, M4 notifications Off on Android 12 909fda1, N4 drafts line fccde21; plus an empty duplicate draft
    after fast typing, f5142e0). P1 and P2 (offline half) PASS on the emulator (PM's OK). P4 battery PASS. P6 PASS (Home's
    "Edited …" now recomputed on return, 4e9251d). B6 re-run PASS with backend's per-client terms_bump (a real re-ask).
  - Gate: "no demo or debug code in Release" done (11667ee: demo backend in src/debug, demo UI behind
    BuildConfig.DEBUG, strings check clean); logs/analytics/secrets and `!!` lines re-checked and ticked.
  - M1 (paid run): tap a real Raviga notification with the app closed, in the background and in front; read
    `logcat -b crash` after each (iOS crashed on the tap). Pre-check 00:58 with the raviga://project link (unknown id):
    no crash in any state, stays on Home.
  - I3 code done (4fa33a2, as iOS): expired quote says so from expiresAt (clock ticks at expiry), server's quote_stale
    message shown, no automatic re-quote. Device check with backend's quote_expiry switch in the paid run.
  - Next: the paid P1 cases on 11-03's budget (today's ledger is at $2.91 of $3): L1-L8, M1-M3, M5, O1-O4, then O5-O7
    with backend's switches; P1-P3/P5 offline cases need the emulator (asked the PM; airplane mode cuts Wi-Fi adb).
    Then P2 (Q, R); release-APK inspection; the independent review; build only with her yes. R-083 after the suite.
  - Dev clients to delete after the suite (backend): cl_01m6efysre00xd9cpwwd1mc9qk (throwaway, its phone signed out in
    the R-079 check), cl_01m6ehqybe00nzgdstet9sy868 (made on restart after that sign-out, then signed out too), and
    the one in use now, cl_01m6ehvcsp00shvab7t7r3mnrw; plus the O8 re-registrations cl_01m6ek2f8s00q5et6rse3jbjdh and
    cl_01m6ekpkmp00ksmjy1tqf9ccab (backend has them), and the emulator's P1/P2 client cl_01m6enrsky004dc42e71h7ppv2. The recovery email on cl_01m6ehvcsp... is the SES test address. The OnePlus's demo client cl_01m63jpnye... is kept; restore it
    on the phone with its key file in ~/Downloads after the suite. Backend deleted the emulator's R-076 client.
  - Backend 52fe82c on dev: the H10 notice names the file (no app change); an I3 quote-expiry switch
    (`team.py --stage dev fault quote_expiry --client <id> [--minutes N]`) for the expiry check in the suite.
  - Row 37 (RevenueCat Play app) waits for her at the Mac (sign-ins, two yes lines here).
- R-074 done (61b1f5f, emulator check passed); R-075 done (bb7c264); R-076 smoke done (voice blocked).
- **Drafts saved as you go (her bug report via the PM, 2026-11-02): done, reported to the PM.** Typed, spoken and
  uploaded notes are saved in the local draft while they're made; drafts with no brief take more notes ("+"), and
  notes can be edited or deleted; Home deletes a draft by swipe or long-press, with a confirm; a written brief
  shows "N notes not in the brief yet" and adds them in one tap (c8fc19f, ecde5a3). Checked on the
  OnePlus on every exit path (screenshots in `~/Downloads/raviga-device-checks/2026-11-02-drafts/`). Speech can't
  be tried on the OnePlus (no on-device recogniser); it goes through the same autosave as typing.
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
1. **Privacy policy URL:** `https://legal.ravigaapps.com/privacy-policy` (live 2026-10-30).
2. **Data safety form:** draft answers ready, including analytics. Enter them, founder to confirm.
3. **App access:** reviewer instructions with a team.py demo account and the verified restore taps (8 steps).
4. **Ads:** none.
5. **Content rating (IARC):** questionnaire.
6. **Target audience:** 18+ (decided 2026-10-30).
7. **Account deletion URL:** `https://legal.ravigaapps.com/delete-account` (live 2026-10-30).
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
