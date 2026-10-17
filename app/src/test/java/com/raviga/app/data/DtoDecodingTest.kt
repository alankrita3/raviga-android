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
        assertEquals(config.legal.termsUrl, demo.legal.termsUrl)
        assertEquals(config.legal.privacyUrl, demo.legal.privacyUrl)
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
