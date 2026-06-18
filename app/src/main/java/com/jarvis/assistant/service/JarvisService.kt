package com.jarvis.assistant.service

import android.app.*
import android.content.*
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
        // Restart listening delay after speech ends (ms)
        private const val RESTART_DELAY = 300L
    }

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isCommandMode = false        // true = listening for a command after wake word
    private var isSpeaking = false           // true = Jarvis is talking, don't listen
    private var isListenerActive = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val claudeApi = ClaudeApi()
    private val conversationHistory = mutableListOf<Map<String, String>>()
    private val handler = Handler(Looper.getMainLooper())

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Initialising..."))
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── TTS Init ─────────────────────────────────────────────────────────────

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Prefer South African English; fall back to British
            val saLocale = Locale("en", "ZA")
            val result = tts?.setLanguage(saLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.UK)
            }
            tts?.setSpeechRate(0.92f)
            tts?.setPitch(0.88f)

            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { isSpeaking = true }
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    // Resume listening after Jarvis finishes speaking
                    handler.postDelayed({ startContinuousListening() }, RESTART_DELAY)
                }
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    handler.postDelayed({ startContinuousListening() }, RESTART_DELAY)
                }
            })

            startContinuousListening()
            updateNotification("Online — Say \"Hey Jarvis\"")
        }
    }

    // ─── Continuous Listening (wake word mode) ────────────────────────────────
    //
    // Strategy: we run SpeechRecognizer in a tight loop.
    // Every result is scanned for "hey jarvis".
    // When detected, we switch to command mode for ONE recognition pass,
    // then send the command to Claude.

    private fun startContinuousListening() {
        if (isSpeaking || isListenerActive) return

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val intent = buildRecognitionIntent(longSilence = !isCommandMode)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListenerActive = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListenerActive = false }

            override fun onError(error: Int) {
                isListenerActive = false
                // Errors 6 (no match) and 7 (no speech) are normal in background listening
                handler.postDelayed({ startContinuousListening() }, RESTART_DELAY)
            }

            override fun onResults(results: Bundle?) {
                isListenerActive = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""

                Log.d(TAG, "Heard: $text | commandMode=$isCommandMode")

                if (isCommandMode) {
                    // This is the user's command after "Hey Jarvis"
                    isCommandMode = false
                    if (text.isNotBlank()) {
                        processCommand(text)
                    } else {
                        startContinuousListening()
                    }
                } else {
                    // Scan for wake phrase
                    if (text.contains(WAKE_PHRASE)) {
                        onWakeWordDetected(text)
                    } else {
                        // Nothing relevant — keep listening
                        handler.postDelayed({ startContinuousListening() }, RESTART_DELAY)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Check partial results too for faster wake word response
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase(Locale.getDefault()) ?: return

                if (!isCommandMode && partial.contains(WAKE_PHRASE)) {
                    // Stop current recognition and switch to command mode immediately
                    speechRecognizer?.stopListening()
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun buildRecognitionIntent(longSilence: Boolean): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-ZA")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-ZA")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Longer silence window in wake-word mode so it doesn't cut out too fast
            val silence = if (longSilence) 4000L else 2000L
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silence / 2)
        }
    }

    // ─── Wake Word Detected ───────────────────────────────────────────────────

    private fun onWakeWordDetected(fullText: String) {
        vibratePhone()
        updateNotification("Listening for command...")

        // Check if the command was spoken in the same utterance as the wake word
        // e.g. "Hey Jarvis what time is it"
        val afterWake = fullText.substringAfter(WAKE_PHRASE).trim()
        if (afterWake.length > 3) {
            // Command already in the same utterance
            processCommand(afterWake)
        } else {
            // Switch to command mode — next recognition pass captures the command
            isCommandMode = true
            speak("Yes?")
            // Don't restart listener here — TTS done listener will restart it
        }
    }

    // ─── Claude AI Processing ─────────────────────────────────────────────────

    private fun processCommand(command: String) {
        Log.d(TAG, "Processing: $command")
        updateNotification("Thinking...")

        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("jarvis_name", "Alfred") ?: "Alfred"
        val claudeKey = prefs.getString("claude_api_key", "") ?: ""

        if (claudeKey.isEmpty()) {
            speak("No Claude API key configured. Please add your key in settings, $userName.")
            return
        }

        conversationHistory.add(mapOf("role" to "user", "content" to command))

        serviceScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    claudeApi.sendMessage(
                        apiKey = claudeKey,
                        userName = userName,
                        messages = conversationHistory
                    )
                }
                conversationHistory.add(mapOf("role" to "assistant", "content" to response))
                if (conversationHistory.size > 20) {
                    conversationHistory.removeAt(0)
                    conversationHistory.removeAt(0)
                }
                speak(response)
                updateNotification("Online — Say \"Hey Jarvis\"")

            } catch (e: Exception) {
                Log.e(TAG, "Claude error: ${e.message}")
                speak("I'm having trouble reaching my intelligence core. Please check your internet connection.")
                updateNotification("Online — Say \"Hey Jarvis\"")
                handler.postDelayed({ startContinuousListening() }, 500L)
            }
        }
    }

    // ─── TTS ──────────────────────────────────────────────────────────────────

    private fun speak(text: String) {
        // Stop listening while speaking to avoid feedback loop
        speechRecognizer?.stopListening()
        isListenerActive = false
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utt")
    }

    // ─── Notification ─────────────────────────────────────────────────────────

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
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(status))
    }

    // ─── Vibration ────────────────────────────────────────────────────────────

    private fun vibratePhone() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(android.os.VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        tts?.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }
}
