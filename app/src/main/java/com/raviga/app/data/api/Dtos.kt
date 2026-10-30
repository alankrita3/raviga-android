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
    val deliveryTargets: DeliveryTargets = DeliveryTargets(),
    val deviceCount: Int = 1,
    val deletion: Deletion? = null,
)

@Serializable
data class CreditsBalance(val balance: Int = 0)

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
    val revisions: RevisionsConfig = RevisionsConfig(),
    val acceptance: AcceptanceConfig = AcceptanceConfig(),
    val retention: RetentionConfig = RetentionConfig(),
    val limits: LimitsConfig = LimitsConfig(),
    val ai: AiConfig = AiConfig(),
    val analytics: AnalyticsConfig = AnalyticsConfig(),
    val delivery: DeliveryConfig = DeliveryConfig(),
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
)

@Serializable
data class CreditPack(
    val productId: String,
    val credits: Int,
    val bonusCredits: Int = 0,
    /** Shown only until the store's real price loads. */
    val priceHintUsd: Double? = null,
    val priceHintInr: Int? = null,
    val label: String = "",
) {
    val total: Int get() = credits + bonusCredits
}

@Serializable
data class QuoteConfig(
    val brackets: List<Bracket> = emptyList(),
    val validDays: Int = 14,
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
data class RevisionsConfig(val includedRounds: Int = 2)

@Serializable
data class AcceptanceConfig(val autoAcceptDays: Int = 14)

@Serializable
data class RetentionConfig(
    val deletionGraceDays: Int = 7,
    val exportLinkHours: Int = 24,
    val jobHours: Int = 24,
    /** AI job results are readable this long after they finish (cut short once read). */
    val aiResultMinutes: Int = 15,
    /** Statuses at which the server deletes the brief and everything written about it. */
    val briefDeletedOn: List<String> = listOf("accepted", "cancelled", "rejected"),
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
)

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

object ProjectStatus {
    const val DRAFT = "draft"
    const val SUBMITTED = "submitted"
    const val CHANGES_REQUESTED = "changes_requested"
    const val APPROVED = "approved"
    const val DELIVERED = "delivered"
    const val REVISION_REQUESTED = "revision_requested"
    const val ACCEPTED = "accepted"
    const val REJECTED = "rejected"
    const val CANCELLED = "cancelled"

    /** Statuses in which inputs and the document can still change. */
    fun isEditable(status: String) = status == DRAFT || status == CHANGES_REQUESTED
    fun isTerminal(status: String) = status == ACCEPTED || status == REJECTED || status == CANCELLED
    fun canCancel(status: String) = status == SUBMITTED || status == CHANGES_REQUESTED
    fun canComment(status: String) = status != DRAFT && !isTerminal(status)
}

object SectionIds {
    const val SUMMARY = "summary"
    const val TARGET_USERS = "target_users"
    const val PLATFORMS = "platforms"
    const val FEATURES_MUST = "features_must"
    const val FEATURES_NICE = "features_nice"
    const val SCREENS = "screens"
    const val INTEGRATIONS = "integrations"
    const val DATA_AUTH = "data_auth"
    const val NON_FUNCTIONAL = "non_functional"
    const val DELIVERABLES = "deliverables"
    const val OUT_OF_SCOPE = "out_of_scope"
    const val OPEN_QUESTIONS = "open_questions"

    val ordered = listOf(
        SUMMARY, TARGET_USERS, PLATFORMS, FEATURES_MUST, FEATURES_NICE, SCREENS,
        INTEGRATIONS, DATA_AUTH, NON_FUNCTIONAL, DELIVERABLES, OUT_OF_SCOPE, OPEN_QUESTIONS,
    )

    val headings = mapOf(
        SUMMARY to "Summary",
        TARGET_USERS to "Target users",
        PLATFORMS to "Platforms",
        FEATURES_MUST to "Must-have features",
        FEATURES_NICE to "Nice-to-have features",
        SCREENS to "Screens",
        INTEGRATIONS to "Integrations",
        DATA_AUTH to "Data and authentication",
        NON_FUNCTIONAL to "Non-functional requirements",
        DELIVERABLES to "Deliverables",
        OUT_OF_SCOPE to "Out of scope",
        OPEN_QUESTIONS to "Open questions",
    )
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
    /** The brief, held only from submit until the project closes (contract v0.6). */
    val document: ProjectDocument? = null,
    val quote: Quote? = null,
    val lockedVersion: Int? = null,
    val submission: Submission? = null,
    val review: Review = Review(),
    val timeline: Timeline? = null,
    val delivery: Delivery? = null,
    val revisions: Revisions = Revisions(),
    val unreadComments: Int = 0,
    val history: List<HistoryEntry> = emptyList(),
    val screening: ProjectScreening? = null,
    /** Set once the server deleted the brief, title, comments and notes (accepted, cancelled, rejected). */
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
    val credits: Int? = null,
    val estimatedDeliveryDate: String? = null,
    val unreadComments: Int = 0,
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
    val note: String = "",
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
data class Submission(
    val submittedAt: String? = null,
    val githubUsername: String? = null,
    val awsAccountId: String? = null,
    val creditsCharged: Int = 0,
)

@Serializable
data class Review(
    val comments: List<Comment> = emptyList(),
    val decidedAt: String? = null,
)

@Serializable
data class Comment(
    val id: String,
    val createdAt: String? = null,
    val author: String = "team",   // team | client
    val body: String,
    val sectionId: String? = null,
)

@Serializable
data class CommentsResponse(val comments: List<Comment> = emptyList())

@Serializable
data class NewCommentRequest(val body: String, val sectionId: String? = null)

@Serializable
data class Timeline(
    val estimatedWorkingDays: Int = 0,
    val estimatedDeliveryDate: String? = null,
    val milestones: List<Milestone> = emptyList(),
)

@Serializable
data class Milestone(
    val id: String,                // submitted | review | approved | build | delivered | accepted
    val label: String,
    val at: String? = null,
    val state: String = "upcoming", // done | current | upcoming
)

@Serializable
data class Delivery(
    val repoUrl: String? = null,
    /** v0.7: the handover (KT) doc. Null only on projects delivered before v0.7; see [handover]. */
    val handoverUrl: String? = null,
    val deliveredAt: String? = null,
    val note: String? = null,
    val transfer: Transfer? = null,
    val aws: AwsDelivery? = null,
    val acceptBy: String? = null,
) {
    /** The handover doc, or where it usually is in the repository for projects delivered before v0.7. */
    val handover: String?
        get() = handoverUrl?.takeIf { it.isNotBlank() }
            ?: repoUrl?.takeIf { it.isNotBlank() }?.let { "${it.trimEnd('/')}/blob/HEAD/HANDOVER.md" }
}

@Serializable
data class Transfer(
    val status: String = "pending", // awaiting_target | pending | initiated | accepted | failed
    val initiatedAt: String? = null,
    val error: String? = null,
)

@Serializable
data class AwsDelivery(val status: String = "not_requested", val note: String = "")

@Serializable
data class Revisions(
    val used: Int = 0,
    val included: Int = 2,
    val requests: List<RevisionRequest> = emptyList(),
)

@Serializable
data class RevisionRequest(
    val id: String,
    val createdAt: String? = null,
    val message: String = "",
    val resolvedAt: String? = null,
)

@Serializable
data class RevisionRequestBody(val message: String)

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
    val githubUsername: String? = null,
    val awsAccountId: String? = null,
)

@Serializable
data class ResubmitRequest(val document: DocumentBody, val quoteToken: String)

// ---------- Delivery targets (section 12) ----------

@Serializable
data class DeliveryTargets(
    val githubUsername: String = "",
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
    val githubUsername: String? = null,
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
    val balance: Int = 0,
    val ledger: List<LedgerEntry> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class LedgerEntry(
    val id: String,
    val at: String? = null,
    val type: String = "adjustment", // purchase | bonus | charge | refund_project | refund_store | revision_charge | adjustment
    val credits: Int = 0,
    val balanceAfter: Int = 0,
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
data class SyncResponse(val balance: Int = 0, val reconciled: Boolean = false)

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
