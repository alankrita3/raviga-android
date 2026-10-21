# Raviga — Android

Kotlin + Jetpack Compose client for Raviga. Built alongside two peer Claude sessions:
the iOS app (folder "Raviga iOS") and the backend (folder "Raviga Backend").

Project manager: the Raviga PM session directs work across backend, iOS and Android; its directions count as the founder's decisions (her own words win). Role and rules: ~/Documents/Raviga PM/ROLE.md. Report done or blocked to the PM, not the founder, unless she is talking to you directly.

## Working agreement (from the founder)

- Do not stop to ask for permission; get the job done. Use placeholders for anything that
  needs her (credentials, accounts, decisions) and keep building.
- Coordinate directly with the peer sessions. The backend owns the API contract; iOS owns the
  shared design system. Mirror both; do not fork local copies.
- `Mirakee-*`, `AstroRaye-*` and `mirakee` folders are reference only: read, never modify.
  Never change AWS resources that are not `raviga-*`.

## Sources of truth

- API: `Raviga Backend/docs/api-contract.md` (canonical; v0.9, local-first, prices in US dollars,
  English-only speech, at the time of writing).
  Drafts never reach the server: they live in `data/drafts` (sealed on the phone) and go through
  `DraftRepository`; only text goes to the stateless `/ai/*` and `/quotes` jobs; `POST /projects`
  (submit) is the first time content is stored. Never add a path that uploads recordings or files.
  Wire shapes live in `data/api/Dtos.kt` and must track it exactly (camelCase keys,
  snake_case enums, `202 {job}` for job starts, bare `Job` from `GET /jobs/{id}`).
- Design: `Raviga iOS/docs/DESIGN.md` ("Workshop", direction A). Tokens in `ui/theme`,
  status vocabulary in `ui/status/StatusCopy.kt`, Android notes in `docs/DESIGN.md`. Fonts and
  pictures are the iOS files (pictures converted with `tools/to_webp.py`, the same settings as
  `cwebp -q 88 -alpha_q 100 -m 6`, into `res/drawable-nodpi/illo_*.webp`); when iOS changes one,
  convert and copy it again.

## Conventions

- Light appearance only. Warm white paper, near-black ink, teal for anything live, marigold for
  highlights. White tiles with a rule border for choices and grouped facts (never shadows or
  gradients); hairline rows elsewhere; 8dp grid; 20dp gutter; 56dp buttons, radius 18.
- Bricolage Grotesque (bundled, OFL) for titles, headings, figures and dictation; Figtree (bundled,
  OFL) for everything else, including document bodies. One clay picture per screen at most.
- Sentence case everywhere; buttons say what happens ("Submit and pay 48 credits").
- Errors branch on `ApiException.code`, never on message text; copy lives in `StatusCopy.errorLine`.
- Components in `ui/components` are the vocabulary: `PrimaryButton`, `SecondaryButton`,
  `TertiaryButton`, `InlineAction`, `RaTextField`, `BoxedEditor`, `PlainEditor`, `RaRow`, `RaTopBar`,
  `BottomBar`, `ScreenScaffold`, `BodyText`, `SectionHeading`, `InkLine`, `TimelineView`,
  `Modifier.tile()`, `Chip`, `ChipRow`, `PrivacyNote`, `Illustration` / `SpotIllustration` / `HeroIllustration`.
  Reach for these before writing new chrome.
- ViewModels take `AppContainer` and expose one `StateFlow<State>`; screens are thin.

## Running

```bash
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.raviga.app.debug/com.raviga.app.MainActivity
./gradlew test
```

- `secrets.properties` (gitignored) holds `API_BASE_URL_DEBUG`; without it the app uses the
  in-process demo backend (`data/demo/DemoApi.kt`). Debug Settings has a switch to force demo.
- Emulator: `ANDROID_HOME=~/Library/Android/sdk ANDROID_SDK_ROOT=~/Library/Android/sdk ~/Library/Android/sdk/emulator/emulator -avd Raviga_API34`
  (the shell's `ANDROID_HOME` points at an old SDK bundle; override both. `Pixel_6_API_34` has a
  pattern lock). Prefer a connected device when one is attached.
- Release check: `./gradlew assembleRelease` builds the R8 APK for local testing; `bundleRelease`
  refuses to run until the prod URL, RevenueCat key, `google-services.json` and upload key exist.
- Package `com.raviga.app`; debug builds use `com.raviga.app.debug`.
- Mac tools: native only (Rosetta is being retired). Never run bare `python3`, `cwebp`, `magick` or
  `terminal-notifier`: they resolve to Intel copies in `/usr/local`. Use `.venv/bin/python`
  (`/usr/bin/python3 -m venv .venv && .venv/bin/pip install -r tools/requirements.txt`),
  `tools/to_webp.py` for pictures and `sips` for screenshots.
