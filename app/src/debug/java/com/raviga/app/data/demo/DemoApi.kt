package com.raviga.app.data.demo

import com.raviga.app.data.api.*
import com.raviga.app.data.local.CacheStore
import com.raviga.app.util.Time
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.ceil

/**
 * A complete in-process Raviga backend (api-contract v0.3) for the time
 * before the real one is deployed (API_BASE_URL empty). It implements the same
 * interface, keeps its state on disk, and walks submitted projects through the
 * review lifecycle on a timer so every screen can be exercised. Nothing here
 * talks to the network.
 */
/** The demo backend's 6-digit code for the recovery email (R-079). */
private const val DEMO_OTP = "123456"

class DemoApi(private val cache: CacheStore, private val json: Json) : RavigaApi {

    @Serializable
    private data class State(
        val clientId: String,
        val accessToken: String,
        val recoveryKey: String,
        val createdAt: String,
        val consent: ConsentState = ConsentState(),
        val deliveryTargets: DeliveryTargets = DeliveryTargets(),
        val pushToken: String? = null,
        val projects: Map<String, Project> = emptyMap(),
        /** quoteToken → the quote and the hash of the exact brief it priced (single use). */
        val quotes: Map<String, DemoQuote> = emptyMap(),
        /** Idempotency-Key → project id, so a retried submit replays instead of charging twice. */
        val submits: Map<String, String> = emptyMap(),
        val ledger: List<LedgerEntry> = emptyList(),
        val statusChangedAt: Map<String, String> = emptyMap(),
        /** v0.11.1 (R-099): new briefs this account has written (the first config.credits.freeBriefs are free). */
        val briefsWritten: Int = 0,
        /** R-079: the confirmed recovery email, and one waiting for its code (always 123456 in demo). */
        val recoveryEmail: String? = null,
        val pendingEmail: String? = null,
        /** v0.12.0 (R-109): where the TestFlight and Play test invites go. */
        val storeEmails: StoreEmails = StoreEmails(),
    )

    @Serializable
    private data class DemoQuote(val quote: Quote, val documentHash: String, val used: Boolean = false)

    private class DemoJob(
        val id: String,
        val type: String,
        val projectId: String?,
        val steps: List<String>,
        val stepMillis: Long,
        val produce: suspend () -> JsonElement,
    ) {
        val startedAt = System.currentTimeMillis()
        var result: JsonElement? = null
        var error: ApiErrorBody? = null
    }

    private val lock = Mutex()
    private var state: State? = null
    private val jobs = mutableMapOf<String, DemoJob>()

    val config: AppConfig = demoConfig()

    // ----- state plumbing -----

    private suspend fun load(): State {
        state?.let { return it }
        val loaded = cache.read(STATE, State.serializer()) ?: freshState()
        state = loaded
        return loaded
    }

    private suspend fun save(next: State) {
        state = next
        cache.write(STATE, State.serializer(), next)
    }

    private fun freshState() = State(
        clientId = "cl_demo" + UUID.randomUUID().toString().replace("-", "").take(10),
        accessToken = "rat_demo_" + UUID.randomUUID(),
        recoveryKey = "rark_" + List(4) { UUID.randomUUID().toString().replace("-", "").take(4).uppercase() }.joinToString("-"),
        createdAt = Time.nowIso(),
    )

    private suspend fun <T> mutate(block: (State) -> Pair<State, T>): T = lock.withLock {
        val (next, value) = block(load())
        save(next)
        value
    }

    private suspend fun <T> readState(block: (State) -> T): T = lock.withLock { block(load()) }

    private fun balanceOf(s: State): Double = s.ledger.lastOrNull()?.balanceAfter ?: 0.0

    private fun meOf(s: State) = Me(
        clientId = s.clientId,
        createdAt = s.createdAt,
        credits = CreditsBalance(balanceOf(s), freeBriefsLeft = (config.credits.freeBriefs - s.briefsWritten).coerceAtLeast(0)),
        consent = s.consent,
        deliveryTargets = s.deliveryTargets,
        storeEmails = s.storeEmails,
        deviceCount = 1,
        recoveryEmail = s.recoveryEmail?.let { RecoveryEmail(it, verified = true) },
    )

    private fun requireProject(s: State, id: String): Project =
        s.projects[id] ?: throw ApiException(ApiException.NOT_FOUND, 404, "No such project.")

    private fun put(s: State, project: Project, touch: Boolean = true): State {
        val p = if (touch) project.copy(updatedAt = Time.nowIso()) else project
        return s.copy(projects = s.projects + (p.id to p))
    }

    private fun transition(s: State, project: Project, status: String, by: String): Pair<State, Project> {
        val now = Time.nowIso()
        var p = project.copy(status = status, statusChangedAt = now, history = project.history + HistoryEntry(status, now, by))
        // v0.6: closing deletes the brief and everything written about it; dates and counts stay.
        if (status in config.retention.briefDeletedOn) {
            p = p.copy(
                document = null,
                title = "",
                reports = p.reports.map { it.copy(description = null) },
                // v0.12.0: change descriptions and briefs go with the rest; ids, statuses, quotes and dates stay.
                delivery = p.delivery?.copy(changes = p.delivery.changes.map { it.copy(description = null, brief = null) }),
                contentDeletedAt = now,
            )
        }
        val withMilestones = p.copy(timeline = p.timeline?.copy(milestones = milestones(status, p)))
        val lined = withMilestones.copy(statusLine = statusLine(withMilestones))
        return put(s, lined).copy(statusChangedAt = s.statusChangedAt + (p.id to now)) to lined
    }

    /** Contract v0.10.0 section 5: the fixed line per status, credits and dates filled in. */
    private fun statusLine(p: Project): String = when (p.status) {
        ProjectStatus.SUBMITTED -> "We're checking your brief against what we build. This step is free."
        ProjectStatus.APPROVED -> {
            val expired = p.quote?.expiresAt?.let { Time.parse(it)?.isBefore(Instant.now()) } == true
            if (expired) "Accepted. This price has expired: get a new quote to start building."
            else "Accepted. Pay ${p.quote?.credits ?: 0} credits to start building."
        }
        ProjectStatus.REJECTED -> if ((p.payment?.credits ?: 0) > 0) "Not accepted. Your credits are refunded." else "Not accepted. This project is outside what we build. Nothing was charged."
        // v0.12.0 (R-109): a paid change rebuilds; the demo, approval and live lines.
        ProjectStatus.BUILDING -> if (p.delivery?.changes?.any { it.status == Change.PAID } == true) "Change paid"
            else "Building and testing. Estimated delivery by ${Time.shortDate(p.timeline?.estimatedDeliveryDate)}."
        ProjectStatus.DELIVERED -> "Demo ready: check your invites"
        ProjectStatus.REVISION_REQUESTED -> "We're fixing what you reported."
        ProjectStatus.ACCEPTED -> "Approved. Next: make it live in your own stores."
        ProjectStatus.LIVE -> "Live in your stores."
        ProjectStatus.CANCELLED -> "Cancelled."
        else -> ""
    }

