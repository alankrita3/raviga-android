# Raviga Android — handoff: what only the founder can plug in

Everything below is a placeholder today. The app builds, runs and demos without any of it: with no
backend URL it uses the in-app demo backend, and the debug build already points at the backend's dev
stage. Each item says where the value goes. Backend and iOS keep their own lists; items marked (shared)
are the same credential used by more than one repo.

## Accounts and keys

| # | Item | Where it goes | Status |
|---|---|---|---|
| 1 | **Play Console app** for `com.raviga.app` (Raviga Apps Private Limited), name "Raviga" | Play Console → Create app | done 2026-10-28 (Google Play, below) |
| 2 | **Four consumable in-app products**: `credits_10`, `credits_25`, `credits_50`, `credits_100` with USD base prices ($99.99 / $249.99 / $499.99 / $999.99, from `/config` `priceHintUsd`; let Play convert to local currencies; the backend's `/config` carries the credit amounts) (shared with the App Store) | Play Console → Monetise → In-app products | done 2026-10-28, all active (Google Play, below) |
| 3 | **RevenueCat** project with the Android app, the Google public SDK key `goog_…`, Play service-account credentials, and the webhook URL + shared secret from the backend (shared) | `secrets.properties` → `REVENUECAT_API_KEY`; backend SSM | missing |
| 4 | **Firebase project** with the Android app `com.raviga.app` (and `com.raviga.app.debug` for debug builds) added; download `google-services.json`; give the backend the service-account JSON for FCM (shared) | `app/google-services.json` (gitignored) | in place: Firebase project `raviga-e6b92`, file on this Mac (2026-10-21); the app registers its FID with dev. Backend's service account is next |
| 5 | **Upload key** for Play App Signing: a keystore plus `keystore.properties` with `storeFile`, `storePassword`, `keyAlias`, `keyPassword` | `keystore.properties` (gitignored) | missing; release builds are unsigned until then |
| 6 | **Production API URL** once the backend deploys prod | `secrets.properties` → `API_BASE_URL` | placeholder |
| 7 | **Legal pages**: the backend publishes Terms and Privacy as a static site (dev: `https://d1mi85jr39d7la.cloudfront.net/terms-of-use`, `/privacy-policy` and, since v0.9.1, `/delete-account`) and `/config` points at them; the app opens whatever `/config` says. Read them once before launch | backend | served; needs your read-through |
| 8 | **Registered company address** (your decision, 2026-10-30, R-060): Plot No-7, Manzil Apartment, Flat No.11, Sector 9 Dwarka, New Delhi 110077. The grievance email (membersupport@miraquill.com, 15-day response) and support email are live in `/config`. Since contract v0.9.2 (R-055) the app shows no person's name and no address, only the one-line grievance route | backend `/config` → `legal.companyAddress`, `legal.grievance.address` (backend sets it) | confirmed |
| 9 | **Play Data safety form** and the microphone / notifications declarations; no audio or files leave the phone (speech is recognised on the device); the app sends project text (to OpenAI, to write and price briefs), a device identifier and purchase history | Play Console → App content | to fill |
| 10 | **AWS role policy** for `RavigaDeployRole` (currently AdministratorAccess per the backend contract); the Connect AWS screen describes the role in plain words | backend CloudFormation template | to decide |
| 11 | **Anything you registered under the old app id** (the old name after `com.raviga.`): items 1, 3 and 4 were not created as far as this repo knows, but if any were, create them again for `com.raviga.app`. App ids can't be renamed in Play Console or Firebase | Play Console, Firebase, RevenueCat | check |
| 12 | **OpenAI Zero Data Retention**: until OpenAI approves it, OpenAI may keep the text of notes and briefs up to 30 days for abuse checks; the privacy page and `/config`'s provider line say so meanwhile (shared) | OpenAI account; backend provider line | to apply |

## Google Play (2026-10-28)

- Developer account: organization, "Raviga Apps" (renamed from "Miraquill Quotes & Poetry"; this also shows on Mirakee's
  listing). App "Raviga" (`com.raviga.app`, Play app id 4972798470571758057), free, English (US).
- Internal testing: a draft release, versionCode 1, built with `./gradlew bundleRelease -Praviga.internalTest=true` (dev
  server, upload key in `~/.raviga-android-signing/`). Play keeps the app signing key (Play App Signing).
- In-app products (consumables), all ACTIVE: `credits_10` $99.99, `credits_25` $249.99, `credits_50`
  $499.99, `credits_100` $999.99. Your rule (2026-10-28): where Play's local maximum is below the converted price, the pack
  sells at that maximum. That applies to `credits_100` in 76 countries (India ₹84,000, UK £810, Germany €940, Japan ¥155,000,
  Canada CA$1,380). Korea: 10 and 25 only (her call 10-28). There the cap made `credits_50` and `credits_100` the same
  ₩600,000, about 59% off for the 100 pack, so both are not sold in Korea (`NOT_SOLD` in `tools/play_release.py` keeps a re-run
  from adding Korea back). Sold in: 174 / 174 / 173 / 173 regions. India's prices for the smaller packs
  were set to your plan prices on 2026-10-30 (R-060): ₹8,499 / ₹21,249 / ₹42,499 (Play's conversions had been ₹11,200 / 28,100 /
  56,200). `credits_100` stays at the ₹84,000 cap. Change one with `tools/play_release.py set-price <pack> IN <amount>`; read
  every price back with `regions`.
- API access: `tools/play_release.py` with Raviga's own service account (raviga-play-publisher@raviga-e6b92). Its key lives
  in `~/.config/raviga/`, outside the repo; it can release to testing tracks and manage products for Raviga only.
- BillDesk KYC must be completed by 15 Mar 2027 for sales outside India (payments contact: team@mirakee.com).

## Verified against the backend dev stage (2026-10-09)

The whole lifecycle ran from the Android app on an emulator against the real dev API: register →
terms → typed description → AI consent → generate (real OpenAI draft, job polling) → the rendered brief →
quote (93 credits, Standard) → submit (document locked, team emailed) → team comment → edit Summary (version 2)
→ re-quote (95) → resubmit (2-credit difference charged) → approved → team comment → delivered → revision
requested → delivered again → AWS done → accepted. Also: versions list and preview, credits ledger, delivery
targets, AWS connect (external ID issued), settings, privacy screen. All 31 of the iOS session's captured dev
responses decode with the Android models (`app/src/test/resources/fixtures`).

Contract v0.5 (document upload, screening, retention) was then run on dev the same way: re-consent
to the 2026-10-09 terms → upload a PDF → "Here's what we read" → file input → generated brief, and a
policy refusal shown with the appeal line. The on-device check for keys, cards, Aadhaar and PAN, the
refused-project block and the demo backend's equivalents were exercised in demo mode.

Contract v0.6 (local-first) was run on dev too: re-consent to the 2026-10-10 terms → typed notes with an
AWS key kept on purpose → `/ai/draft` (server removed it, notice shown, local note redacted) → `/quotes` (104)
→ `POST /projects` (RA-DWPJBD, the first time the brief reached the server) → team requested changes → edited
on the phone → re-quoted (95) → resubmitted (9 credits back) → approved → delivered → accepted → the server
deleted the brief and title, and the phone's copy is still readable. Drafts on disk were checked to be
ciphertext. A PDF and a Word file were read on the phone, in debug and in the R8 release build.

After the rename to Raviga (contract v0.8), the same flow ran on the new raviga-* dev stack from
`com.raviga.app.debug`: register → terms → AI consent → generated brief → quote (34, Standard) → submit
(RA-Q88NBG) → approved → delivered (raviga-builds repo, handover link) → the `raviga://project/{id}` link
opened it → accepted, with the brief kept on the phone after the server deleted its copy. Connect AWS
showed `RavigaDeployRole` and an `ra-ext-` id. The R8 release build `com.raviga.app` installs and runs.

The minified release build (R8, signed with a local debug key) was also installed and run against dev:
register → terms → typed description → AI consent → generated brief → quote, all decoding correctly.
`./gradlew bundleRelease` refuses to build until items 3 to 6 are in place (`checkReleaseReadiness`), so
a Play upload cannot ship the demo backend by accident; `assembleRelease` stays open for local checks.

Not exercised end to end: voice capture (the emulator has no on-device speech recogniser; on a real phone
running Android 12+ the on-device `SpeechRecognizer` drives live dictation and the ink line), OCR of a scanned
PDF (ML Kit's model comes from Play services on first use), store purchases (no RevenueCat key), and push
(no `google-services.json`). The code paths exist and compile; they need the items above.

## Decisions I made that you may want to change

- Local-first, per your request: drafts are sealed on the phone (AES-256-GCM, Keystore key, no backup), so
  a draft cannot be recovered on another phone or after a reinstall; only submitted projects move with the
  recovery key. Voice needs Android 12+ with an offline speech model (most phones sold in India since 2022);
  older phones type or upload instead, because the only alternative would send audio to Google's cloud.
  Decided (founder, 2026-10-21, final): voice stays off on phones without an on-device recogniser (checked on a
  OnePlus 7, Android 12, which has none); they type or upload.
- Reading PDFs on the phone adds about 6.5 MB to the app (PDFBox); scans use ML Kit through Play services.
- 1 credit = $10 (contract v0.7; a typical clinic booking app quotes about 30 credits); brackets micro/starter/standard/pro/enterprise; two included revision rounds; quotes
  valid 14 days; auto-accept 14 days after delivery; the server deletes a brief when its project closes. All of these live in the backend
  `/config`; the app renders whatever it is sent.
- Notifications permission is asked once on the home screen (Android 13+). Change in `HomeScreen.kt`.
- Light appearance only. The "Workshop" design you approved on iOS (Bricolage Grotesque and Figtree, warm
  white, teal and marigold, clay pictures), built from the same font and picture files. The app icon is your
  pick, option A (2026-10-23): a navy "r" with a floating blue square on cream (#14183A, #6F8CFF, #F6F3EE), the
  same as iOS. It is drawn as vectors from your SVG in `res/drawable/ic_launcher_foreground.xml` (on
  `ic_launcher_background.xml`), `ic_launcher_monochrome.xml` (themed icons), `ic_splash_mark.xml` and
  `ic_notification.xml` (white). `docs/icon/` keeps the source SVG, the 1024 PNG and the 512 px Play Store icon.
  None of the clay pictures show the old name.
- Speech is English only (US, UK, India or Australia accent, from the phone's region), as you asked.
- `minSdk 26` (Android 8.0, covers ~97% of Indian Android devices); `targetSdk 36` as Play requires.

## Risks to know before store review

- **Google Play's payments policy** (like App Store guideline 3.1.3(e)): in-app billing is meant for digital
  content used inside the app. Raviga sells a real-world service delivered outside the app. Review may
  push back. Options: position credits as a prepaid digital balance consumed in-app for AI drafting and
  quoting (true today), or move project payment to an external invoice. Decide before submission; no code
  depends on it beyond the Credits screen copy. Policy, fees and a recommendation: `docs/play-payments-policy.md`.
- The microphone is only used while the Capture screen is listening, and speech is recognised on the phone.
  The Data safety form can say no audio or files are collected; the text of notes and briefs is sent to
  OpenAI to write and price briefs (kept up to 30 days for abuse checks, per the backend's provider line),
  and submitted briefs are stored until the project closes.

## How to run it tomorrow

1. Open the folder in Android Studio, or from a terminal: `./gradlew assembleDebug`.
2. Plug in the OnePlus (or start the `Raviga_API34` emulator; the `Pixel_6_API_34` one has a pattern
   lock) and press Run, or `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
3. The debug build talks to the backend's dev stage (`secrets.properties`, already on this Mac). Drafting
   uses OpenAI; credits on dev need the backend's admin flow until RevenueCat is connected.
4. For a self-contained demo with no network: Settings → "Demo backend (debug)" on, restart the app. Then
   Describe a project → "Use a sample description" (or speak) → the brief writes itself → Get a quote → Buy
   credits (instant) → Submit → Settings → Demo controls → "Advance the latest project" to play the team's
   moves (comment, approve, deliver) → Accept.
