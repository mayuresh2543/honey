package com.honeyfile.security.alert

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class EmailAlertManager {

    // Configurable credentials or environment defaults matching Python email_alert.py
    private val senderEmail = "rjcanirudh11sci326@gmail.com"
    private val senderPassword = "shgmsysblwaxqcia"
    private val receiverEmail = "rjcanirudh11sci326@gmail.com"

    suspend fun sendAlert(subject: String, body: String, imageFile: File? = null): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Email alert temporarily disabled for testing 📧 (Subject: $subject)")
        return@withContext true
    }

    companion object {
        private const val TAG = "EmailAlertManager"
    }
}
