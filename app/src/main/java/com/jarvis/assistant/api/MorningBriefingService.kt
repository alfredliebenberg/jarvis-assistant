package com.jarvis.assistant.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * MorningBriefingService
 *
 * Fetches:
 *  1. Current weather from OpenWeatherMap (free tier, no auth beyond API key)
 *  2. Today's Outlook calendar events via Microsoft Graph API (requires MS OAuth token)
 *
 * Returns a plain-English briefing string that JarvisService speaks aloud.
 */
class MorningBriefingService(private val context: Context) {

    companion object {
        private const val TAG = "MorningBriefing"
        private const val OWM_BASE = "https://api.openweathermap.org/data/2.5/weather"
        private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Build the full morning briefing.
     * @param owmApiKey   OpenWeatherMap API key (free at openweathermap.org)
     * @param msGraphToken Microsoft Graph access token (from MSAL OAuth flow)
     * @param city        City name for weather e.g. "Johannesburg"
     * @param userName    User's name for the greeting
     */
    suspend fun buildBriefing(
        owmApiKey: String,
        msGraphToken: String,
        city: String,
        userName: String
    ): String = withContext(Dispatchers.IO) {

        val sb = StringBuilder()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        sb.append("$greeting, $userName. ")

        // --- Weather ---
        try {
            val weather = fetchWeather(owmApiKey, city)
            sb.append(weather).append(" ")
        } catch (e: Exception) {
            Log.e(TAG, "Weather failed: ${e.message}")
            sb.append("I was unable to retrieve the weather right now. ")
        }

        // --- Calendar ---
        if (msGraphToken.isNotBlank()) {
            try {
                val calendar = fetchOutlookCalendar(msGraphToken)
                sb.append(calendar)
            } catch (e: Exception) {
                Log.e(TAG, "Calendar failed: ${e.message}")
                sb.append("I could not access your calendar at this time.")
            }
        } else {
            sb.append("Your Microsoft account is not connected, so I cannot retrieve your calendar. " +
                    "You can link it in the Jarvis settings.")
        }

        sb.toString()
    }

    private fun fetchWeather(apiKey: String, city: String): String {
        val url = "$OWM_BASE?q=${city}&appid=$apiKey&units=metric"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty weather response")
        val json = JSONObject(body)

        if (!response.isSuccessful) {
            val msg = json.optJSONObject("message") ?: throw Exception("Weather API error: ${response.code}")
        }

        val temp = json.getJSONObject("main").getDouble("temp").toInt()
        val feelsLike = json.getJSONObject("main").getDouble("feels_like").toInt()
        val desc = json.getJSONArray("weather").getJSONObject(0).getString("description")
        val humidity = json.getJSONObject("main").getInt("humidity")
        val windSpeed = json.getJSONObject("wind").getDouble("speed").toInt()

        return "In $city right now, it is $temp degrees Celsius with $desc. " +
               "It feels like $feelsLike degrees, humidity at $humidity percent, " +
               "and winds at $windSpeed kilometres per hour."
    }

    private fun fetchOutlookCalendar(accessToken: String): String {
        // Get today's start and end in ISO 8601 UTC
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val startOfDay = sdf.format(cal.time)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        val endOfDay = sdf.format(cal.time)

        val url = "$GRAPH_BASE/me/calendarView" +
                  "?startDateTime=$startOfDay" +
                  "&endDateTime=$endOfDay" +
                  "&\$select=subject,start,end,location" +
                  "&\$orderby=start/dateTime" +
                  "&\$top=10"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty calendar response")
        val json = JSONObject(body)

        if (!response.isSuccessful) {
            throw Exception("Graph API error: ${response.code}")
        }

        val events = json.getJSONArray("value")
        if (events.length() == 0) {
            return "You have no meetings scheduled for today. Your calendar is clear."
        }

        val sb = StringBuilder("Here is your schedule for today. ")
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        timeFmt.timeZone = TimeZone.getDefault()
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS", Locale.US)
        isoFmt.timeZone = TimeZone.getTimeZone("UTC")

        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            val subject = event.optString("subject", "Untitled meeting")
            val startStr = event.getJSONObject("start").optString("dateTime", "")
            val endStr = event.getJSONObject("end").optString("dateTime", "")
            val location = event.optJSONObject("location")?.optString("displayName", "") ?: ""

            val startTime = try { timeFmt.format(isoFmt.parse(startStr)!!) } catch (e: Exception) { "" }
            val endTime = try { timeFmt.format(isoFmt.parse(endStr)!!) } catch (e: Exception) { "" }

            sb.append("At $startTime")
            if (endTime.isNotBlank()) sb.append(" until $endTime")
            sb.append(", $subject")
            if (location.isNotBlank()) sb.append(", at $location")
            sb.append(". ")
        }

        sb.append("That is all for today.")
        return sb.toString()
    }
}
