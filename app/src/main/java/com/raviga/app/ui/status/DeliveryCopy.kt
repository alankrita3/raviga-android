package com.raviga.app.ui.status

/**
 * R-109 (her delivery model; PM-approved copy, iOS docs/R-109-copy.md, mirrored word for word): the demo on the
 * client's phone, change requests and Make it live. Lines the server sends (statusLine, change and store lines) are
 * shown as sent and never live here.
 */
object DeliveryCopy {
    // C. Pay step: after paying, and the test invites.
    const val BUILDING_LINE = "We're building. You'll get TestFlight and Play invites to try it."
    const val INVITES_HEADING = "Where to send your test invites"
    const val APPLE_EMAIL = "Apple ID email"
    const val APPLE_EMAIL_HINT = "For TestFlight on your iPhone."
    const val GOOGLE_EMAIL = "Google account email"
    const val GOOGLE_EMAIL_HINT = "For the Google Play test on your Android phone."
    const val SAVE = "Save"
    const val SAVED = "Saved. You can change these until the invites go out."
    const val EMAIL_EMPTY = "Add an email address."
    const val EMAIL_BAD = "That doesn't look like an email address."

    /** The invite email's problem, or null when it will do (the server checks it again). */
    fun emailProblem(email: String): String? {
        val e = email.trim()
        return when {
            e.isEmpty() -> EMAIL_EMPTY
            e.length > 254 || !Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""").matches(e) -> EMAIL_BAD
            else -> null
        }
    }

    // D. Demo ready.
    const val TRY_IT = "Try it on your phone"
    fun testflightReady(email: String) = "Install TestFlight from the App Store, then accept the invite we sent to $email."
    const val TESTFLIGHT_WAITING = "Waiting for Apple's beta review. This usually takes about a day."
    const val OPEN_TESTFLIGHT = "Open TestFlight"
    fun playReady(email: String) = "Accept the invite we sent to $email, then install the app from Google Play."
    const val OPEN_PLAY = "Open in Google Play"
    const val APPROVE = "It works, approved"
    const val SOMETHING_WRONG = "Something's wrong"
    const val REQUEST_CHANGE = "Request a change"
    const val APPROVE_TITLE = "Approve this app?"
    const val APPROVE_BODY = "Next, we publish it to your App Store and Google Play accounts. Your brief is deleted from our servers."

    // E. Request a change.
    const val CHANGE_LINE =
        "Describe what's new or different. Bugs and anything that doesn't match the brief are fixed free: use Something's wrong for those."
    const val CHANGE_FIELD = "What should change?"
    const val GET_A_PRICE = "Get a price"
    const val PRICING = "Pricing your change"
    const val YOUR_CHANGE = "Your change"
    fun changePrice(credits: String, usd: String) = "This change: $credits, about $usd."
    const val CHANGE_BUILDING = "We're building your change. You'll get new invites to try it."

    // F. Make it live.
    const val LIVE_TITLE = "Make it live"
    const val LIVE_INTRO =
        "Your app goes into your own App Store and Google Play accounts, under your name. Here's what you do, and what we do."
    const val STEP_ACCOUNTS = "Create your store accounts"
    const val APPLE_JOIN = "Join the Apple Developer Program (US$99 a year)."
    const val APPLE_ENROL_GUIDE = "Apple's enrolment guide"
    const val APPLE_ENROL_URL = "https://developer.apple.com/programs/enroll/"
    const val APPLE_ORG =
        "A business should enrol as an organisation. That needs a D-U-N-S number, which is free and can take up to 5 business days."
    const val DUNS = "Get a D-U-N-S number"
    const val DUNS_URL = "https://developer.apple.com/support/D-U-N-S"
    const val GOOGLE_JOIN = "Create a Google Play developer account (US$25, once)."
    const val GOOGLE_SIGNUP_GUIDE = "Google's sign-up guide"
    const val GOOGLE_SIGNUP_URL = "https://support.google.com/googleplay/android-developer/answer/6112435"
    const val GOOGLE_ORG =
        "A business should choose an organisation account, which also needs a D-U-N-S number. A new personal account must run a closed test with at least 12 testers for 14 days before an app can go live."
    const val CLOSED_TEST = "About this test"
    const val CLOSED_TEST_URL = "https://support.google.com/googleplay/android-developer/answer/14151465"
    const val STEP_INVITE = "Invite Raviga"
    const val COPY = "Copy"
    fun appleInvite(email: String) =
        "In App Store Connect, open Users and Access, then People, and tap +. Invite $email with the App Manager role, and turn on Access to Certificates, Identifiers & Profiles."
    const val APPLE_GUIDE = "Apple's guide"
    const val APPLE_GUIDE_URL = "https://developer.apple.com/help/app-store-connect/manage-your-team/add-and-edit-users"
    fun googleInvite(email: String) =
        "In Play Console, open Users and permissions, then Invite new users. Invite $email and turn on: Release to production, exclude devices, and use Play App Signing; Release apps to testing tracks; Manage testing tracks and edit tester lists; Manage store presence; Manage policy declarations; View app information (read-only)."
    const val GOOGLE_GUIDE = "Google's guide"
    const val GOOGLE_GUIDE_URL = "https://support.google.com/googleplay/android-developer/answer/9844686"
    const val INVITES_SENT = "I've sent the invites"
    const val INVITES_THANKS = "Thanks. We'll check and start publishing."
    const val STEP_AWS = "Connect your AWS"
    const val AWS_LINE = "Your app's backend runs in your own AWS account. Connect it once and we set it up there."
    const val CONNECT_AWS = "Connect AWS"
    const val CONNECTED = "Connected"
    const val STEP_PUBLISH = "We publish"
    const val PUBLISH_LINE = "We submit it under your name. Apple and Google review every new app before it appears."
    const val STEP_LIVE = "Live"
    const val LIVE_LINE = "Your app is in the App Store and Google Play under your name."
    const val OPEN_APP_STORE = "Open in the App Store"
    const val OPEN_GOOGLE_PLAY = "Open in Google Play"
    const val BACKEND_LIVE = "Its backend runs in your AWS account."
    fun stuck(supportEmail: String) = "Stuck on a step? Write to $supportEmail."

    /** Per store, when the server sent no line of its own (`handover.storeLines` wins). */
    fun storeLine(store: String, status: String): String = when (status) {
        "invited" -> "Publishing"
        "in_review" -> if (store == "apple") "In Apple's review" else "In Google's review"
        "published" -> "Published"
        else -> "Waiting for your invite"
    }

    // G. Other app lines.
    const val CONNECT_AWS_INTRO =
        "Your app's backend runs in your own AWS account. Connecting adds one role that only Raviga can use, and you can remove it any time."
    const val NOTHING_YET = "These steps appear once you approve your app."
    /** Connect AWS, under the account id field (the PM's copy, 2026-11-09). */
    const val AWS_ID_HINT = "Your 12-digit AWS account id. We deploy your app's backend here when it goes live."
}
