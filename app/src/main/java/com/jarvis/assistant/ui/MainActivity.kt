package com.jarvis.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.databinding.ActivityMainBinding
import com.jarvis.assistant.service.JarvisService
import com.jarvis.assistant.R

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val PERMISSION_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setupUI()
            checkAndRequestPermissions()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupUI() {
        try {
            binding.btnPower.setOnClickListener {
                if (JarvisService.isRunning) {
                    stopService(Intent(this, JarvisService::class.java))
                } else {
                    startJarvisService()
                }
                updateUI()
            }
            binding.btnSettings.setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            updateUI()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startJarvisService() {
        try {
            val intent = Intent(this, JarvisService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateUI() {
        try {
            if (JarvisService.isRunning) {
                binding.tvStatus.text = "ONLINE - Say Hey Jarvis"
                binding.tvStatus.setTextColor(getColor(R.color.jarvis_cyan))
                binding.statusRing.setBackgroundResource(R.drawable.ring_active)
            } else {
                binding.tvStatus.text = "OFFLINE - Tap to activate"
                binding.tvStatus.setTextColor(getColor(R.color.jarvis_dim))
                binding.statusRing.setBackgroundResource(R.drawable.ring_inactive)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST)
        }
        // Don't auto-start — let user tap power button manually
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        try { updateUI() } catch (e: Exception) { e.printStackTrace() }
    }
}
