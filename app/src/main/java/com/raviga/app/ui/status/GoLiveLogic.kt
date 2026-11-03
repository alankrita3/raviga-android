package com.raviga.app.ui.status

import com.raviga.app.data.api.Revisions
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

    enum class Action { OpenGuide, ConnectAws }

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
        /** R-092 #41 (iOS review): the project's own rounds, never the config's (they can differ per project). */
        revisions: Revisions,
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
        val aws = delivery.aws?.status ?: "not_requested"
        add(
            Step(
                "aws", "Move the backend to your AWS",
                awsLine(delivery.aws) + if (aws == "not_requested") " Connect your account and we move it for you." else "",
                done = aws == "deployed",
                action = if (aws == "not_requested") Action.ConnectAws else null,
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
        val accepted = status == ProjectStatus.ACCEPTED
        val rounds = if (revisions.included == 1) "One round is" else "${revisions.included} rounds are"
        add(
            Step(
                "accept",
                if (accepted) "Delivery accepted" else "Accept the delivery",
                if (accepted) "It's yours. Ask the team any time if you need more." else "Or ask for a revision. $rounds included.",
                done = accepted,
            ),
        )
    }

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

    /** Where the backend runs (same wording as iOS). */
    fun awsLine(aws: AwsDelivery?): String = when (aws?.status ?: "not_requested") {
        "not_requested" -> "The backend runs on our AWS until you connect yours."
        "deployed" -> aws?.note?.trim().orEmpty().let { if (it.isEmpty()) "Deployed to your AWS account." else "Deployed to your AWS account. $it" }
        else -> "Deploying to your AWS account."
    }
}
