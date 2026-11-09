package com.raviga.app.data.repo

import com.raviga.app.data.api.PayRequest
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectSummary
import com.raviga.app.data.api.RavigaApi
import com.raviga.app.data.api.ReportRequest
import com.raviga.app.data.api.SubmitProjectRequest
import com.raviga.app.data.api.apiCall
import com.raviga.app.data.local.CacheStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Submitted projects: the server's side of Raviga (contract v0.6). Drafts
 * never get here; see [DraftRepository]. Holds the latest known copy of each
 * project, mirrors it to disk for offline reads, and funnels every action
 * through the API.
 */
class ProjectRepository(
    private val api: RavigaApi,
    private val json: Json,
    private val cache: CacheStore,
) {
    private val _summaries = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val summaries: StateFlow<List<ProjectSummary>> = _summaries.asStateFlow()

    private val _loadedOnce = MutableStateFlow(false)
    val loadedOnce: StateFlow<Boolean> = _loadedOnce.asStateFlow()

    private val _projects = MutableStateFlow<Map<String, Project>>(emptyMap())

    private val listGate = RefreshGate<List<ProjectSummary>>()

    fun project(id: String): Flow<Project?> = _projects.map { it[id] }.distinctUntilChanged()

    /** Every project this phone holds in full (opened, or cached from before), by id. */
    val all: StateFlow<Map<String, Project>> = _projects.asStateFlow()
    fun cachedProject(id: String): Project? = _projects.value[id]

    suspend fun warmFromCache() {
        cache.read(CacheStore.PROJECTS, ListSerializer(ProjectSummary.serializer()))?.let {
            _summaries.value = it
            _loadedOnce.value = true
        }
    }

    suspend fun warmProject(id: String) {
        if (_projects.value[id] == null) {
            cache.read(CacheStore.project(id), Project.serializer())?.let { p -> _projects.update { it + (id to p) } }
        }
    }

    /** R-056: before Agree there is no account, so nothing to load; Home shows its empty state, not a blank page. */
    fun noAccountYet() {
        _loadedOnce.value = true
    }

    /** Moves on at every [reset]: a list fetched for the previous client is dropped when it lands (review #23). */
    @Volatile private var generation = 0

    /** Forgets everything held for the previous client (sign-out, recovery, deletion). */
    suspend fun reset() {
        generation++
        _summaries.value = emptyList()
        _projects.value = emptyMap()
        _loadedOnce.value = false
        listGate.reset()
        cache.clearProjects()
    }

    /** [force] false lets a refresh from a few seconds ago stand (see [RefreshGate]). */
    suspend fun refreshAll(force: Boolean = true): List<ProjectSummary> = listGate.run(force) { fetchAll() }

    private suspend fun fetchAll(): List<ProjectSummary> {
        val startedFor = generation
        val list = mutableListOf<ProjectSummary>()
        val seen = HashSet<String>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = apiCall(json) { api.projects(before = cursor) }
            // R-094 P2-9 (iOS review 2): a project already listed is never listed twice, and a repeated cursor ends it.
            page.projects.filterTo(list) { seen.add(it.id) }
            cursor = Paging.next(sent = cursor, answered = page.nextCursor)
        } while (cursor != null && ++pages < MAX_PAGES)
        // Review #23 (Android): the phone moved to another client while this ran; the list is the old client's.
        if (startedFor != generation) return _summaries.value
        _summaries.value = list
        _loadedOnce.value = true
        cache.write(CacheStore.PROJECTS, ListSerializer(ProjectSummary.serializer()), list)
        return list
    }

    suspend fun refresh(id: String): Project = store(apiCall(json) { api.project(id) })

    /** The first time any content reaches the server. Free since v0.10.0. [idempotencyKey] is reused on retry. */
    suspend fun submit(body: SubmitProjectRequest, idempotencyKey: String): Project =
        store(apiCall(json) { api.submitProject(idempotencyKey, body) })

    /**
     * v0.10.0 (R-103): pays an accepted project and starts the build. [quoteToken] only after the price expired (a new
     * quote for the same brief). [idempotencyKey] is reused on retry.
     */
    suspend fun pay(id: String, idempotencyKey: String, quoteToken: String? = null): Project =
        store(apiCall(json) { api.pay(id, idempotencyKey, PayRequest(quoteToken)) })

    /**
     * v0.12.0 (R-109): asks for a change; the AI writes a short change brief and prices it (a `change` job). Returns
     * the priced change; the project is refreshed so its `delivery.changes` has it too.
     */
    suspend fun requestChange(id: String, description: String, jobs: com.raviga.app.data.api.JobRunner, onProgress: (com.raviga.app.data.api.JobState) -> Unit = {}): com.raviga.app.data.api.Change {
        val started = apiCall(json) { api.requestChange(id, com.raviga.app.data.api.ChangeRequest(description.trim())) }
        runCatching { refresh(id) }
        val change = try {
            jobs.await(started.job, com.raviga.app.data.api.ChangeResult.serializer(), onProgress).change
        } finally {
            runCatching { refresh(id) }
        }
        return change
    }

    /** v0.12.0: pays a priced change; the project goes back to building. [idempotencyKey] is reused on retry. */
    suspend fun payChange(id: String, changeId: String, idempotencyKey: String): Project =
        store(apiCall(json) { api.payChange(id, changeId, idempotencyKey) })

    /** v0.12.0: "I've sent the invites" for one store ("apple" | "play"). */
    suspend fun handoverInvited(id: String, store: String): Project =
        store(apiCall(json) { api.handoverInvited(id, com.raviga.app.data.api.HandoverInvitedRequest(store)) })

    suspend fun cancel(id: String): Project = store(apiCall(json) { api.cancelProject(id) })

    suspend fun accept(id: String): Project = store(apiCall(json) { api.accept(id) })

    /** v0.10.0 (R-103): "Something's wrong" with a delivery: a fixed category and a short description. */
    suspend fun report(id: String, category: String, description: String): Project =
        store(apiCall(json) { api.report(id, ReportRequest(category, description.trim())) })

    private suspend fun store(project: Project): Project {
        _projects.update { it + (project.id to project) }
        cache.write(CacheStore.project(project.id), Project.serializer(), project)
        val summary = ProjectSummary(
            id = project.id,
            ref = project.ref,
            title = project.title,
            status = project.status,
            statusLine = project.statusLine,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt,
            statusChangedAt = project.statusChangedAt ?: project.history.lastOrNull()?.at,
            credits = project.quote?.credits,
            estimatedDeliveryDate = project.timeline?.estimatedDeliveryDate,
            contentDeletedAt = project.contentDeletedAt,
        )
        _summaries.update { list ->
            (listOf(summary) + list.filterNot { it.id == project.id }).sortedByDescending { it.updatedAt ?: "" }
        }
        cache.write(CacheStore.PROJECTS, ListSerializer(ProjectSummary.serializer()), _summaries.value)
        return project
    }

    private companion object {
        /** 50 per page; a client with more than 500 projects sees the newest 500. */
        const val MAX_PAGES = 10
    }
}
