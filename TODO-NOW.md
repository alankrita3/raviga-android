# Raviga Android: what's pending now

Kept current by the Android session (the founder's rule, 2026-10-28: nothing pending lives only inside a session).
The PM reads this file if the session has closed. Updated 2026-11-13.

## In flight

- **Keep open: R-132 in progress (2026-11-13, her yes via the PM): build 9 to Play's internal track only.** Build 9
  (versionCode 9, 6cbc6bf merged e094a4c, sha256 38df8ab6298b, dev server, release notes 462 characters) uploaded
  by the API as a **draft**; build 8 stays live for testers. **Waiting on her yes typed in this window** for two
  Console-level steps: (1) roll out 9 to internal testing (`tools/play_release.py rollout 9`), (2) add Meenakshi to
  the "Raviga internal" email list (Console, admin account, Chrome Profile 27). Lists in the account: Raviga
  internal (1, the only one ticked for Raviga), claude test (1), License testers (auto-generated) (4), Testers1 (3),
  Testing (2). She is already on the account-wide license-tester list. Opt-in link:
  https://play.google.com/apps/internaltest/4700044944112014609

- **R-130 done (2026-11-13, the PM; Testing Full): the v0.16.0 AI-edit live check on the OnePlus against dev**, QA
  account (paid-run ledger: 1 brief + 8 AI edits, est. $0.35). New brief "Car Wash Booking App": 5 of 5 → 0 of 5 free
  (4 Regenerate + 1 Add more, the count also above Capture's add button), the 402 confirm in the server's words, Not
  now (instruction kept, nothing charged), Buy → "4 of 5 AI edits left", then Add more (3) and Regenerate (2) worked.
  Credits history: one "More AI edits −1/12 credit" (+ "Brief written −1/12"), 150.66 → 150.50. Short-of-credits
  sheet not reached (account has 150.50; no cheap way to be short). **Bug found and fixed (28ff184, merged):** Add
  more from an open brief was collected twice (the brief screen underneath resumed the same job, R-083 since 11-03):
  two identical versions per Add more (charged once) and a blank body until reopened. Demo on the OnePlus: before the
  fix, two versions; after, one. Tests debug 300, release 262. The dev Car Wash draft keeps its duplicate versions
  (6/7, 9/10). Play internal track: build 8 (versionCode 8, 11-03, contract v0.9.7, talks to dev) only.

- **R-124 on main (3b4c4aa, 4ae0fc5, merged f3d3dfb; her decisions 2026-11-12): contract v0.16.0 AI edits and the
  11-12 Welcome copy.** briefId kept from the draft's 202 (on the draft, in exports), sent on append and regenerate;
  an old draft gets one from POST /briefs; brief_not_found swaps it once; GET ai-edits on opening a brief. Count line in
  iOS's final words (round-2 doc "v0.16.0", the PM approved them as built) under the brief's status line, on the
  Regenerate sheets (+ "Typing your own changes is always free."), Capture's add and the not-in-the-brief sheet; the
  402 confirm "5 more AI edits for 1/12 credit?" (server's message, Buy / Not now); short of credits, the credits
  sheet, and a purchase from it resends the edit with the flag; ledger "More AI edits". Welcome step 2 body and step
  3 note, How it works step 3. Tests debug 299, release 261. OnePlus: dev (old draft IPhone Android Dog: POST
  /briefs, then the 402 confirm, Not now, nothing charged, so that draft now holds a dev briefId with 0 edits; How it
  works step 3) and demo (5 free counted down, confirm, Not now keeps the instruction, Buy, Add more, ledger,
  header). Not on device: the short-of-credits sheet (demo account had 27 credits; unit tests cover it) and the
  Welcome pages (first launch only; unit test on the words). **Next: the AI live check on dev tomorrow** (no AI spend
  today, her $3 cap): a new brief's 5 free edits, the pack bought, one ledger line, to log in the paid-run ledger first.

- **R-121 on main (4aaf253, merged d149763; her decisions 2026-11-12): contract v0.15.0 and "no keep or delete
  words".** Brief in three sections (Screens and What you get retired; old drafts and earlier projects show three);
  one-price Quote (no chip, drivers, breakdown or Assumes; free-fix line under the ownership line; step 4 "You get a
  demo on your phone to try…"); every keep/delete line gone or replaced word for word with iOS's doc (lock line,
  card, consent, Privacy, cancel "Nothing was charged.", "This brief is no longer available.", approve confirm,
  Delete draft/note); hasBackend false until the builder says; demo drafter on the v0.15.0 rules. Tests debug 287,
  release 251. OnePlus on dev: all of it seen, incl. RA-0NA02Y paid, demo with --backend yes, approved, AWS step.
  Android-only call: the download caption stops at "…in a drafts folder." (no Save my drafts button here).
  Accepted by the PM 2026-11-12 with all three decisions (no Save my drafts button needed). RA-0NA02Y (approved on
  dev, Make it live) is on the PM's cleanup list. Nothing in flight; next work comes from the PM.

- **R-116 items 1-7 on main (her walkthrough round 2; iOS docs/walkthrough-round2-2026-11-11.md).** Welcome (no
  brief-price line, step 3 body, "By Raviga Apps", page 1 body), Home's empty line, keep/delete wording (AI "What we
  keep.", rights, Delete my data confirm and row), How it works step 3 without the code line. Tests debug 280,
  release 248; OnePlus: the delete confirm (dismissed with Keep) and How it works. Item 8 (contract v0.14.0, the
  five-section brief) on main too (429620f): old drafts load as five (retired dropped, must-have as Features, today's
  headings), regenerate only for the five, demo drafter in plain words. OnePlus on dev v0.14.0: an old 12-section draft
  opens as five; a new brief (five sections, regenerate Features), quoted 26 and submitted as RA-0NA02Y (accepted,
  unpaid, its brief five sections). Tests debug 282, release 249.

- **R-113 on main (48b904d, 1dfda1f; her GO 11-11): the walkthrough bundle, items 1-6**, from iOS
  docs/walkthrough-bundle-2026-11-10.md and contract v0.12.1/v0.13.0. Tests debug 278, release 246 (incl. the demo
  lifecycle: pay, launch, window start, a fix after launch, 409 after it). OnePlus pass over Wi-Fi in demo mode
  (11-11): pay confirm, Buy credits → 50+6 highlighted → demo purchase → "Credits added. Pay 28 credits to start
  building?"; demo, approve, Make it live (fee line, fold, 4 steps no AWS), live ("Your app is live", Something's
  wrong in the window), report from live → fix → "Approve this version?", window ended → Request a change alone and
  the sheet's short line; brief with 11 sections and the new deliverables; the quote's ownership line; How it works
  from Settings and the timeline. Not on device: the old-quote 409 re-quote, the 409 free_fixes_ended sheet (tests
  only), the Home card (the phone's Home isn't empty). Lock line (the PM, 11-11): "Private. We delete your brief from our servers once you approve your app." (contract:
  deleted at approval); iOS told. iOS's as-built doc (3a7c946) matched in b227649. versionCode 9 waits for her word.

- **R-109 on main (4cd526f, merged 2026-11-09; hint fac0dae): the new delivery model.** Contract v0.12.0, copy A to H,
  her Welcome page 4, the PM's decisions ("approve your app" in three lines, four small additions, the Connect AWS
  hint). Live check on dev v0.12.0 on the OnePlus (USB), project RA-EY2BXV: paid without AWS, invite emails, demo
  (TestFlight + Play), change priced 3 credits → paid → redelivered, approved, Make it live (invites, AWS via the
  aws_verified switch, publish, backend-deployed) → live. Native-only decline seen at a quote. Tests debug 262,
  release 233.
  - Play listing H done (226bdfe): full description = App Store Connect's, 2,080, read back. Short description set
    to her pick (74, read back). Play App access ("Sign in details") was never
    declared, so no old AWS line there; declaring it is a Console click that needs her typed yes.
- **Device pass 2026-11-09 (USB): R-107 re-ask, R-108 add-more order, "Brief refunded" all passed.** QA account
  cl_01m6ehvcsp now at 176.83 credits; a "Refund check" draft and two older drafts stay on the phone.

- **R-108 on main (57251c0; her decisions via the PM): "Add more" on every review**, Speak / Type / Upload a
  document; each part its own note at the end (same way twice = new part); tab switch or Back to the choices ends
  the part; review and choices list the parts in order with type; the draft's "+" reads "Add more". Nine orders +
  chains tested (debug 251, release 224). **Device pass pending (adb)**, with the scripted dictation for Speak. The
  QA suite's three "Record more" / "Edit what I typed" rows need the PM's update.
- **Record more / owed (78885ea):** Record more shows the reviewed text, then only the new take (as iOS de0be16);
  "You need X more" rounds up. "Terms of Use" everywhere (65c09f4).

- **R-107 on main (4c41d1a; her decision via the PM, contract v0.11.4): AI consent merged into "Before you
  start".** One PUT {termsVersion, privacyVersion, aiProcessing:true, analytics:true only if on}, also for the
  re-ask; no separate AI sheet, no Settings AI switch (the AI_CONSENT route shows the merged sheet with "Before you
  start"). iOS's verbatim AI section (four lines with a lead-in), "Allow AI and continue", the caption; "Not now" on
  the first sheet and the AI re-ask only. iOS's choices 2-6 (Your rights unchanged, Settings row, re-ask heading,
  consent_required line, Not now) are with the PM; her answer may change them. Unit tests debug 244, release 217.
  **Device check pending** (adb, below).
- **Contract v0.11.3 on main (c6ff760):** "Brief refunded" ledger line (+1/12 credit, as iOS 507af2e); Capture
  refreshes credits after a draft fails with ai_unavailable/internal. Not checked on the phone (adb below).
- **The OnePlus is on USB (2026-11-09, her plug-in): serial `3bdf6ba7`.** Wi-Fi adb doesn't hold from this Mac: its
  Wi-Fi address is now 192.168.1.12 (was .4); `adb tcpip 5555` works, but a server started from this session gets "No
  route to host" (macOS Local Network), and one started from a Terminal window connected once, then went offline. She
  keeps USB for today.
- **Post-launch backlog (the PM's call, via backend):** install fingerprint so the free brief can't be farmed: the
  per-app ANDROID_ID, hashed, sent at register and recover; backend sends a short contract when picked up. Data safety
  would then declare a device ID.

- **2026-11-04, her walkthrough decisions (via the PM), all on main:**
  - Welcome: four pages, AI not named (0593263, a8cb447); chip 1 "Pay only when it's a go".
  - R-106 (contract v0.11.0): no GitHub; the code and the handover guide are delivered in the client's AWS
    (82370ba). Play listing equals iOS's App Store text (1,947 characters, read back).
  - **R-099 (contract v0.11.1-2): brief pricing, 3a879f3.** First brief free, then 1/12 credit; line above "Looks
    right, write the brief" (iOS's rule: free only when /me's freeBriefsLeft isn't 0 and the phone has no other brief
    or submitted project; no line on a server without brief pricing); 402 sheet with the smallest pack; credits_quarter
    row; ledger "Brief written" / "−1/12 credit". Checked live on the OnePlus against dev (QA account
    cl_01m6ehvcsp: 203 → 202.9167; screenshots r099-* in the session scratchpad). Unit tests debug 240, release 213.
  - Play product **credits_quarter** active (US$2.49, ₹209, 174 regions; ce48c51). RevenueCat: all five Play products
    imported and attached to the "default" offering. The app takes packs only from /config (no built-in list), so
    prod (v0.9.9 until the launch deploy, ignores quarter purchases) never shows it.
  - Play listing has R-099 sentence O, word for word as App Store Connect (2,033 characters, read back; 1395cf0).
  - Next upload is versionCode 9 (R-103, R-100, R-105, R-106, R-099), only with her yes; prod release only after the
    launch deploy.

- **Android is code-complete for build 8 (the PM, 2026-11-03).** Gate file
  `~/Documents/Raviga PM/qa/results/2026-11-02-android-gate.md`: 23 signed, 7 open, all hers.
  - Hers: row 37 (two lines), voice, entering Data safety, the rollout click. The release notes with versionCode 8
    and the upload: her yes, 11-03 (the gate file is the PM's to update).
  - The TalkBack listen-through is in her pass on the internal build.
- **Build 8 uploaded to Play's internal track as a draft (2026-11-03 12:53, her yes via the PM).** build-8 merged
  into main (0e5f114). Draft "8 internal test": versionCode 8, status draft, en-US release notes (446 characters),
  sha256 79de35d565a8…, dev stage. It replaced the never-rolled-out versionCode 1 draft.
  **Rolled out to internal 16:44** by the Play service account (API edit draft→completed, commit 200; her typed yes).
  The robot can't do the email tester list, its opt-in link or license testers (Console only).
  **Data safety applied 16:48** through the API (`tools/play_release.py data-safety docs/store/data-safety.csv`;
  Play accepted it). Partial deletion is No until the legal site has a "Delete some of your data" section (backend
  is drafting it for her legal read), then switch to Yes and re-apply. License testers are held until row 37 (the
  PM: an account-wide setting that also touches the Mirakee apps).
  - **The PM (13:00): we do the Console clicks in Chrome ourselves.** These are: (1) the rollout, (2) the tester list
    with administrator@miraquill.com, (3) Data safety from `docs/store/data-safety.md`, (4) license testing,
    RESPOND_NORMALLY. Steps and clicks are in `docs/store/internal-test.md` sections 3 and 4.
  - **Her yes, typed in this window at 16:3x:** "yes: accept the Play invite, build 8 internal rollout, tester
    list, Data safety, license testing".
  - **Testers done (about 18:50, as the admin account in Chrome Profile 27):** invite accepted (Raviga Apps; the
    admin account sees only Raviga, app 4972798470571758057). The new email list "Raviga internal" (the admin account,
    see the Play Console tester list) is the only list ticked on internal testing. The track reads "Active · Latest
    release: 8 internal test". **Opt-in link: https://play.google.com/apps/internaltest/4700044944112014609**. On her
    phone, signed in as the admin account: open it, tap "Become a tester", install from Play. Play shows the temporary
    name "com.raviga.app (unreviewed)" until the app is reviewed.
  - Play store listing (en-US short and full description) set by API from `docs/store/play-listing.md` (a785f72). The
    four credit packs exist and are active.
  - `docs/store/data-safety.md`: the Play form's answers in order (step 3).
- **Row 37 (RevenueCat Play app), in progress since 19:30, the PM's go.** The split:
  - Her decision (about 20:00): no new service account. RevenueCat uses the existing Play robot's key
    (~/.config/raviga/raviga-play-publisher.json; path sent to the PM, never the contents). The PM adds View financial
    data and Manage orders and subscriptions to the robot on com.raviga.app, and uploads the JSON into RevenueCat (her yes).
  - Pub/Sub (real-time notifications): the robot has no GCP permissions (403), so nothing was enabled. It's optional for
    purchase checks. Turning it on needs the owner to enable the API and give the robot Pub/Sub Admin (an IAM change,
    so it needs her yes; backend can do it with gcloud).
  - **Done (about 20:40):** robot's financial access verified by API (voided purchases 200). RevenueCat Play app
    created in project Raviga (4cdddfbb): "Raviga (Play Store)", com.raviga.app, **app id app01d8cd2f54**. The PM
    uploaded the JSON ("Valid credentials"). Pub/Sub notifications not connected (later). goog_ key in
    secrets.properties (written blind from the clipboard, clipboard restored). App id sent to backend for
    REVENUECAT_APP_IDS on prod. Release APK against prod with the key built locally (not installed, not uploaded).
  - **Waiting:** backend to confirm prod takes app01d8cd2f54; the license tester for the admin account (the owner's
    page; the Mirakee PM has the ask). Then: install the prod release APK on the OnePlus (device lock), one Play
    sandbox purchase, check with `team.py --stage prod ledger --sandbox`.
  - Done: backend's prod URL is in secrets.properties (API_BASE_URL), and Play's listing has the six build-8
    screenshots, the feature graphic and the icon (f0a91aa).
- **Play screenshots at 30 credits (raviga-design, the PM's direction): recaptured 21:40 after her review.**
  `~/Downloads/raviga-play-screenshots/build8-30credits/`: 01-07 plus 06b (team comment) and 06c (approved), with
  captions.txt. One dev account cl_01m6h22gp4, project RA-TGQ2B0, approved by team.py with a note (no deliver: that
  runs a real GitHub transfer). **The dev release app (com.raviga.app) stays installed on the OnePlus for more
  shots;** uninstall it before installing the prod release APK for the sandbox purchase (different stage, same package).
  The first set (RA-AYVR3V) is in `superseded-RA-AYVR3V/`; its account went with an uninstall.
  - Her review fixes, pushed: f4b8f94 (the quote's lock line on one line, centred with an inline lock; 13 to 12sp,
    then balanced wrap) and 0838142 ("This price holds until 17 Nov.", the PM's wording; iOS 605c803 added the same).
- **R-105 done on main (7e1814f; contract v0.10.2, dev): Connect AWS before Pay**, iOS's copy; Make it live and the
  Backend line from delivery.aws (consoleUrl, statusLine). Live on dev (RA-STT9ES). Play listing sentence 7e02f8a
  (read-back equal, 1,957). QA on dev: `team.py fault aws_verified --client <id>` then Check connection, before any pay.
  iOS's K/L tables run on the OnePlus (Raviga PM c3e17a6): K 5 pass + 3 not run (K2 server email, K4 server path,
  K8 airplane mode over wireless adb), L 8/8, I9 pass; 0 fail. **Next:** her screen-by-screen review decisions via the PM.
- **Store screenshots parked until the product is final (her call, 2026-11-04).** No captures for design meanwhile.
- **R-103 merged into main (1a13412) for versionCode 9 only; prod stays v0.9.9 until the PM says.** Do not build a
  prod release from main before that. Follow-ups on main: PM parity calls (18cbbfc: step 1 "We check your brief
  against what we build.", no delivery note, Credits empty line), "Ready in … after payment" (1acdf9b), dates in
  statusLines kept on one line (caef6c6). Unit tests debug 234, release 209.
  - R-103 Play captures delivered (captions.txt in build8-30credits/; raviga-design's session closed, relayed via the
    PM).
  - **QA v0.10.0 (PM split):** suite I3, O9/O10, Q rewritten (Raviga PM d796cd3, Q6 corrected 15b2ad1); run on
    the OnePlus against dev: 21 pass, 0 fail (`~/Documents/Raviga PM/qa/results/2026-11-04-android-v0100.md`);
    one bug found and fixed (cf5d296, "you.." on a closed brief). Section M (push): 4 pass; accepted/building not
    testable on dev (pushes only on team actions; the review path can't be forced). **Waiting:** iOS's rewritten K and
    L, then run them; J waits only on the license tester (RevenueCat prod webhook and v2 key done, per the PM).
  - 05-quote: PM decision (c), design masks the "after approval" line; noted in captions.txt.
  - Screenshot account cl_01m6h22gp4: GitHub target cleared (no transfers on deliver). Debug account
    cl_01m6ehvcsp: GitHub target cleared too. A third salon draft (quoted 29) stays on the phone.
- **R-100 on main (1477bdd):** "Price valid until <date>." small grey at the end of the quote; expired → "Get a new
  quote" + "Prices are updated every three months." (as iOS a5f8fc9). Supersedes 0838142's line. For the next build.
- **03b-speak done (her pick for screen 1):** real Speak screen via the debug-only scripted dictation (8fa0c34):
  `files/dictation-script.txt` in com.raviga.app.debug (run-as), deleted after. Mic permission back to not granted.
- **05-quote retaken after R-101 (done ~22:30):** same account, a second local draft (same title) quoted at 30 (25, 29,
  30), shot about 30px below the top so the paragraph ends above Submit. Old shot in `superseded-RA-TGQ2B0-quote/`. The
  second draft stays on the phone for free re-shots of the quote. **Status-screen captures on hold** (no client
  messaging; R-103 contract coming); 06b is held.
- **Sandbox purchase:** prod accepts app01d8cd2f54; RevenueCat prod webhook and v2 key done (PM, 11-04). Waits only on
  the license tester (owner). Note: main is now v0.10.0, so the prod release APK for the purchase must wait for prod
  v0.10.0 or be built from the last v0.9.9 commit (d02c71b + R-100 fixes) on the PM's call.
- **Next step (mine):** the sandbox purchase once both waits clear. Then fix whatever her pass on the Play build
  turns up. The next upload is versionCode 9 (+1 per upload).
- **Unit tests** debug 240, release 213; instrumented 6 (run with `am instrument`, never `connectedAndroidTest`: it
  uninstalls the app and its dev client). Kotlin warnings 0, lint errors 0, no `!!`.
- **The OnePlus:** `tools/device_lock.sh take 3bdf6ba7 <session>` (USB; Wi-Fi 192.168.1.12:5555 when it works) before using it, `give` after.
- **Emulator:** started only with load under 20. It was shut down from outside this session twice today (08:0x, 09:1x).
  The PM told the iOS sessions not to stop processes they didn't start. If it happens again, tell the PM with the time.
- **Test accounts:** backend deleted the dev clients on 11-03 (purge 11-10): cl_01m6efysre, cl_01m6ehqybe,
  cl_01m6ek2f8s, cl_01m6ekpkmp, cl_01m6fj8etv, cl_01m6fpnbc8 (RA-WJEKYD rejected first). cl_01m6ehvcsp… stays (the
  OnePlus debug app's QA account). The two short-lived accounts from Agree-before-Restore were not named.
- **After the suite (when the PM says the QA account can go):** restore the OnePlus's demo account
  cl_01m63jpnye… with its key file in ~/Downloads (key never printed), then ask backend to delete cl_01m6ehvcsp….
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
