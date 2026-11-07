package de.laraai.hub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Read-only client for the LARA Core job list (python-worker /v1/jobs). */
internal object CoreClient {
    const val DEFAULT_URL = "http://127.0.0.1:8000"
    const val CREDENTIAL_ID = "lara-core"

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
