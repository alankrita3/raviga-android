# Build 8 on Play's internal testing track

**Done 2026-11-03 12:53, on her yes ("yes go ahead with build 8 on both", via the PM):** build-8 merged into main
(0e5f114), bundle built (versionCode 8, versionName 1.0.0, dev stage, sha256 79de35d565a8…, 13.6 MB; unit tests
231 debug and 207 release, all passing), uploaded as the draft **"8 internal test"** on the internal track with the
446-character en-US release notes. Play reads back status `draft`, versionCodes `[8]`. It replaced the old
versionCode 1 draft, which was never rolled out.

**Rolled out to internal testing 2026-11-03 16:44 by the Play service account** (her typed yes: "build 8 internal
rollout"): an API edit set the release from `draft` to `completed`, and the commit returned 200. Alpha, beta and
production are empty. Section 3's Console clicks are no longer needed. What the robot can't do: email tester lists
and their opt-in link (the API holds Google Groups only) and license testers. Those are Console-only (section 4).
Data safety can go through the API (`applications.dataSafety`, Play's CSV format).

**Testers set 2026-11-03, about 18:50** (as the admin account in Chrome, after accepting the developer-account
invite): the email list "Raviga internal" (the admin account, see the Play Console tester list) is ticked on
internal testing, and the track is Active. **Opt-in link: https://play.google.com/apps/internaltest/4700044944112014609**.

## 1. On the PM's go: merge the build-8 commit

```bash
cd ~/Documents/"Raviga Android"
git fetch origin && git switch main && git pull --ff-only
git merge --no-ff origin/build-8 -m "Build 8: versionCode 8"   # the branch's one commit: versionCode 8, release notes wording
git push
```

## 2. Build the internal-test bundle

```bash
./gradlew clean bundleRelease -Praviga.internalTest=true
# → app/build/outputs/bundle/release/app-release.aab, versionCode 8, signed with the upload key
#   (~/.raviga-android-signing/; Play App Signing re-signs it). R8 mapping goes in the bundle.
```

- `-Praviga.internalTest=true`: the bundle talks to the **dev** stage (`API_BASE_URL_DEBUG`), as the first internal
  draft did. A prod bundle needs the prod URL and the RevenueCat key (`checkReleaseReadiness` refuses without them).
- Until row 37 (the RevenueCat Play app and its `goog_` key in `secrets.properties`), Credits says "Purchases are not
  set up on this build yet." Everything else works.

Check before uploading:

```bash
unzip -l app/build/outputs/bundle/release/app-release.aab | grep -c "base/dex/"   # dex files present (non-zero)
.venv/bin/python tools/play_release.py notes docs/store/release-notes-build8.md   # the What's new text, 446 characters
```

## 3. Upload (with her yes)

```bash
.venv/bin/python tools/play_release.py upload app/build/outputs/bundle/release/app-release.aab \
    --track internal --notes docs/store/release-notes-build8.md
# → "uploaded versionCode 8 (sha256 …) as a draft internal release"
```

Then, in Play Console (her), app **Raviga**:
1. Left menu **Test and release → Testing → Internal testing**, tab **Releases**.
2. On the draft **"8 internal test"**, click **Edit release**. The bundle (8) and release notes are already filled in.
3. **Next** (bottom right). The preview page lists any errors Play wants fixed first. Warnings don't block.
4. **Save and publish** (older Console wording: **Start rollout to Internal testing**), then confirm.
Internal releases aren't reviewed. Testers can install within minutes of the rollout.

## 4. Testers (her accounts; Play Console, signed in as the account owner)

**Internal testing list.** Testing → Internal testing → Testers → Create email list "Raviga internal", then tick it.
These people can install the build from the opt-in link on that page. The link is not public.

| Email | Who | Status |
|---|---|---|
| administrator@miraquill.com | Founder | her choice, 2026-11-03 (via the PM): the only tester for now |

Others are added only when she names them.

Her clicks: **Internal testing → Testers** tab → **Create email list** → name "Raviga internal", add
administrator@miraquill.com, **Save changes** → tick the list → **Save**. Then, under **How testers join your test**,
**Copy link**, open it while signed in to that Google account and **Accept invite**, then install Raviga from Play.

**License testers (purchases).** Settings (the account's, not the app's) → License testing → add the same emails, and
set "License response" to **RESPOND_NORMALLY**. On those accounts, Play Billing purchases in the internal build are test
purchases: no charge, booked by RevenueCat with SANDBOX in the ledger note (contract, RevenueCat section). This only
matters once row 37 puts the RevenueCat key in the build.

Her pass on the internal build includes the TalkBack listen-through (the PM, 2026-11-03).
