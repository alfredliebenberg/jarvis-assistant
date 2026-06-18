package com.jarvis.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.R
import com.jarvis.assistant.databinding.ActivityMainBinding
import com.jarvis.assistant.service.JarvisService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val PERMISSION_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        requestPermissions()
    }

    private fun setupUI() {
        // Status ring animation
        binding.statusRing.startAnimation(
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse)
        )

        // Power toggle button
        binding.btnPower.setOnClickListener {
            if (JarvisService.isRunning) {
                stopJarvisService()
            } else {
                startJarvisService()
            }
            updateUI()
        }

        // Settings button
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateUI()
    }

    private fun startJarvisService() {
        val intent = Intent(this, JarvisService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopJarvisService() {
        stopService(Intent(this, JarvisService::class.java))
    }

    private fun updateUI() {
        if (JarvisService.isRunning) {
            binding.btnPower.setImageResource(R.drawable.ic_power_on)
            binding.tvStatus.text = "ONLINE — Say \"Hey Jarvis\""
            binding.tvStatus.setTextColor(getColor(R.color.jarvis_cyan))
            binding.statusRing.setBackgroundResource(R.drawable.ring_active)
        } else {
            binding.btnPower.setImageResource(R.drawable.ic_power_off)
            binding.tvStatus.text = "OFFLINE"
            binding.tvStatus.setTextColor(getColor(R.color.jarvis_dim))
            binding.statusRing.setBackgroundResource(R.drawable.ring_inactive)
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST)
        } else {
            // Permissions already granted — auto-start
            startJarvisService()
            updateUI()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startJarvisService()
                updateUI()
            } else {
                binding.tvStatus.text = "Microphone permission required"
                binding.tvStatus.setTextColor(getColor(R.color.jarvis_red))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
