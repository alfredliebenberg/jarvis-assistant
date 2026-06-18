package com.jarvis.assistant.ui

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSettings()

        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        binding.etClaudeKey.setText(prefs.getString("claude_api_key", ""))
        binding.etJarvisName.setText(prefs.getString("jarvis_name", "Alfred"))
        binding.switchAutoStart.isChecked = prefs.getBoolean("auto_start", true)
        binding.switchSaAccent.isChecked = prefs.getBoolean("sa_accent", true)
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("claude_api_key", binding.etClaudeKey.text.toString().trim())
            .putString("jarvis_name", binding.etJarvisName.text.toString().trim().ifEmpty { "Alfred" })
            .putBoolean("auto_start", binding.switchAutoStart.isChecked)
            .putBoolean("sa_accent", binding.switchSaAccent.isChecked)
            .apply()

        Toast.makeText(this, "Settings saved. Restart Jarvis to apply.", Toast.LENGTH_LONG).show()
        finish()
    }
}
