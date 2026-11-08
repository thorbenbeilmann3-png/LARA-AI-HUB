package de.laraai.hub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Read-only client for the LARA Core job list (python-worker /v1/jobs). */
internal object CoreClient {
    const val DEFAULT_URL = "http://127.0.0.1:8000"
    const val CREDENTIAL_ID = "lara-core"
    const val APPROVER_CREDENTIAL_ID = "lara-core-approver"

    data class DraftSummary(val id: String, val title: String, val goal: String, val status: String,
                            val note: String?, val acceptance: List<String>, val estimatedCost: String?)

    data class JobSummary(val title: String, val percent: Double, val state: String,
                          val currentStep: String?, val nextStep: String?, val activeModel: String?)

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    suspend fun listJobs(baseUrl: String, token: String): List<JobSummary> = withContext(Dispatchers.IO) {
        val root = baseUrl.trim().trimEnd('/')
        require(root.startsWith("http://", true) || root.startsWith("https://", true)) {
            "Bitte eine gültige Core-Adresse eingeben."
        }
        require(CleartextPolicy.isAllowed(root)) {
            "Unverschlüsseltes HTTP ist nur für Dienste auf diesem Handy erlaubt (z. B. $DEFAULT_URL)."
        }
        val request = Request.Builder().url("$root/v1/jobs").header("Authorization", "Bearer $token").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when (response.code) {
                200 -> parseJobs(JSONObject(body))
                401 -> error("Core-Token ist falsch.")
                503 -> error("Im Core ist kein LARA_AUTOMATION_TOKEN gesetzt.")
                else -> error("Core antwortet mit HTTP ${response.code}.")
            }
        }
    }

    suspend fun listDrafts(baseUrl: String, token: String): List<DraftSummary> = withContext(Dispatchers.IO) {
        val root = checkedRoot(baseUrl)
        val request = Request.Builder().url("$root/v1/drafts").header("Authorization", "Bearer $token").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code != 200) error(coreError(response.code))
            parseDrafts(JSONObject(body)).filter { it.status == "draft" || it.status == "revise" }
        }
    }

    /** decision: approve | reject | revise. Requires the approver token (never the client token). */
    suspend fun decideDraft(baseUrl: String, approverToken: String, draftId: String, decision: String, note: String?): String =
        withContext(Dispatchers.IO) {
            require(decision in setOf("approve", "reject", "revise")) { "Unbekannte Entscheidung." }
            require(draftId.matches(Regex("[a-f0-9]{32}"))) { "Ungültige Entwurfs-ID." }
            val root = checkedRoot(baseUrl)
            val payload = JSONObject().put("decision", decision).put("note", note ?: JSONObject.NULL).toString()
            val request = Request.Builder().url("$root/v1/drafts/$draftId/decision")
                .header("Authorization", "Bearer $approverToken")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code != 200) error(coreError(response.code) + " " + body.take(160))
                JSONObject(body).optString("status")
            }
        }

    internal fun parseDrafts(json: JSONObject): List<DraftSummary> {
        val drafts = json.optJSONArray("drafts") ?: return emptyList()
        return (0 until drafts.length()).map { i ->
            val d = drafts.getJSONObject(i)
            val details = d.optJSONObject("details") ?: JSONObject()
            val acc = details.optJSONArray("acceptance_criteria")
            DraftSummary(
                id = d.optString("id"), title = d.optString("title"), goal = d.optString("goal"),
                status = d.optString("status"),
                note = if (d.isNull("note")) null else d.optString("note"),
                acceptance = if (acc == null) emptyList() else (0 until acc.length()).map { acc.optString(it) },
                estimatedCost = if (details.isNull("estimated_cost") || !details.has("estimated_cost")) null else details.optString("estimated_cost")
            )
        }
    }

    private fun checkedRoot(baseUrl: String): String {
        val root = baseUrl.trim().trimEnd('/')
        require(root.startsWith("http://", true) || root.startsWith("https://", true)) { "Bitte eine gültige Core-Adresse eingeben." }
        require(CleartextPolicy.isAllowed(root)) { "Unverschlüsseltes HTTP ist nur für Dienste auf diesem Handy erlaubt (z. B. $DEFAULT_URL)." }
        return root
    }

    private fun coreError(code: Int): String = when (code) {
        401 -> "Token ist falsch (Client- bzw. Freigabe-Token prüfen)."
        400 -> "Der Core hat die Entscheidung abgelehnt (z. B. schon entschieden oder Notiz fehlt)."
        404 -> "Entwurf nicht gefunden."
        429 -> "Zu viele Anfragen – kurz warten."
        503 -> "Im Core ist der passende Token nicht gesetzt."
        else -> "Core antwortet mit HTTP $code."
    }

    /** Unauthenticated liveness check of the Core (GET /health). Never throws. */
    suspend fun isAlive(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val root = baseUrl.trim().trimEnd('/')
            if (!CleartextPolicy.isAllowed(root)) return@runCatching false
            val request = Request.Builder().url("$root/health").get().build()
            client.newCall(request).execute().use { it.code == 200 }
        }.getOrDefault(false)
    }

    internal fun parseJobs(json: JSONObject): List<JobSummary> {
        val jobs = json.optJSONArray("jobs") ?: return emptyList()
        return (0 until jobs.length()).map { i ->
            val j = jobs.getJSONObject(i)
            JobSummary(
                title = j.optString("title"),
                percent = j.optDouble("overall_percent", 0.0),
                state = j.optString("state"),
                currentStep = j.optJSONObject("current_step")?.optString("title"),
                nextStep = j.optJSONObject("next_step")?.optString("title"),
                activeModel = if (j.isNull("active_model")) null else j.optString("active_model")
            )
        }
    }
}
