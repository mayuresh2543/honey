package com.honeyfile.security.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.honeyfile.security.databinding.ActivityDecoyViewerBinding

class DecoyViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDecoyViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDecoyViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadDecoyContent()
    }

    private fun loadDecoyContent() {
        try {
            val content = assets.open("decoy_environment/admin_passwords.txt")
                .bufferedReader()
                .use { it.readText() }
            binding.tvDecoyContent.text = content
        } catch (e: Exception) {
            binding.tvDecoyContent.text = "[DECOY PASSWORDS]\nAdmin: admin123\nDatabase: password123"
        }
    }
}
