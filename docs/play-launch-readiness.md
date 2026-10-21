# Play launch readiness (R-012): drafts only

Nothing here has been submitted or published. The store listing text and the deletion page are public, so the founder
approves them first.

Sources:
- the code at raviga-android `c56e8c1` and the merged release manifest (checked 2026-10-21);
- the SDK disclosures for [ML Kit](https://developers.google.com/ml-kit/android-data-disclosure),
  [RevenueCat](https://www.revenuecat.com/docs/platform-resources/google-platform-resources/google-plays-data-safety)
  and Firebase.

## 1. Data safety form: draft answers

**Overview**
- Does the app collect or share required user data? **Yes.**
- Is all user data encrypted in transit? **Yes.** The app talks to the backend over HTTPS only; ML Kit, RevenueCat
  and FCM also use HTTPS.
- Can users request deletion? **Yes.** In the app: Settings → Privacy and data → "Delete my data". On the web: a deletion page
  is still needed (checklist item 9).
- Account creation: **yes, "Other"**. The app creates a client account for the phone by itself, with no password but
  with a recovery key. Play's deletion rule covers this
  ([account deletion](https://support.google.com/googleplay/android-developer/answer/13327111)).
- **Shared: no, for every type.** OpenAI, RevenueCat and Google (FCM, ML Kit) process data on Raviga's behalf, which
  Play doesn't count as sharing.

**Collected**

| Play data type | What it is | Purpose | Optional? |
|---|---|---|---|
| App activity → Other user-generated content | Notes and brief text, sent to the backend to draft and price (OpenAI may keep it up to 30 days) and stored on submit until the project closes; text read from uploaded files | App functionality | Required for the core flow |
| Messages → Other in-app messages | Comments between the client and the team on a project | App functionality | Optional |
| Personal info → Other info | GitHub username and AWS account ID, for delivery | App functionality | Optional |
| Financial info → Purchase history | Credit purchases through Play Billing and RevenueCat | App functionality, account management | Required to buy credits |
| Device or other IDs | The app's install ID, the Firebase Installation ID (push), the RevenueCat user ID (the client ID), ML Kit's identifier | App functionality; analytics (ML Kit diagnostics only) | Required |
| App info and performance → Diagnostics | Device model, OS version, app version and locale sent at registration; ML Kit's latency and error codes | App functionality; analytics (ML Kit) | Required |
| App activity → App interactions | Contract v0.9 usage funnel through Google Analytics for Firebase: onboarding_complete, brief_started, brief_generated, quote_viewed, project_submitted, credits_purchased, delivery_accepted, plus the automatic events the contract discloses (first_open, session_start, user_engagement, screen_view by class name, app_update, in_app_purchase, and FCM's notification_receive/foreground/open/dismiss). Values are enums and counts only; never text, titles, file names or ids | Analytics | **Optional**: off until the client turns it on (consent screen or Settings); not for ads, not shared |
| Device or other IDs (analytics) | Analytics' app instance id. No advertising id, no Android ID (SSAID off), no user id | Analytics | Optional, same switch |

**Not collected:**
- Audio: speech is recognised on the phone, and no audio leaves it.
- Files and docs: read on the phone; only the extracted text goes up, declared above as user content.
- Photos and videos, location, contacts, calendar, health, SMS or email, installed apps, web browsing, search history.
- Crash logs: there's no crash-reporting SDK.
- Advertising id: never. AD_ID and the Privacy Sandbox ad id, attribution and install-referrer permissions are removed from the manifest, and `play-services-ads-identifier` is excluded from both Analytics and RevenueCat.
- Name, email, phone and address: the app never asks for them. A client could type them into notes; that text is
  covered above.

## 2. Pre-launch checklist (Android)

**Console and accounts (founder)**
1. **Decided (founder, 2026-10-21):** publish on the existing Google Play account "Miraquill Quotes & Poetry";
   the PM creates the app.
   - The listing's developer name will read "Miraquill Quotes & Poetry".
   - Google requires the privacy policy to name either that entity or the app
     ([User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)).
     The live policy names "Raviga", so that's met.
   - The 12-tester, 14-day closed test applies only if this is a *personal* account created after 13 Nov 2023
     ([source](https://support.google.com/googleplay/android-developer/answer/14151465)).
2. Create the app "Raviga" (`com.raviga.app`): HANDOFF item 1.
3. Payments profile: the existing account's own. Buyers' Play receipts will show that merchant, while the in-app terms name
   Raviga Apps Private Limited as the seller. That consistency is a business question for the founder, not a Play policy block.
4. Create the in-app products `credits_10/25/50/100` (HANDOFF item 2). `credits_100` at $999.99 can't be sold in
   South Korea, where the cap is US$400.
5. Set up Play App Signing and an upload key (HANDOFF item 5).
6. Supply the RevenueCat key with Play credentials, `google-services.json` and the prod URL (HANDOFF items 3, 4 and 6).

**App content declarations**
7. Privacy policy URL: the prod legal site, on ravigaapps.com once the domain exists.
8. Data safety: section 1 above.
9. Account deletion web link: published by backend in contract v0.9.1. Dev: `https://d1mi85jr39d7la.cloudfront.net/delete-account`;
   the prod page will be on ravigaapps.com. Enter the prod URL in the Data safety form.
10. App access: no login is needed, but submitting costs credits. We need reviewer instructions, and a decision on
    whether reviewers get test credits.
11. Ads: none.
12. Content rating (IARC): clients talk only to our team, never to other users.
13. Target audience: 18+ only, a business service with payments. This keeps the app out of the Families policy.
14. Payments policy: decided per `docs/play-payments-policy.md` (R-013).

**Store listing (public; founder approves)**
15. Name "Raviga", short description (≤80 characters), full description (≤4,000), category Business, contact email
    and website.
16. 512×512 icon (waits on the real logo; the interim "R." exists), 1024×500 feature graphic, and 2 to 8 phone
    screenshots.

**Build and release**
17. `bundleRelease`. It is blocked until items 5 and 6 exist (`checkReleaseReadiness` enforces this).
18. Internal testing track first, then production. Read the pre-launch report before promoting.

**Already verified (2026-10-21, by me, on the release APK)**
- targetSdk 36 and minSdk 26. R8 builds.
- 16 KB page sizes: both native libraries, `libandroidx.graphics.path` and `libdatastore_shared_counter`, are
  16 KB-aligned in the zip and in their ELF LOAD segments (checked on arm64 and x86_64). ML Kit OCR runs through
  Play services and adds no native code.
- Permissions: INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, RECORD_AUDIO, VIBRATE, WAKE_LOCK, BILLING and
  the FCM receive permission. There is no AD_ID, QUERY_ALL_PACKAGES, location, foreground service or other
  restricted permission.
- Analytics (v0.9, added 2026-10-21): Firebase Analytics ships with collection off in the manifest and turns on only
  while the client's consent is on. Verified on the OnePlus: "App measurement disabled via the manifest" at start;
  the switch sends `{"analytics":true}` and the SDK logs "enabled: true"; brief_started, brief_generated and
  quote_viewed carry only enums and counts; turning it off sends `{"analytics":false}` and logs "enabled: false".
- No crash-reporting or ads SDK. Correction to the first draft: RevenueCat had been pulling in
  `play-services-ads-identifier` 17.0.1. It is now excluded, so no ad-id code ships.
