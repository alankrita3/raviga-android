package com.raviga.app.data.demo

import com.raviga.app.data.api.AppConfig
import com.raviga.app.data.api.Document
import com.raviga.app.data.api.QuoteTimeline
import com.raviga.app.data.api.Section
import com.raviga.app.data.api.SectionIds
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Stand-in for the backend's LLM while there is no backend: turns a client's
 * rambling description into the twelve-section brief with plain heuristics.
 * Good enough to walk every screen; the real document comes from the API.
 */
object BriefWriter {

    data class Draft(val title: String, val sections: List<Section>)

    data class QuoteParts(
        val credits: Int,
        val bracketId: String?,
        val workingDays: Int,
        val timeline: QuoteTimeline,
        val score: Int,
        val drivers: List<String>,
        val assumptions: List<String>,
        val breakdown: List<Pair<String, Int>>,
    )

    val headings: Map<String, String> get() = SectionIds.headings

    val hints = mapOf(
        SectionIds.SUMMARY to "What the product is, in a few sentences.",
        SectionIds.TARGET_USERS to "Who uses it first, and who else.",
        SectionIds.PLATFORMS to "Android, iOS, web, admin panel.",
        SectionIds.FEATURES_MUST to "What the first release cannot ship without.",
        SectionIds.FEATURES_NICE to "What can wait for a later release.",
        SectionIds.SCREENS to "The screens a user will move through.",
        SectionIds.INTEGRATIONS to "Payments, maps, messaging, other services.",
        SectionIds.DATA_AUTH to "How people sign in and what is stored.",
        SectionIds.NON_FUNCTIONAL to "Speed, scale, security, accessibility.",
        SectionIds.DELIVERABLES to "What you receive at handover.",
        SectionIds.OUT_OF_SCOPE to "What this project does not include.",
        SectionIds.OPEN_QUESTIONS to "Things we still need you to decide.",
    )