    private fun ledger(s: State, type: String, credits: Double, ref: LedgerRef? = null, note: String = ""): State {
        val entry = LedgerEntry(
            id = "le_" + UUID.randomUUID().toString().take(8),
            at = Time.nowIso(),
            type = type,
            credits = credits,
            // Four decimals, as the server books them (v0.11.1).
            balanceAfter = java.math.BigDecimal.valueOf(balanceOf(s) + credits).setScale(4, java.math.RoundingMode.HALF_UP).toDouble(),
            ref = ref,
            note = note,
        )
        return s.copy(ledger = s.ledger + entry)
    }

    private fun requireConsent(s: State) {
        val c = s.consent
        val missing = buildList {
            if (c.terms == null) add("terms")
            if (c.privacy == null) add("privacy")
            if (!c.aiGranted) add("aiProcessing")
        }
        if (missing.isNotEmpty()) throw ApiException(
            ApiException.CONSENT_REQUIRED, 403, "Consent is required first.",
            details = buildJsonObject { put("missing", kotlinx.serialization.json.JsonArray(missing.map { kotlinx.serialization.json.JsonPrimitive(it) })) },
        )
    }

    /** Stand-in for the server's hash over the normalised brief. */
    private fun hashOf(doc: DocumentBody): String {
        val normal = doc.title.trim() + "\n" + doc.sections.sortedBy { it.id }.joinToString("\n") { it.id + ":" + it.body.trim().replace(Regex("\\s+"), " ") }
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(normal.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    /** A request brief with the server's headings and hints filled back in. */
    private fun fullDocument(body: DocumentBody): Document = Document(
        title = body.title,
        sections = SectionIds.ordered.map { id ->
            Section(id = id, heading = SectionIds.headings[id] ?: id, body = body.sections.firstOrNull { it.id == id }?.body.orEmpty())
        },
    )

    // ----- lifecycle simulation -----

    /** Moves a project along the review lifecycle if enough demo time has passed. */
    private fun advance(s: State, project: Project): Pair<State, Project> {
        val since = Time.parse(s.statusChangedAt[project.id] ?: project.updatedAt) ?: return s to project
        val elapsed = System.currentTimeMillis() - since.toEpochMilli()
        var next = s
        var p = project
        when (p.status) {
            // v0.10.0: the demo team accepts a reviewed brief after a few seconds; the client then pays.
            ProjectStatus.SUBMITTED -> if (elapsed > 8_000) {
                val (n, t) = transition(next, p.copy(decidedAt = Time.nowIso()), ProjectStatus.APPROVED, "team")
                next = n; p = t
            }
            // v0.12.0 (R-109): the demo team sends TestFlight and Play invites from Raviga's accounts (a paid change too).
            ProjectStatus.BUILDING, ProjectStatus.REVISION_REQUESTED -> if (elapsed > (if (p.status == ProjectStatus.BUILDING) 40_000 else 25_000)) {
                val now = Time.nowIso()
                p = p.copy(
                    reports = p.reports.map { r -> if (r.resolvedAt == null) r.copy(resolvedAt = now) else r },
                    delivery = demoDelivery(s, p, now),
                )
                val (n, t) = transition(next, p, ProjectStatus.DELIVERED, "team")
                next = n; p = t
            }
            // Make it live: an invited store is published after a while; a backend is deployed once AWS is verified.
            ProjectStatus.ACCEPTED -> {
                val h = p.delivery?.handover ?: return next to p
                val stores = h.stores.mapValues { (_, st) ->
                    when {
                        st == "invited" && elapsed > 8_000 -> "in_review"
                        st == "in_review" && elapsed > 16_000 -> "published"
                        else -> st
                    }
                }
                val backend = if (h.backend == "pending" && s.deliveryTargets.aws?.verified == true) "deployed" else h.backend
                val handover = h.copy(
                    stores = stores,
                    storeLines = stores.mapValues { (store, st) -> com.raviga.app.ui.status.DeliveryCopy.storeLine(store, st) },
                    storeUrls = stores.mapValues { (store, st) ->
                        if (st != "published") null
                        else if (store == "apple") "https://apps.apple.com/app/id0000000000" else "https://play.google.com/store/apps/details?id=com.example.demo"
                    },
                    backend = backend,
                    consoleUrl = if (backend == "deployed") "https://ap-south-1.console.aws.amazon.com/console/home?region=ap-south-1" else null,
                    statusLine = handoverLine(stores, backend),
                )
                if (handover != h) {
                    p = p.copy(delivery = p.delivery.copy(handover = handover))
                    if (stores.values.all { it == "published" } && backend != "pending") {
                        val (n, t) = transition(next, p, ProjectStatus.LIVE, "system")
                        next = n; p = t
                    } else {
                        next = put(next, p.copy(statusLine = statusLine(p))).copy(statusChangedAt = next.statusChangedAt + (p.id to Time.nowIso()))
                        p = next.projects.getValue(p.id)
                    }
                }
            }
        }
        return next to p
    }

    /** The demo (v0.12.0): an invite per app, to the store emails; a paid change is in it. */
    private fun demoDelivery(s: State, p: Project, now: String): Delivery {
        val apps = p.apps.ifEmpty { listOf("ios", "android") }
        return (p.delivery ?: Delivery()).copy(
            deliveredAt = now,
            acceptBy = Instant.now().plusSeconds(config.acceptance.autoAcceptDays * 86_400L).toString(),
            demo = Demo(
                testflight = DemoStore("ready", "https://testflight.apple.com/join/DEMO1234").takeIf { "ios" in apps },
                play = DemoStore("ready", "https://play.google.com/apps/internaltest/4700000000000000000").takeIf { "android" in apps },
                invitedEmails = s.storeEmails,
            ),
            changes = p.delivery?.changes.orEmpty().map {
                if (it.status == Change.PAID) it.copy(status = Change.DELIVERED, statusLine = "In your latest demo", deliveredAt = now) else it
            },
        )
    }

    /** The handover's line for where it stands (v0.12.0). */
    private fun handoverLine(stores: Map<String, String>, backend: String): String = when {
        stores.values.any { it == "pending" } -> "Next: create your store accounts and invite Raviga."
        backend == "pending" -> "Next: connect your AWS account."
        else -> "We're publishing your app."
    }

    /** v0.12.0: Submitted, Approved, Paid, Building and QA, Demo ready, Approved by you, Live. */
    private fun milestones(status: String, p: Project): List<Milestone> {
        val order = listOf(
            "submitted" to "Submitted", "approved" to "Approved", "paid" to "Paid",
            "build" to "Building and QA", "demo" to "Demo ready", "accepted" to "Approved by you", "live" to "Live",
        )
        val current = when (status) {
            ProjectStatus.SUBMITTED, ProjectStatus.REJECTED, ProjectStatus.CANCELLED -> 2
            ProjectStatus.APPROVED -> 3
            ProjectStatus.BUILDING, ProjectStatus.REVISION_REQUESTED -> 4
            ProjectStatus.DELIVERED -> 5
            ProjectStatus.ACCEPTED -> 7
            ProjectStatus.LIVE -> 8
            else -> 0
        }
        return order.mapIndexed { i, (id, label) ->
            val st = when {
                i + 1 < current -> "done"
                i + 1 == current -> "current"
                else -> "upcoming"
            }
            val at = when (id) {
                "submitted" -> p.submission?.submittedAt
                "approved" -> p.decidedAt
                "paid", "build" -> p.payment?.paidAt
                "demo" -> p.delivery?.deliveredAt
                "accepted" -> p.history.lastOrNull { it.status == ProjectStatus.ACCEPTED }?.at
                "live" -> p.history.lastOrNull { it.status == ProjectStatus.LIVE }?.at
                else -> null
            }
            Milestone(id = id, label = label, at = at.takeIf { st == "done" }, state = st)
        }
    }

    // ----- jobs -----

    private fun startJob(type: String, projectId: String?, steps: List<String>, stepMillis: Long, produce: suspend () -> JsonElement): JobEnvelope {
        val job = DemoJob("jb_" + UUID.randomUUID().toString().take(8), type, projectId, steps, stepMillis, produce)
        synchronized(jobs) { jobs[job.id] = job }
        return JobEnvelope(Job(jobId = job.id, type = type, status = "queued", projectId = projectId, progress = 0.0, progressMessage = steps.first(), createdAt = Time.nowIso()))
    }

    override suspend fun job(jobId: String): Job {
        val job = synchronized(jobs) { jobs[jobId] } ?: throw ApiException(ApiException.NOT_FOUND, 404, "No such job.")
        val elapsed = System.currentTimeMillis() - job.startedAt
        val total = job.steps.size * job.stepMillis
        if (elapsed < total) {
            val idx = (elapsed / job.stepMillis).toInt().coerceIn(0, job.steps.lastIndex)
            return Job(jobId, job.type, "running", job.projectId, progress = (elapsed.toDouble() / total).coerceIn(0.0, 0.95), progressMessage = job.steps[idx])
        }
        if (job.result == null && job.error == null) {
            try {
                job.result = job.produce()
            } catch (e: ApiException) {
                job.error = ApiErrorBody(code = e.code, message = e.message, details = e.details)
            }
        }
        return if (job.error != null) Job(jobId, job.type, "failed", job.projectId, progress = 1.0, error = job.error)
        else Job(jobId, job.type, "done", job.projectId, progress = 1.0, result = job.result)
    }

    // ----- health, config, identity -----

    override suspend fun health() = HealthResponse(ok = true, version = "demo", stage = "demo")

    override suspend fun config(): AppConfig = config

    override suspend fun registerDevice(body: RegisterDeviceRequest): RegisterDeviceResponse = readState {
        RegisterDeviceResponse(it.clientId, "dv_demo", it.accessToken, it.recoveryKey, isNewClient = it.projects.isEmpty() && it.ledger.isEmpty())
    }

    override suspend fun recoverDevice(body: RecoverDeviceRequest): RegisterDeviceResponse = readState {
        if (body.recoveryKey.trim().equals(it.recoveryKey, ignoreCase = true)) RegisterDeviceResponse(it.clientId, "dv_demo", it.accessToken, null, false)
        else throw ApiException(ApiException.OTP_INVALID, 400, "That recovery key does not match.")
    }

    override suspend fun issueRecoveryKey(body: Empty): RecoveryKeyResponse = mutate { s ->
        val fresh = freshState().recoveryKey
        s.copy(recoveryKey = fresh) to RecoveryKeyResponse(fresh)
    }

    override suspend fun createLinkCode(body: Empty): LinkCodeResponse =
        LinkCodeResponse(code = List(2) { UUID.randomUUID().toString().take(4).uppercase() }.joinToString("-"), expiresAt = Instant.now().plusSeconds(600).toString())

    override suspend fun me(): Me = readState { meOf(it) }

    // R-079 in demo: one phone, and the email code is always 123456.
    override suspend fun setRecoveryEmail(body: RecoveryEmailRequest): PendingResponse =
        mutate { it.copy(pendingEmail = body.email.trim()) to PendingResponse("pending") }

    override suspend fun confirmRecoveryEmail(body: OtpRequest): RecoveryEmailConfirmed = mutate { s ->
        val email = s.pendingEmail ?: throw ApiException(ApiException.INVALID_STATE, 409, "Ask for a code first.")
        if (body.otp != DEMO_OTP) throw ApiException(ApiException.OTP_INVALID, 400, "That code doesn't match.")
        s.copy(recoveryEmail = email, pendingEmail = null) to RecoveryEmailConfirmed(email, Time.nowIso())
    }

    override suspend fun removeRecoveryEmail() = mutate { it.copy(recoveryEmail = null, pendingEmail = null) to Unit }

    override suspend fun recoverByEmail(body: RecoverByEmailRequest): Empty = Empty()

    override suspend fun recoverByEmailConfirm(body: RecoverByEmailConfirmRequest): RegisterDeviceResponse = readState {
        if (it.recoveryEmail.equals(body.email.trim(), ignoreCase = true) && body.otp == DEMO_OTP) {
            RegisterDeviceResponse(it.clientId, "dv_demo", it.accessToken, null, false)
        } else {
            throw ApiException(ApiException.OTP_INVALID, 400, "That code doesn't match.")
        }
    }

    override suspend fun devices(): DevicesResponse = DevicesResponse(
        listOf(DeviceSummary("dv_demo", "android", DeviceInfo.deviceModel, DeviceInfo.appVersion, lastSeenAt = Time.nowIso(), isCurrent = true)),
    )

    override suspend fun revokeDevice(deviceId: String) = Unit

    override suspend fun putPushToken(body: PushTokenRequest) = mutate { it.copy(pushToken = body.fid ?: body.token) to Unit }

    override suspend fun deletePushToken() = mutate { it.copy(pushToken = null) to Unit }

    override suspend fun consent(): ConsentState = readState { it.consent }

    override suspend fun putConsent(body: ConsentRequest): ConsentState = mutate { s ->
        val now = Time.nowIso()
        var c = s.consent
        body.termsVersion?.let {
            if (it != config.legal.termsVersion) throw ApiException(ApiException.INVALID_ARGUMENT, 400, "termsVersion is not current.")
            c = c.copy(terms = ConsentVersion(it, now))
        }
        body.privacyVersion?.let {
            if (it != config.legal.privacyVersion) throw ApiException(ApiException.INVALID_ARGUMENT, 400, "privacyVersion is not current.")
            c = c.copy(privacy = ConsentVersion(it, now))
        }
        body.aiProcessing?.let { c = c.copy(aiProcessing = AiConsent(it, now)) }
        body.analytics?.let { c = c.copy(analytics = AiConsent(it, now)) }
        c = c.copy(current = c.terms?.version == config.legal.termsVersion && c.privacy?.version == config.legal.privacyVersion)
        s.copy(consent = c) to c
    }

    // ----- projects -----

    override suspend fun projects(before: String?, limit: Int): ProjectsResponse = mutate { s ->
        var next = s
        val advanced = s.projects.values.filter { it.status != ProjectStatus.DRAFT }.map { p -> val (n, a) = advance(next, p); next = n; a }
        next to ProjectsResponse(
            advanced.sortedByDescending { it.updatedAt ?: "" }.map {
                ProjectSummary(
                    id = it.id, ref = it.ref, title = it.title, status = it.status, createdAt = it.createdAt, updatedAt = it.updatedAt,
                    statusChangedAt = it.statusChangedAt ?: it.history.lastOrNull()?.at ?: it.updatedAt,
                    statusLine = it.statusLine,
                    credits = it.quote?.credits, estimatedDeliveryDate = it.timeline?.estimatedDeliveryDate,
                    contentDeletedAt = it.contentDeletedAt,
                )
            },
        )
    }

    override suspend fun project(id: String): Project = mutate { s ->
        val (next, p) = advance(s, requireProject(s, id))
        next to p
    }

    override suspend fun cancelProject(id: String, body: Empty): Project = mutate { s ->
        val p = requireProject(s, id)
        // v0.10.0: free until paid; after payment it's 409.
        if (!ProjectStatus.canCancel(p.status) || p.payment != null) throw ApiException(ApiException.INVALID_STATE, 409, "This project can no longer be cancelled.", details = buildJsonObject { put("status", p.status) })
        transition(s, p, ProjectStatus.CANCELLED, "client")
    }

    // ----- stateless AI (contract v0.6): text in, brief out, nothing kept -----

    private fun demoPolicyHit(text: String): Boolean =
        listOf("phishing", "malware", "stalkerware", "keylogger").any { it in text.lowercase() }

    /** Like the server, the message carries the appeal sentence; the apps show it as is. */
    private fun policyRefusal(message: String) = ApiException(
        ApiException.CONTENT_REJECTED, 422,
        "$message If you think this is a mistake, write to ${config.legal.supportEmail}.",
        buildJsonObject { put("kind", "policy") },
    )

    /** The demo's stand-in for screening inside the AI jobs. */
    private fun screenInputs(inputs: List<AiInput>) {
        inputs.forEachIndexed { i, input ->
            if (demoPolicyHit(input.text)) throw policyRefusal("This isn't something Raviga can build. It falls outside our Acceptable use policy.")
            if (input.text.trim().length < 12) throw ApiException(
                ApiException.CONTENT_REJECTED, 422,
                "That's too short for us to work with. Say a little more about what it should do.",
                buildJsonObject { put("kind", "quality"); put("check", "too_short"); put("inputIndex", i) },
            )
        }
    }

    /** Redacts secrets the way the server does and says which input they were in. */
    private fun redacted(inputs: List<AiInput>): Pair<List<String>, List<String>> {
        val notices = mutableListOf<String>()
        val texts = inputs.mapIndexed { i, input ->
            val found = com.raviga.app.data.screening.SensitiveScan.scan(input.text)
            if (found.isNotEmpty()) {
                notices += "We removed ${found.map { it.kind.phrase }.distinct().joinToString(" and ")} from input ${i + 1}."
                com.raviga.app.data.screening.SensitiveScan.redact(input.text, found)
            } else input.text
        }
        return texts to notices
    }

    private fun aiResult(title: String, sections: List<Section>, summary: String, notices: List<String>): JsonElement =
        json.encodeToJsonElement(AiDocumentResult.serializer(), AiDocumentResult(AiDocument(title, sections, summary), notices))

    override suspend fun aiDraft(body: AiDraftRequest): JobEnvelope {
        readState { requireConsent(it) }
        // v0.11.1 (R-099): the first config.credits.freeBriefs drafts are free, then a twelfth of a credit each,
        // booked before the job and never refunded; short of it, 402 and nothing counted.
        mutate { s ->
            var next = s.copy(briefsWritten = s.briefsWritten + 1)
            if (s.briefsWritten >= config.credits.freeBriefs) {
                val price = config.credits.briefPriceCredits
                val balance = balanceOf(s)
                if (balance < price) throw ApiException(
                    ApiException.INSUFFICIENT_CREDITS, 402, "You need a twelfth of a credit for a new brief. Buy credits to continue.",
                    details = buildJsonObject { put("required", price); put("balance", balance) },
                )
                next = ledger(next, "brief", -price, null, "New brief") // as dev: the server never sees a title
            }
            next to Unit
        }
        return startJob("draft", null, listOf("Reading your notes", "Finding the features", "Writing the brief"), 1_100) {
            screenInputs(body.inputs)
            val (texts, notices) = redacted(body.inputs)
            val draft = BriefWriter.write(texts)
            aiResult(body.title?.ifBlank { null } ?: draft.title, draft.sections, "Written from your notes", notices)
        }
    }

    override suspend fun aiAppend(body: AiAppendRequest): JobEnvelope {
        readState { requireConsent(it) }
        return startJob("append", null, listOf("Reading your notes", "Adding to the brief"), 1_100) {
            screenInputs(body.inputs)
            val (texts, notices) = redacted(body.inputs)
            val current = fullDocument(body.document)
            aiResult(current.title, BriefWriter.append(current, texts), "Added from your new notes", notices)
        }
    }

    override suspend fun aiRegenerate(body: AiRegenerateRequest): JobEnvelope {
        readState { requireConsent(it) }
        return startJob("regenerate", null, listOf("Rereading the section", "Rewriting"), 900) {
            val current = fullDocument(body.document)
            val section = current.section(body.sectionId) ?: throw ApiException(ApiException.NOT_FOUND, 404, "No such section.")
            if (demoPolicyHit(body.instruction.orEmpty())) throw policyRefusal("This isn't something Raviga can build. It falls outside our Acceptable use policy.")
            val context = body.inputs.orEmpty().map { it.text }.ifEmpty { current.sections.map { it.body } }
            val rewritten = BriefWriter.regenerate(section, context, body.instruction)
            aiResult(current.title, current.sections.map { if (it.id == body.sectionId) rewritten else it }, "Regenerated ${section.heading}", emptyList())
        }
    }

    override suspend fun createQuote(body: QuoteRequest): JobEnvelope {
        readState { requireConsent(it) }
        return startJob("quote", null, listOf("Sizing the work", "Checking the timeline"), 900) {
            val doc = fullDocument(body.document)
            // The document screen runs inside the quote job.
            if (demoPolicyHit(doc.title + " " + doc.sections.joinToString(" ") { it.body })) {
                throw policyRefusal("This brief describes software we can't build under our Acceptable use policy.")
            }
            // v0.12.0 (R-109): native iPhone and Android apps only.
            if (appsOf(doc.section(com.raviga.app.data.api.SectionIds.PLATFORMS)?.body.orEmpty()).isEmpty()) throw ApiException(
                ApiException.CONTENT_REJECTED, 422, "Raviga builds native iPhone and Android apps only.",
                details = buildJsonObject { put("kind", "scope") },
            )
            val parts = BriefWriter.quote(doc, config)
            val hash = hashOf(body.document)
            val quote = Quote(
                id = "qt_" + UUID.randomUUID().toString().take(8),
                status = "current",
                credits = parts.credits,
                usd = parts.credits * config.credits.creditValueUsd,
                inr = parts.credits * config.credits.creditValueInr,
                bracketId = parts.bracketId,
                estimatedWorkingDays = parts.workingDays,
                timeline = parts.timeline,
                complexity = Complexity(parts.score, parts.drivers),
                breakdown = parts.breakdown.map { BreakdownItem(it.first, it.second) },
                assumptions = parts.assumptions,
                createdAt = Time.nowIso(),
                expiresAt = Instant.now().plusSeconds(config.quote.validDays * 86_400L).toString(),
                documentHash = hash,
            )
            val token = "qtk_" + UUID.randomUUID().toString().replace("-", "")
            mutate { s -> s.copy(quotes = s.quotes + (token to DemoQuote(quote, hash))) to Unit }
            json.encodeToJsonElement(QuoteResult.serializer(), QuoteResult(quote, token))
        }
    }

    /** The token must be this brief's, unused and unexpired. */
    private fun requireToken(s: State, token: String, doc: DocumentBody): DemoQuote {
        val q = s.quotes[token]
        val expired = q?.quote?.expiresAt?.let { Time.parse(it)?.isBefore(Instant.now()) } == true
        if (q == null || q.used || expired || q.documentHash != hashOf(doc)) {
            throw ApiException(ApiException.QUOTE_STALE, 409, "The brief changed since this quote. Get a new quote.")
        }
        return q
    }

    private fun held(doc: DocumentBody, version: Int) = ProjectDocument(
        version = version,
        title = doc.title,
        sections = fullDocument(doc).sections,
        documentHash = hashOf(doc),
        updatedAt = Time.nowIso(),
    )

    override suspend fun submitProject(idempotencyKey: String, body: SubmitProjectRequest): Project = mutate { s ->
        s.submits[idempotencyKey]?.let { id -> return@mutate s to requireProject(s, id) }
        requireConsent(s)
        var next = s
        if (body.awsAccountId != null) {
            next = applyTargets(next, DeliveryTargetsRequest(body.awsAccountId)).first
        }
        val q = requireToken(next, body.quoteToken, body.document)
        val quote = q.quote
        // v0.10.0: submitting is free; the demo team reviews it (submitted), then accepts it (approved) and it's paid.
        val now = Time.nowIso()
        val id = "pr_" + UUID.randomUUID().toString().replace("-", "").take(10)
        val project = Project(
            id = id,
            ref = "RA-" + UUID.randomUUID().toString().replace("-", "").take(6).uppercase(),
            title = body.document.title,
            status = ProjectStatus.SUBMITTED,
            createdAt = now,
            updatedAt = now,
            document = held(body.document, 1),
            quote = quote.copy(status = "used"),
            submission = Submission(submittedAt = now, awsAccountId = next.deliveryTargets.aws?.accountId, creditsCharged = 0),
            apps = appsOf(body.document.sections.firstOrNull { it.id == com.raviga.app.data.api.SectionIds.PLATFORMS }?.body.orEmpty()),
            hasBackend = body.document.sections.any { it.id == com.raviga.app.data.api.SectionIds.DATA_AUTH && it.body.isNotBlank() } ||
                body.document.sections.any { Regex("(?i)admin|dashboard|account|login|sync").containsMatchIn(it.body) },
            timeline = Timeline(estimatedWorkingDays = quote.estimatedWorkingDays, estimatedDeliveryDate = null, milestones = emptyList()),
            history = emptyList(),
        )
        next = next.copy(quotes = next.quotes + (body.quoteToken to q.copy(used = true)), submits = next.submits + (idempotencyKey to id))
        transition(next, project, ProjectStatus.SUBMITTED, "client")
    }

    /** v0.10.0 section 11: pay an accepted project; after its price expired, with a new quote's token for the same brief. */
    override suspend fun pay(id: String, idempotencyKey: String, body: PayRequest): Project = mutate { s ->
        s.submits[idempotencyKey]?.let { return@mutate s to requireProject(s, it) }
        val p = requireProject(s, id)
        if (p.status != ProjectStatus.APPROVED || p.payment != null) throw ApiException(ApiException.INVALID_STATE, 409, "This project can't be paid now.", details = buildJsonObject { put("status", p.status) })
        requireConsent(s)
        // v0.12.0 (R-109): no AWS before paying; the invite emails may come with the payment.
        var next = if (body.appleIdEmail != null || body.googleEmail != null) {
            s.copy(storeEmails = StoreEmails(body.appleIdEmail ?: s.storeEmails.apple, body.googleEmail ?: s.storeEmails.google))
        } else {
            s
        }
        var quote = p.quote ?: throw ApiException(ApiException.INVALID_STATE, 409, "No price on this project.")
        val token = body.quoteToken
        if (token == null) {
            val expired = quote.expiresAt?.let { Time.parse(it)?.isBefore(Instant.now()) } == true
            if (expired) throw ApiException(
                ApiException.QUOTE_STALE, 409, "This price has expired. Get a new quote to start building.",
                details = buildJsonObject { put("expired", true) },
            )
        } else {
            val doc = p.document ?: throw ApiException(ApiException.INVALID_STATE, 409, "The brief is no longer held.")
            val q = requireToken(s, token, com.raviga.app.data.api.DocumentBody(doc.title, doc.sections.map { com.raviga.app.data.api.SectionBody(it.id, it.body) }))
            quote = q.quote.copy(status = "used")
            next = next.copy(quotes = next.quotes + (token to q.copy(used = true)))
        }
        val balance = balanceOf(next)
        if (balance < quote.credits) throw ApiException(
            ApiException.INSUFFICIENT_CREDITS, 402, "You need ${com.raviga.app.util.Money.credits(quote.credits - balance)} more.",
            details = buildJsonObject { put("required", quote.credits); put("balance", balance) },
        )
        val now = Time.nowIso()
        next = ledger(next, "charge", -quote.credits.toDouble(), LedgerRef(projectId = id), p.title)
        val eta = Time.addWorkingDays(LocalDate.now(), quote.estimatedWorkingDays.coerceAtLeast(1))
        val paid = p.copy(
            quote = quote,
            payment = Payment(quote.credits, now),
            submission = p.submission?.copy(creditsCharged = quote.credits),
            timeline = (p.timeline ?: Timeline()).copy(estimatedWorkingDays = quote.estimatedWorkingDays, estimatedDeliveryDate = Time.isoDate(eta)),
        )
        next = next.copy(submits = next.submits + (idempotencyKey to id))
        transition(next, paid, ProjectStatus.BUILDING, "client")
    }

    // ----- delivery -----

    override suspend fun accept(id: String, body: Empty): Project = mutate { s ->
        val p = requireProject(s, id)
        if (p.status != ProjectStatus.DELIVERED) throw ApiException(ApiException.INVALID_STATE, 409, "Nothing to accept yet.")
        // v0.12.0: approval starts Make it live: a store per app waiting for the client's invite, AWS if it has a backend.
        val stores = p.apps.ifEmpty { listOf("ios", "android") }.map { if (it == "ios") Handover.APPLE else Handover.PLAY }.associateWith { "pending" }
        val backend = if (p.hasBackend) "pending" else "none"
        val project = p.copy(
            delivery = (p.delivery ?: Delivery()).copy(
                handover = Handover(
                    stores = stores,
                    storeLines = stores.mapValues { (store, st) -> com.raviga.app.ui.status.DeliveryCopy.storeLine(store, st) },
                    backend = backend,
                    statusLine = handoverLine(stores, backend),
                ),
            ),
        )
        transition(s, project, ProjectStatus.ACCEPTED, "client")
    }

    /** v0.10.0 section 12: "Something's wrong" with a delivery: a fixed category and a short description. */
    override suspend fun report(id: String, body: ReportRequest): Project = mutate { s ->
        val p = requireProject(s, id)
        if (p.status != ProjectStatus.DELIVERED) throw ApiException(ApiException.INVALID_STATE, 409, "Reports are for a delivered project.", details = buildJsonObject { put("status", p.status) })
        val text = body.description.trim()
        if (body.category !in config.report.categories.map { it.id } || text.isEmpty() || text.length > config.report.descriptionMaxChars) {
            throw ApiException(ApiException.INVALID_ARGUMENT, 400, "Pick what's wrong and describe it in up to ${config.report.descriptionMaxChars} characters.")
        }
        if (demoPolicyHit(text)) throw policyRefusal("We can't send that report. Describe what isn't working in the delivered project.")
        val report = Report(id = "rp_" + UUID.randomUUID().toString().take(6), createdAt = Time.nowIso(), category = body.category, description = text)
        transition(s, p.copy(reports = p.reports + report), ProjectStatus.REVISION_REQUESTED, "client")
    }

    // ----- v0.12.0 (R-109): changes, Make it live, store emails -----

    override suspend fun requestChange(id: String, body: ChangeRequest): ChangeStarted {
        val text = body.description.trim()
        val change = mutate { s ->
            val p = requireProject(s, id)
            if (p.status !in setOf(ProjectStatus.DELIVERED, ProjectStatus.ACCEPTED, ProjectStatus.LIVE) || p.delivery?.openChange != null) {
                throw ApiException(ApiException.INVALID_STATE, 409, "A change can't be requested now.", details = buildJsonObject { put("status", p.status) })
            }
            if (text.isEmpty() || text.length > config.report.descriptionMaxChars) {
                throw ApiException(ApiException.INVALID_ARGUMENT, 400, "Describe the change in up to ${config.report.descriptionMaxChars} characters.")
            }
            if (demoPolicyHit(text)) throw policyRefusal("We can't take that change. Describe what's new or different in the app.")
            val c = Change(id = "ch_" + UUID.randomUUID().toString().take(8), createdAt = Time.nowIso(), description = text, statusLine = "Change requested")
            put(s, p.copy(delivery = (p.delivery ?: Delivery()).copy(changes = p.delivery?.changes.orEmpty() + c))) to c
        }
        val job = startJob("change", id, listOf("Reading your change", "Writing the change brief", "Pricing it"), 900) {
            val priced = mutate { s ->
                val p = requireProject(s, id)
                val words = text.split(Regex("\\s+")).take(5).joinToString(" ").trimEnd('.', ',')
                val quoted = change.copy(
                    status = Change.QUOTED,
                    statusLine = "Change requested",
                    brief = ChangeBrief(title = words.replaceFirstChar { it.uppercase() }, items = listOf(text)),
                    quote = ChangeQuote(credits = 2.0, usd = 2 * config.credits.creditValueUsd, estimatedWorkingDays = 3,
                        expiresAt = Instant.now().plusSeconds(config.quote.validDays * 86_400L).toString()),
                )
                val delivery = p.delivery ?: Delivery()
                put(s, p.copy(delivery = delivery.copy(changes = delivery.changes.map { if (it.id == change.id) quoted else it }))) to quoted
            }
            json.encodeToJsonElement(ChangeResult.serializer(), ChangeResult(priced))
        }
        return ChangeStarted(change, job.job)
    }

    override suspend fun payChange(id: String, changeId: String, idempotencyKey: String, body: Empty): Project = mutate { s ->
        s.submits[idempotencyKey]?.let { return@mutate s to requireProject(s, it) }
        val p = requireProject(s, id)
        val change = p.delivery?.changes?.firstOrNull { it.id == changeId }
            ?: throw ApiException(ApiException.NOT_FOUND, 404, "No such change.")
        if (change.status != Change.QUOTED) throw ApiException(ApiException.INVALID_STATE, 409, "This change can't be paid now.")
        val credits = change.quote?.credits ?: 0.0
        val balance = balanceOf(s)
        if (balance < credits) throw ApiException(
            ApiException.INSUFFICIENT_CREDITS, 402, "You need ${com.raviga.app.util.Money.credits(credits - balance)} more.",
            details = buildJsonObject { put("required", credits); put("balance", balance) },
        )
        var next = ledger(s, "charge", -credits, LedgerRef(projectId = id), "Change paid")
        val paid = change.copy(status = Change.PAID, statusLine = "Change paid", paidAt = Time.nowIso())
        val delivery = p.delivery ?: Delivery()
        val project = p.copy(delivery = delivery.copy(changes = delivery.changes.map { if (it.id == changeId) paid else it }))
        next = next.copy(submits = next.submits + (idempotencyKey to id))
        transition(next, project, ProjectStatus.BUILDING, "client")
    }

    override suspend fun handoverInvited(id: String, body: HandoverInvitedRequest): Project = mutate { s ->
        val p = requireProject(s, id)
        if (p.status != ProjectStatus.ACCEPTED) throw ApiException(ApiException.INVALID_STATE, 409, "Not making it live now.")
        val h = p.delivery?.handover ?: throw ApiException(ApiException.INVALID_STATE, 409, "Not making it live now.")
        if (body.store !in h.stores) throw ApiException(ApiException.INVALID_ARGUMENT, 400, "This project has no app for that store.")
        val stores = h.stores + (body.store to (h.stores.getValue(body.store).takeIf { it != "pending" } ?: "invited"))
        val handover = h.copy(
            stores = stores,
            storeLines = stores.mapValues { (store, st) -> com.raviga.app.ui.status.DeliveryCopy.storeLine(store, st) },
            statusLine = handoverLine(stores, h.backend),
        )
        val project = p.copy(delivery = p.delivery.copy(handover = handover), updatedAt = Time.nowIso())
        put(s, project).copy(statusChangedAt = s.statusChangedAt + (id to Time.nowIso())) to project
    }

    override suspend fun storeEmails(): StoreEmails = readState { it.storeEmails }

    override suspend fun putStoreEmails(body: StoreEmails): StoreEmails = mutate { s ->
        listOfNotNull(body.apple, body.google).forEach {
            if (it.length > 254 || !Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""").matches(it.trim())) {
                throw ApiException(ApiException.INVALID_ARGUMENT, 400, "That doesn't look like an email address.")
            }
        }
        val emails = StoreEmails(body.apple?.trim() ?: s.storeEmails.apple, body.google?.trim() ?: s.storeEmails.google)
        s.copy(storeEmails = emails) to emails
    }

    /** v0.12.0: the store apps a brief's Platforms section asks for; none means it's not a native app (declined). */
    private fun appsOf(platforms: String): List<String> {
        val lower = platforms.lowercase()
        if (lower.isBlank()) return listOf("ios", "android")
        val ios = listOf("iphone", "ios", "ipad", "app store").any { it in lower }
        val android = listOf("android", "play store", "google play").any { it in lower }
        val mobile = listOf("mobile app", "phone app", "both platforms", "mobile").any { it in lower }
        return when {
            ios || android -> listOfNotNull("ios".takeIf { ios }, "android".takeIf { android })
            mobile -> listOf("ios", "android")
            else -> emptyList()
        }
    }

    // ----- delivery targets -----

    private fun applyTargets(s: State, body: DeliveryTargetsRequest): Pair<State, DeliveryTargets> {
        var targets = s.deliveryTargets
        body.awsAccountId?.let { acct ->
            val digits = acct.filter { it.isDigit() }
            if (acct.isBlank()) targets = targets.copy(aws = null)
            else if (digits.length != 12) throw ApiException(ApiException.INVALID_ARGUMENT, 400, "awsAccountId must be 12 digits.")
            else if (targets.aws?.accountId != digits) targets = targets.copy(
                aws = AwsTarget(
                    accountId = digits,
                    externalId = targets.aws?.externalId ?: ("ra-ext-" + UUID.randomUUID().toString().take(12)),
                    roleName = config.delivery.awsRoleName,
                    roleArn = "arn:aws:iam::$digits:role/${config.delivery.awsRoleName}",
                    verified = false,
                ),
            )
        }
        return s.copy(deliveryTargets = targets) to targets
    }

    override suspend fun deliveryTargets(): DeliveryTargets = readState { it.deliveryTargets }

    override suspend fun putDeliveryTargets(body: DeliveryTargetsRequest): DeliveryTargets = mutate { s -> applyTargets(s, body) }

    override suspend fun putDeliveryTargetsRaw(body: kotlinx.serialization.json.JsonObject): DeliveryTargets = mutate { s ->
        var targets = s.deliveryTargets
        if (body.containsKey("awsAccountId")) {
            val v = body["awsAccountId"]
            targets = if (v == null || v is kotlinx.serialization.json.JsonNull) targets.copy(aws = null)
            else applyTargets(s.copy(deliveryTargets = targets), DeliveryTargetsRequest(awsAccountId = (v as kotlinx.serialization.json.JsonPrimitive).content)).second
        }
        s.copy(deliveryTargets = targets) to targets
    }

    override suspend fun awsConnect(): AwsConnectInfo = readState { s ->
        val aws = s.deliveryTargets.aws ?: throw ApiException(ApiException.NOT_FOUND, 404, "Enter your AWS account ID first.")
        val external = aws.externalId ?: "ra-ext"
        val region = config.delivery.awsRegion
        val template = "https://raviga-templates.s3.$region.amazonaws.com/raviga-deploy-role.yaml"
        AwsConnectInfo(
            accountId = aws.accountId,
            externalId = external,
            roleName = config.delivery.awsRoleName,
            trustedAccountId = config.delivery.awsTrustedAccountId,
            quickCreateUrl = "https://$region.console.aws.amazon.com/cloudformation/home?region=$region#/stacks/quickcreate" +
                "?templateURL=$template&stackName=${config.delivery.awsRoleName}&param_TrustedAccountId=${config.delivery.awsTrustedAccountId}&param_ExternalId=$external",
            templateUrl = template,
            instructions = listOf(
                "Sign in to the AWS account you want the project deployed to.",
                "Review the stack. It creates one IAM role named ${config.delivery.awsRoleName}.",
                "Tick the acknowledgement that the template creates IAM resources, then choose Create stack.",
                "Come back here and check the connection.",
            ),
        )
    }

    override suspend fun awsVerify(body: Empty): AwsTarget = mutate { s ->
        val aws = s.deliveryTargets.aws ?: throw ApiException(ApiException.NOT_FOUND, 404, "Enter your AWS account ID first.")
        val verified = aws.copy(verified = true, verifiedAt = Time.nowIso(), lastError = null)
        s.copy(deliveryTargets = s.deliveryTargets.copy(aws = verified)) to verified
    }

    // ----- credits -----

    override suspend fun credits(before: String?, limit: Int): CreditsResponse = readState { s ->
        CreditsResponse(balance = balanceOf(s), ledger = s.ledger.asReversed().take(limit))
    }

    override suspend fun syncCredits(idempotencyKey: String, body: Empty): SyncResponse = readState { SyncResponse(balanceOf(it), reconciled = true) }

    /** Demo-only: grants a pack as if the store purchase had cleared. */
    suspend fun demoPurchase(productId: String) = mutate { s ->
        val pack = config.credits.packs.firstOrNull { it.productId == productId } ?: throw ApiException(ApiException.NOT_FOUND, 404, "No such pack.")
        val tx = "DEMO." + UUID.randomUUID().toString().take(8)
        var next = ledger(s, "purchase", pack.credits, LedgerRef(productId = productId, storeTransactionId = tx), "Demo purchase")
        if (pack.bonusCredits > 0.0) next = ledger(next, "bonus", pack.bonusCredits, LedgerRef(productId = productId, storeTransactionId = tx), "Pack bonus")
        next to Unit
    }

    // ----- demo controls (Settings, demo mode only) -----

    /** Forces the next lifecycle step on the most recently updated non-terminal project. */
    suspend fun demoAdvanceLatest(): String? = mutate { s ->
        val latest = s.projects.values
            .filter { it.status != ProjectStatus.DRAFT && !ProjectStatus.isTerminal(it.status) }
            .maxByOrNull { it.updatedAt ?: "" } ?: return@mutate s to null
        val backdated = s.copy(statusChangedAt = s.statusChangedAt + (latest.id to Instant.now().minusSeconds(3600).toString()))
        val (next, p) = advance(backdated, latest)
        next to "${p.title.ifBlank { "Project" }} is now ${p.status.replace('_', ' ')}"
    }

    suspend fun demoAddCredits(credits: Int) = mutate { s ->
        ledger(s, "adjustment", credits.toDouble(), null, "Demo credits") to Unit
    }

    suspend fun demoReset() = lock.withLock {
        save(freshState())
        synchronized(jobs) { jobs.clear() }
    }

    // ----- privacy -----

    override suspend fun exportData(body: Empty): JobEnvelope =
        startJob("export", null, listOf("Collecting your projects", "Packaging"), 900) {
            json.encodeToJsonElement(ExportResult.serializer(), ExportResult(downloadUrl = DEMO_EXPORT_URL, expiresAt = Instant.now().plusSeconds(config.retention.exportLinkHours * 3600L).toString(), sizeBytes = 48_213))
        }

    override suspend fun deleteMe(body: DeleteMeRequest): DeleteMeResponse = lock.withLock {
        val s = load()
        val active = s.projects.values.filter { it.status != ProjectStatus.DRAFT && !ProjectStatus.isTerminal(it.status) }.map { it.id }
        if (active.isNotEmpty()) throw ApiException(
            ApiException.ACTIVE_PROJECTS, 409, "Finish or cancel your active projects first.",
            details = buildJsonObject { put("projectIds", kotlinx.serialization.json.JsonArray(active.map { kotlinx.serialization.json.JsonPrimitive(it) })) },
        )
        save(freshState())
        synchronized(jobs) { jobs.clear() }
        DeleteMeResponse(requestedAt = Time.nowIso(), purgeAt = Instant.now().plusSeconds(config.retention.deletionGraceDays * 86_400L).toString())
    }

    companion object {
        private const val STATE = "demo_state"
        /** Demo exports have no server part; [com.raviga.app.data.files.ExportBuilder] adds only the drafts. */
        const val DEMO_EXPORT_URL = "demo:export"

        fun demoConfig() = AppConfig(
            minAppVersion = mapOf("ios" to "1.0.0", "android" to "1.0.0"),
            credits = CreditsConfig(
                creditValueUsd = 10.0,
                creditValueInr = 850,
                packs = listOf(
                    // v0.11.1 (R-099): the quarter credit, three briefs, first.
                    CreditPack("credits_quarter", 0.25, 0.0, priceHintUsd = 2.49, priceHintInr = 209, label = "3 briefs"),
                    CreditPack("credits_10", 10.0, 0.0, priceHintUsd = 99.99, priceHintInr = 8_499, label = "10 credits"),
                    CreditPack("credits_25", 25.0, 2.0, priceHintUsd = 249.99, priceHintInr = 21_249, label = "25 + 2 credits"),
                    CreditPack("credits_50", 50.0, 6.0, priceHintUsd = 499.99, priceHintInr = 42_499, label = "50 + 6 credits"),
                    CreditPack("credits_100", 100.0, 15.0, priceHintUsd = 999.99, priceHintInr = 84_000, label = "100 + 15 credits"),
                ),
                briefPriceCredits = 0.0833,
                freeBriefs = 1,
            ),
            quote = QuoteConfig(
                brackets = listOf(
                    Bracket("micro", "Micro", 5, 11, "One job, one screen or a simple site", QuoteTimeline(1, 1)),
                    Bracket("starter", "Starter", 12, 22, "A focused app with a handful of screens", QuoteTimeline(1, 1)),
                    Bracket("standard", "Standard", 23, 49, "A full product with accounts, payments or integrations", QuoteTimeline(1, 2)),
                    Bracket("pro", "Pro", 50, 150, "Multiple platforms, complex data, admin tooling", QuoteTimeline(2, 4)),
                    Bracket("enterprise", "Enterprise", 151, null, "Quoted as a custom engagement", null),
                ),
                validDays = 90,
                timelineNote = "Most projects are delivered well ahead of this estimate.",
            ),
            payment = com.raviga.app.data.api.PaymentConfig(90),
            acceptance = AcceptanceConfig(14),
            report = com.raviga.app.data.api.ReportConfig(),
            briefLimits = com.raviga.app.data.api.BriefLimits(0, 0),
            retention = RetentionConfig(deletionGraceDays = 7, exportLinkHours = 24, jobHours = 24, aiResultMinutes = 15),
            limits = LimitsConfig(),
            ai = AiConfig(
                // One provider, the Claude API; no promise of human review of every project (her attorney, 2026-11-04).
                listOf("Anthropic (Claude writes, prices and checks briefs from text only, and builds submitted projects with automated quality checks and developer oversight when needed; deleted within 30 days, longer only if flagged for abuse; never trains on it)"),
            ),
            analytics = AnalyticsConfig(
                listOf("Google Analytics for Firebase (counts how the app is used, such as which steps you reach; never your project's content, never for ads)"),
                retentionMonths = 2,
            ),
            // v0.12.0 (R-109): Raviga's publishing user (one address for both stores) and the Make it live details.
            delivery = DeliveryConfig("521901166785", "RavigaDeployRole", "ap-south-1", publishingEmail = "publishing@ravigaapps.com"),
            handover = com.raviga.app.data.api.HandoverConfig(
                appleRole = "App Manager, with access to Certificates, Identifiers & Profiles",
                playPermissions = listOf(
                    "Release to production, exclude devices, and use Play App Signing", "Release apps to testing tracks",
                    "Manage testing tracks and edit tester lists", "Manage store presence", "Manage policy declarations",
                    "View app information (read-only)",
                ),
                guides = com.raviga.app.data.api.HandoverGuides(
                    appleEnroll = "https://developer.apple.com/programs/enroll/",
                    playSignup = "https://support.google.com/googleplay/android-developer/answer/6112435",
                ),
            ),
            legal = LegalConfig(
                companyName = "Raviga Apps Private Limited",
                // The legal site on Raviga's domain (dev /config since backend 5fe84e0); demo mode opens the same pages.
                termsUrl = "https://legal.ravigaapps.com/terms-of-use",
                termsVersion = "2026-11-04",
                privacyUrl = "https://legal.ravigaapps.com/privacy-policy",
                privacyVersion = "2026-11-04",
                // One public address (her yes, backend a934b4c, 2026-11-04).
                grievance = Grievance("support@ravigaapps.com", 15),
                supportEmail = "support@ravigaapps.com",
                rightsUrl = "https://legal.ravigaapps.com/your-rights",
            ),
        )
    }
}
