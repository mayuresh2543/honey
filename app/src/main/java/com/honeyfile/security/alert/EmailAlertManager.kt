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

    suspend fun sendAlert(
        context: android.content.Context? = null,
        subject: String,
        body: String,
        imageFile: File? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val recipients = if (context != null) {
                val faceAuthManager = com.honeyfile.security.auth.FaceAuthManager(context)
                faceAuthManager.getNotificationRecipients()
            } else {
                emptyList()
            }

            if (recipients.isEmpty()) {
                Log.w(TAG, "No registered admin email address found. Skipping email alert dispatch.")
                return@withContext false
            }

            val recipientAddresses = recipients.map { InternetAddress(it) }.toTypedArray()

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "10000")
                put("mail.smtp.writetimeout", "10000")
                put("mail.smtp.ssl.protocols", "TLSv1.2")
                put("mail.smtp.ssl.trust", "smtp.gmail.com")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(senderEmail, senderPassword)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(senderEmail, "Honeyfile Security Engine"))
                setRecipients(Message.RecipientType.TO, recipientAddresses)
                setSubject("🚨 $subject")

                val multipart = MimeMultipart("related")

                val htmlBody = """
                    <div style="font-family: Arial, sans-serif; background-color: #0f172a; color: #f8fafc; padding: 20px; border-radius: 12px;">
                        <h2 style="color: #ef4444; margin-top: 0;">🚨 Intruder Breach Alert</h2>
                        <p style="font-size: 15px; color: #cbd5e1;">$body</p>
                        ${if (imageFile != null && imageFile.exists()) """
                            <div style="margin-top: 15px; text-align: center;">
                                <p style="font-weight: bold; color: #38bdf8;">Captured Intruder Evidence Photo:</p>
                                <img src="cid:intruder_photo" style="max-width: 100%; height: auto; border: 2px solid #ef4444; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.5);" />
                            </div>
                        """ else ""}
                        <hr style="border: 0; border-top: 1px solid #334155; margin-top: 20px;" />
                        <p style="font-size: 12px; color: #94a3b8;">Honeyfile Deception & Endpoint Protection Engine</p>
                    </div>
                """.trimIndent()

                val htmlPart = MimeBodyPart().apply {
                    setContent(htmlBody, "text/html; charset=utf-8")
                }
                multipart.addBodyPart(htmlPart)

                if (imageFile != null && imageFile.exists()) {
                    val imagePart = MimeBodyPart().apply {
                        val source = FileDataSource(imageFile)
                        dataHandler = DataHandler(source)
                        setHeader("Content-ID", "<intruder_photo>")
                        disposition = MimeBodyPart.INLINE
                        fileName = imageFile.name
                    }
                    multipart.addBodyPart(imagePart)
                }

                setContent(multipart)
            }

            Transport.send(message)
            Log.d(TAG, "Email alert with intruder photo sent successfully to ${recipients.joinToString(", ")} 📧")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email alert ❌ (Check internet connection or SMTP credentials)", e)
            false
        }
    }

    companion object {
        private const val TAG = "EmailAlertManager"
    }
}
