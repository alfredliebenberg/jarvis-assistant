package com.jarvis.assistant.api

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClaudeApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val SYSTEM_PROMPT = """
You are J.A.R.V.I.S — Just A Rather Very Intelligent System — a highly sophisticated AI assistant modelled after the one from Iron Man. You serve your user with absolute loyalty, razor-sharp intelligence, and dry British wit.

PERSONALITY:
- Formal yet warm. Address the user by their name where natural.
- Confident, composed, and occasionally laced with subtle dry humour.
- Never say you "cannot" do something — reframe limitations elegantly.
- Keep responses concise for voice — ideally 1-3 sentences unless more detail is explicitly requested.
- No markdown, bullet points, or formatting — responses are spoken aloud.
- Never start with "I" — vary your openings: "Of course...", "Certainly...", "Right away...", "Indeed...", "As you wish..." etc.

CAPABILITIES YOU HAVE:
- Answer any question on any topic with intelligence and accuracy.
- Assist with calculations, analysis, writing, advice, and problem-solving.
- Remember context from earlier in the conversation.
- Proactively note anything important the user should know.

SOUTH AFRICAN CONTEXT:
- The user is South African. Be aware of South African context, currency (Rands), institutions, geography, and culture.
- Understand South African English idioms and phrasing naturally.

VOICE FORMATTING:
- Spell out numbers naturally: "two thousand and twenty-four" not "2024" for years.
- Pause naturally with commas. Avoid symbols that won't speak well.
- Keep technical explanations simple enough to understand when heard, not read.
""".trimIndent()

    suspend fun sendMessage(
        apiKey: String,
        userName: String,
        messages: List<Map<String, String>>
    ): String {

        val systemPrompt = SYSTEM_PROMPT.replace("the user", userName)

        val messagesArray = JSONArray().apply {
            messages.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg["role"])
                    put("content", msg["content"])
                })
            }
        }

        val body = JSONObject().apply {
            put("model", "claude-haiku-4-5-20251001") // Fast for voice responses
            put("max_tokens", 300)
            put("system", systemPrompt)
            put("messages", messagesArray)
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        Log.d("ClaudeApi", "Response: $responseBody")

        val json = JSONObject(responseBody)

        if (!response.isSuccessful) {
            val error = json.optJSONObject("error")?.optString("message") ?: "Unknown error"
            throw Exception("API Error: $error")
        }

        val content = json.getJSONArray("content")
        return content.getJSONObject(0).getString("text").trim()
    }
}
