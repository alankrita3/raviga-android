package com.raviga.app.data.repo

import com.raviga.app.data.api.Comment
import com.raviga.app.data.api.RavigaApi
import com.raviga.app.data.api.NewCommentRequest
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectSummary
import com.raviga.app.data.api.ResubmitRequest
import com.raviga.app.data.api.RevisionRequestBody
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

    fun project(id: String): Flow<Project?> = _projects.map { it[id] }.distinctUntilChanged()
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

    /** Forgets everything held for the previous client (sign-out, recovery, deletion). */
    suspend fun reset() {
        _summaries.value = emptyList()
        _projects.value = emptyMap()
        _loadedOnce.value = false
        cache.clearProjects()
    }

    suspend fun refreshAll(): List<ProjectSummary> {
        val list = mutableListOf<ProjectSummary>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = apiCall(json) { api.projects(before = cursor) }
            list += page.projects
            cursor = page.nextCursor
        } while (cursor != null && ++pages < MAX_PAGES)
        _summaries.value = list
        _loadedOnce.value = true
        cache.write(CacheStore.PROJECTS, ListSerializer(ProjectSummary.serializer()), list)
        return list
    }

    suspend fun refresh(id: String): Project = store(apiCall(json) { api.project(id) })

    /** The first time any content reaches the server. [idempotencyKey] is reused on retry. */
    suspend fun submit(body: SubmitProjectRequest, idempotencyKey: String): Project =
        store(apiCall(json) { api.submitProject(idempotencyKey, body) })

    suspend fun resubmit(id: String, body: ResubmitRequest, idempotencyKey: String): Project =
        store(apiCall(json) { api.resubmit(id, idempotencyKey, body) })

    suspend fun cancel(id: String): Project = store(apiCall(json) { api.cancelProject(id) })

    /** Newest first; the backend marks team comments read. */
    suspend fun comments(id: String): List<Comment> {
        val list = apiCall(json) { api.comments(id) }.comments
        _projects.value[id]?.let { p -> store(p.copy(review = p.review.copy(comments = list), unreadComments = 0)) }
        return list
    }

    suspend fun addComment(id: String, body: String): Comment {
        val comment = apiCall(json) { api.addComment(id, NewCommentRequest(body.trim())) }
        _projects.value[id]?.let { p -> store(p.copy(review = p.review.copy(comments = listOf(comment) + p.review.comments))) }
        return comment
    }

    suspend fun accept(id: String): Project = store(apiCall(json) { api.accept(id) })

    suspend fun requestRevision(id: String, message: String): Project =
        store(apiCall(json) { api.requestRevision(id, RevisionRequestBody(message.trim())) })

    private suspend fun store(project: Project): Project {
        _projects.update { it + (project.id to project) }
        cache.write(CacheStore.project(project.id), Project.serializer(), project)
        val summary = ProjectSummary(
            id = project.id,
            ref = project.ref,
            title = project.title,
            status = project.status,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt,
            statusChangedAt = project.statusChangedAt ?: project.history.lastOrNull()?.at,
            credits = project.quote?.credits,
            estimatedDeliveryDate = project.timeline?.estimatedDeliveryDate,
            unreadComments = project.unreadComments,
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
