package com.jarvis.assistant.ui

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

        val etClaudeKey = findViewById<EditText>(R.id.etClaudeKey)
        val etName = findViewById<EditText>(R.id.etName)
        val etOwmKey = findViewById<EditText>(R.id.etOwmKey)
        val etCity = findViewById<EditText>(R.id.etCity)
        val etMsToken = findViewById<EditText>(R.id.etMsToken)
        val swMorning = findViewById<Switch>(R.id.swMorningBriefing)
        val npHour = findViewById<NumberPicker>(R.id.npHour)
        val npMinute = findViewById<NumberPicker>(R.id.npMinute)
        val btnSave = findViewById<Button>(R.id.btnSave)

        // Configure number pickers
        npHour.minValue = 0
        npHour.maxValue = 23
        npMinute.minValue = 0
        npMinute.maxValue = 59
        npMinute.setFormatter { i -> i.toString().padStart(2, '0') }

        // Load saved values
        etClaudeKey.setText(prefs.getString("claude_api_key", ""))
        etName.setText(prefs.getString("jarvis_name", "Alfred"))
        etOwmKey.setText(prefs.getString("owm_api_key", ""))
        etCity.setText(prefs.getString("weather_city", "Johannesburg"))
        etMsToken.setText(prefs.getString("ms_graph_token", ""))
        swMorning.isChecked = prefs.getBoolean("morning_briefing_enabled", true)
        npHour.value = prefs.getInt("morning_briefing_hour", 7)
        npMinute.value = prefs.getInt("morning_briefing_minute", 0)

        btnSave.setOnClickListener {
            prefs.edit().apply {
                putString("claude_api_key", etClaudeKey.text.toString().trim())
                putString("jarvis_name", etName.text.toString().trim().ifEmpty { "Alfred" })
                putString("owm_api_key", etOwmKey.text.toString().trim())
                putString("weather_city", etCity.text.toString().trim().ifEmpty { "Johannesburg" })
                putString("ms_graph_token", etMsToken.text.toString().trim())
                putBoolean("morning_briefing_enabled", swMorning.isChecked)
                putInt("morning_briefing_hour", npHour.value)
                putInt("morning_briefing_minute", npMinute.value)
                apply()
            }
            Toast.makeText(this, "Configuration saved. Restart Jarvis to apply alarm changes.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
