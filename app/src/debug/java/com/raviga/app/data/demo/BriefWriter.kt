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
 * rambling description into the brief with plain heuristics. v0.15.0 (R-118): three sections in plain words a shop
 * owner understands, no technical names, deciding nothing technical, as the server's drafter: the only build statement
 * is "native iPhone and Android apps"; sign-in is Sign in with Apple or Google; payments go through the App Store and
 * Google Play (digital things) or are made in person (goods and services); a service that needs a paid or separate
 * account (text messages, one-time codes, WhatsApp, a map inside the app, emails, AI) isn't offered and is one plain
 * sentence in Summary. Good enough to walk every screen.
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

    val hints: Map<String, String> get() = SectionIds.hints

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
        Signal("\\b(log ?in|sign ?(in|up)|account|register|otp|password)\\b", SIGN_IN, listOf("Sign in"), null, "user profiles"),
        Signal("\\b(pay|payment|checkout|upi|razorpay|stripe|price|buy|purchase|wallet)\\b", PAYS_IN_STORES, listOf("Checkout", "Payment history"), "payments", "payments"),
        Signal("\\b(subscription|subscribe|monthly plan|premium)\\b", "Subscription plans through the App Store and Google Play", listOf("Plans"), "payments", "subscriptions"),
        Signal("\\b(chat|message|messaging|dm|inbox)\\b", "In-app chat between users", listOf("Conversations", "Chat"), null, "messages"),
        Signal("\\b(notif|push|alert|remind)\\w*", "Sends notifications and reminders", listOf("Notifications"), "messages", null),
        Signal("\\b(map|location|gps|track|nearby|navigate|route)\\w*", "Shows places on a map, with live location", listOf("Map"), "maps", "locations"),
        Signal("\\b(search|filter|browse|discover)\\w*", "Search with filters", listOf("Search results"), null, null),
        Signal("\\b(book|booking|appointment|schedule|slot|calendar|reserve)\\w*", "Booking with a calendar and time slots", listOf("Calendar", "Booking details"), null, "bookings"),
        Signal("\\b(upload|photo|image|camera|picture|gallery)\\w*", "Add photos from the camera or gallery", listOf("Upload"), null, "media files"),
        Signal("\\b(video|stream|reel)\\w*", "Add and play videos", listOf("Video player"), null, "videos"),
        Signal("\\b(rating|review|feedback|star)\\w*", "Ratings and reviews", listOf("Reviews"), null, "reviews"),
        Signal("\\b(order|cart|deliver|delivery|shop|store|product|catalog|menu|inventory|ecommerce|e-commerce)\\w*", "Catalogue, cart and order status", listOf("Catalogue", "Cart", "Order tracking"), null, "products and orders"),
        Signal("\\b(profile|bio|avatar)\\w*", "User profiles", listOf("Profile"), null, "user profiles"),
        Signal("\\b(admin|dashboard|panel|backoffice|back-office|manage users)\\w*", "A place for the owner to manage people and content", listOf("Admin"), null, null),
        Signal("\\b(report|analytics|statistic|insight|chart|graph)\\w*", "Reports with simple charts", listOf("Reports"), null, null),
        Signal("\\b(social|share|feed|post|follow|like|comment|community)\\w*", "Social feed with posts, likes and follows", listOf("Feed", "Post details", "Create post"), null, "posts"),
        Signal("\\b(ai|chatbot|gpt|assistant|recommend|smart|llm)\\b", "An assistant that answers questions in the app", listOf("Assistant"), null, null),
        Signal("\\b(voice|speak|audio|record)\\w*", "Voice input and playback", emptyList(), null, "recordings"),
        Signal("\\b(qr|barcode|scan)\\w*", "QR code scanning", listOf("Scanner"), null, null),
        Signal("\\b(whatsapp)\\b", "Sends updates on WhatsApp", emptyList(), "messages", null),
        Signal("\\b(email|mail)\\b", "Sends confirmations by email", emptyList(), "messages", null),
        Signal("\\b(google|apple|facebook) (sign|log)", SIGN_IN, listOf("Sign in"), null, "user profiles"),
        Signal("\\b(invoice|bill|billing|gst|receipt)\\w*", "Invoices and receipts with GST", listOf("Invoices"), null, "invoices"),
        Signal("\\b(attendance|check-?in|shift|roster)\\w*", "Attendance and check-in", listOf("Attendance"), null, "attendance records"),
        Signal("\\b(document|pdf|file|form)\\w*", "Add and view documents", listOf("Documents"), null, "documents"),
        Signal("\\b(offline)\\b", "Works offline and syncs later", emptyList(), null, null, nice = true),
        Signal("\\b(hindi|tamil|telugu|marathi|bengali|kannada|language|multilingual|regional)\\w*", "Works in more than one language", emptyList(), null, null, nice = true),
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
        val users = audiences.filter { it.regex.containsMatchIn(text) }.map { it.label }.distinct()
        val clientLines = clientLines(text)
        val title = title(text, users)
        val purpose = purpose(text)
        val apps = apps(text)

        // One list of everything that is built (no nice-to-have: later wishes are paid changes), only what is offered;
        // sign-in and payments in the standard words (v0.15.0).
        val inPerson = PAYS_IN_PERSON_WORDS.containsMatchIn(text)
        val features = (offered(clientLines.take(6)).filterNot { STANDARD_WORDS.containsMatchIn(it) } + offered(found.map { it.feature }))
            .map { if (it == PAYS_IN_STORES && inPerson) PAYS_IN_PERSON else plain(it) }
            .distinctBy { it.lowercase() }.take(12)

        val summary = buildString {
            append(title).append(" is ").append(apps)
            if (users.isNotEmpty()) append(" for ").append(naturalJoin(users))
            val cleanPurpose = purpose.replace(Regex("\\s+(called|named)\\s+\\S+$", RegexOption.IGNORE_CASE), "").trim()
            if (cleanPurpose.isNotBlank()) {
                val nounPhrase = Regex("^(my|our|a|an|the|this)\\b", RegexOption.IGNORE_CASE).containsMatchIn(cleanPurpose)
                append(if (nounPhrase) " for " else " that ").append(cleanPurpose)
            }
            append(".")
            val focus = features.filterNot { it.equals(cleanPurpose, true) }.take(2)
            if (focus.isNotEmpty()) append(" It focuses on ").append(naturalJoin(focus.map { f -> f.replaceFirstChar { c -> c.lowercase() } })).append(".")
            // v0.14.0, widened in v0.15.0: what was asked for and isn't offered is one plain sentence.
            val other = notOffered(text)
            if (other.isNotEmpty()) {
                append(" We build native iPhone and Android apps; ").append(naturalJoin(other))
                append(if (other.size == 1) " isn't" else " aren't").append(" part of this.")
            }
        }

        val targetUsers = buildString {
            if (users.isNotEmpty()) {
                append("- ").append(users.first().replaceFirstChar { it.uppercase() }).append(", who use it day to day")
                if (users.size > 1) append("\n- ").append(naturalJoin(users.drop(1)).replaceFirstChar { it.uppercase() })
            } else {
                append("- People who ").append(purpose.ifBlank { "use it day to day" })
            }
            if (found.any { it.feature.startsWith("A place for the owner") } || Regex("\\b(owner|staff)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                append("\n- The owner or staff, who run things day to day")
            }
        }

        fun section(id: String, body: String) = Section(id = id, heading = headings.getValue(id), body = body, hint = hints[id])
        fun bullets(items: List<String>) = items.joinToString("\n") { "- $it" }

        return Draft(
            title = title,
            sections = listOf(
                section(SectionIds.SUMMARY, summary),
                section(SectionIds.TARGET_USERS, targetUsers),
                section(SectionIds.FEATURES, bullets(features)),
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
        val fresh = write(inputs).sections.firstOrNull { it.id == section.id } ?: return section
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
        // v0.15.0: the server prices from the same text (apps, features, services, what the owner manages); the client
        // sees only the price (DemoApi sends the breakdown and drivers empty, as the server).
        val text = (doc.title + "\n" + doc.sections.joinToString("\n") { it.body }).lowercase()
        val drivers = mutableListOf<String>()
        var apps = 0
        val ios = Regex("\\b(ios|iphone|ipad|apple)\\b").containsMatchIn(text)
        val android = Regex("\\bandroid\\b").containsMatchIn(text)
        if (android || !ios) { apps += 6; drivers += "Android app" }
        if (ios || !android) { apps += 6; drivers += "iPhone app" }
        if (Regex("\\b(admin|owner to manage)\\b").containsMatchIn(text)) { apps += 6; drivers += "Admin screens" }
        val featureCount = lines(SectionIds.FEATURES).coerceAtMost(12)
        val features = featureCount * 3
        if (featureCount >= 6) drivers += "$featureCount features"
        val serviceCount = signals.mapNotNull { it.integration }.distinct().count { kind -> signals.any { it.integration == kind && it.regex.containsMatchIn(text) } }
        val integrations = serviceCount * 2
        val backend = 6 + featureCount
        // The rubric counts person-days; contract v0.7 prices them at x0.38, at least the Micro minimum.
        val personDays = (apps + features + integrations + backend).coerceAtLeast(15)
        val credits = ceil(personDays * PRICE_SCALE).toInt().coerceAtLeast(MIN_CREDITS)
        val breakdown = scaled(
            listOf("Apps" to apps, "Features" to features, "Backend" to backend, "Services" to integrations).filter { it.second > 0 },
            credits,
        )
        val bracket = config.quote.brackets.firstOrNull { b -> credits >= b.minCredits && (b.maxCredits == null || credits <= b.maxCredits) }
        // Contract v0.7.1: the timeline is the bracket's; Enterprise is 4 to max(6, credits / 30) weeks.
        val timeline = bracket?.timeline ?: QuoteTimeline(minWeeks = 4, maxWeeks = maxOf(6, ceil(credits / 30.0).toInt()))
        val days = timeline.maxWeeks * 5
        val score = (personDays / 40.0 * 10).roundToInt().coerceIn(1, 10)
        val assumptions = buildList {
            // R-111 (as iOS's mock): no asks of the client. R-121 (the PM, 2026-11-12): no third-party billing line.
            if (!text.contains("language")) add("One language (English) in the first release")
            // R-112: the assumptions end with the free-fix rule.
            add(com.raviga.app.data.api.ReportConfig.FREE_FIXES_LINE)
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

    /** Which apps the words ask for, in the one build statement (v0.15.0, as iOS's mock). */
    private fun apps(text: String): String {
        val t = text.lowercase()
        val android = Regex("\\bandroid\\b").containsMatchIn(t)
        val ios = Regex("\\b(ios|iphone|ipad)\\b").containsMatchIn(t)
        return when {
            android && !ios -> "a native Android app"
            ios && !android -> "a native iPhone app"
            else -> "a pair of native iPhone and Android apps"
        }
    }

    private const val SIGN_IN = "Sign in with Apple or Google"
    private const val PAYS_IN_STORES = "Takes payments through the App Store and Google Play"
    private const val PAYS_IN_PERSON = "Customers pay in person"

    /** Goods and services are paid in person (v0.15.0); only digital things go through the stores. */
    private val PAYS_IN_PERSON_WORDS = Regex(
        "\\b(book|booking|appointment|salon|order|deliver|delivery|food|shop|product|service|store|restaurant)\\w*", RegexOption.IGNORE_CASE,
    )

    /** A client's own line about signing in or paying gives way to the standard words. */
    private val STANDARD_WORDS = Regex("\\b(sign ?in|sign ?up|log ?in|login|pay|payment|upi|checkout|otp|password)\\w*", RegexOption.IGNORE_CASE)

    /** Words for what isn't offered (v0.15.0): a website or desktop app, and services that need a paid or separate account. */
    private val UNOFFERED = Regex(
        "\\b(sms|otp|one-time code|text message|whatsapp|maps?|gps|live location|live tracking|e-?mails?|website|web app|desktop|ai|chatbot|gpt|assistant|llm)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Features keep only what is offered; the rest is the Summary sentence. */
    private fun offered(items: List<String>): List<String> = items.filterNot { UNOFFERED.containsMatchIn(it) }

    /** Asked for but not offered, as the server says in Summary (v0.15.0; iOS's mock says the same). */
    private fun notOffered(text: String): List<String> {
        val t = text.lowercase()
        fun has(pattern: String) = Regex(pattern).containsMatchIn(t)
        return buildList {
            if (has("\\b(website|web app|webapp)\\b")) add("the website")
            if (has("\\bdesktop\\b")) add("the desktop app")
            if (has("\\b(sms|otp|text messages?|one-time code)\\b")) add("text messages")
            if (has("\\bwhatsapp\\b")) add("WhatsApp messages")
            if (has("\\b(maps?|gps|live location|live tracking)\\b")) add("a map inside the app")
            if (has("\\be-?mails?\\b")) add("emails")
            if (has("\\b(ai|chatbot|gpt|llm)\\b")) add("an AI assistant")
        }
    }

    /** No technical names (v0.14.0), and no admin panel or dashboard (v0.15.0: what the owner does is a Feature). */
    private fun plain(item: String): String = item
        .replace(Regex("\\b[Aa]n? admin (panel|dashboard|screen)\\b"), "a place for the owner to manage things")
        .replace(Regex("\\badmin (panel|dashboard|screen)\\b", RegexOption.IGNORE_CASE), "a place for the owner to manage things")
        .replace(Regex("\\bdashboard\\b"), "view")
        .replace(Regex("\\bDashboard\\b"), "View")
        .replace(Regex("\\b[Pp]ush notifications\\b"), "notifications")
        .replaceFirstChar { it.uppercase() }

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

    private fun titleCase(s: String) = s.split(" ").filter { it.isNotBlank() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}
