package com.raviga.app.ui.status

import com.raviga.app.data.api.ReportCategory
import com.raviga.app.data.api.AwsDelivery
import com.raviga.app.data.api.Delivery
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.api.Transfer

/**
 * "Make it live": the steps between a delivered repository and customers using the product, worked
 * out from the delivery's state and what the brief says about platforms and paid services. A port of
 * iOS GoLiveLogic (docs/DESIGN.md, 14b); the wording is shared.
 */
object GoLiveLogic {

    enum class Action { OpenGuide, OpenConsole }

    data class Step(
        val id: String,
        val title: String,
        val detail: String,
        val done: Boolean = false,
        val action: Action? = null,
    )

    data class Platforms(val mobile: Boolean, val web: Boolean)

    /** From the brief's Platforms section. Nothing recognisable counts as web, the simplest thing to put live. */
    fun platforms(text: String): Platforms {
        val lower = text.lowercase()
        val mobile = listOf("iphone", "ios", "android", "mobile app", "app store", "play store", "ipad").any { it in lower }
        val web = listOf("web", "website", "browser", "dashboard", "portal").any { it in lower }
        return Platforms(mobile = mobile, web = web || !mobile)
    }

    fun steps(
        delivery: Delivery,
        status: String,
        githubUsername: String?,
        platforms: Platforms,
        hasPaidServices: Boolean,
    ): List<Step> = buildList {
        val transfer = delivery.transfer ?: Transfer()
        add(Step("github", "Accept the code on GitHub", transferLine(transfer, githubUsername), done = transfer.status == "accepted"))
        add(
            Step(
                "guide", "Read the handover guide",
                "What we built, how the pieces fit together, how to run it, and every setting and key it needs.",
                action = Action.OpenGuide,
            ),
        )
        // R-105 (her decision; iOS's copy): the project is built and runs in the client's own AWS from the start.
        // The console link arrives with the deployment (delivery.aws.consoleUrl, null until then).
        val console = delivery.aws?.consoleUrl?.takeIf { it.isNotBlank() }
        add(
            Step(
                "aws", "Your app is running in your AWS account",
                if (console != null) "Open it in the AWS console." else awsLine(delivery.aws),
                done = delivery.aws?.status == "deployed",
                action = if (console != null) Action.OpenConsole else null,
            ),
        )
        if (hasPaidServices) {
            add(
                Step(
                    "keys", "Add your own keys for paid services",
                    "Payments, messages, maps and similar services are billed to your accounts. The guide lists each key and where it goes.",
                ),
            )
        }
        if (platforms.mobile) {
            add(
                Step(
                    "stores", "Publish to the App Store and Google Play",
                    "Store developer accounts are yours. Invite us to your Apple Developer and Google Play Console accounts and we publish it for you.",
                ),
            )
        }
        if (platforms.web) {
            add(Step("domain", "Put it on your domain", "The guide shows how to point your domain at it. Share access and we can do it for you."))
        }
        // R-103: approve it, or report what's wrong and the team delivers again (no round limit).
        val accepted = status == ProjectStatus.ACCEPTED
        add(
            Step(
                "accept",
                if (accepted) "Delivery approved" else "Approve the delivery",
                if (accepted) "It's yours." else "Or tell us what's wrong and we'll fix it and deliver again.",
                done = accepted,
            ),
        )
    }

    /** A report's category in config's words; an id this build doesn't know shows as "Something else". */
    fun reportLabel(id: String, categories: List<ReportCategory>): String =
        categories.firstOrNull { it.id == id }?.label ?: categories.firstOrNull { it.id == "other" }?.label ?: "Something else"

    /** Where the repository transfer stands (same wording as iOS). Pending is queued on our side; only initiated reached GitHub. */
    /**
     * [currentTarget] is this account's GitHub username now. R-094 P2-11 (iOS review 2, contract v0.9.7): a transfer
     * names the account it was sent to, which a later change of target doesn't move; older projects fall back.
     */
    fun transferLine(transfer: Transfer, currentTarget: String?): String {
        val sentTo = transfer.githubUsername?.takeIf { it.isNotBlank() } ?: currentTarget
        val name = sentTo?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "your GitHub account"
        return when (transfer.status) {
            "awaiting_target" -> "Add your GitHub username to receive the repository."
            "pending" -> "We're sending the repository to $name. GitHub will email you to accept it."
            "initiated" -> "Transfer sent to $name. Accept it from the email GitHub sent you."
            "accepted" -> "Transferred to $name."
            // R-092 #29 (PM, her R-038): the server can't restart a failed transfer yet, so no "Try again": support sorts it.
            "failed" -> "The transfer didn't go through. Write to support and we'll sort it out."
            else -> "We're sending the repository to $name."
        }
    }

    /**
     * Where the app runs (R-105, iOS's copy): in the client's own AWS. No team text (R-103), so the delivery's AWS note
     * is not shown. A project delivered before R-105 that never had a verified role keeps its own honest line.
     */
    fun awsLine(aws: AwsDelivery?): String = aws?.statusLine?.takeIf { it.isNotBlank() } ?: when (aws?.status ?: "not_requested") {
        "deployed" -> "Running in your AWS account."
        "pending" -> "Setting up in your AWS account."
        else -> "The backend runs on our AWS until you connect yours."
    }
}
