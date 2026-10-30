package com.raviga.app.data

import com.raviga.app.data.api.AppConfig
import com.raviga.app.data.api.Document
import com.raviga.app.data.api.Job
import com.raviga.app.data.api.JobEnvelope
import com.raviga.app.data.api.Me
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectsResponse
import com.raviga.app.data.api.Quote
import com.raviga.app.data.api.RegisterDeviceResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Decodes real backend responses (captured by the iOS session against dev) and
 * hand-written contract examples with the app's Json configuration.
 */
class DtoDecodingTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; isLenient = true }

    private fun fixture(name: String): String? {
        val url = javaClass.classLoader?.getResource("fixtures/$name") ?: return null
        return File(url.toURI()).readText()
    }

    private fun fixtures(): List<File> {
        val url = javaClass.classLoader?.getResource("fixtures") ?: return emptyList()
        return File(url.toURI()).listFiles { f -> f.extension == "json" }?.toList().orEmpty()
    }

    /** Captured from dev GET /config on 2026-10-09 (contract v0.7.1). */
    @Test
    fun `v071 config has dollars and bracket timelines`() {
        val config = json.decodeFromString(com.raviga.app.data.api.AppConfig.serializer(), fixture("config-v071.json")!!)
        assertEquals(10.0, config.credits.creditValueUsd, 0.0)
        assertEquals(listOf(99.99, 249.99, 499.99, 999.99), config.credits.packs.map { it.priceHintUsd })
        val timelines = config.quote.brackets.associate { it.id to it.timeline }
        assertEquals(com.raviga.app.data.api.QuoteTimeline(1, 1), timelines["micro"])
        assertEquals(com.raviga.app.data.api.QuoteTimeline(1, 2), timelines["standard"])
        assertEquals(com.raviga.app.data.api.QuoteTimeline(2, 4), timelines["pro"])
        assertTrue("enterprise" in timelines && timelines["enterprise"] == null)
        // The demo backend's config says the same.
        val demo = com.raviga.app.data.demo.DemoApi.demoConfig()
        assertEquals(config.quote.brackets.map { Triple(it.id, it.minCredits, it.timeline) }, demo.quote.brackets.map { Triple(it.id, it.minCredits, it.timeline) })
        assertEquals(config.credits.packs.map { it.priceHintUsd }, demo.credits.packs.map { it.priceHintUsd })
    }

    /** Captured from the raviga-* dev GET /config on 2026-10-09 (contract v0.8: the rename, shapes as v0.7.1). */
    @Test
    fun `v08 config carries the Raviga names and matches the demo`() {
        val config = json.decodeFromString(AppConfig.serializer(), fixture("config-v08.json")!!)
        val demo = com.raviga.app.data.demo.DemoApi.demoConfig()
        assertEquals("RavigaDeployRole", config.delivery.awsRoleName)
        assertEquals(config.delivery.awsRoleName, demo.delivery.awsRoleName)
        assertEquals("Raviga Apps Private Limited", config.legal.companyName)
        // The legal URLs moved to Raviga's domain (backend 5fe84e0); the v0.9.3 test pins the demo to them.
        assertEquals(config.quote.brackets.map { it.timeline }, demo.quote.brackets.map { it.timeline })
        assertEquals(config.credits.packs.map { it.productId }, demo.credits.packs.map { it.productId })
    }

    @Test
    fun `v06 submitted project holds its brief until it closes`() {
        val held = """
            {"id":"pr_1","ref":"RA-A1B2C3","title":"Tiffin delivery app","status":"submitted","createdAt":"2026-10-10T04:30:00Z","updatedAt":"2026-10-10T04:31:00Z",
             "document":{"version":1,"title":"Tiffin delivery app","sections":[{"id":"summary","heading":"Summary","body":"An app.","hint":"What it is"}],"documentHash":"ab12","updatedAt":"2026-10-10T04:31:00Z"},
             "quote":{"id":"qt_1","credits":48,"inr":48000,"bracketId":"standard","estimatedWorkingDays":30,"timeline":{"minWeeks":4,"maxWeeks":6},"documentHash":"ab12"},
             "submission":{"submittedAt":"2026-10-10T04:31:00Z","creditsCharged":48},"review":{"comments":[]},
             "revisions":{"used":0,"included":2,"requests":[]},"unreadComments":0,"history":[{"status":"submitted","at":"2026-10-10T04:31:00Z","by":"client","note":""}]}
        """.trimIndent()
        val p = json.decodeFromString(Project.serializer(), held)
        assertEquals("RA-A1B2C3", p.ref)
        assertEquals("An app.", p.document?.sections?.single()?.body)
        assertEquals(48, p.quote?.credits)
        assertEquals(null, p.contentDeletedAt)

        val closed = """
            {"id":"pr_1","ref":"RA-A1B2C3","title":"","status":"accepted","document":null,"contentDeletedAt":"2026-11-10T00:00:00Z",
             "quote":{"id":"qt_1","credits":48,"bracketId":"standard","estimatedWorkingDays":30},
             "delivery":{"repoUrl":"https://github.com/x/y","deliveredAt":"2026-11-01T00:00:00Z","note":""},
             "history":[{"status":"accepted","at":"2026-11-10T00:00:00Z","by":"client","note":""}]}
        """.trimIndent()
        val c = json.decodeFromString(Project.serializer(), closed)
        assertEquals("2026-11-10T00:00:00Z", c.contentDeletedAt)
        assertEquals(null, c.document)
        assertEquals("", c.title)
        // The closed stub has no title; screens name it from the phone's copy of the brief.
        assertEquals("Tiffin delivery app", com.raviga.app.ui.status.StatusCopy.projectTitle(c, "Tiffin delivery app"))
        assertEquals("Project RA-A1B2C3", com.raviga.app.ui.status.StatusCopy.projectTitle(c, null))
    }

    /** Captured from the raviga-* dev GET /config on 2026-10-21 (contract v0.9: analytics). */
    @Test
    fun `v09 config carries the analytics line and consent decodes analytics`() {
        val config = json.decodeFromString(AppConfig.serializer(), fixture("config-v09.json")!!)
        assertTrue(config.analytics.providers.single().startsWith("Google Analytics for Firebase"))
        assertEquals(2, config.analytics.retentionMonths)
        assertEquals(config.analytics, com.raviga.app.data.demo.DemoApi.demoConfig().analytics)
        val consent = json.decodeFromString(
            com.raviga.app.data.api.ConsentState.serializer(),
            """{"terms":null,"privacy":null,"aiProcessing":{"granted":true},"analytics":{"granted":false,"updatedAt":null},"current":false}""",
        )
        assertTrue(consent.aiGranted)
        assertEquals(false, consent.analyticsGranted)
        val app = Json { explicitNulls = false; encodeDefaults = true }
        assertEquals("""{"analytics":true}""", app.encodeToString(com.raviga.app.data.api.ConsentRequest.serializer(), com.raviga.app.data.api.ConsentRequest(analytics = true)))
    }

    /** Captured from the raviga-* dev GET /config on 2026-10-21 (contract v0.9.1: new legal versions, two AI providers). */
    @Test
    fun `v091 config has the new legal versions and both AI providers, as the demo does`() {
        val config = json.decodeFromString(AppConfig.serializer(), fixture("config-v091.json")!!)
        assertEquals("2026-10-21", config.legal.termsVersion)
        assertEquals("2026-10-21", config.legal.privacyVersion)
        assertEquals(2, config.ai.providers.size)
        assertTrue(config.ai.providers[1].startsWith("Anthropic"))
        val demo = com.raviga.app.data.demo.DemoApi.demoConfig()
        assertEquals(config.ai.providers, demo.ai.providers)
    }

    /** Captured from the raviga-* dev GET /config on 2026-10-29 (contract v0.9.2); name and address scrubbed for the repo. */
    @Test
    fun `v092 config has the new legal versions, the same shape, and the demo matches`() {
        val raw = fixture("config-v092.json")!!
        assertTrue(raw.contains("officerName") && raw.contains("\"address\""))   // still sent, for older builds
        val config = json.decodeFromString(AppConfig.serializer(), raw)
        assertEquals("2026-10-29", config.legal.termsVersion)
        assertEquals("2026-10-29", config.legal.privacyVersion)
        assertEquals("Questions or complaints: write to membersupport@miraquill.com. We reply within 15 days.",
            com.raviga.app.ui.status.StatusCopy.grievanceLine(config.legal.grievance))
    }

    /** v0.9.3 (R-066/R-067): the "Your rights" page and the 2026-10-30 legal versions; the demo matches dev. */
    @Test
    fun `v093 config has the your rights page and the new legal versions, and the demo matches`() {
        val config = json.decodeFromString(AppConfig.serializer(), fixture("config-v093.json")!!)
        assertEquals("https://legal.ravigaapps.com/your-rights", config.legal.rightsUrl)
        assertEquals("2026-10-30", config.legal.termsVersion)
        assertEquals("2026-10-30", config.legal.privacyVersion)
        val demo = com.raviga.app.data.demo.DemoApi.demoConfig()
        assertEquals(config.legal.rightsUrl, demo.legal.rightsUrl)
        assertEquals(config.legal.termsUrl, demo.legal.termsUrl)
        assertEquals(config.legal.privacyUrl, demo.legal.privacyUrl)
        assertEquals(config.legal.termsVersion, demo.legal.termsVersion)
        assertEquals(config.legal.privacyVersion, demo.legal.privacyVersion)
        assertEquals(config.legal.grievance, demo.legal.grievance)
    }

    /** R-060 (10-30): Play sells India at the plan prices and the 100 pack at India's cap; dev's /config hints match (backend c5f333b). */
    @Test
    fun `demo rupee hints match Play's India prices`() {
        val demo = com.raviga.app.data.demo.DemoApi.demoConfig()
        assertEquals(listOf(8_499, 21_249, 42_499, 84_000), demo.credits.packs.map { it.priceHintInr })
    }

    /** R-055: /config may still send a person's name and an address; the app decodes neither and shows one line. */
    @Test
    fun `grievance shows only the email and days, never a name or an address`() {
        val raw = """{"companyName":"Raviga Apps Private Limited","companyAddress":"1 Example Street, Delhi",
            "grievance":{"officerName":"Example Officer","email":"grievance@example.com","address":"1 Example Street, Delhi","responseDays":15},
            "supportEmail":"support@example.com"}"""
        val legal = json.decodeFromString(com.raviga.app.data.api.LegalConfig.serializer(), raw)
        val line = com.raviga.app.ui.status.StatusCopy.grievanceLine(legal.grievance)
        assertEquals("Questions or complaints: write to grievance@example.com. We reply within 15 days.", line)
        val kept = json.encodeToString(com.raviga.app.data.api.LegalConfig.serializer(), legal)
        for (personal in listOf("Example Officer", "Example Street", "officerName", "address")) {
            assertFalse(personal, kept.contains(personal) || line!!.contains(personal))
        }
        assertEquals(null, com.raviga.app.ui.status.StatusCopy.grievanceLine(com.raviga.app.data.api.Grievance(email = "", responseDays = 15)))
        val demo = com.raviga.app.data.demo.DemoApi.demoConfig().legal
        assertEquals("Questions or complaints: write to membersupport@miraquill.com. We reply within 15 days.",
            com.raviga.app.ui.status.StatusCopy.grievanceLine(demo.grievance))
    }

    /** R-066: her words for "What we don't keep", on the consent screens and Privacy and data. */
    @Test
    fun `the local-first promise is her R-066 wording`() {
        assertEquals(
            "Recordings and files never leave your phone. Drafts are stored only on your phone. To write or price a brief, its text is sent to an AI service, used once, and not stored by us. When you submit, we keep the brief to review and build it, and delete it when you accept the delivery.",
            com.raviga.app.ui.status.StatusCopy.LOCAL_FIRST_PROMISE,
        )
    }

    /** Contract v0.8.2: exactly one of fid or token goes up; the other key is left out, not sent as null. */
    @Test
    fun `push registration sends only the fid`() {
        val app = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
        val fid = app.encodeToString(com.raviga.app.data.api.PushTokenRequest.serializer(), com.raviga.app.data.api.PushTokenRequest(fid = "cX1fid"))
        assertEquals("""{"fid":"cX1fid"}""", fid)
        val legacy = app.encodeToString(com.raviga.app.data.api.PushTokenRequest.serializer(), com.raviga.app.data.api.PushTokenRequest(token = "tok"))
        assertEquals("""{"token":"tok"}""", legacy)
    }

    @Test
    fun `job envelope and bare job decode`() {
        val envelope = json.decodeFromString(JobEnvelope.serializer(), """{"job":{"jobId":"jb_1","type":"generate","status":"queued","projectId":"pr_1","progress":0,"progressMessage":null}}""")
        assertEquals("jb_1", envelope.job.jobId)
        val done = json.decodeFromString(Job.serializer(), """{"jobId":"jb_1","type":"generate","status":"done","progress":1,"result":{"version":1,"title":"x","sections":[]}}""")
        assertTrue(done.isDone)
        assertEquals(1, done.result?.jsonObject?.get("version")?.toString()?.toInt())
    }

    @Test
    fun `quote with breakdown and timeline decodes`() {
        val raw = """{"id":"qt_1","documentVersion":3,"status":"current","credits":42,"inr":42000,"bracketId":"starter","estimatedWorkingDays":30,
            "timeline":{"minWeeks":4,"maxWeeks":6},"complexity":{"score":5,"drivers":["Payments","Two platforms"]},
            "breakdown":[{"area":"Mobile apps","credits":24},{"area":"Backend","credits":14}],"assumptions":["Single language UI"],"createdAt":"2026-10-09T04:30:00Z","expiresAt":"2026-10-23T04:30:00Z"}"""
        val q = json.decodeFromString(Quote.serializer(), raw)
        assertEquals(42, q.credits)
        assertEquals(6, q.timeline?.maxWeeks)
        assertEquals(2, q.breakdown.size)
    }

    @Test
    fun `register response without recovery key decodes`() {
        val r = json.decodeFromString(RegisterDeviceResponse.serializer(), """{"clientId":"cl_1","deviceId":"dv_1","accessToken":"rat_x","recoveryKey":null,"isNewClient":false}""")
        assertEquals(null, r.recoveryKey)
        assertEquals(false, r.isNewClient)
    }

    @Test
    fun `me with nulls decodes`() {
        val me = json.decodeFromString(Me.serializer(), """{"clientId":"cl_1","createdAt":"2026-10-09T04:30:00Z","credits":{"balance":25},
            "consent":{"terms":null,"privacy":null,"aiProcessing":{"granted":false,"updatedAt":null},"current":false},
            "recoveryEmail":null,"deliveryTargets":{"githubUsername":"","aws":null},"deviceCount":2,"deletion":null}""")
        assertEquals(25, me.credits.balance)
        assertEquals(false, me.consent.aiGranted)
        assertEquals("", me.deliveryTargets.githubUsername)
    }

    @Test
    fun `captured dev fixtures decode by name`() {
        // Any fixture copied from the iOS session decodes with the matching DTO, chosen by filename.
        val files = fixtures()
        var checked = 0
        files.forEach { f ->
            val text = f.readText()
            val name = f.nameWithoutExtension.lowercase()
            runCatching {
                when {
                    name.startsWith("job") && text.trimStart().startsWith("{\"job\"") -> json.decodeFromString(JobEnvelope.serializer(), text).also { assertTrue(it.job.jobId.isNotBlank()) }
                    name.startsWith("job") -> json.decodeFromString(Job.serializer(), text).also { assertTrue(it.jobId.isNotBlank()) }
                    name.startsWith("error") -> json.decodeFromString(com.raviga.app.data.api.ApiErrorEnvelope.serializer(), text).also { assertTrue(it.error.code.isNotBlank()) }
                    name == "config" -> json.decodeFromString(AppConfig.serializer(), text).also { assertTrue(it.credits.packs.isNotEmpty()) }
                    name == "me" -> json.decodeFromString(Me.serializer(), text)
                    name == "credits" -> json.decodeFromString(com.raviga.app.data.api.CreditsResponse.serializer(), text)
                    name == "comments" -> json.decodeFromString(com.raviga.app.data.api.CommentsResponse.serializer(), text)
                    name == "comment" -> json.decodeFromString(com.raviga.app.data.api.Comment.serializer(), text)
                    name.startsWith("delivery-targets") -> json.decodeFromString(com.raviga.app.data.api.DeliveryTargets.serializer(), text)
                    name == "aws-connect" -> json.decodeFromString(com.raviga.app.data.api.AwsConnectInfo.serializer(), text)
                    name.startsWith("projects") -> json.decodeFromString(ProjectsResponse.serializer(), text)
                    name.startsWith("project") -> json.decodeFromString(Project.serializer(), text).also { assertNotNull(it.id) }
                    name.startsWith("document") -> json.decodeFromString(Document.serializer(), text).also { assertEquals(12, it.sections.size) }
                    name.startsWith("quote") -> json.decodeFromString(Quote.serializer(), text).also { assertTrue(it.credits > 0) }
                    name.startsWith("register") -> json.decodeFromString(RegisterDeviceResponse.serializer(), text)
                    else -> null
                }
            }.onSuccess { if (it != null) checked++ }
                .onFailure { throw AssertionError("fixture ${f.name} failed to decode: ${it.message}", it) }
        }
        println("decoded $checked of ${files.size} fixtures")
    }
}
