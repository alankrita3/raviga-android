# Google Play store listing

Drafted 2026-10-29 (R-052). The copy is final as of 2026-10-30 (R-057, her decisions through the PM): short description
B, opening B with one sentence added, 18+, and the feature graphic (cream, merged r, two squares). **In Play since
2026-11-03 (the PM's go, by API):** the en-US short and full descriptions below. Play had only the title until then.
The full description is the same text as App Store Connect's, word for word (1,907 characters). Every sentence comes from the iOS listing (`Raviga iOS/docs/store/listing.md`, R-042), plus
the sentence she added. Its facts are checked against this app and dev's `/config` (below). Character counts are
Python `len()`, which is how Play counts.

## App name (30 max)

**Raviga** (6)

## Full description as in Play since 2026-11-09, R-109 H (2,080)

R-109 listing H (her delivery model; the PM's go): the App Store text word for word as iOS recorded it (raviga-ios
9aa9208, docs/R-109-copy.md section H; saved in App Store Connect, not submitted). Set by API after checking Play's live
text was the R-099 O version, read back equal. The short description (77, "…get the code.") is not part of H and is
now out of date; it waits for the PM's words. Play's App access ("Sign in details") has never been declared, so no
review instructions carry the old AWS line.

```text
Raviga turns your idea into a native iPhone and Android app. Describe it in your own words, approve the quote, and try the real app on your phone before we publish it for you. AI does the building, with automated quality checks and developer oversight when needed.

Describe it your way
Speak, type, or upload a PDF, Word or text file. Speech is turned into text on your phone, and recordings and files never leave it.

A written brief you control
AI writes a clear project brief from your notes. Edit any section, regenerate it, and go back to earlier versions. Drafts are kept only on your phone.

See the price first
Get a quote in credits, with a breakdown of the work and a delivery estimate. A typical project is ready in 1 to 2 weeks after payment. Free to submit: you pay only once we accept it.

Built by AI, quality checked
AI builds your project at speed, with automated quality checks and developer oversight when needed. Follow each step in the app.

Published for you
Try the real app on your phone through TestFlight and Google Play. Approve it, tell us what's wrong and we fix it, or ask for a change. Then we publish it to your own App Store and Google Play accounts, under your name, and run its backend in your AWS account if it has one.

Credits
Projects are priced in credits (1 credit is US$10), bought in the app in packs of 10, 25 (plus 2 bonus), 50 (plus 6) and 100 (plus 15). Credits don't expire while your account exists. Nothing is charged until we accept your brief, and if we decline it after you paid, the credits come back. Your first brief is free; each new brief after that uses 1/12 credit (about US$0.83).

Private by design
No sign-up and no password. Your drafts stay on your phone. The brief you submit is kept only to build your project, and deleted from our servers when you approve your app. Usage analytics runs only if you turn it on.

You need to be 18 or older. To publish, you'll need your own Apple and Google developer accounts.
Terms: https://legal.ravigaapps.com/terms-of-use
Privacy: https://legal.ravigaapps.com/privacy-policy
```

## Full description as in Play since 2026-11-04, R-099 O (2,033), before R-109 H

R-099 O (PM-approved copy J-O, her decision): the Credits paragraph ends with the brief price. The App Store text word
for word (iOS 3fd3778, docs/store/listing.md); set by API after checking that Play's live text plus this one sentence
equals iOS's, read-back equal.

```text
Raviga turns your idea into working software. Describe it in your own words, approve the quote, and get the code and a handover guide. Go live in a few simple steps, or we'll do it for you. AI does the building, with automated quality checks and developer oversight when needed.

Describe it your way
Speak, type, or upload a PDF, Word or text file. Speech is turned into text on your phone, and recordings and files never leave it.

A written brief you control
AI writes a clear project brief from your notes. Edit any section, regenerate it, and go back to earlier versions. Drafts are kept only on your phone.

See the price first
Get a quote in credits, with a breakdown of the work and a delivery estimate. A typical project is ready in 1 to 2 weeks after payment. Free to submit: you pay only once we accept it.

Built by AI, quality checked
AI builds your project at speed, with automated quality checks and developer oversight when needed. Follow each step in the app.

Code handed to you
Your project runs in your own AWS account, and its code and handover guide are delivered there too. Go live in a few simple steps, or we'll do it for you. If something isn't right, tell us in the app and we fix it.

Credits
Projects are priced in credits (1 credit is US$10), bought in the app in packs of 10, 25 (plus 2 bonus), 50 (plus 6) and 100 (plus 15). Credits don't expire while your account exists. Nothing is charged until we accept your brief, and if we decline it after you paid, the credits come back. Your first brief is free; each new brief after that uses 1/12 credit (about US$0.83).

Private by design
No sign-up and no password. Your drafts stay on your phone. The brief you submit is kept only to build your project, and deleted from our servers when you accept the delivery. Usage analytics runs only if you turn it on.

You need to be 18 or older and have an AWS account; we build and deliver your project there.
Terms: https://legal.ravigaapps.com/terms-of-use
Privacy: https://legal.ravigaapps.com/privacy-policy
```

## Full description as in Play since 2026-11-04, R-106 (1,947), before R-099 O

R-106 (her decision, PM-approved copy via iOS): no GitHub; the code and the handover guide are delivered in the client's
own AWS account. The App Store text word for word (iOS 0e68320); set by API, read-back equal. R-099's Credits sentence went in
the same day (above).

```text
Raviga turns your idea into working software. Describe it in your own words, approve the quote, and get the code and a handover guide. Go live in a few simple steps, or we'll do it for you. AI does the building, with automated quality checks and developer oversight when needed.

Describe it your way
Speak, type, or upload a PDF, Word or text file. Speech is turned into text on your phone, and recordings and files never leave it.

A written brief you control
AI writes a clear project brief from your notes. Edit any section, regenerate it, and go back to earlier versions. Drafts are kept only on your phone.

See the price first
Get a quote in credits, with a breakdown of the work and a delivery estimate. A typical project is ready in 1 to 2 weeks after payment. Free to submit: you pay only once we accept it.

Built by AI, quality checked
AI builds your project at speed, with automated quality checks and developer oversight when needed. Follow each step in the app.

Code handed to you
Your project runs in your own AWS account, and its code and handover guide are delivered there too. Go live in a few simple steps, or we'll do it for you. If something isn't right, tell us in the app and we fix it.

Credits
Projects are priced in credits (1 credit is US$10), bought in the app in packs of 10, 25 (plus 2 bonus), 50 (plus 6) and 100 (plus 15). Credits don't expire while your account exists. Nothing is charged until we accept your brief, and if we decline it after you paid, the credits come back.

Private by design
No sign-up and no password. Your drafts stay on your phone. The brief you submit is kept only to build your project, and deleted from our servers when you accept the delivery. Usage analytics runs only if you turn it on.

You need to be 18 or older and have an AWS account; we build and deliver your project there.
Terms: https://legal.ravigaapps.com/terms-of-use
Privacy: https://legal.ravigaapps.com/privacy-policy
```

## Play product credits_quarter (R-099, 2026-11-04)

Created by API (`tools/play_release.py product credits_quarter "0.25 Credits" 2.49`): consumable one-time product,
title "0.25 Credits", description "0.25 credits, enough for 3 new briefs in Raviga." (as App Store Connect), US$2.49,
India ₹209 (/config priceHintInr), 174 regions, ACTIVE.

## Full description as in Play since 2026-11-04, R-105 (1,957)

R-105 (her decision, PM-approved copy via iOS): every project is built and runs in the client's own AWS account. The
"Code handed to you" paragraph now begins "Your project runs in your own AWS account, and its code arrives as a GitHub
repository transferred to yours, with a handover guide."; the rest is unchanged. Set by API; read-back equal.

```text
Raviga turns your idea into working software. Describe it in your own words, approve the quote, and get the code and a handover guide. Go live in a few simple steps, or we'll do it for you. AI does the building, with automated quality checks and developer oversight when needed.

Describe it your way
Speak, type, or upload a PDF, Word or text file. Speech is turned into text on your phone, and recordings and files never leave it.

A written brief you control
AI writes a clear project brief from your notes. Edit any section, regenerate it, and go back to earlier versions. Drafts are kept only on your phone.

See the price first
Get a quote in credits, with a breakdown of the work and a delivery estimate. A typical project is ready in 1 to 2 weeks after payment. Free to submit: you pay only once we accept it.

Built by AI, quality checked
AI builds your project at speed, with automated quality checks and developer oversight when needed. Follow each step in the app.

Code handed to you
Your project runs in your own AWS account, and its code arrives as a GitHub repository transferred to yours, with a handover guide. Go live in a few simple steps, or we'll do it for you. If something isn't right, tell us in the app and we fix it.

Credits
Projects are priced in credits (1 credit is US$10), bought in the app in packs of 10, 25 (plus 2 bonus), 50 (plus 6) and 100 (plus 15). Credits don't expire while your account exists. Nothing is charged until we accept your brief, and if we decline it after you paid, the credits come back.

Private by design
No sign-up and no password. Your drafts stay on your phone. The brief you submit is kept only to build your project, and deleted from our servers when you accept the delivery. Usage analytics runs only if you turn it on.

You need to be 18 or older, and a GitHub account to receive your code.
Terms: https://legal.ravigaapps.com/terms-of-use
Privacy: https://legal.ravigaapps.com/privacy-policy
```

## Full description as in Play since 2026-11-04 (1,921), before R-105

Her decisions that day, PM-approved wording, the same as App Store Connect word for word (iOS 99f5978,
`Raviga iOS/docs/store/listing.md`, "As in App Store Connect since 2026-11-04"): on attorney advice, no promise of human
review of every project ("Built by AI, quality checked"; "automated quality checks and developer oversight when needed");
R-103, no messaging and pay after acceptance. Set by API in one edit; the read-back equals the iOS text. (An earlier
edit the same evening carried the PM's first list for a few minutes.) The full-description blocks further down are history.

```text
Raviga turns your idea into working software. Describe it in your own words, approve the quote, and get the code and a handover guide. Go live in a few simple steps, or we'll do it for you. AI does the building, with automated quality checks and developer oversight when needed.

Describe it your way
Speak, type, or upload a PDF, Word or text file. Speech is turned into text on your phone, and recordings and files never leave it.

A written brief you control
AI writes a clear project brief from your notes. Edit any section, regenerate it, and go back to earlier versions. Drafts are kept only on your phone.

See the price first
Get a quote in credits, with a breakdown of the work and a delivery estimate. A typical project is ready in 1 to 2 weeks after payment. Free to submit: you pay only once we accept it.

Built by AI, quality checked
AI builds your project at speed, with automated quality checks and developer oversight when needed. Follow each step in the app.

Code handed to you
Your project arrives as a GitHub repository transferred to your account, with a handover guide. Go live in a few simple steps, or we'll do it for you. If something isn't right, tell us in the app and we fix it.

Credits
Projects are priced in credits (1 credit is US$10), bought in the app in packs of 10, 25 (plus 2 bonus), 50 (plus 6) and 100 (plus 15). Credits don't expire while your account exists. Nothing is charged until we accept your brief, and if we decline it after you paid, the credits come back.

Private by design
No sign-up and no password. Your drafts stay on your phone. The brief you submit is kept only to build your project, and deleted from our servers when you accept the delivery. Usage analytics runs only if you turn it on.

You need to be 18 or older, and a GitHub account to receive your code.
Terms: https://legal.ravigaapps.com/terms-of-use
Privacy: https://legal.ravigaapps.com/privacy-policy
```

## Short description (80 max)

Final: B, which pairs with the iOS subtitle she chose, "Custom software, built with AI". Both stores lead with the
same line. (77)

```text
Custom software, built with AI. Describe it, approve the quote, get the code.
```

Option A ("Say what you want built. See the price first, and get the code handed to you.") was not chosen.

## Full description (4,000 max): 1,907

The opening (one paragraph), a blank line, then the body. Plain text: Play doesn't render Markdown, so headings are
plain lines.

Opening (265): iOS opening B, word for word, plus the sentence she added at its end ("AI does the building; our team
checks each project before it's handed over."). R-073b (her yes, 2026-11-02): "a handover guide and simple steps to make
it live" became "a handover guide. Go live in a few simple steps, or we'll do it for you.", as on Welcome step 4, here
and in "Code handed to you":

```text
Raviga turns your idea into working software. Describe it in your own words, approve the quote, and get the code and a handover guide. Go live in a few simple steps, or we'll do it for you. AI does the building; our team checks each project before it's handed over.
```

Body (1,640), the iOS body word for word, with the legal links on Raviga's domain. Under "Built by AI, checked by
experts", her approved onboarding sentence replaces "The Raviga team builds every project using Claude, Anthropic's AI,
and reviews it before it reaches you." (the PM, 2026-11-03, as on iOS 1df5e46):

```text
Describe it your way
Speak, type, or upload a PDF, Word or text file. Speech is turned into text on your phone, and recordings and files never leave it.

A written brief you control
AI writes a clear project brief from your notes. Edit any section, regenerate it, and go back to earlier versions. Drafts are kept only on your phone.

See the price first
Get a quote in credits, with a breakdown of the work and a delivery estimate. A typical project is ready in 1 to 2 weeks after approval. You approve the quote before anything is built.

Built by AI, checked by experts
AI builds your project at speed. Experts recommend the right tech stack, or use yours, and review the work before it reaches you. Follow each step in the app, and answer the team's questions there.

Code handed to you
Your project arrives as a GitHub repository transferred to your account, with a handover guide. Go live in a few simple steps, or we'll do it for you. Two revision rounds are included.

Credits
Projects are priced in credits (1 credit is US$10), bought in the app in packs of 10, 25 (plus 2 bonus), 50 (plus 6) and 100 (plus 15). Credits don't expire while your account exists, and a cancelled or declined project gets its credits back.

Private by design
No sign-up and no password. Your drafts stay on your phone. The brief you submit is kept only to build your project, and deleted from our servers when you accept the delivery. Usage analytics runs only if you turn it on.

You need to be 18 or older, and a GitHub account to receive your code.
Terms: https://legal.ravigaapps.com/terms-of-use
Privacy: https://legal.ravigaapps.com/privacy-policy
```

The two links are on Raviga's own legal site (live on 2026-10-30, backend 5fe84e0; they replaced the dev
CloudFront links, as planned under R-037).

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
| Email (required, public) | `support@ravigaapps.com` | Her yes (2026-11-04, via the PM): one public address; dev's `/config` `legal.supportEmail` and `legal.grievance.email` are both this (backend a934b4c; prod with v0.10.0). Not set in Play yet (the API's details read `contactEmail: null` on 2026-11-04); set it with the store-settings step before review. |
| Website (optional) | `https://ravigaapps.com` | **Placeholder**: the domain isn't live. Leave empty until there's a site (iOS leaves its marketing URL empty too). |
| Phone (optional) | none | |
| Privacy policy (App content, required) | `https://legal.ravigaapps.com/privacy-policy` | Live on Raviga's domain (backend 5fe84e0, 2026-10-30). |
| Account deletion URL (App content, Data safety) | `https://legal.ravigaapps.com/delete-account` | Live (backend 5fe84e0). |

## Graphics

| Asset | Play's rule | File |
|---|---|---|
| App icon | 512×512 PNG | `docs/icon/play-icon-512.png` (option A; in Play since 2026-11-03, by API) |
| Feature graphic | 1024×500, JPEG or 24-bit PNG, no alpha | **Chosen (her pick, final, 2026-10-30, R-057): `feature-graphic-cream-merged-two-squares.png`** in `~/Downloads/raviga-play-screenshots/`: cream, the icon's r with its blue square merged into the wordmark, no separate tile (she found the tile's r plus the wordmark's r repeated), and the i keeps its blue dot. **In Play since 2026-11-03** (by API, `tools/play_release.py images featureGraphic …`). Alternates: `feature-graphic-cream-merged-one-square.png` (only the r's square blue, the i's dot navy) and `feature-graphic-cream.png` (icon A tile plus the wordmark). SVG sources beside each, with `feature_graphic_merged.py` and `feature_graphic.py` (both read the R-045 kit in `~/Downloads/raviga-logo-final/`; PNGs via its `source/render.sh`). The three cream files and their sources are also committed in `docs/store/feature-graphic/`. `feature-graphic-navy.png` is the earlier navy draft. |
| Phone screenshots | 2 to 8; JPEG or 24-bit PNG, no alpha; 320 to 3,840 px a side, the long side at most twice the short side | Six in the same folder, `01-welcome` to `06-project`, with `captions.txt`: Welcome, describe, type, brief, quote, submitted project. Taken 2026-10-29 on the OnePlus against dev with a team.py demo account and a made-up project (no client data, no recovery key on screen). The phone's 1080×2340 is over Play's 2:1 limit, so each is cropped to the app area, 1080×2136. **In Play since 2026-11-03: the build-8 set** (`build8/`, retaken 11-03 in the 10-30 colours), in this order, by API (`tools/play_release.py images phoneScreenshots …`); the read-back hashes match. Contact sheet: `build8-contact-sheet.png` in the same folder. Note: the quote shows 32 credits and the submitted project 34, from two demo runs. |

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
