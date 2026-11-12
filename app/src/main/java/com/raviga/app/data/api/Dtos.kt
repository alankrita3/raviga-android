package com.raviga.app.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * Wire shapes for the Raviga API, mirroring
 * "Raviga Backend/docs/api-contract.md" v0.3 (canonical). They double as the
 * app's domain models: the contract is already shaped for display. Everything
 * optional is nullable with a default so an older app keeps parsing a newer
 * backend. Keys are camelCase; enum values and section ids are snake_case.
 */

// ---------- Identity (section 3) ----------

@Serializable
data class RegisterDeviceRequest(
    val platform: String = "android",
    val installId: String,
    val appVersion: String,
    val deviceModel: String,
    val osVersion: String,
    val locale: String? = null,
    val linkCode: String? = null,
)

@Serializable
data class RegisterDeviceResponse(
    val clientId: String,
    val deviceId: String? = null,
    val accessToken: String,
    val recoveryKey: String? = null,
    val isNewClient: Boolean = false,
)

@Serializable
data class RecoverDeviceRequest(
    val recoveryKey: String,
    val platform: String = "android",
    val installId: String,
    val appVersion: String,
    val deviceModel: String,
    val osVersion: String,
)

@Serializable
data class RecoveryKeyResponse(val recoveryKey: String)

@Serializable
data class LinkCodeResponse(val code: String, val expiresAt: String? = null)

// ----- Recovery email and devices (contract 3.1-3.3, R-079) -----

@Serializable
data class RecoveryEmailRequest(val email: String)

/** `202 { "status": "pending" }`: a 6-digit code is on its way. */
@Serializable
data class PendingResponse(val status: String = "")

@Serializable
data class OtpRequest(val otp: String)

@Serializable
data class RecoveryEmailConfirmed(val email: String = "", val verifiedAt: String? = null)

/** `POST /devices/recover { email }`: always 202, even for an unknown email (no enumeration). */
@Serializable
data class RecoverByEmailRequest(val email: String)

@Serializable
data class RecoverByEmailConfirmRequest(
    val email: String,
    val otp: String,
    val platform: String = "android",
    val installId: String,
    val appVersion: String,
    val deviceModel: String,
    val osVersion: String,
)

@Serializable
data class DevicesResponse(val devices: List<DeviceSummary> = emptyList())

@Serializable
data class DeviceSummary(
    val deviceId: String,
    val platform: String = "",
    val deviceModel: String? = null,
    val appVersion: String? = null,
    val createdAt: String? = null,
    val lastSeenAt: String? = null,
    val isCurrent: Boolean = false,
)

@Serializable
/** Exactly one of [fid] (contract v0.8.2, the Firebase Installation ID) or [token] (older builds' FCM token). */
data class PushTokenRequest(val fid: String? = null, val token: String? = null)

@Serializable
data class Me(
    val clientId: String,
    val createdAt: String? = null,
    val credits: CreditsBalance = CreditsBalance(),
    val consent: ConsentState = ConsentState(),
    val recoveryEmail: RecoveryEmail? = null,
    /** v0.12.0 (R-109): where the TestFlight and Play test invites go; each null until set. */
    val storeEmails: StoreEmails = StoreEmails(),
    val deliveryTargets: DeliveryTargets = DeliveryTargets(),
    val deviceCount: Int = 1,
    val deletion: Deletion? = null,
)

@Serializable
/** v0.11.1 (R-099): credits are numbers with up to 4 decimals (a brief is a twelfth of a credit). */
data class CreditsBalance(
    val balance: Double = 0.0,
    /** R-099: new briefs this account can still write free (null until backend sends it; then the phone guesses). */
    val freeBriefsLeft: Int? = null,
)

@Serializable
data class RecoveryEmail(val email: String, val verified: Boolean = false)

@Serializable
data class Deletion(val requestedAt: String? = null, val purgeAt: String? = null)

// ---------- Consent (section 4) ----------

