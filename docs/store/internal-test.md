# Build 8 on Play's internal testing track: ready, waiting for the go

Nothing here has been run. Merging, building the bundle and uploading wait for the PM's go and her yes (no Play upload
without her yes). The upload makes a **draft** release. Rolling it out to testers is her click in Play Console.

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

Then, in Play Console (her): **Testing → Internal testing → Releases → "8 internal test" → Edit → Review release →
Start rollout to Internal testing.**

## 4. Testers (her accounts; Play Console, signed in as the account owner)

**Internal testing list.** Testing → Internal testing → Testers → Create email list "Raviga internal", then tick it.
These people can install the build from the opt-in link on that page. The link is not public.

| Email | Who | Status |
|---|---|---|
| administrator@miraquill.com | Founder (the Play account owner; the account that owns the Raviga Bugs sheet) | to confirm it is the Google account on her Android phone |
| _her Android phone's Google account, if different_ | Founder | to fill |
| _the OnePlus test phone's Google account_ | Dev test phone (the sessions' device checks) | to fill, only if she wants the sessions to test the Play build there |

**License testers (purchases).** Settings (the account's, not the app's) → License testing → add the same emails, and
set "License response" to **RESPOND_NORMALLY**. On those accounts, Play Billing purchases in the internal build are test
purchases: no charge, booked by RevenueCat with SANDBOX in the ledger note (contract, RevenueCat section). This only
matters once row 37 puts the RevenueCat key in the build.

Her pass on the internal build includes the TalkBack listen-through (the PM, 2026-11-03).
