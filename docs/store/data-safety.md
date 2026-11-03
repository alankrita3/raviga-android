# Play Console: Data safety form, answers for build 8

**Applied 2026-11-03 16:48 by the Play service account** (her typed yes: "Data safety"), through the API:
`.venv/bin/python tools/play_release.py data-safety docs/store/data-safety.csv`. Play checked the file line by line
and accepted it. `docs/store/data-safety.csv` is the form in Play's own CSV format: 50 answered rows, the answers below.
To change an answer, edit the CSV and run the same command again. The Console form is no longer needed.

Two differences from the first draft below (the PM's decisions, 2026-11-03):
- Step 2, question 5 (delete some data without deleting the account) is **No** for now. Yes requires a web page that
  explains how, and the legal site describes only whole-account deletion. Backend is drafting a "Delete some of your
  data" section of legal.ravigaapps.com/delete-account for her legal read. Once it's live, switch to Yes: set
  `DATA_DELETION_YES`, add the page as `PSL_DATA_DELETION_URL` and re-apply. The store listing reads the same either
  way ("You can request that data be deleted", from the account-deletion link).
- "Can users log in with accounts created outside the app?" (`PSL_HAS_OUTSIDE_APP_ACCOUNTS`) stays blank. Play only
  allows it for apps that don't create their own accounts.

The questions below are in the form's order, each with the answer to pick. Prepared 2026-11-03 from the app at main
569679c (checked against the code, the merged manifest and `docs/play-launch-readiness.md` section 1).

## Step 1: Overview

Read, then **Next**.

## Step 2: Data collection and security

1. **Does your app collect or share any of the required user data types?** → **Yes**
2. **Is all of the user data collected by your app encrypted in transit?** → **Yes**
   (HTTPS only to the backend; RevenueCat, Firebase and ML Kit use HTTPS too.)
3. **Which of the following methods of account creation does your app support?** → **Other**. If it asks for a
   description: *"The app creates an account for the phone when the person agrees to the terms, with no username or
   password. They can come back to it with a recovery key, an optional recovery email, or a link code from another
   phone."*
4. **Add a link that users can use to request that their account and associated data is deleted** →
   `https://legal.ravigaapps.com/delete-account`
5. **Do you provide a way for users to request that some or all of their data is deleted, without requiring them to
   delete their account?** → **No, for now** (applied). It becomes **Yes** once the legal site has the "Delete some of
   your data" section (see the top). In the app you can already remove the recovery email, the GitHub username or the
   AWS account, and delete drafts and notes, which never leave the phone.
6. If asked about an **independent security review** badge → **No** (none done).

**Next**.

## Step 3: Data types (tick exactly these)

| Category | Tick | Leave unticked |
|---|---|---|
| Location | none | Approximate, Precise |
| Personal info | **Email address**, **Other info** | Name, User IDs, Address, Phone number, Race and ethnicity, Political or religious beliefs, Sexual orientation |
| Financial info | **Purchase history** | User payment info, Credit score, Other financial info |
| Health and fitness | none | all |
| Messages | **Other in-app messages** | Emails, SMS or MMS |
| Photos and videos | none | all |
| Audio files | none | Voice or sound recordings, Music files, Other audio files |
| Files and docs | none | Files and docs (only the text read from a file is sent: counted under user-generated content) |
| Calendar | none | Calendar events |
| Contacts | none | Contacts |
| App activity | **App interactions**, **Other user-generated content** | In-app search history, Installed apps, Other actions |
| Web browsing | none | Web browsing history |
| App info and performance | **Diagnostics** | Crash logs (no crash SDK), Other app performance data |
| Device or other IDs | **Device or other IDs** | |

**Next**.

## Step 4: Data usage and handling (one set of questions per ticked type)

Each type asks these:
- Is this data collected, shared, or both?
- Is this data processed ephemerally?
- Is it required, or can users choose?
- Why is it collected?

"Shared" is **never** ticked. Anthropic (Claude API), RevenueCat, Google (Firebase, FCM, ML Kit) process data on
Raviga's behalf, which Play doesn't count as sharing.

| Data type | Collected / shared | Processed ephemerally? | Required or optional | Why collected (tick) |
|---|---|---|---|---|
| Personal info → **Email address** | Collected | No | **Optional** (only if they add a recovery email) | **Account management** |
| Personal info → **Other info** (GitHub username, AWS account ID) | Collected | No | **Optional** (only to hand over the project) | **App functionality** |
| Financial info → **Purchase history** (credit packs, through Google Play Billing and RevenueCat) | Collected | No | **Optional** (only when they buy credits) | **App functionality**, **Account management** |
| Messages → **Other in-app messages** (comments with the Raviga team on a project) | Collected | No | **Optional** | **App functionality** |
| App activity → **App interactions** (usage events: onboarding, brief written, quote viewed, submitted, purchased, accepted; enums and counts only) | Collected | No | **Optional** (off until they turn usage analytics on) | **Analytics** |
| App activity → **Other user-generated content** (the text of notes and briefs, including text read from an uploaded file) | Collected | No (briefs are kept from submit until the project closes) | **Required** (it is the product) | **App functionality** |
| App info and performance → **Diagnostics** (device model, OS and app version, locale at registration; ML Kit's latency and error codes) | Collected | No | **Required** | **App functionality**, **Analytics** |
| Device or other IDs (the app's install ID, the Firebase Installation ID for notifications, the RevenueCat user ID, the analytics app instance ID; no advertising ID, no Android ID) | Collected | No | **Required** | **App functionality**, **Analytics** |

**Next** → **Preview**, check it reads as above.

## Step 5: Preview

The store's safety section should read:
- **No data shared with third parties.**
- **Data collected:** Personal info, Financial info, Messages, App activity, App info and performance, Device or other
  IDs.
- **Data is encrypted in transit.**
- **You can request that data be deleted.**

Then **Submit** (her click).

## Why these answers (for her questions)

- **Audio:** speech is recognised on the phone, and no recording leaves it.
- **Files:** files are read on the phone and deleted after. Only the text she checks is kept, in a draft, and sent to
  write and price the brief.
- **Drafts:** drafts stay on the phone. Their text reaches the server only through the stateless AI calls (not stored)
  and at submit.
- **Advertising ID:** never. AD_ID and the Privacy Sandbox permissions are removed from the manifest, and
  play-services-ads-identifier is excluded.
- **Anthropic:** deletes the text within 30 days, longer only if it is flagged for abuse (the backend's provider line in
  `/config`).
- **Usage analytics:** off by default, turned on only on the consent screen or in Settings.
- **Deletion:** in the app (Settings → Privacy and data → Delete my data) or on the web page above. Submitted projects
  go after 7 days.