@Serializable
data class ConsentState(
    val terms: ConsentVersion? = null,
    val privacy: ConsentVersion? = null,
    val aiProcessing: AiConsent? = null,
    /** v0.9: usage analytics (section 22). Optional, never gates anything, off until granted. */
    val analytics: AiConsent? = null,
    val current: Boolean = false,
) {
    val aiGranted: Boolean get() = aiProcessing?.granted == true
    val analyticsGranted: Boolean get() = analytics?.granted == true
}

@Serializable
data class ConsentVersion(val version: String, val acceptedAt: String? = null)

/** A granted flag with its time; used for AI processing and (v0.9) analytics. */
@Serializable
data class AiConsent(val granted: Boolean = false, val updatedAt: String? = null)

@Serializable
data class ConsentRequest(
    val termsVersion: String? = null,
    val privacyVersion: String? = null,
    val aiProcessing: Boolean? = null,
    val analytics: Boolean? = null,
)

// ---------- Config (section 6) ----------

@Serializable
data class AppConfig(
    val minAppVersion: Map<String, String> = emptyMap(),
    val credits: CreditsConfig = CreditsConfig(),
    val quote: QuoteConfig = QuoteConfig(),
    /** v0.10.0 (R-103): an accepted project left unpaid this long is cancelled. */
    val payment: PaymentConfig = PaymentConfig(),
    val acceptance: AcceptanceConfig = AcceptanceConfig(),
    /** v0.10.0 (R-103): "Something's wrong" with a delivery: fixed categories, a short description. */
    val report: ReportConfig = ReportConfig(),
    /** v0.10.0: the fair-use cap on new briefs (0 means none); past it /ai/draft answers 429 rate_limited. */
    val briefLimits: BriefLimits = BriefLimits(),
    val retention: RetentionConfig = RetentionConfig(),
    val limits: LimitsConfig = LimitsConfig(),
    val ai: AiConfig = AiConfig(),
    val analytics: AnalyticsConfig = AnalyticsConfig(),
    val delivery: DeliveryConfig = DeliveryConfig(),
    /** v0.12.0 (R-109): Make it live: the Apple role, the Play permissions and the official guides. */
    val handover: HandoverConfig = HandoverConfig(),
    val legal: LegalConfig = LegalConfig(),
) {
    fun bracket(id: String?): Bracket? = quote.brackets.firstOrNull { it.id == id }
}

@Serializable
data class CreditsConfig(
    /** v0.7: prices are shown in US dollars; "about $X" for credits is credits x this. */
    val creditValueUsd: Double = 10.0,
    /** Kept by the server for older builds. */
    val creditValueInr: Int = 850,
    val packs: List<CreditPack> = emptyList(),
    /** v0.11.1 (R-099): what a new brief costs after the free ones (a twelfth of a credit); 0 on an older server (as iOS). */
    val briefPriceCredits: Double = 0.0,
    /** v0.11.1: new briefs per account that cost nothing. */
    val freeBriefs: Int = 1,
)

@Serializable
data class CreditPack(
    val productId: String,
    val credits: Double,
    val bonusCredits: Double = 0.0,
    /** Shown only until the store's real price loads. */
    val priceHintUsd: Double? = null,
    val priceHintInr: Int? = null,
    val label: String = "",
) {
    val total: Double get() = credits + bonusCredits
}

@Serializable
data class QuoteConfig(
    val brackets: List<Bracket> = emptyList(),
    val validDays: Int = 90,
    val timelineNote: String = "Most projects are delivered well ahead of this estimate.",
)

@Serializable
data class Bracket(
    val id: String,
    val name: String,
    val minCredits: Int = 0,
    val maxCredits: Int? = null,
    val blurb: String = "",
    /** v0.7.1: how long this size takes, in weeks; null for Enterprise (scaled by credits on the server). */
    val timeline: QuoteTimeline? = null,
)

@Serializable
data class PaymentConfig(val unpaidCancelDays: Int = 90)

