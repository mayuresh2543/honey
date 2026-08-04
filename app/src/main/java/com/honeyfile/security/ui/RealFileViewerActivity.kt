package com.honeyfile.security.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.honeyfile.security.databinding.ActivityRealFileViewerBinding

class RealFileViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRealFileViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRealFileViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadRealContent()
    }

    private fun loadRealContent() {
        try {
            val content = assets.open("log/admin_passwords.txt")
                .bufferedReader()
                .use { it.readText() }
            binding.tvRealContent.text = content
        } catch (e: Exception) {
            binding.tvRealContent.text = "[CONFIDENTIAL ADMIN DATA]\nMaster Admin: admin_v4_prod_pass_9921!"
        }
    }
}
