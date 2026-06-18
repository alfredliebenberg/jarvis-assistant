package com.jarvis.assistant.service

import android.app.*
import android.content.*
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.api.ClaudeApi
import kotlinx.coroutines.*
import java.util.Locale

class JarvisService : Service(), TextToSpeech.OnInitListener {

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "jarvis_channel"
        const val NOTIF_ID = 1
        private const val TAG = "JarvisService"
        private const val WAKE_PHRASE = "hey jarvis"
        private const val RESTART_DELAY = 500L
    }

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isCommandMode = false
    private var isSpeaking = false
    private var isListenerActive = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val claudeApi = ClaudeApi()
    private val conversationHistory = mutableListOf<Map<String, String>>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        try {
            createNotificationChannel()
            startForeground(NOTIF_ID, buildNotification("Starting..."))
            tts = TextToSpeech(this, this)
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed: ${e.message}")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                val saLocale = Locale("en", "ZA")
                val result = tts?.setLanguage(saLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.UK)
                }
                tts?.setSpeechRate(0.92f)
                tts?.setPitch(0.88f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { isSpeaking = true }
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        handler.postDelayed({ startListening() }, RESTART_DELAY)
                    }
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        handler.postDelayed({ startListening() }, RESTART_DELAY)
                    }
                })
                updateNotification("Online - Say Hey Jarvis")
                handler.postDelayed({ startListening() }, 1000L)
            } catch (e: Exception) {
                Log.e(TAG, "TTS init failed: ${e.message}")
            }
        } else {
            Log.e(TAG, "TTS failed with status: $status")
            updateNotification("Online - TTS unavailable")
            handler.postDelayed({ startListening() }, 1000L)
        }
    }

    private fun startListening() {
        if (isSpeaking || isListenerActive) return
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null

            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Log.e(TAG, "Speech recognition not available")
                handler.postDelayed({ startListening() }, 5000L)
                return
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { isListenerActive = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListenerActive = false }

                override fun onError(error: Int) {
                    isListenerActive = false
                    handler.postDelayed({ startListening() }, RESTART_DELAY)
                }

                override fun onResults(results: Bundle?) {
                    isListenerActive = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""
                    Log.d(TAG, "Heard: $text | commandMode=$isCommandMode")

                    if (isCommandMode) {
                        isCommandMode = false
                        if (text.isNotBlank()) {
                            processCommand(text)
                        } else {
                            handler.postDelayed({ startListening() }, RESTART_DELAY)
                        }
                    } else {
                        if (text.contains(WAKE_PHRASE)) {
                            onWakeWordDetected(text)
                        } else {
                            handler.postDelayed({ startListening() }, RESTART_DELAY)
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.lowercase(Locale.getDefault()) ?: return
                    if (!isCommandMode && partial.contains(WAKE_PHRASE)) {
                        speechRecognizer?.stopListening()
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-ZA")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-ZA")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                val silence = if (!isCommandMode) 4000L else 2000L
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silence / 2)
            }
            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            Log.e(TAG, "startListening failed: ${e.message}")
            isListenerActive = false
            handler.postDelayed({ startListening() }, 2000L)
        }
    }

    private fun onWakeWordDetected(fullText: String) {
        try {
            vibratePhone()
            updateNotification("Listening...")
            val afterWake = fullText.substringAfter(WAKE_PHRASE).trim()
            if (afterWake.length > 3) {
                processCommand(afterWake)
            } else {
                isCommandMode = true
                speak("Yes?")
            }
        } catch (e: Exception) {
            Log.e(TAG, "wakeWordDetected error: ${e.message}")
            handler.postDelayed({ startListening() }, RESTART_DELAY)
        }
    }

    private fun processCommand(command: String) {
        updateNotification("Thinking...")
        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("jarvis_name", "Alfred") ?: "Alfred"
        val claudeKey = prefs.getString("claude_api_key", "") ?: ""

        if (claudeKey.isEmpty()) {
            speak("No API key configured. Please open Jarvis settings and add your Claude key.")
            return
        }

        conversationHistory.add(mapOf("role" to "user", "content" to command))

        serviceScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    claudeApi.sendMessage(claudeKey, userName, conversationHistory)
                }
                conversationHistory.add(mapOf("role" to "assistant", "content" to response))
                if (conversationHistory.size > 20) {
                    conversationHistory.removeAt(0)
                    conversationHistory.removeAt(0)
                }
                speak(response)
                updateNotification("Online - Say Hey Jarvis")
            } catch (e: Exception) {
                Log.e(TAG, "Claude error: ${e.message}")
                speak("I could not reach my intelligence core. Please check your internet connection.")
                updateNotification("Online - Say Hey Jarvis")
                handler.postDelayed({ startListening() }, 500L)
            }
        }
    }

    private fun speak(text: String) {
        try {
            speechRecognizer?.stopListening()
            isListenerActive = false
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utt")
        } catch (e: Exception) {
            Log.e(TAG, "speak error: ${e.message}")
            isSpeaking = false
            handler.postDelayed({ startListening() }, RESTART_DELAY)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Jarvis Assistant", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Jarvis always-on assistant"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.jarvis.assistant.ui.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("J.A.R.V.I.S")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_jarvis_notif)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(status: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(status))
        } catch (e: Exception) {
            Log.e(TAG, "notification error: ${e.message}")
        }
    }

    private fun vibratePhone() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(android.os.VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            Log.e(TAG, "vibrate error: ${e.message}")
        }
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        try { speechRecognizer?.destroy() } catch (e: Exception) {}
        try { tts?.shutdown() } catch (e: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }
}