@Serializable
data class ReportConfig(
    val categories: List<ReportCategory> = listOf(
        ReportCategory("not_as_brief", "It doesn't match the brief"),
        ReportCategory("bug", "Something doesn't work (a bug)"),
        ReportCategory("other", "Something else"),
    ),
    val descriptionMaxChars: Int = 1000,
    /** v0.13.0 (R-112): free fixes end this many days after the first store is published. */
    val freeFixDays: Int = 7,
    /** v0.13.0: the rule in one line, shown by the report and change buttons while the window is open. */
    val freeFixesLine: String = FREE_FIXES_LINE,
) {
    companion object {
        /** Her words (2026-11-11); the built-in fallback when /config doesn't send the line. */
        const val FREE_FIXES_LINE =
            "Bugs and anything that doesn't match the brief are fixed free until 7 days after launch. New features are quoted as changes."
    }
}

@Serializable
data class ReportCategory(val id: String, val label: String)

@Serializable
data class BriefLimits(val perDay: Int = 0, val perMonth: Int = 0)

@Serializable
data class AcceptanceConfig(val autoAcceptDays: Int = 14)

@Serializable
data class RetentionConfig(
    val deletionGraceDays: Int = 7,
    val exportLinkHours: Int = 24,
    val jobHours: Int = 24,
    /** AI job results are readable this long after they finish (cut short once read). */
    val aiResultMinutes: Int = 15,
    /** Statuses at which the server deletes the brief and everything written about it (v0.15.0, R-119: not approval). */
    val briefDeletedOn: List<String> = listOf("cancelled", "rejected"),
)

@Serializable
data class LimitsConfig(
    val audioMaxBytes: Long = 52_428_800,
    val audioMaxSeconds: Int = 1200,
    val inputsPerProject: Int = 10,
    val textInputMaxChars: Int = 20_000,
    /** Whole brief, all sections, as sent to the AI and quote calls. */
    val documentMaxChars: Int = 60_000,
    val acceptedMimeTypes: List<String> = listOf("audio/m4a", "audio/mp4"),
    val fileMaxBytes: Long = 10_485_760,
    val fileMaxPages: Int = 60,
    /** MIME types the app reads on the device (v0.6: files never leave the phone). */
    val acceptedFileTypes: List<String> = FileTypes.DEFAULT,
)

/** Documents the app reads on the device: MIME types and the extensions that map to them. */
object FileTypes {
    const val PDF = "application/pdf"
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val TXT = "text/plain"
    const val MD = "text/markdown"
    const val RTF = "application/rtf"
    val DEFAULT = listOf(PDF, DOCX, TXT, MD, RTF)

    private val byExtension = mapOf(
        "pdf" to PDF, "docx" to DOCX, "txt" to TXT, "text" to TXT,
        "md" to MD, "markdown" to MD, "rtf" to RTF,
    )

    /** The MIME type to declare: the provider's if accepted, else from the extension. Null if unsupported. */
    fun resolve(fileName: String, providerType: String?, accepted: List<String>): String? {
        val fromProvider = providerType?.substringBefore(';')?.trim()?.lowercase()?.let {
            if (it == "text/rtf") RTF else if (it == "text/x-markdown") MD else it
        }
        if (fromProvider != null && fromProvider in accepted) return fromProvider
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return byExtension[ext]?.takeIf { it in accepted }
    }
}

@Serializable
data class AiConfig(val providers: List<String> = emptyList())

/** v0.9: the line(s) the consent screen shows next to the analytics switch, and how long Analytics keeps data. */
@Serializable
data class AnalyticsConfig(val providers: List<String> = emptyList(), val retentionMonths: Int? = null)

@Serializable
data class DeliveryConfig(
    val awsTrustedAccountId: String = "",
    val awsRoleName: String = "RavigaDeployRole",
    val awsRegion: String = "ap-south-1",
    /** v0.12.0 (R-109): Raviga's publishing user, one address for both stores; the client invites it. */
    val publishingEmail: String = "",
)

/** v0.12.0 (R-109): what the client gives Raviga's publishing user in each store, and where to sign up. */
@Serializable
data class HandoverConfig(
    val appleRole: String = "",
    val playPermissions: List<String> = emptyList(),
    val guides: HandoverGuides = HandoverGuides(),
)

