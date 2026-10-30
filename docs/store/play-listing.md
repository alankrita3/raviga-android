# Google Play store listing

Drafted 2026-10-29 (R-052). The copy is final as of 2026-10-30 (R-057, her decisions through the PM): short description
B, opening B with one sentence added, 18+, and the feature graphic (cream, merged r, two squares). Nothing goes into
Play Console until the PM says so. Every sentence comes from the iOS listing (`Raviga iOS/docs/store/listing.md`, R-042), plus
the sentence she added. Its facts are checked against this app and dev's `/config` (below). Character counts are
Python `len()`, which is how Play counts.

## App name (30 max)

**Raviga** (6)

## Short description (80 max)

Final: B, which pairs with the iOS subtitle she chose, "Custom software, built with AI". Both stores lead with the
same line. (77)

```text
Custom software, built with AI. Describe it, approve the quote, get the code.
```

Option A ("Say what you want built. See the price first, and get the code handed to you.") was not chosen.

## Full description (4,000 max): 1,853

The opening (one paragraph), a blank line, then the body. Plain text: Play doesn't render Markdown, so headings are
plain lines.

Opening (240): iOS opening B, word for word, plus the sentence she added at its end ("AI does the building; our team
checks each project before it's handed over."):

```text
Raviga turns your idea into working software. Describe it in your own words, approve the quote, and get the code, a handover guide and simple steps to make it live. AI does the building; our team checks each project before it's handed over.
```

Body (1,611), the iOS body word for word:

```text
Describe it your way
Speak, type, or upload a PDF, Word or text file. Speech is turned into text on your phone, and recordings and files never leave it.

A written brief you control
AI writes a clear project brief from your notes. Edit any section, regenerate it, and go back to earlier versions. Drafts are kept only on your phone.

See the price first
Get a quote in credits, with a breakdown of the work and a delivery estimate. A typical project is ready in 1 to 2 weeks after approval. You approve the quote before anything is built.

Built by AI, checked by experts
The Raviga team builds every project using Claude, Anthropic's AI, and reviews it before it reaches you. Follow each step in the app, and answer the team's questions there.

Code handed to you
Your project arrives as a GitHub repository transferred to your account, with a handover guide and simple steps to make it live. Two revision rounds are included.

Credits
Projects are priced in credits (1 credit is US$10), bought in the app in packs of 10, 25 (plus 2 bonus), 50 (plus 6) and 100 (plus 15). Credits don't expire while your account exists, and a cancelled or declined project gets its credits back.

Private by design
No sign-up and no password. Your drafts stay on your phone. The brief you submit is kept only to build your project, and deleted from our servers when you accept the delivery. Usage analytics runs only if you turn it on.

You need to be 18 or older, and a GitHub account to receive your code.
Terms: https://d1mi85jr39d7la.cloudfront.net/terms-of-use
Privacy: https://d1mi85jr39d7la.cloudfront.net/privacy-policy
```

When the legal site is live (R-037), change the two dev links to `https://legal.ravigaapps.com/terms-of-use` and
`/privacy-policy`. The full description then gets 18 characters shorter (1,835).

Option A's opening ("Say what you want built. Talk it through, type it, or upload a document you already have. Raviga
writes the brief, prices it, and builds it.") was not chosen.

## Category and tags

- App or game: **App**. Category: **Business** (as on iOS and in RevenueCat).
- Tags: optional, up to five from Play's list. None picked; her call.

## Age rating: 18+ (final, R-057)

Play has no age-rating override like Apple's. 18+ comes from two App content answers: **Target audience** "18 and
over" only, and the **IARC questionnaire** answered as drafted in `docs/play-launch-readiness.md` (items 12 and 13:
clients talk only to our team, never to other users; a business service with payments, outside the Families policy).

## Contact details

| Field | Value | Status |
|---|---|---|
| Email (required, public) | `support@miraquill.com` | Live: it's the `supportEmail` dev's `/config` returns today (also on the support page). If she wants a Raviga address, `support@ravigaapps.com` is the placeholder until the domain is live. |
| Website (optional) | `https://ravigaapps.com` | **Placeholder**: the domain isn't live. Leave empty until there's a site (iOS leaves its marketing URL empty too). |
| Phone (optional) | none | |
| Privacy policy (App content, required) | `https://d1mi85jr39d7la.cloudfront.net/privacy-policy` | Dev's site, as on iOS. Switch to `https://legal.ravigaapps.com/privacy-policy` when backend says R-037 is live. |

## Graphics

| Asset | Play's rule | File |
|---|---|---|
| App icon | 512×512 PNG | `docs/icon/play-icon-512.png` (option A, ready) |
| Feature graphic | 1024×500, JPEG or 24-bit PNG, no alpha | **Chosen (her pick, final, 2026-10-30, R-057): `feature-graphic-cream-merged-two-squares.png`** in `~/Downloads/raviga-play-screenshots/`: cream, the icon's r with its blue square merged into the wordmark, no separate tile (she found the tile's r plus the wordmark's r repeated), and the i keeps its blue dot. Not uploaded to Play yet. Alternates: `feature-graphic-cream-merged-one-square.png` (only the r's square blue, the i's dot navy) and `feature-graphic-cream.png` (icon A tile plus the wordmark). SVG sources beside each, with `feature_graphic_merged.py` and `feature_graphic.py` (both read the R-045 kit in `~/Downloads/raviga-logo-final/`; PNGs via its `source/render.sh`). The three cream files and their sources are also committed in `docs/store/feature-graphic/`. `feature-graphic-navy.png` is the earlier navy draft. |
| Phone screenshots | 2 to 8; JPEG or 24-bit PNG, no alpha; 320 to 3,840 px a side, the long side at most twice the short side | Six in the same folder, `01-welcome` to `06-project`, with `captions.txt`: Welcome, describe, type, brief, quote, submitted project. Taken 2026-10-29 on the OnePlus against dev with a team.py demo account and a made-up project (no client data, no recovery key on screen). The phone's 1080×2340 is over Play's 2:1 limit, so each is cropped to the app area, 1080×2136. |

## Facts checked against Android (2026-10-29)

- Word, PDF and text files: `acceptedFileTypes` in dev's `/config`, and `data/files` reads them on the phone.
- Speech on the phone: `DictationEngine` uses Android's on-device recogniser only (`createOnDeviceSpeechRecognizer`),
  so no audio leaves the phone.
- Earlier versions: `ui/document/VersionsScreen.kt`.
- 1 credit is US$10, the bonuses (25+2, 50+6, 100+15) and two revision rounds: dev's `/config`.
- Analytics off until the client turns it on: verified on the OnePlus (`docs/play-launch-readiness.md`).
- "Our team checks each project before it's handed over": the project timeline's review steps, and the body's "Built
  by AI, checked by experts".

## Notes for her

- **"Speak" doesn't work on every Android phone.** Dictation needs Android 12 or later, with an on-device recogniser
  and its English model. On other phones the app explains why and typing and files still work (the OnePlus 7 is one).
  The copy stays accurate as long as she's comfortable with "Speak" describing most phones, not all.
- Play adds "In-app purchases" and the price range to the listing by itself; the description doesn't need prices.
- Play's metadata rules (no rankings, no emoji, no capitals for emphasis, no keyword lists) are already met. Naming
  Claude, Anthropic and GitHub is factual use.
