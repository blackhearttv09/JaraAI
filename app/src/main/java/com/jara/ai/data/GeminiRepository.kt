package com.jara.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMsg(val fromUser: Boolean, val text: String)

/** Plain REST client for the Gemini Developer API (generateContent + key validation). */
class GeminiRepository {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val base = "https://generativelanguage.googleapis.com/v1beta"

    private fun errorOf(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
        val hint = when (code) {
            400 -> "অনুরোধ সঠিক নয় বা API কী ভুল"
            401, 403 -> "API কী অবৈধ বা অনুমতি নেই"
            404 -> "মডেলটি পাওয়া যায়নি"
            429 -> "কোটা/রেট লিমিট শেষ, একটু পরে চেষ্টা করুন"
            else -> "সার্ভার ত্রুটি"
        }
        return "$hint ($code)" + (msg?.let { ": $it" } ?: "")
    }

    suspend fun validateKey(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$base/models?pageSize=1").header("x-goog-api-key", key).build()
            http.newCall(req).execute().use { r ->
                val b = r.body?.string().orEmpty()
                if (!r.isSuccessful) error(errorOf(r.code, b))
            }
        }
    }

    suspend fun chat(key: String, model: String, history: List<ChatMsg>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(key.isNotBlank()) { "আগে API কী সেটিংসে Gemini API Key দিন" }
            val contents = JSONArray()
            history.forEach {
                contents.put(JSONObject().put("role", if (it.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", it.text))))
            }
            val body = JSONObject().put("contents", contents)
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
                    "You are Jara, a friendly smart assistant. Reply in the same language the user writes in (Bengali, Hindi, English). Be concise."))))
            val req = Request.Builder().url("$base/models/$model:generateContent").header("x-goog-api-key", key)
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { r ->
                val b = r.body?.string().orEmpty()
                if (!r.isSuccessful) error(errorOf(r.code, b))
                val parts = JSONObject(b).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                val sb = StringBuilder()
                if (parts != null) for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
                sb.toString().ifBlank { error("মডেল কোনো উত্তর দেয়নি (নিরাপত্তা ফিল্টার হতে পারে)") }
            }
        }
    }
}