@Serializable
data class HandoverGuides(val appleEnroll: String = "", val playSignup: String = "")

// Contract v0.9.2 (R-055, founder, 2026-10-29): no person's name and no address in the app. The server still sends
// companyAddress, grievance.officerName and grievance.address for older builds; they are not decoded, so nothing can
// show them.
@Serializable
data class LegalConfig(
    val companyName: String = "Raviga Apps Private Limited",
    val termsUrl: String = "",
    val termsVersion: String = "",
    val privacyUrl: String = "",
    val privacyVersion: String = "",
    val grievance: Grievance = Grievance(),
    val supportEmail: String = "",
    /** v0.9.3: the "Your rights" page (withdrawing consent, download and delete, grievance, the Data Protection Board). */
    val rightsUrl: String = "",
)

@Serializable
data class Grievance(
    val email: String = "",
    val responseDays: Int = 30,
)

// ---------- Projects (sections 5, 7) ----------

/**
 * Contract v0.10.0 (R-103): submit is free; the policy check or the team accepts (approved, unpaid) or declines
 * (rejected); the client pays (building); the team delivers; the client approves (accepted) or reports a problem
 * (revision_requested) and the team delivers again. No messaging, no resubmit.
 */
object ProjectStatus {
    const val DRAFT = "draft"
    const val SUBMITTED = "submitted"
    const val APPROVED = "approved"
    const val BUILDING = "building"
    const val DELIVERED = "delivered"
    const val REVISION_REQUESTED = "revision_requested"
    const val ACCEPTED = "accepted"
    /** v0.12.0 (R-109): published in the client's stores (and its backend running in their AWS). */
    const val LIVE = "live"
    const val REJECTED = "rejected"
    const val CANCELLED = "cancelled"

    /** Only a draft (on the phone) can change; a submitted brief is v1 for good. */
    fun isEditable(status: String) = status == DRAFT
    /** Nothing more happens without the client (v0.12.0: approval starts Make it live, so `accepted` still moves). */
    fun isTerminal(status: String) = status == LIVE || status == REJECTED || status == CANCELLED
    /** Free until paid: submitted, or accepted and not yet paid. */
    fun canCancel(status: String) = status == SUBMITTED || status == APPROVED
}

object SectionIds {
    // v0.15.0 (R-118, her decision 2026-11-12): the brief is three plain sections that decide nothing technical.
    const val SUMMARY = "summary"
    const val TARGET_USERS = "target_users"
    /** Everything that is built, one list, including what the owner and staff can do (later wishes are paid changes). */
    const val FEATURES = "features"

    /** The brief's three sections, in order (v0.15.0). */
    val ordered = listOf(SUMMARY, TARGET_USERS, FEATURES)

    val headings = mapOf(
        SUMMARY to "Summary",
        TARGET_USERS to "Who it's for",
        FEATURES to "Features",
    )

    /** The server's hints (shown when a section is empty). */
    val hints = mapOf(
        SUMMARY to "What is being built, for whom, and why, in a few sentences.",
        TARGET_USERS to "The people who use it: customers, staff, the owner.",
        FEATURES to "Everything that is built, in plain words.",
    )

    /**
     * Sections a draft or project from an older build may still carry: dropped quietly when they load (their text isn't
     * moved, as the server does). Open questions went in v0.12.1 (R-111), six in v0.14.0 (R-115), Screens and What you
     * get in v0.15.0 (R-118).
     */
    const val RETIRED_OPEN_QUESTIONS = "open_questions"
    const val RETIRED_SCREENS = "screens"
    const val RETIRED_DELIVERABLES = "deliverables"
    val RETIRED = setOf(
        RETIRED_OPEN_QUESTIONS, "platforms", "features_nice", "integrations", "data_auth", "non_functional", "out_of_scope",
        RETIRED_SCREENS, RETIRED_DELIVERABLES,
    )

