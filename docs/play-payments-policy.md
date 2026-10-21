# Can Raviga sell credits through Google Play? (R-013)

Checked against Google's own pages on 2026-10-21. The pages carry no effective date, so check them again before
submitting.

**Answer:** yes. Keeping Google Play Billing for credits (the current build) is allowed, and it is the lowest-risk
path. Play also probably lets us skip Play Billing, because the code is delivered outside the app. That would only
hold up if we sold each project as a one-off payment rather than as "credits".

## What Play's Payments policy says

- In-app payment for features or services must use Play Billing, unless an exception applies
  ([Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738), section 2).
- Play Billing **must not** be used for physical goods or **physical** services, such as transport, cleaning,
  airfare, gym memberships, food delivery and event tickets (section 3).
- Play Billing **is not required** in three cases
  ([policy guide](https://support.google.com/googleplay/android-developer/answer/10281818)):
  - digital goods consumed only outside the app, such as "web-only content never consumed in the app";
  - one-to-one live services between two individuals that can't be replayed, such as lessons or coaching.
- Outside those exceptions, the app may not steer users to another way to pay. There are opt-in programs for
  alternative billing and external links in eligible countries (sections 8 and 9).
- The maximum price for an in-app product is US$999.99, raised from US$400 in 2024. South Korea is still capped at
  US$400 ([Play Console help](https://support.google.com/googleplay/android-developer/answer/16247119)).

## Where Raviga fits

- **Not physical.** The section 3 ban doesn't apply, so using Play Billing is permitted.
- **Not one-to-one.** A team builds the project, not one individual, so the lessons-and-coaching exception is a poor fit.
- **The closest exception is "consumed outside the app".** The repo lives on GitHub, the deployment runs in the
  client's AWS, and the app only links to them. That argument works for a one-off project payment. It is weak for
  "credits": a balance bought and spent in the app reads like in-app currency, which needs Play Billing.

## Options

1. **Keep Play Billing for credits (current build).**
   - Compliant, with no app change.
   - Google's fee depends on where the buyer is
     ([service fees](https://support.google.com/googleplay/android-developer/answer/112622)):
     - US, UK, EEA, Japan and Australia: 20% for new installs or 25% for existing ones, plus a 5% billing fee;
     - elsewhere, including India: 15% of the first US$1M a year (for developers enrolled in the 15% tier),
       then 30%.
   - On a 34-credit ($340) project that's roughly $51 (India, enrolled) to $85–$102 (US).
   - Play handles refunds.
   - The $999.99 pack (`credits_100`) can't be sold in South Korea.
2. **Our own checkout per project (Razorpay or Stripe), no Play Billing.**
   - Saves the fee, but relies on the "consumed outside the app" exception, with a real risk of rejection.
   - The app would change: "credits" would go; the submit screen would pay for that project through a checkout
     SDK or a web checkout; RevenueCat would be removed on Android.
   - Backend would need a payments contract change.
   - The terms (5 and 13) and the Data safety form (payment info) would change.
3. **Play's alternative billing in India (section 8).** Offer our checkout next to Play Billing for a lower fee.
   This needs enrolment and both payment paths in the app.

## Recommendation

Launch with **option 1** and build the fee into the price of a credit. It needs no code change and carries no
review risk.

Revisit option 2 after launch if the fee matters. Before rebuilding anything, ask Play support for a pre-review.
Decide together with the App Store half: Apple's rule for services consumed outside the app points the other way
(it can require **non**-IAP payment), and both apps should sell the same way.

Not checked: how Fiverr- or Upwork-style apps take payment on Play today. They are the nearest precedent, but I
haven't verified their current checkout.
