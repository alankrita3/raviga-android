# Raviga — Android

Android client for Raviga: describe the software you want by voice or text, get a
proper requirements brief written by AI, edit it, get a quote in credits, submit, and
receive the finished code as a GitHub repository (optionally deployed to your AWS account).

Operator: Raviga Apps Private Limited, India.

## Related repositories

- [raviga-ios](https://github.com/alankrita3/raviga-ios) — iOS client (owns the shared design system)
- [raviga-backend](https://github.com/alankrita3/raviga-backend) — API, AI, email flow (owns the API contract)

## Status

Every screen in the shared design is built: Welcome, Terms, Home, Capture (live dictation
with the ink line), AI consent, Transcript, Drafting, Document (edit, regenerate, versions),
Quote with delivery targets, Connect AWS, Project status with team comments, Delivery with
revisions, Credits, Settings (delivery targets, notifications, recovery key, privacy and data).

The debug build talks to the backend's **dev** stage when `secrets.properties` names it.
Without that file the app runs on a built-in demo backend that implements the same API in
process (with a fake review lifecycle), so every screen can be exercised on any phone.

## Getting started

```bash
git clone https://github.com/alankrita3/raviga-android.git
cd raviga-android
cp secrets.properties.example secrets.properties   # fill in what you have; all keys optional
./gradlew assembleDebug
```

Open the folder in Android Studio (Ladybug or newer, JDK 17+) and run `app` on a device or
emulator with API 26+.

### Configuration (all optional, all gitignored)

| file | what it holds |
|---|---|
| `secrets.properties` | `API_BASE_URL_DEBUG`, `API_BASE_URL`, `REVENUECAT_API_KEY` |
| `app/google-services.json` | Firebase project for push notifications |
| `keystore.properties` | path and passwords of the Play upload key for release builds |

- No `API_BASE_URL*` → the in-app demo backend serves everything.
- No `REVENUECAT_API_KEY` → purchases are unavailable; the demo backend grants credits directly.
- No `google-services.json` → the app builds and runs, but receives no push notifications.
- Debug builds have a "Demo backend" switch in Settings that overrides the URL on next launch.

## Build and test

```bash
./gradlew assembleDebug        # debug APK at app/build/outputs/apk/debug/
./gradlew test                 # unit tests (JVM)
./gradlew assembleRelease      # minified; signed only when keystore.properties exists
```

## Architecture

Kotlin, Jetpack Compose, Material 3, single activity, hand-wired dependencies.

```
com.raviga.app
├── data/api        Retrofit interface + DTOs for api-contract.md v0.9, errors, job polling
├── data/demo       In-process demo backend (DemoApi) and its heuristic brief writer
├── data/drafts     Drafts on the phone: sealed JSON files under a Keystore AES-GCM key
├── data/files      On-device document reading (PDFBox, ML Kit OCR, Word/RTF/text) and export
├── data/local      EncryptedSharedPreferences session, DataStore prefs, JSON cache
├── data/repo       Session, Draft (local-first), AI (stateless), Project (submitted), Credits
├── data/screening  On-device check for keys, passwords, cards, Aadhaar and PAN
├── data/billing    RevenueCat over Google Play Billing
├── data/audio      On-device SpeechRecognizer dictation (Android 12+, never the cloud)
├── push            FCM service, token registration, notifications
├── di              AppContainer (one per process)
└── ui              theme, components, nav, one package per screen
```

- **Identity**: no sign-in. A stable `installId` registers the device; the token lives in
  EncryptedSharedPreferences; a recovery key moves projects to another phone.
- **Local-first** (founder's rule, contract v0.6): recordings and files never leave the phone, and
  drafts (notes, every version of the brief, the quote) live only on it. To write or price a brief,
  its text goes to the stateless `/ai/*` and `/quotes` jobs, which keep nothing. Submit
  (`POST /projects`) is the first time content reaches the server, which deletes it when the project
  closes; the phone keeps its own copy.
- **Submitted projects**: the backend is the source of truth; repositories cache the last known
  state on disk so the app opens instantly and reads work offline.
- **Voice**: live dictation in English (US, UK, India or Australia accent) through the on-device
  SpeechRecognizer drives the ink line and the on-screen transcript. Phones without an offline
  model are offered typing or upload instead.
- **Prices** are shown in US dollars (1 credit = $10 from `/config`); packs show Google Play's price.
- **Design**: "Workshop", mirrored from `Raviga iOS/docs/DESIGN.md` with the same fonts and clay
  pictures; Android notes in `docs/DESIGN.md`.

## Licences

Bricolage Grotesque and Figtree are used under the SIL Open Font License
(`LICENSES/BricolageGrotesque-Figtree-OFL.txt`, also shipped in the app under Settings > Fonts).
The illustrations were made for Raviga with gpt-image-2.5 (prompts in `Raviga iOS/docs/illustrations`).