    /** v0.14.0: an older draft's must-have list becomes Features when it has none. */
    const val RENAMED_FEATURES_MUST = "features_must"
}

@Serializable
data class Project(
    val id: String,
    val ref: String = "",
    val title: String = "",
    val status: String = ProjectStatus.DRAFT,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** When the project entered its current status (contract v0.8.1); `updatedAt` also moves on other changes. */
    val statusChangedAt: String? = null,
    /** The brief, from submit on (contract v0.6; v0.15.0: gone only when the project is cancelled or declined). */
    val document: ProjectDocument? = null,
    /** v0.10.0: the fixed line for the status, filled in by the server (credits, dates). Show it as it comes. */
    val statusLine: String = "",
    /** What the project keeps of its quote: price, timeline and expiresAt (paid at /pay; re-quoted when expired). */
    val quote: Quote? = null,
    val submission: Submission? = null,
    /** v0.12.0 (R-109): the store apps this project builds ("ios", "android"), from the quote. */
    val apps: List<String> = emptyList(),
    /** v0.12.0: the project needs a server (an admin panel counts); only then does Make it live ask for AWS. */
    val hasBackend: Boolean = false,
    /**
     * v0.13.0 (R-112): when free fixes end, 7 days after the first store is published; null before launch (fixes are
     * free throughout the demo and handover). It never moves.
     */
    val freeFixesUntil: String? = null,
    /** v0.10.0: null until paid. */
    val payment: Payment? = null,
    /** When the project was accepted or declined. */
    val decidedAt: String? = null,
    val timeline: Timeline? = null,
    val delivery: Delivery? = null,
    /** v0.10.0: the client's delivery reports, oldest first. */
    val reports: List<Report> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val screening: ProjectScreening? = null,
    /** Set once the server deleted the brief and title (accepted, cancelled, rejected). */
    val contentDeletedAt: String? = null,
) {
    val isEditable: Boolean get() = ProjectStatus.isEditable(status)

    /** Screening refused the project: the brief is frozen and only deletion is offered. */
    val isRejected: Boolean get() = screening?.status == "rejected"

}

@Serializable
data class ProjectSummary(
    val id: String,
    val ref: String = "",
    val title: String = "",
    val status: String = ProjectStatus.DRAFT,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** When the project entered its current status (contract v0.8.1). */
    val statusChangedAt: String? = null,
    /** v0.10.0: the same fixed line as the project's. */
    val statusLine: String = "",
    val credits: Int? = null,
    val estimatedDeliveryDate: String? = null,
    /** Set once the server deleted the brief and title (accepted, cancelled, rejected). */
    val contentDeletedAt: String? = null,
)

@Serializable
data class ProjectsResponse(
    val projects: List<ProjectSummary> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class HistoryEntry(
    val status: String,
    val at: String? = null,
    val by: String = "system",
)

/** clear | review | rejected. Only rejected changes what the client sees. */
@Serializable
data class ProjectScreening(
    val status: String = "clear",
    val reason: String? = null,
    val checkedAt: String? = null,
)

/** The brief as the server holds it after submit. */
@Serializable
data class ProjectDocument(
    val version: Int = 0,
    val title: String = "",
    val sections: List<Section> = emptyList(),
    val documentHash: String? = null,
    val updatedAt: String? = null,
) {
    fun asDocument() = Document(version = version, title = title, sections = sections, createdAt = updatedAt, source = "submitted")
}

@Serializable
data class Document(
    /** Server documents carry a version; stateless AI results (v0.6) don't, and drafts number their own. */
    val version: Int = 0,
    val title: String = "",
    val sections: List<Section> = emptyList(),
    val createdAt: String? = null,
    val source: String = "ai",     // ai | append | regenerate | edit | restore
    val changeSummary: String? = null,
    val generatedFrom: GeneratedFrom? = null,
) {
    fun section(id: String): Section? = sections.firstOrNull { it.id == id }

    /** What the AI and quote calls take: title and section bodies only. */
    fun body() = DocumentBody(title = title, sections = sections.map { SectionBody(it.id, it.body) })

    /**
     * v0.12.1, v0.14.0 and v0.15.0: an older draft in today's sections, as the server reads it: the retired sections dropped
     * (their text isn't moved), and "Must-have features" read as Features when there's no Features yet.
     */
    fun withoutRetired(): Document {
        val hasFeatures = sections.any { it.id == SectionIds.FEATURES }
        if (sections.none { it.id in SectionIds.RETIRED || it.id == SectionIds.RENAMED_FEATURES_MUST }) return this
        val mapped = sections.mapNotNull { s ->
            val id = when {
                s.id in SectionIds.RETIRED -> return@mapNotNull null
                s.id == SectionIds.RENAMED_FEATURES_MUST -> if (hasFeatures) return@mapNotNull null else SectionIds.FEATURES
                else -> s.id
            }
            // Today's headings and hints ("Who it's for"), as the server sends them now.
            SectionIds.headings[id]?.let { heading -> s.copy(id = id, heading = heading, hint = SectionIds.hints[id]) } ?: s
        }
        return copy(sections = mapped.sortedBy { SectionIds.ordered.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } })
    }
}