    private class Signal(
        pattern: String,
        val feature: String,
        val screens: List<String> = emptyList(),
        val integration: String? = null,
        val record: String? = null,
        val nice: Boolean = false,
    ) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private val signals = listOf(
        Signal("\\b(log ?in|sign ?(in|up)|account|register|otp|password)\\b", "Sign in with email or phone number", listOf("Sign in", "Verify code"), "SMS sign-in codes (Twilio or Firebase Auth)", "user profiles"),
        Signal("\\b(pay|payment|checkout|upi|razorpay|stripe|price|buy|purchase|wallet)\\b", "Payments by card, Apple Pay and Google Pay", listOf("Checkout", "Payment history"), "Stripe (cards, Apple Pay, Google Pay; Razorpay for customers in India)", "payments"),
        Signal("\\b(subscription|subscribe|monthly plan|premium)\\b", "Subscription plans with a free tier", listOf("Plans"), "Google Play Billing and Apple In-App Purchase", "subscriptions"),
        Signal("\\b(chat|message|messaging|dm|inbox)\\b", "In-app chat between users", listOf("Conversations", "Chat"), null, "messages"),
        Signal("\\b(notif|push|alert|remind)\\w*", "Push notifications and reminders", listOf("Notifications"), "Firebase Cloud Messaging", null),
        Signal("\\b(map|location|gps|track|nearby|navigate|route)\\w*", "Maps with live location", listOf("Map"), "Google Maps Platform", "locations"),
        Signal("\\b(search|filter|browse|discover)\\w*", "Search with filters", listOf("Search results"), null, null),
        Signal("\\b(book|booking|appointment|schedule|slot|calendar|reserve)\\w*", "Booking with a calendar and time slots", listOf("Calendar", "Booking details"), null, "bookings"),
        Signal("\\b(upload|photo|image|camera|picture|gallery)\\w*", "Photo upload from camera or gallery", listOf("Upload"), "Amazon S3 for media", "media files"),
        Signal("\\b(video|stream|reel)\\w*", "Video upload and playback", listOf("Video player"), "Amazon S3 and CloudFront for video", "videos"),
        Signal("\\b(rating|review|feedback|star)\\w*", "Ratings and reviews", listOf("Reviews"), null, "reviews"),
        Signal("\\b(order|cart|deliver|delivery|shop|store|product|catalog|menu|inventory|ecommerce|e-commerce)\\w*", "Catalogue, cart and order tracking", listOf("Catalogue", "Cart", "Order tracking"), null, "products and orders"),
        Signal("\\b(profile|bio|avatar)\\w*", "User profiles", listOf("Profile"), null, "user profiles"),
        Signal("\\b(admin|dashboard|panel|backoffice|back-office|manage users)\\w*", "Admin panel to manage users and content", listOf("Admin dashboard (web)"), null, null),
        Signal("\\b(report|analytics|statistic|insight|chart|graph)\\w*", "Reports and analytics", listOf("Reports"), "Firebase Analytics", null),
        Signal("\\b(social|share|feed|post|follow|like|comment|community)\\w*", "Social feed with posts, likes and follows", listOf("Feed", "Post details", "Create post"), null, "posts"),
        Signal("\\b(ai|chatbot|gpt|assistant|recommend|smart|llm)\\b", "AI assistant that answers questions in the app", listOf("Assistant"), "LLM provider (Anthropic Claude)", null),
        Signal("\\b(voice|speak|audio|record)\\w*", "Voice input and playback", emptyList(), null, "recordings"),
        Signal("\\b(qr|barcode|scan)\\w*", "QR code scanning", listOf("Scanner"), null, null),
        Signal("\\b(whatsapp)\\b", "WhatsApp messages for updates", emptyList(), "WhatsApp Business API", null),
        Signal("\\b(email|mail)\\b", "Email confirmations", emptyList(), "Transactional email (Amazon SES)", null),
        Signal("\\b(google|apple|facebook) (sign|log)", "Sign in with Google and Apple", listOf("Sign in"), "Google and Apple sign in", "user profiles"),
        Signal("\\b(invoice|bill|billing|gst|receipt)\\w*", "Invoices and receipts with GST", listOf("Invoices"), null, "invoices"),
        Signal("\\b(attendance|check-?in|shift|roster)\\w*", "Attendance and check-in", listOf("Attendance"), null, "attendance records"),
        Signal("\\b(document|pdf|file|form)\\w*", "Document upload and viewing", listOf("Documents"), "Amazon S3 for files", "documents"),
        Signal("\\b(offline)\\b", "Works offline and syncs later", emptyList(), null, null, nice = true),
        Signal("\\b(hindi|tamil|telugu|marathi|bengali|kannada|language|multilingual|regional)\\w*", "Multiple languages", emptyList(), null, null, nice = true),
        Signal("\\b(refer|referral|invite)\\w*", "Referral links and invites", listOf("Invite friends"), null, null, nice = true),
        Signal("\\b(dark mode|theme)\\b", "Dark mode", emptyList(), null, null, nice = true),
        Signal("\\b(coupon|discount|offer|promo)\\w*", "Coupons and offers", listOf("Offers"), null, "coupons", nice = true),
        Signal("\\b(leaderboard|points|reward|gamif|badge)\\w*", "Points, rewards and leaderboards", listOf("Rewards"), null, "points", nice = true),
    )

    private class Audience(pattern: String, val label: String) { val regex = Regex(pattern, RegexOption.IGNORE_CASE) }
    private val audiences = listOf(
        Audience("\\bstudents?\\b", "students"), Audience("\\bteachers?|tutors?|coach(es)?\\b", "teachers and tutors"),
        Audience("\\bdoctors?|clinics?|hospitals?\\b", "doctors and clinics"), Audience("\\bpatients?\\b", "patients"),
        Audience("\\brestaurants?|cafes?|caf\u00e9s?|kitchens?\\b", "restaurants and cafés"), Audience("\\bshops?|stores?|retailers?|kirana|merchants?\\b", "shop owners"),
        Audience("\\bvendors?|sellers?|suppliers?\\b", "vendors and sellers"), Audience("\\bbuyers?|customers?|shoppers?|clients?\\b", "customers"),
        Audience("\\bdrivers?|riders?|delivery (partners?|boys?|agents?)\\b", "drivers and delivery partners"), Audience("\\bparents?\\b", "parents"),
        Audience("\\bkids?|children\\b", "children"), Audience("\\bgyms?|trainers?|fitness\\b", "gyms and trainers"),
        Audience("\\bemployees?|staff|workers?\\b", "employees"), Audience("\\bteams?|managers?\\b", "teams and managers"),
        Audience("\\bfreelancers?|contractors?\\b", "freelancers"), Audience("\\btenants?|landlords?|flats?|societ(y|ies)\\b", "residents and landlords"),
        Audience("\\bfarmers?\\b", "farmers"), Audience("\\btourists?|travell?ers?|hotels?\\b", "travellers"),
        Audience("\\bcreators?|influencers?|artists?\\b", "creators"), Audience("\\bdesigners?|developers?|agencies\\b", "agencies and developers"),
        Audience("\\bsalons?|barbers?|spa\\b", "salons"), Audience("\\blawyers?|advocates?|ca\\b|accountants?\\b", "professionals"),
        Audience("\\bsociety|housing|apartment|building\\b", "housing societies"), Audience("\\bschools?|colleges?|institutes?\\b", "schools and colleges"),
    )

