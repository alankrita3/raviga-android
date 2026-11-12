package com.raviga.app.ui.status

import com.raviga.app.data.api.Handover
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.api.ReportCategory

/**
 * "Make it live" (R-109, her delivery model; PM-approved copy as iOS): after approval Raviga publishes the app into the
 * client's own store accounts and, when the project has a backend, deploys it into their AWS. The client creates the
 * store accounts and invites Raviga; the server's handover says where each store stands.
 */
object GoLiveLogic {

    enum class StepId { ACCOUNTS, INVITE, AWS, PUBLISH, LIVE }

    data class Step(val id: StepId, val title: String, val done: Boolean)

    /** The stores this project goes into: the handover's own, else the project's apps (both when an older server is silent). */
    data class Stores(val apple: Boolean, val play: Boolean) {
        val list: List<String> get() = listOfNotNull(Handover.APPLE.takeIf { apple }, Handover.PLAY.takeIf { play })
    }

    fun stores(project: Project): Stores {
        val handover = project.delivery?.handover?.stores.orEmpty()
        if (handover.isNotEmpty()) return Stores(apple = Handover.APPLE in handover, play = Handover.PLAY in handover)
        val apps = project.apps
        if (apps.isEmpty()) return Stores(apple = true, play = true)
        return Stores(apple = "ios" in apps, play = "android" in apps)
    }

    /** Make it live's steps, from approval on; null before it (the screen says the steps appear once approved). */
    fun steps(project: Project, awsVerified: Boolean): List<Step>? {
        if (project.status != ProjectStatus.ACCEPTED && project.status != ProjectStatus.LIVE) return null
        val handover = project.delivery?.handover
        val states = stores(project).list.map { handover?.stores?.get(it) ?: "pending" }
        val live = project.status == ProjectStatus.LIVE
        val invited = live || states.none { it == "pending" }
        val published = live || states.all { it == "published" }
        return buildList {
            add(Step(StepId.ACCOUNTS, DeliveryCopy.STEP_ACCOUNTS, done = invited))
            add(Step(StepId.INVITE, DeliveryCopy.STEP_INVITE, done = invited))
            // R-113 (4b, as iOS): AWS only when the server says there's a backend (pending or deployed).
            if (hasBackendStep(handover)) {
                add(Step(StepId.AWS, DeliveryCopy.STEP_AWS, done = live || awsVerified || handover?.backend == "deployed"))
            }
            add(Step(StepId.PUBLISH, DeliveryCopy.STEP_PUBLISH, done = published))
            add(Step(StepId.LIVE, DeliveryCopy.STEP_LIVE, done = live))
        }
    }

    /** The AWS step and every AWS line appear only with a backend: `handover.backend` pending or deployed. */
    fun hasBackendStep(handover: Handover?): Boolean = handover?.backend == "pending" || handover?.backend == "deployed"

    /** A store's line in "We publish": the server's (`storeLines`), else the same words from its state. */
    fun storeLine(handover: Handover?, store: String): String =
        handover?.storeLines?.get(store)?.takeIf { it.isNotBlank() }
            ?: DeliveryCopy.storeLine(store, handover?.stores?.get(store) ?: "pending")

    /** Stores still waiting for the client's invite: "I've sent the invites" is offered while there are any. */
    fun waitingForInvite(project: Project): List<String> =
        stores(project).list.filter { (project.delivery?.handover?.stores?.get(it) ?: "pending") == "pending" }

    /** Settings shows "AWS account" only when a project with a backend has reached Make it live (R-109, G). */
    fun needsAwsRow(projects: Collection<Project>): Boolean =
        projects.any {
            // v0.15.0: hasBackend is the builder's answer at the demo; Make it live's own word is handover.backend.
            (it.hasBackend || hasBackendStep(it.delivery?.handover)) && (it.status == ProjectStatus.ACCEPTED || it.status == ProjectStatus.LIVE)
        }

    /** A report's category in config's words; an id this build doesn't know shows as "Something else". */
    fun reportLabel(id: String, categories: List<ReportCategory>): String =
        categories.firstOrNull { it.id == id }?.label ?: categories.firstOrNull { it.id == "other" }?.label ?: "Something else"
}