@Serializable
data class GeneratedFrom(val inputIds: List<String> = emptyList())

@Serializable
data class Section(
    val id: String,
    val heading: String,
    val body: String = "",
    val hint: String? = null,
)

@Serializable
data class Quote(
    val id: String,
    /** Local brief version it priced (set by the app; the server tracks [documentHash]). */
    val documentVersion: Int = 0,
    val status: String = "current", // current | stale | expired | used
    val credits: Int,
    /** v0.7: credits x creditValueUsd; the price to show. */
    val usd: Double? = null,
    val inr: Int? = null,
    val bracketId: String? = null,
    val estimatedWorkingDays: Int = 0,
    val timeline: QuoteTimeline? = null,
    val complexity: Complexity? = null,
    val breakdown: List<BreakdownItem> = emptyList(),
    val assumptions: List<String> = emptyList(),
    val createdAt: String? = null,
    val expiresAt: String? = null,
    /** Display only; the app never computes it. */
    val documentHash: String? = null,
)

@Serializable
data class QuoteTimeline(val minWeeks: Int = 0, val maxWeeks: Int = 0)

@Serializable
data class Complexity(val score: Int = 0, val drivers: List<String> = emptyList())

@Serializable
data class BreakdownItem(val area: String, val credits: Int)

@Serializable
data class Payment(val credits: Int = 0, val paidAt: String? = null)

/** v0.10.0: "Something's wrong" with a delivery. [description] is null once the content is deleted. */
@Serializable
data class Report(
    val id: String,
    val createdAt: String? = null,
    val category: String = "other",
    val description: String? = null,
    val resolvedAt: String? = null,
)

@Serializable
data class ReportRequest(val category: String, val description: String)

/** Empty while the project's quote holds; after it expired, the token of a new quote for the same brief. */
@Serializable
data class PayRequest(
    val quoteToken: String? = null,
    /** v0.12.0 (R-109): the test invites' addresses, saved to /me/store-emails first when given. */
    val appleIdEmail: String? = null,
    val googleEmail: String? = null,
)

/** v0.12.0 (R-109): `GET`/`PUT /me/store-emails`, and `me.storeEmails`. */
@Serializable
data class StoreEmails(val apple: String? = null, val google: String? = null)

@Serializable
data class Submission(
    val submittedAt: String? = null,
    val awsAccountId: String? = null,
    val creditsCharged: Int = 0,
)

@Serializable
data class Timeline(
    val estimatedWorkingDays: Int = 0,
    val estimatedDeliveryDate: String? = null,
    val milestones: List<Milestone> = emptyList(),
)

@Serializable
data class Milestone(
    val id: String,                // v0.12.0: submitted | approved | paid | build | demo | accepted | live
    val label: String,
    val at: String? = null,
    val state: String = "upcoming", // done | current | upcoming
)