    private val stopWords = setOf(
        "a", "an", "the", "i", "we", "you", "want", "need", "to", "build", "make", "create", "app", "apps", "for", "that",
        "which", "and", "or", "of", "in", "on", "with", "is", "it", "its", "my", "our", "this", "like", "be", "can", "should",
        "would", "so", "also", "then", "at", "by", "from", "as", "have", "has", "get", "got", "just", "please", "something",
        "some", "kind", "type", "basically", "um", "uh", "where", "there", "they", "them", "their", "will", "let", "able",
        "users", "user", "people", "platform", "website", "web", "mobile", "application", "called", "named", "new", "simple",
    )

    fun write(inputs: List<String>): Draft {
        val text = inputs.joinToString("\n").trim()
        val found = signals.filter { it.regex.containsMatchIn(text) }
        val platforms = platforms(text)
        val users = audiences.filter { it.regex.containsMatchIn(text) }.map { it.label }.distinct()
        val clientLines = clientLines(text)
        val title = title(text, users)
        val purpose = purpose(text)

        val must = (clientLines.take(6) + found.filter { !it.nice }.map { it.feature }).distinctBy { it.lowercase() }.take(10)
        val nice = (found.filter { it.nice }.map { it.feature } + defaultNice(found)).distinct().take(5)
        val screens = (listOf("Welcome", "Home") + found.flatMap { it.screens } + listOf("Settings")).distinct()
        val integrations = (found.mapNotNull { it.integration } + defaultIntegrations(found, platforms)).distinct()
        val records = found.mapNotNull { it.record }.distinct()

        val summary = buildString {
            append(title).append(" is ")
            append(if (platforms.any { it.contains("web", true) } && platforms.none { it.contains("Android") }) "a web product" else "a mobile app")
            if (users.isNotEmpty()) append(" for ").append(naturalJoin(users))
            val cleanPurpose = purpose.replace(Regex("\\s+(called|named)\\s+\\S+$", RegexOption.IGNORE_CASE), "").trim()
            if (cleanPurpose.isNotBlank()) {
                val nounPhrase = Regex("^(my|our|a|an|the|this)\\b", RegexOption.IGNORE_CASE).containsMatchIn(cleanPurpose)
                append(if (nounPhrase) " for " else " that ").append(cleanPurpose)
            }
            append(". ")
            append("The first release ships ${must.size} must-have ${if (must.size == 1) "feature" else "features"} across ")
            append(naturalJoin(platforms.map { shortPlatform(it) }))
            append(".")
            val focus = must.filterNot { it.equals(cleanPurpose, true) }.take(2)
            if (focus.isNotEmpty()) append(" It focuses on ").append(naturalJoin(focus.map { f -> f.replaceFirstChar { c -> c.lowercase() } })).append(".")
        }

        val targetUsers = buildString {
            if (users.isNotEmpty()) {
                append("- Primary: ").append(users.first()).append("\n")
                if (users.size > 1) append("- Secondary: ").append(users.drop(1).joinToString(", ")).append("\n")
            } else {
                append("- Primary: people who ").append(purpose.ifBlank { "use the product day to day" }).append("\n")
            }
            append("- Region: where your customers are, English interface")
        }

        val dataAuth = buildString {
            val signIn = found.firstOrNull { it.feature.startsWith("Sign in") }?.feature ?: "No accounts in the first release; a device-bound identity, upgradeable to sign in later"
            append("- Sign in: ").append(signIn).append("\n")
            val roles = mutableListOf("user")
            if (found.any { it.feature.startsWith("Admin") }) roles += "admin"
            if (users.any { it.contains("vendor") || it.contains("seller") || it.contains("shop") }) roles += "vendor"
            if (users.any { it.contains("driver") }) roles += "driver"
            append("- Roles: ").append(roles.joinToString(", ")).append("\n")
            append("- Core records: ").append((listOf("user profiles") + records).distinct().joinToString(", ")).append("\n")
            append("- Data is stored on AWS in the region closest to your customers, encrypted at rest, and exportable or deletable on request")
        }

        val nonFunctional = listOf(
            "Supports Android 8 and newer${if (platforms.any { it.contains("iOS") }) ", and iOS 15 and newer" else ""}",
            "Home screen loads in under 2 seconds on a 4G connection",
            "Handles 10,000 monthly active users without changes",
            "All traffic over HTTPS; personal data encrypted at rest",
            "Privacy basics: consent, data export, deletion and a contact for requests (GDPR and similar laws)",
            "Font scaling and screen reader labels on every screen",
        )

        val deliverables = buildList {
            add("Source code in a GitHub repository transferred to your account")
            if (platforms.any { it.contains("Android") }) add("Play Store ready Android build (AAB)")
            if (platforms.any { it.contains("iOS") }) add("App Store ready iOS build")
            if (platforms.any { it.contains("web", true) }) add("Deployed web app with your domain pointed at it")
            add("Backend deployed on AWS, handed over through a cross-account role")
            add("Setup notes and a README for the next developer")
            add("Two rounds of revisions after delivery")
        }

        val outOfScope = buildList {
            add("Logo, brand design and marketing material")
            add("Writing product content, listings or help articles")
            add("Store listing assets, developer account fees and ongoing hosting costs")
            if (found.none { it.feature.startsWith("Admin") }) add("An admin web panel (can be added as a second project)")
            if (platforms.none { it.contains("iOS") }) add("An iOS app")
            if (platforms.none { it.contains("web", true) }) add("A public website")
        }

        val questions = buildList {
            if (!Regex("\\b(pay|price|free|monet|subscri|ads?|revenue|commission)\\w*", RegexOption.IGNORE_CASE).containsMatchIn(text))
                add("How will the product make money, if at all?")
            if (users.isEmpty()) add("Who is the first group of people who will use it?")
            if (found.none { it.feature.startsWith("Sign in") }) add("Do people need accounts, or can they use it without signing in?")
            add("Do you have a name, logo and colours we should use?")
            if (found.none { it.feature == "Multiple languages" }) add("Should it support languages other than English?")
            if (!Regex("\\b(existing|current|already|migrate|old)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text))
                add("Is there an existing system or data we must connect to or migrate from?")
        }.take(5)

        fun section(id: String, body: String) = Section(id = id, heading = headings.getValue(id), body = body, hint = hints[id])
        fun bullets(items: List<String>) = items.joinToString("\n") { "- $it" }

        return Draft(
            title = title,
            sections = listOf(
                section(SectionIds.SUMMARY, summary),
                section(SectionIds.TARGET_USERS, targetUsers),
                section(SectionIds.PLATFORMS, bullets(platforms)),
                section(SectionIds.FEATURES_MUST, bullets(must)),
                section(SectionIds.FEATURES_NICE, bullets(nice)),
                section(SectionIds.SCREENS, bullets(screens)),
                section(SectionIds.INTEGRATIONS, bullets(integrations)),
                section(SectionIds.DATA_AUTH, dataAuth),
                section(SectionIds.NON_FUNCTIONAL, bullets(nonFunctional)),
                section(SectionIds.DELIVERABLES, bullets(deliverables)),
                section(SectionIds.OUT_OF_SCOPE, bullets(outOfScope)),
                section(SectionIds.OPEN_QUESTIONS, bullets(questions)),
            ),
        )
    }

    /** Merges anything new in [inputs] into [existing] without rewriting it. */
    fun append(existing: Document, inputs: List<String>): List<Section> {
        val fresh = write(inputs).sections.associateBy { it.id }
        return existing.sections.map { section ->
            val incoming = fresh[section.id] ?: return@map section
            if (!incoming.body.startsWith("- ")) return@map section
            val have = section.body.lines().map { it.trim().lowercase() }.toSet()
            val extra = incoming.body.lines().filter { it.trim().lowercase() !in have }
            if (extra.isEmpty()) section else section.copy(body = (section.body.trimEnd() + "\n" + extra.joinToString("\n")).trim())
        }
    }

    fun regenerate(section: Section, inputs: List<String>, instruction: String?): Section {
        val fresh = write(inputs).sections.first { it.id == section.id }
        val wish = instruction?.trim().orEmpty()
        val body = when {
            wish.contains("short", true) || wish.contains("brief", true) || wish.contains("concise", true) -> {
                val lines = fresh.body.lines()
                if (lines.size > 2) lines.take(ceil(lines.size / 2.0).toInt()).joinToString("\n") else fresh.body
            }
            wish.startsWith("add ", true) -> (fresh.body.trimEnd() + "\n- " + wish.drop(4).trim().replaceFirstChar { it.uppercase() }).trim()
            wish.startsWith("remove ", true) -> fresh.body.lines().filterNot { it.contains(wish.drop(7).trim(), true) }.joinToString("\n")
            wish.isNotBlank() -> (fresh.body.trimEnd() + "\n- " + wish.replaceFirstChar { it.uppercase() }).trim()
            else -> fresh.body
        }
        return section.copy(body = body)
    }

    fun quote(doc: Document, config: AppConfig): QuoteParts {
        fun lines(id: String) = doc.section(id)?.body?.lines()?.count { it.trim().startsWith("-") } ?: 0
        val platforms = doc.section(SectionIds.PLATFORMS)?.body.orEmpty().lowercase()
        val drivers = mutableListOf<String>()
        var apps = 0
        if (platforms.contains("android")) { apps += 6; drivers += "Android app" }
        if (platforms.contains("ios")) { apps += 6; drivers += "iOS app" }
        if (platforms.contains("web")) { apps += 8; drivers += "Web app" }
        if (platforms.contains("admin")) { apps += 6; drivers += "Admin panel" }
        val must = lines(SectionIds.FEATURES_MUST).coerceAtMost(12)
        val features = must * 3
        if (must >= 6) drivers += "$must must-have features"
        val integrationCount = lines(SectionIds.INTEGRATIONS)
        val integrations = integrationCount * 2
        if (integrationCount >= 3) drivers += "$integrationCount integrations"
        val backend = 6 + must
        // The rubric counts person-days; contract v0.7 prices them at x0.38, at least the Micro minimum.
        val personDays = (apps + features + integrations + backend).coerceAtLeast(15)
        val credits = ceil(personDays * PRICE_SCALE).toInt().coerceAtLeast(MIN_CREDITS)
        val breakdown = scaled(
            listOf("Apps" to apps, "Features" to features, "Backend" to backend, "Integrations" to integrations).filter { it.second > 0 },
            credits,
        )
        val bracket = config.quote.brackets.firstOrNull { b -> credits >= b.minCredits && (b.maxCredits == null || credits <= b.maxCredits) }
        // Contract v0.7.1: the timeline is the bracket's; Enterprise is 4 to max(6, credits / 30) weeks.
        val timeline = bracket?.timeline ?: QuoteTimeline(minWeeks = 4, maxWeeks = maxOf(6, ceil(credits / 30.0).toInt()))
        val days = timeline.maxWeeks * 5
        val score = (personDays / 40.0 * 10).roundToInt().coerceIn(1, 10)
        val assumptions = buildList {
            add("Design follows Raviga's standard minimal interface unless you provide brand guidelines")
            add("Third-party accounts (Stripe, Google Maps, Apple Developer) are opened by you; we set them up")
            add("Product content and images are provided by you")
            if (!doc.section(SectionIds.FEATURES_NICE)?.body.orEmpty().contains("languages", true)) add("One language (English) in the first release")
            add("Nice-to-have features are not included in this quote")
        }
        return QuoteParts(credits, bracket?.id, days, timeline, score, drivers.take(4), assumptions, breakdown)
    }

    private const val PRICE_SCALE = 0.38
    private const val MIN_CREDITS = 5

    /** Shares of [total] in proportion to each part, adding up exactly (the largest absorbs the rounding). */
    private fun scaled(parts: List<Pair<String, Int>>, total: Int): List<Pair<String, Int>> {
        val sum = parts.sumOf { it.second }.coerceAtLeast(1)
        val shares = parts.map { it.first to (it.second * total / sum).coerceAtLeast(1) }.toMutableList()
        val largest = shares.indices.maxByOrNull { shares[it].second } ?: return shares
        shares[largest] = shares[largest].first to (shares[largest].second + total - shares.sumOf { it.second }).coerceAtLeast(1)
        return shares
    }

    // ----- helpers -----

    private fun platforms(text: String): List<String> {
        val t = text.lowercase()
        val out = mutableListOf<String>()
        val android = Regex("\\bandroid\\b").containsMatchIn(t)
        val ios = Regex("\\b(ios|iphone|ipad|apple)\\b").containsMatchIn(t)
        val web = Regex("\\b(web|website|browser|webapp|web app|portal|site)\\b").containsMatchIn(t)
        val admin = Regex("\\b(admin|dashboard|panel|backoffice|back-office)\\b").containsMatchIn(t)
        if (android || (!ios && !web)) out += "Android app"
        if (ios || (!android && !web)) out += "iOS app"
        if (web) out += "Web app"
        if (admin) out += "Admin panel (web)"
        out += "Backend API on AWS"
        return out
    }

    private fun defaultNice(found: List<Signal>): List<String> = buildList {
        if (found.none { it.feature == "Works offline and syncs later" }) add("Works offline and syncs later")
        if (found.none { it.feature == "Multiple languages" }) add("More languages")
        add("Share to WhatsApp")
        if (found.none { it.feature == "Reports and analytics" }) add("Usage analytics for you")
    }

    private fun defaultIntegrations(found: List<Signal>, platforms: List<String>): List<String> = buildList {
        if (found.none { it.integration == "Firebase Cloud Messaging" }) add("Firebase Cloud Messaging for push notifications")
        add("Amazon S3 for files and images")
        if (platforms.any { it.contains("web", true) }) add("Amazon CloudFront for the web app")
    }

    private fun purpose(text: String): String {
        val first = text.split(Regex("[.!?\\n]")).map { it.trim() }.firstOrNull { it.length > 12 } ?: return ""
        val stripped = first.replace(
            Regex("^(so|basically|okay|ok|hi|hello|um|uh|yeah|like)[, ]+", RegexOption.IGNORE_CASE), "",
        ).replace(
            Regex("^(i|we)\\s+(want|need|would like|am looking|are looking|wish)\\s+(to\\s+)?(build|make|create|have|get|develop)?\\s*(an?|the)?\\s*(mobile\\s+)?(app|application|platform|website|web app|tool|service|system|marketplace|portal)?\\s*(that|which|for|to|where|so that)?\\s*", RegexOption.IGNORE_CASE), "",
        ).trim().trimEnd('.', ',', ';')
        return if (stripped.length in 8..160) stripped.replaceFirstChar { it.lowercase() } else ""
    }

    private fun title(text: String, users: List<String>): String {
        Regex("\\b(called|named|name is)\\s+([A-Z][\\w']*(?:\\s+[A-Z][\\w']*){0,2})").find(text)?.let { return it.groupValues[2] }
        Regex("\\b(called|named|name is)\\s+\"?([\\w']+(?:\\s+[\\w']+){0,2})\"?", RegexOption.IGNORE_CASE).find(text)?.let { return titleCase(it.groupValues[2]) }
        val words = purpose(text).ifBlank { text }
            .split(Regex("[^A-Za-z0-9']+"))
            .filter { it.length > 2 && it.lowercase() !in stopWords }
        val picked = words.take(3)
        if (picked.isEmpty()) return if (users.isNotEmpty()) titleCase(users.first()) + " App" else "Untitled project"
        return titleCase(picked.joinToString(" "))
    }

    private fun clientLines(text: String): List<String> = text
        .split(Regex("[.!?\\n;]+"))
        .map { it.trim() }
        .filterNot { Regex("^(i|we)\\s+(want|need|would like|am looking|are looking|wish)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) && Regex("\\b(app|application|platform|website|web app|tool|service|system|marketplace|portal)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        .map {
            it.replace(Regex("^(so|basically|okay|ok|um|uh|yeah|like|also|and|then|plus)[, ]+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^(i|we)\\s+(also\\s+)?(want|need|would like|think|wish)\\s+(it\\s+)?(to\\s+|that\\s+)?", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^(the\\s+)?(app|it|users?|people|customers?)\\s+(should|must|can|will|needs? to|has to|have to)\\s+(be able to\\s+)?", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^(there\\s+should\\s+be|it\\s+should\\s+have)\\s+", RegexOption.IGNORE_CASE), "")
                .trim()
        }
        .filter { it.length in 12..140 }
        .map { it.replaceFirstChar { c -> c.uppercase() } }
        .distinctBy { it.lowercase() }

    private fun naturalJoin(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> items[0] + " and " + items[1]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    private fun shortPlatform(p: String): String = when {
        p.startsWith("Android") -> "Android"
        p.startsWith("iOS") -> "iOS"
        p.startsWith("Web") -> "the web"
        p.startsWith("Admin") -> "an admin panel"
        else -> "a backend API on AWS"
    }

    private fun titleCase(s: String) = s.split(" ").filter { it.isNotBlank() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}
