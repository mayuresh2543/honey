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
        try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(senderEmail, senderPassword)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(senderEmail))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(receiverEmail))
                setSubject("🚨 $subject")

                val multipart = MimeMultipart()

                // Text part
                val textPart = MimeBodyPart().apply {
                    setText(body)
                }
                multipart.addBodyPart(textPart)

                // Attachment part (intruder photo)
                if (imageFile != null && imageFile.exists()) {
                    val attachmentPart = MimeBodyPart().apply {
                        val source = FileDataSource(imageFile)
                        dataHandler = DataHandler(source)
                        fileName = imageFile.name
                    }
                    multipart.addBodyPart(attachmentPart)
                }

                setContent(multipart)
            }

            Transport.send(message)
            Log.d(TAG, "Email alert sent successfully 📧")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email alert ❌", e)
            false
        }
    }

    companion object {
        private const val TAG = "EmailAlertManager"
    }
}