/**
 * Contract v0.12.0 (R-109, her delivery model): no source code is delivered. The demo is the real app on the client's
 * phone (TestFlight and a Play internal test from Raviga's accounts); changes are paid requests; after approval
 * [handover] tracks Make it live: publishing into the client's own stores, and the backend into their AWS.
 */
@Serializable
data class Delivery(
    val deliveredAt: String? = null,
    val acceptBy: String? = null,
    val demo: Demo? = null,
    val changes: List<Change> = emptyList(),
    val handover: Handover? = null,
) {
    /** The change still open (pricing, quoted or paid); at most one at a time. */
    val openChange: Change? get() = changes.lastOrNull { it.status in Change.OPEN }
}

@Serializable
data class Demo(
    val testflight: DemoStore? = null,
    val play: DemoStore? = null,
    val invitedEmails: StoreEmails = StoreEmails(),
)

/** One store's test invite: `pending` until it's ready (Apple's first beta review takes about a day). */
@Serializable
data class DemoStore(val status: String = "pending", val url: String? = null) {
    val ready: Boolean get() = status == "ready" && !url.isNullOrBlank()
}

@Serializable
data class Handover(
    /** "apple" / "play" → pending | invited | in_review | published, one per app the project builds. */
    val stores: Map<String, String> = emptyMap(),
    /** The fixed line per store ("Waiting for your invite", "Publishing", "In Apple's review", "Published"). */
    val storeLines: Map<String, String> = emptyMap(),
    val storeUrls: Map<String, String?> = emptyMap(),
    /** none | pending | deployed */
    val backend: String = "none",
    val awsAccountId: String? = null,
    val consoleUrl: String? = null,
    val statusLine: String? = null,
) {
    companion object {
        const val APPLE = "apple"
        const val PLAY = "play"
    }
}

/** v0.12.0 (R-109): a change request: described by the client, briefed and priced by the AI (floor 1 credit), paid. */
@Serializable
data class Change(
    val id: String,
    val createdAt: String? = null,
    /** Null once the project's content is deleted. */
    val description: String? = null,
    /** pricing | quoted | paid | delivered | expired | failed */
    val status: String = PRICING,
    /** The fixed line for the status ("Change requested", "Change paid", …); show it as sent. */
    val statusLine: String = "",
    val brief: ChangeBrief? = null,
    val quote: ChangeQuote? = null,
    val paidAt: String? = null,
    val deliveredAt: String? = null,
    val jobId: String? = null,
) {
    companion object {
        const val PRICING = "pricing"
        const val QUOTED = "quoted"
        const val PAID = "paid"
        const val DELIVERED = "delivered"
        const val EXPIRED = "expired"
        const val FAILED = "failed"
        val OPEN = setOf(PRICING, QUOTED, PAID)
    }
}

@Serializable
data class ChangeBrief(val title: String = "", val items: List<String> = emptyList())

@Serializable
data class ChangeQuote(
    val credits: Double = 0.0,
    val usd: Double = 0.0,
    val estimatedWorkingDays: Int = 0,
    val expiresAt: String? = null,
)

/** `POST /projects/{id}/changes`. */
@Serializable
data class ChangeRequest(val description: String)

/** `202 { change, job }` from `POST /projects/{id}/changes`. */
@Serializable
data class ChangeStarted(val change: Change, val job: Job)

/** The `change` job's result: the same object as in `project.delivery.changes`. */
@Serializable
data class ChangeResult(val change: Change)

/** `POST /projects/{id}/handover/invited`: the client invited Raviga in that store ("apple" | "play"). */
@Serializable
data class HandoverInvitedRequest(val store: String)

// ---------- Stateless AI, quotes, submit (contract v0.6) ----------

@Serializable
data class SectionBody(val id: String, val body: String)

/** A brief as sent to the server: headings and hints are the server's, so only bodies go. */
@Serializable
data class DocumentBody(val title: String, val sections: List<SectionBody>)

@Serializable
data class AiInput(
    val kind: String,                 // voice | text | file
    val text: String,
    val languageDetected: String? = null,
    val fileName: String? = null,
)

