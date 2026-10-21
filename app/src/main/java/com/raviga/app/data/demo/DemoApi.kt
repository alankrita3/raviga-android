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
        val resubmitted: Set<String> = emptySet(),
        val statusChangedAt: Map<String, String> = emptyMap(),
        val unread: Map<String, Int> = emptyMap(),
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

    private fun balanceOf(s: State) = s.ledger.lastOrNull()?.balanceAfter ?: 0

    private fun meOf(s: State) = Me(
        clientId = s.clientId,
        createdAt = s.createdAt,
        credits = CreditsBalance(balanceOf(s)),
        consent = s.consent,
        deliveryTargets = s.deliveryTargets,
        deviceCount = 1,
    )

    private fun requireProject(s: State, id: String): Project =
        s.projects[id] ?: throw ApiException(ApiException.NOT_FOUND, 404, "No such project.")

    private fun put(s: State, project: Project, touch: Boolean = true): State {
        val p = if (touch) project.copy(updatedAt = Time.nowIso()) else project
        return s.copy(projects = s.projects + (p.id to p))
    }

    private fun transition(s: State, project: Project, status: String, by: String, note: String = ""): Pair<State, Project> {
        val now = Time.nowIso()
        var p = project.copy(status = status, history = project.history + HistoryEntry(status, now, by, note))
        // v0.6: closing deletes the brief and everything written about it; dates and counts stay.
        if (status in config.retention.briefDeletedOn) {
            p = p.copy(
                document = null,
                title = "",
                review = p.review.copy(comments = emptyList()),
                revisions = p.revisions.copy(requests = emptyList()),
                history = p.history.map { it.copy(note = "") },
                delivery = p.delivery?.copy(note = ""),
                contentDeletedAt = now,
            )
        }
        val withMilestones = p.copy(timeline = p.timeline?.copy(milestones = milestones(status, p)))
        return put(s, withMilestones).copy(statusChangedAt = s.statusChangedAt + (p.id to now)) to withMilestones
    }

    private fun ledger(s: State, type: String, credits: Int, ref: LedgerRef? = null, note: String = ""): State {
        val entry = LedgerEntry(
            id = "le_" + UUID.randomUUID().toString().take(8),
            at = Time.nowIso(),
            type = type,
            credits = credits,
            balanceAfter = balanceOf(s) + credits,
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
            ProjectStatus.SUBMITTED -> {
                val firstPass = p.id !in s.resubmitted
                if (firstPass && elapsed > 20_000) {
                    val comment = Comment(
                        id = "cm_" + UUID.randomUUID().toString().take(6),
                        createdAt = Time.nowIso(),
                        author = "team",
                        body = "Thanks, this is clear and we can build it. One question before we approve: should payments be in the first release, or can it launch without them and add payments in a revision? Edit the brief if anything changes and resubmit.",
                    )
                    p = p.copy(review = p.review.copy(comments = listOf(comment) + p.review.comments, decidedAt = Time.nowIso()), unreadComments = p.unreadComments + 1)
                    val (n, t) = transition(next, p, ProjectStatus.CHANGES_REQUESTED, "team", "Question about payments")
                    next = n; p = t
                } else if (!firstPass && elapsed > 15_000) {
                    val days = p.quote?.estimatedWorkingDays ?: 10
                    val eta = Time.addWorkingDays(LocalDate.now(), days)
                    p = p.copy(
                        review = p.review.copy(decidedAt = Time.nowIso()),
                        timeline = Timeline(estimatedWorkingDays = days, estimatedDeliveryDate = Time.isoDate(eta), milestones = emptyList()),
                    )
                    val (n, t) = transition(next, p, ProjectStatus.APPROVED, "team", "Approved")
                    next = n; p = t
                }
            }
            ProjectStatus.APPROVED -> if (elapsed > 40_000) {
                val slug = p.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "project" }
                val now = Time.nowIso()
                val hasGithub = p.submission?.githubUsername?.isNotBlank() == true || s.deliveryTargets.githubUsername.isNotBlank()
                val hasAws = p.submission?.awsAccountId != null || s.deliveryTargets.aws != null
                p = p.copy(
                    delivery = Delivery(
                        repoUrl = "https://github.com/raviga-builds/$slug",
                        handoverUrl = "https://github.com/raviga-builds/$slug/blob/HEAD/HANDOVER.md",
                        deliveredAt = now,
                        note = "Repo transferred. " + if (hasAws) "Backend deployed to your AWS account: one API Gateway, three Lambda functions, one DynamoDB table and one S3 bucket. The README lists every resource." else "Backend deployment instructions are in the README.",
                        transfer = Transfer(status = if (hasGithub) "initiated" else "awaiting_target", initiatedAt = if (hasGithub) now else null),
                        aws = AwsDelivery(status = if (hasAws) "deployed" else "not_requested", note = if (hasAws) "Deployed with RavigaDeployRole" else ""),
                        acceptBy = Instant.now().plusSeconds(config.acceptance.autoAcceptDays * 86_400L).toString(),
                    ),
                )
                val (n, t) = transition(next, p, ProjectStatus.DELIVERED, "team", "Delivered")
                next = n; p = t
            }
            ProjectStatus.REVISION_REQUESTED -> if (elapsed > 25_000) {
                val last = p.revisions.requests.lastOrNull()?.message ?: "as requested"
                p = p.copy(
                    revisions = p.revisions.copy(requests = p.revisions.requests.map { r -> if (r.resolvedAt == null) r.copy(resolvedAt = Time.nowIso()) else r }),
                    delivery = p.delivery?.copy(deliveredAt = Time.nowIso(), note = "Revision delivered: $last. Pull the latest commit on main."),
                )
                val (n, t) = transition(next, p, ProjectStatus.DELIVERED, "team", "Revision delivered")
                next = n; p = t
            }
        }
        return next to p
    }

    private fun milestones(status: String, p: Project): List<Milestone> {
        val order = listOf("submitted" to "Submitted", "review" to "Reviewed", "approved" to "Approved", "build" to "Building", "delivered" to "Delivered", "accepted" to "Accepted")
        val reached = when (status) {
            ProjectStatus.SUBMITTED -> 2
            ProjectStatus.CHANGES_REQUESTED, ProjectStatus.REJECTED -> 2
            ProjectStatus.APPROVED -> 4
            ProjectStatus.DELIVERED, ProjectStatus.REVISION_REQUESTED -> 6
            ProjectStatus.ACCEPTED -> 7
            else -> 0
        }
        return order.mapIndexed { i, (id, label) ->
            val st = when {
                i + 1 < reached -> "done"
                i + 1 == reached -> "current"
                else -> "upcoming"
            }
            val at = when (id) {
                "submitted" -> p.submission?.submittedAt
                "review" -> p.review.decidedAt
                "approved" -> p.history.lastOrNull { it.status == ProjectStatus.APPROVED }?.at
                "build" -> p.history.lastOrNull { it.status == ProjectStatus.APPROVED }?.at
                "delivered" -> p.delivery?.deliveredAt
                "accepted" -> p.history.lastOrNull { it.status == ProjectStatus.ACCEPTED }?.at
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
                    credits = it.quote?.credits, estimatedDeliveryDate = it.timeline?.estimatedDeliveryDate,
                    unreadComments = it.unreadComments, contentDeletedAt = it.contentDeletedAt,
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
        if (!ProjectStatus.canCancel(p.status)) throw ApiException(ApiException.INVALID_STATE, 409, "This project can no longer be cancelled.")
        var next = s
        val refund = p.submission?.creditsCharged ?: 0
        if (refund > 0) next = ledger(next, "refund_project", refund, LedgerRef(projectId = id), "Cancelled ${p.title}")
        transition(next, p, ProjectStatus.CANCELLED, "client")
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
        if (body.githubUsername != null || body.awsAccountId != null) {
            next = applyTargets(next, DeliveryTargetsRequest(body.githubUsername, body.awsAccountId)).first
        }
        val q = requireToken(next, body.quoteToken, body.document)
        val quote = q.quote
        val balance = balanceOf(next)
        if (balance < quote.credits) throw ApiException(
            ApiException.INSUFFICIENT_CREDITS, 402, "You need ${quote.credits - balance} more credits.",
            details = buildJsonObject { put("required", quote.credits); put("balance", balance) },
        )
        val now = Time.nowIso()
        val id = "pr_" + UUID.randomUUID().toString().replace("-", "").take(10)
        next = ledger(next, "charge", -quote.credits, LedgerRef(projectId = id), body.document.title)
        val project = Project(
            id = id,
            ref = "RA-" + UUID.randomUUID().toString().replace("-", "").take(6).uppercase(),
            title = body.document.title,
            status = ProjectStatus.SUBMITTED,
            createdAt = now,
            updatedAt = now,
            document = held(body.document, 1),
            quote = quote.copy(status = "used"),
            lockedVersion = 1,
            submission = Submission(submittedAt = now, githubUsername = next.deliveryTargets.githubUsername.ifBlank { null }, awsAccountId = next.deliveryTargets.aws?.accountId, creditsCharged = quote.credits),
            timeline = Timeline(estimatedWorkingDays = quote.estimatedWorkingDays, estimatedDeliveryDate = null, milestones = emptyList()),
            revisions = Revisions(used = 0, included = config.revisions.includedRounds),
            history = emptyList(),
        )
        next = next.copy(quotes = next.quotes + (body.quoteToken to q.copy(used = true)), submits = next.submits + (idempotencyKey to id))
        transition(next, project, ProjectStatus.SUBMITTED, "client")
    }

    override suspend fun resubmit(id: String, idempotencyKey: String, body: ResubmitRequest): Project = mutate { s ->
        s.submits[idempotencyKey]?.let { return@mutate s to requireProject(s, it) }
        val p = requireProject(s, id)
        if (p.status != ProjectStatus.CHANGES_REQUESTED) throw ApiException(ApiException.INVALID_STATE, 409, "Only projects with comments can be resubmitted.", details = buildJsonObject { put("status", p.status) })
        requireConsent(s)
        val q = requireToken(s, body.quoteToken, body.document)
        val quote = q.quote
        val charged = p.submission?.creditsCharged ?: 0
        val diff = quote.credits - charged
        var next = s
        if (diff > 0) {
            val balance = balanceOf(s)
            if (balance < diff) throw ApiException(ApiException.INSUFFICIENT_CREDITS, 402, "You need ${diff - balance} more credits.", details = buildJsonObject { put("required", diff); put("balance", balance) })
            next = ledger(next, "charge", -diff, LedgerRef(projectId = id), "Resubmitted ${body.document.title}")
        } else if (diff < 0) {
            next = ledger(next, "refund_project", -diff, LedgerRef(projectId = id), "Resubmitted ${body.document.title}")
        }
        val now = Time.nowIso()
        val version = (p.document?.version ?: 0) + 1
        val project = p.copy(
            title = body.document.title,
            document = held(body.document, version),
            lockedVersion = version,
            quote = quote.copy(status = "used"),
            submission = p.submission?.copy(submittedAt = now, creditsCharged = quote.credits),
            review = p.review.copy(decidedAt = null),
            timeline = Timeline(estimatedWorkingDays = quote.estimatedWorkingDays, estimatedDeliveryDate = null, milestones = emptyList()),
        )
        next = next.copy(quotes = next.quotes + (body.quoteToken to q.copy(used = true)), submits = next.submits + (idempotencyKey to id))
        val (n, t) = transition(next, project, ProjectStatus.SUBMITTED, "client", "Resubmitted")
        n.copy(resubmitted = n.resubmitted + id) to t
    }

    override suspend fun comments(id: String): CommentsResponse = mutate { s ->
        val p = requireProject(s, id)
        val cleared = p.copy(unreadComments = 0)
        put(s, cleared, touch = false) to CommentsResponse(p.review.comments)
    }

    override suspend fun addComment(id: String, body: NewCommentRequest): Comment = mutate { s ->
        val p = requireProject(s, id)
        if (!ProjectStatus.canComment(p.status)) throw ApiException(ApiException.INVALID_STATE, 409, "Comments open after submission.")
        val comment = Comment(id = "cm_" + UUID.randomUUID().toString().take(6), createdAt = Time.nowIso(), author = "client", body = body.body.trim(), sectionId = body.sectionId)
        put(s, p.copy(review = p.review.copy(comments = listOf(comment) + p.review.comments))) to comment
    }

    // ----- delivery -----

    override suspend fun accept(id: String, body: Empty): Project = mutate { s ->
        val p = requireProject(s, id)
        if (p.status != ProjectStatus.DELIVERED) throw ApiException(ApiException.INVALID_STATE, 409, "Nothing to accept yet.")
        val project = p.copy(delivery = p.delivery?.copy(transfer = p.delivery.transfer?.copy(status = "accepted")))
        transition(s, project, ProjectStatus.ACCEPTED, "client")
    }

    override suspend fun requestRevision(id: String, body: RevisionRequestBody): Project = mutate { s ->
        val p = requireProject(s, id)
        if (p.status != ProjectStatus.DELIVERED) throw ApiException(ApiException.INVALID_STATE, 409, "Revisions can be requested after delivery.")
        if (p.revisions.used >= p.revisions.included) throw ApiException(
            ApiException.REVISION_LIMIT_REACHED, 409, "Your included revision rounds are used up.",
            details = buildJsonObject { put("used", p.revisions.used); put("included", p.revisions.included) },
        )
        val now = Time.nowIso()
        val request = RevisionRequest(id = "rv_" + UUID.randomUUID().toString().take(6), createdAt = now, message = body.message.trim())
        val comment = Comment(id = "cm_" + UUID.randomUUID().toString().take(6), createdAt = now, author = "client", body = body.message.trim())
        val project = p.copy(
            revisions = p.revisions.copy(used = p.revisions.used + 1, requests = p.revisions.requests + request),
            review = p.review.copy(comments = listOf(comment) + p.review.comments),
        )
        transition(s, project, ProjectStatus.REVISION_REQUESTED, "client")
    }

    // ----- delivery targets -----

    private fun applyTargets(s: State, body: DeliveryTargetsRequest): Pair<State, DeliveryTargets> {
        var targets = s.deliveryTargets
        body.githubUsername?.let { name ->
            val trimmed = name.trim()
            if (trimmed.isNotBlank() && !GITHUB.matches(trimmed)) throw ApiException(ApiException.GITHUB_USER_NOT_FOUND, 400, "No GitHub user with that name.")
            targets = targets.copy(githubUsername = trimmed)
        }
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
        if (body.containsKey("githubUsername")) {
            val v = body["githubUsername"]
            targets = if (v == null || v is kotlinx.serialization.json.JsonNull) targets.copy(githubUsername = "")
            else applyTargets(s.copy(deliveryTargets = targets), DeliveryTargetsRequest(githubUsername = (v as kotlinx.serialization.json.JsonPrimitive).content)).second
        }
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
        if (pack.bonusCredits > 0) next = ledger(next, "bonus", pack.bonusCredits, LedgerRef(productId = productId, storeTransactionId = tx), "Pack bonus")
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
        ledger(s, "adjustment", credits, null, "Demo credits") to Unit
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
        private val GITHUB = Regex("^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}$")

        fun demoConfig() = AppConfig(
            minAppVersion = mapOf("ios" to "1.0.0", "android" to "1.0.0"),
            credits = CreditsConfig(
                creditValueUsd = 10.0,
                creditValueInr = 850,
                packs = listOf(
                    CreditPack("credits_10", 10, 0, priceHintUsd = 99.99, priceHintInr = 8_499, label = "10 credits"),
                    CreditPack("credits_25", 25, 2, priceHintUsd = 249.99, priceHintInr = 21_249, label = "25 + 2 credits"),
                    CreditPack("credits_50", 50, 6, priceHintUsd = 499.99, priceHintInr = 42_499, label = "50 + 6 credits"),
                    CreditPack("credits_100", 100, 15, priceHintUsd = 999.99, priceHintInr = 84_999, label = "100 + 15 credits"),
                ),
            ),
            quote = QuoteConfig(
                brackets = listOf(
                    Bracket("micro", "Micro", 5, 11, "One job, one screen or a simple site", QuoteTimeline(1, 1)),
                    Bracket("starter", "Starter", 12, 22, "A focused app with a handful of screens", QuoteTimeline(1, 1)),
                    Bracket("standard", "Standard", 23, 59, "A full product with accounts, payments or integrations", QuoteTimeline(1, 2)),
                    Bracket("pro", "Pro", 60, 150, "Multiple platforms, complex data, admin tooling", QuoteTimeline(2, 4)),
                    Bracket("enterprise", "Enterprise", 151, null, "Quoted as a custom engagement", null),
                ),
                validDays = 14,
                timelineNote = "Most projects are delivered well ahead of this estimate.",
            ),
            revisions = RevisionsConfig(2),
            acceptance = AcceptanceConfig(14),
            retention = RetentionConfig(deletionGraceDays = 7, exportLinkHours = 24, jobHours = 24, aiResultMinutes = 15),
            limits = LimitsConfig(),
            ai = AiConfig(
                listOf(
                    "OpenAI (writes and prices briefs from text only; may keep it up to 30 days for abuse checks, never trains on it)",
                    "Anthropic (Claude, used by our team to build submitted projects; deleted within 30 days, never trains on it)",
                ),
            ),
            analytics = AnalyticsConfig(
                listOf("Google Analytics for Firebase (counts how the app is used, such as which steps you reach; never your project's content, never for ads)"),
                retentionMonths = 2,
            ),
            delivery = DeliveryConfig("521901166785", "RavigaDeployRole", "ap-south-1"),
            legal = LegalConfig(
                companyName = "Raviga Apps Private Limited",
                companyAddress = "Raviga Apps Private Limited, India",
                // The dev legal site (contract v0.8); demo mode opens the same pages.
                termsUrl = "https://d1mi85jr39d7la.cloudfront.net/terms-of-use",
                termsVersion = "2026-10-21",
                privacyUrl = "https://d1mi85jr39d7la.cloudfront.net/privacy-policy",
                privacyVersion = "2026-10-21",
                grievance = Grievance("Alankrita Sood", "membersupport@miraquill.com", "Raviga Apps Private Limited, India", 15),
                supportEmail = "support@miraquill.com",
            ),
        )
    }
}
