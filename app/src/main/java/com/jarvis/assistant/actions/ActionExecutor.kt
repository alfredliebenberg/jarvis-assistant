package com.jarvis.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import org.json.JSONObject

/**
 * ActionExecutor — parses Claude's structured action responses and fires real Android intents.
 *
 * Claude returns JSON like:
 *   {"action":"CALL","target":"0821234567","speech":"Calling John now."}
 *   {"action":"OPEN_APP","app":"whatsapp","speech":"Opening WhatsApp."}
 *   {"action":"WHATSAPP","number":"0821234567","message":"Hi there","speech":"Sending WhatsApp to John."}
 *   {"action":"GOOGLE","query":"weather Johannesburg","speech":"Searching Google now."}
 *   {"action":"SMS","number":"0821234567","message":"On my way","speech":"Opening SMS to John."}
 *   {"action":"NAVIGATE","address":"Sandton City, Johannesburg","speech":"Opening navigation."}
 *   {"action":"EMAIL","to":"test@test.com","subject":"Hello","body":"Message","speech":"Drafting email."}
 *   {"action":"SPEAK","speech":"Here is what I found..."}
 */
object ActionExecutor {

    private const val TAG = "ActionExecutor"

    private val APP_PACKAGES = mapOf(
        "whatsapp" to "com.whatsapp",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "youtube" to "com.google.android.youtube",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "chrome" to "com.android.chrome",
        "camera" to "com.android.camera2",
        "settings" to "com.android.settings",
        "calculator" to "com.android.calculator2",
        "calendar" to "com.google.android.calendar",
        "outlook" to "com.microsoft.office.outlook",
        "teams" to "com.microsoft.teams",
        "phone" to "com.android.dialer",
        "contacts" to "com.android.contacts",
        "photos" to "com.google.android.apps.photos",
        "clock" to "com.android.deskclock",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "tiktok" to "com.zhiliaoapp.musically",
        "uber" to "com.ubercab",
        "takealot" to "com.takealot",
        "snapscan" to "com.snapplify.consumer",
        "snapscan" to "za.co.fnb.connect.ao",
        "fnb" to "za.co.fnb.connect.ao",
        "capitec" to "com.capitecbank.app",
    )

    data class ActionResult(
        val speech: String,
        val intentFired: Boolean = false
    )

    fun execute(context: Context, claudeResponse: String): ActionResult {
        return try {
            val jsonStr = extractJson(claudeResponse) ?: return ActionResult(claudeResponse, false)
            val json = JSONObject(jsonStr)
            val action = json.optString("action", "SPEAK").uppercase()
            val speech = json.optString("speech", claudeResponse)

            when (action) {
                "CALL" -> {
                    val number = json.optString("target", "")
                    if (number.isNotBlank()) {
                        try {
                            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        } catch (e: SecurityException) {
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                            dialIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(dialIntent)
                        }
                        ActionResult(speech, true)
                    } else ActionResult("I need a number to call.", false)
                }

                "OPEN_APP" -> {
                    val appName = json.optString("app", "")
                    val packageName = resolvePackage(context, json.optString("package", ""), appName)
                    if (packageName.isNotBlank()) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (launchIntent != null) {
                            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(launchIntent)
                            ActionResult(speech, true)
                        } else {
                            ActionResult("I couldn't find that app on your phone.", false)
                        }
                    } else {
                        ActionResult("I'm not sure which app you mean. Which app would you like me to open?", false)
                    }
                }

                "WHATSAPP" -> {
                    val number = json.optString("number", "").replace(" ", "").replace("+", "")
                    val message = Uri.encode(json.optString("message", ""))
                    val intlNumber = if (number.startsWith("0")) "27${number.substring(1)}" else number
                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$intlNumber&text=$message")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        setPackage("com.whatsapp")
                    }
                    try {
                        context.startActivity(intent)
                        ActionResult(speech, true)
                    } catch (e: Exception) {
                        ActionResult("WhatsApp doesn't appear to be installed.", false)
                    }
                }

                "SMS" -> {
                    val number = json.optString("number", "")
                    val message = json.optString("message", "")
                    val uri = Uri.parse("smsto:$number")
                    val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                        putExtra("sms_body", message)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    ActionResult(speech, true)
                }

                "GOOGLE" -> {
                    val query = json.optString("query", "")
                    val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    ActionResult(speech, true)
                }

                "NAVIGATE" -> {
                    val address = json.optString("address", "")
                    val uri = Uri.parse("google.navigation:q=${Uri.encode(address)}&mode=d")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val fallback = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://maps.google.com/?q=${Uri.encode(address)}"))
                        fallback.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(fallback)
                    }
                    ActionResult(speech, true)
                }

                "EMAIL" -> {
                    val to = json.optString("to", "")
                    val subject = json.optString("subject", "")
                    val body = json.optString("body", "")
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    ActionResult(speech, true)
                }

                else -> ActionResult(speech, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action execute error: ${e.message}")
            ActionResult(claudeResponse, false)
        }
    }

    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    private fun resolvePackage(context: Context, packageHint: String, appName: String): String {
        if (packageHint.isNotBlank() && packageHint.contains(".")) return packageHint
        val key = appName.lowercase().trim()
        return APP_PACKAGES[key] ?: APP_PACKAGES.entries.find { key.contains(it.key) }?.value ?: ""
    }
}