@Serializable
data class AiDraftRequest(
    val inputs: List<AiInput>,
    val title: String? = null,
    val instruction: String? = null,
)

@Serializable
data class AiAppendRequest(val document: DocumentBody, val inputs: List<AiInput>)

@Serializable
data class AiRegenerateRequest(
    val document: DocumentBody,
    val sectionId: String,
    val instruction: String? = null,
    val inputs: List<AiInput>? = null,
)

@Serializable
data class AiDocument(
    val title: String = "",
    val sections: List<Section> = emptyList(),
    val changeSummary: String? = null,
)

/** Result of the draft, append and regenerate jobs. [notices] name redactions to repeat locally. */
@Serializable
data class AiDocumentResult(val document: AiDocument, val notices: List<String> = emptyList())

@Serializable
data class QuoteRequest(val document: DocumentBody)

@Serializable
data class QuoteResult(val quote: Quote, val quoteToken: String)

@Serializable
data class SubmitProjectRequest(
    val document: DocumentBody,
    val quoteToken: String,
    val awsAccountId: String? = null,
)

// ---------- Delivery targets (section 12) ----------

@Serializable
data class DeliveryTargets(
    val aws: AwsTarget? = null,
)

@Serializable
data class AwsTarget(
    val accountId: String,
    val externalId: String? = null,
    val roleName: String? = null,
    val roleArn: String? = null,
    val verified: Boolean = false,
    val verifiedAt: String? = null,
    val lastError: String? = null,
)

@Serializable
data class DeliveryTargetsRequest(
    val awsAccountId: String? = null,
)

@Serializable
data class AwsConnectInfo(
    val accountId: String,
    val externalId: String,
    val roleName: String,
    val trustedAccountId: String = "",
    val quickCreateUrl: String,
    val templateUrl: String? = null,
    val instructions: List<String> = emptyList(),
)

// ---------- Credits (section 13) ----------

@Serializable
data class CreditsResponse(
    val balance: Double = 0.0,
    /** v0.11.2 (R-099): new briefs this account can still write free. */
    val freeBriefsLeft: Int? = null,
    val ledger: List<LedgerEntry> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class LedgerEntry(
    val id: String,
    val at: String? = null,
    val type: String = "adjustment", // purchase | bonus | charge | brief | refund_project | refund_store | revision_charge | adjustment
    val credits: Double = 0.0,
    val balanceAfter: Double = 0.0,
    val ref: LedgerRef? = null,
    val note: String = "",
)

@Serializable
data class LedgerRef(
    val projectId: String? = null,
    val productId: String? = null,
    val storeTransactionId: String? = null,
)

@Serializable
data class SyncResponse(val balance: Double = 0.0, val reconciled: Boolean = false)

// ---------- Privacy (section 14) ----------

@Serializable
data class ExportResult(
    val downloadUrl: String,
    val expiresAt: String? = null,
    val sizeBytes: Long? = null,
)

@Serializable
data class DeleteMeRequest(val confirm: String = "DELETE")

@Serializable
data class DeleteMeResponse(val requestedAt: String? = null, val purgeAt: String? = null)

// ---------- Jobs & errors (sections 2, 5) ----------

@Serializable
data class JobEnvelope(val job: Job)

@Serializable
data class Job(
    val jobId: String,
    val type: String = "",
    val status: String = "queued",  // queued | running | done | failed
    val projectId: String? = null,
    val progress: Double? = null,
    val progressMessage: String? = null,
    val result: JsonElement? = null,
    val error: ApiErrorBody? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val isDone get() = status == "done"
    val isFailed get() = status == "failed"
    /** v0.6: the result was read or aged out and is gone; run the call again. */
    val isExpired get() = status == "expired"
}

@Serializable
data class ApiErrorEnvelope(val error: ApiErrorBody)

@Serializable
data class ApiErrorBody(
    val code: String = "internal",
    val message: String? = null,
    val details: JsonElement? = null,
)

@Serializable
data class HealthResponse(val ok: Boolean = false, val version: String = "", val stage: String = "")

@Serializable
class Empty
