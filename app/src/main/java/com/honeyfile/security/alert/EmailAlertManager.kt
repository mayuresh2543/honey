package com.honeyfile.security.alert

import android.content.Context
import android.util.Log
import com.honeyfile.security.auth.FaceAuthManager
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

data class EmailSendResult(
    val isSuccess: Boolean,
    val message: String,
    val recipients: List<String>
)

class EmailAlertManager {

    // Default sender credentials for SMTP dispatch
    private val senderEmail = "rjcanirudh11sci326@gmail.com"
    private val senderPassword = "shgmsysblwaxqcia"
    private val defaultReceiverEmail = "rjcanirudh11sci326@gmail.com"

    suspend fun sendAlert(
        context: Context? = null,
        subject: String,
        body: String,
        imageFile: File? = null,
        telemetry: DeviceTelemetry? = null
    ): Boolean {
        val result = sendAlertDetailed(context, subject, body, imageFile, telemetry)
        return result.isSuccess
    }

    suspend fun sendAlertDetailed(
        context: Context? = null,
        subject: String,
        body: String,
        imageFile: File? = null,
        telemetry: DeviceTelemetry? = null
    ): EmailSendResult = withContext(Dispatchers.IO) {
        try {
            val targetRecipients = if (context != null) {
                val faceAuthManager = FaceAuthManager(context)
                val list = faceAuthManager.getNotificationRecipients()
                if (list.isNotEmpty()) list else listOf(defaultReceiverEmail)
            } else {
                listOf(defaultReceiverEmail)
            }

            Log.d(TAG, "Attempting email dispatch to recipients: $targetRecipients")

            val recipientAddresses = targetRecipients.map { InternetAddress(it) }.toTypedArray()

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "465")
                put("mail.smtp.socketFactory.port", "465")
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                put("mail.smtp.socketFactory.fallback", "false")
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.connectiontimeout", "15000")
                put("mail.smtp.timeout", "15000")
                put("mail.smtp.writetimeout", "15000")
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

                val multipart = MimeMultipart("mixed")

                val telemetryHtml = if (telemetry != null) """
                    <div style="margin-top: 15px; background-color: #1e293b; padding: 14px; border-radius: 10px; border-left: 4px solid #38bdf8;">
                        <h3 style="color: #38bdf8; margin: 0 0 8px 0; font-size: 14px;">📍 Device & Location Telemetry</h3>
                        <p style="font-size: 13px; margin: 4px 0; color: #e2e8f0;"><b>Location:</b> ${if (telemetry.googleMapsUrl != null) "<a href='${telemetry.googleMapsUrl}' style='color: #38bdf8; font-weight: bold;'>Open Google Maps (${telemetry.latitude}, ${telemetry.longitude})</a>" else "GPS Location Unavailable"}</p>
                        <p style="font-size: 13px; margin: 4px 0; color: #e2e8f0;"><b>Network IP:</b> ${telemetry.ipAddress} &nbsp;|&nbsp; <b>Wi-Fi SSID:</b> ${telemetry.wifiSsid}</p>
                        <p style="font-size: 13px; margin: 4px 0; color: #e2e8f0;"><b>Battery Status:</b> ${telemetry.batteryPercentage}% ${if (telemetry.isCharging) "(⚡ Charging)" else "(Discharging)"}</p>
                    </div>
                """.trimIndent() else ""

                val htmlBody = """
                    <div style="font-family: Arial, sans-serif; background-color: #0f172a; color: #f8fafc; padding: 20px; border-radius: 12px;">
                        <h2 style="color: #ef4444; margin-top: 0;">🚨 Intruder Breach Alert</h2>
                        <p style="font-size: 15px; color: #cbd5e1;">$body</p>
                        $telemetryHtml
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
                    val inlinePart = MimeBodyPart().apply {
                        val source = FileDataSource(imageFile)
                        dataHandler = DataHandler(source)
                        setHeader("Content-ID", "<intruder_photo>")
                        disposition = MimeBodyPart.INLINE
                        fileName = imageFile.name
                    }
                    multipart.addBodyPart(inlinePart)

                    val attachPart = MimeBodyPart().apply {
                        val source = FileDataSource(imageFile)
                        dataHandler = DataHandler(source)
                        disposition = MimeBodyPart.ATTACHMENT
                        fileName = "INTRUDER_EVIDENCE_${imageFile.name}"
                    }
                    multipart.addBodyPart(attachPart)
                }

                setContent(multipart)
            }

            Transport.send(message)
            val successMsg = "Email alert sent successfully to ${targetRecipients.joinToString(", ")}"
            Log.d(TAG, "$successMsg 📧")
            EmailSendResult(isSuccess = true, message = successMsg, recipients = targetRecipients)
        } catch (e: Exception) {
            val errorMsg = "SMTP Dispatch Error: ${e.localizedMessage ?: e.message ?: "Connection failed"}"
            Log.e(TAG, "Failed to send email alert ❌: $errorMsg", e)
            EmailSendResult(isSuccess = false, message = errorMsg, recipients = emptyList())
        }
    }

    companion object {
        private const val TAG = "EmailAlertManager"
    }
}
