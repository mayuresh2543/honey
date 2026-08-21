package com.honeyfile.security.decoy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates realistic multi-format decoy (honeyfile) documents using Android SDK only.
 * No third-party libraries — uses PdfDocument, ZipOutputStream (OpenXML), SQLiteDatabase.
 */
class DecoyGeneratorEngine(private val context: Context) {

    data class DecoyTemplate(
        val fileName: String,
        val mimeType: String,
        val category: DecoyCategory,
        val displayName: String,
        val emoji: String
    )

    enum class DecoyCategory(val label: String) {
        PDF("PDFs"),
        OFFICE("Office Docs"),
        DATABASE("Dev & Database")
    }

    val templates = listOf(
        DecoyTemplate("Chase_Premier_Statement_Q3_2026.pdf",   "application/pdf",   DecoyCategory.PDF,      "Bank Account Statement",    "🏦"),
        DecoyTemplate("NDA_Confidential_Agreement_2026.pdf",   "application/pdf",   DecoyCategory.PDF,      "Non-Disclosure Agreement",  "📋"),
        DecoyTemplate("ITR_2025_Tax_Assessment.pdf",           "application/pdf",   DecoyCategory.PDF,      "Tax Return Filing",         "🧾"),
        DecoyTemplate("Crypto_Seed_Backup_Ledger.docx",        "application/octet-stream", DecoyCategory.OFFICE, "Crypto Seed Backup",   "🔑"),
        DecoyTemplate("Payroll_Q3_2026_Confidential.xlsx",     "application/octet-stream", DecoyCategory.OFFICE, "Executive Payroll",    "💰"),
        DecoyTemplate("gcp_service_account_prod.json",         "application/json",  DecoyCategory.DATABASE, "GCP Service Account Keys",  "☁️"),
        DecoyTemplate("app_secrets.env",                       "text/plain",        DecoyCategory.DATABASE, "App Environment Secrets",   "🔐"),
        DecoyTemplate("database_backup.sql",                   "text/plain",        DecoyCategory.DATABASE, "SQL Database Backup",       "🗄️")
    )

    /**
     * Deploy a single decoy template into the SAF folder at [folderUri].
     * Returns true if file was created, false if it already existed.
     */
    fun deploy(template: DecoyTemplate, folderUri: Uri): Boolean {
        val docDir = DocumentFile.fromTreeUri(context, folderUri) ?: return false
        if (!docDir.exists()) return false

        // Skip if already deployed
        if (docDir.findFile(template.fileName) != null) {
            Log.d(TAG, "Skipping ${template.fileName} — already exists")
            return false
        }

        val bytes = when {
            template.fileName.endsWith(".pdf")  -> generatePdf(template)
            template.fileName.endsWith(".docx") -> generateDocx(template)
            template.fileName.endsWith(".xlsx") -> generateXlsx(template)
            template.fileName.endsWith(".json") -> generateJson(template)
            template.fileName.endsWith(".env")  -> generateEnv(template)
            template.fileName.endsWith(".sql")  -> generateSqlDump(template)
            else -> template.fileName.toByteArray()
        }

        val newFile = docDir.createFile(template.mimeType, template.fileName) ?: return false
        context.contentResolver.openOutputStream(newFile.uri, "wt")?.use { out ->
            out.write(bytes)
        }
        Log.i(TAG, "Deployed decoy: ${template.fileName} (${bytes.size} bytes)")
        return true
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PDF Generator — android.graphics.pdf.PdfDocument
    // ──────────────────────────────────────────────────────────────────────────

    private fun generatePdf(template: DecoyTemplate): ByteArray {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 at 72dpi
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#1a1a2e")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor("#444444")
            textSize = 12f
        }
        val valuePaint = Paint().apply {
            color = Color.BLACK
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#cccccc")
            strokeWidth = 1f
        }
        val redactPaint = Paint().apply {
            color = Color.BLACK
            textSize = 13f
        }

        when {
            template.fileName.contains("Statement") -> drawBankStatement(canvas, titlePaint, labelPaint, valuePaint, dividerPaint)
            template.fileName.contains("NDA")       -> drawNda(canvas, titlePaint, labelPaint, valuePaint, dividerPaint, redactPaint)
            template.fileName.contains("ITR")       -> drawTaxReturn(canvas, titlePaint, labelPaint, valuePaint, dividerPaint)
        }

        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private fun drawBankStatement(canvas: Canvas, title: Paint, label: Paint, value: Paint, divider: Paint) {
        canvas.drawText("CHASE PREMIER BANKING", 50f, 60f, title)
        canvas.drawText("Account Statement — Q3 2026 (Jul–Sep)", 50f, 85f, label)
        canvas.drawLine(50f, 95f, 545f, 95f, divider)

        val rows = listOf(
            "Account Holder" to "Mayuresh Nanal",
            "Account Number" to "••••  ••••  7842",
            "Statement Period" to "01 Jul 2026 – 30 Sep 2026",
            "Opening Balance" to "\$84,293.50",
            "Total Credits" to "\$12,400.00",
            "Total Debits" to "\$3,820.75",
            "Closing Balance" to "\$92,872.75",
            "Currency" to "USD",
            "Branch" to "Chase Manhattan, New York"
        )
        var y = 125f
        for ((l, v) in rows) {
            canvas.drawText(l, 50f, y, label)
            canvas.drawText(v, 300f, y, value)
            y += 28f
        }

        canvas.drawLine(50f, y + 5f, 545f, y + 5f, divider)
        y += 30f
        canvas.drawText("Recent Transactions", 50f, y, title.apply { textSize = 16f })
        y += 25f

        val txns = listOf(
            "15 Jul" to "Salary Deposit — Honeyfile Systems Ltd" to "+\$8,400.00",
            "18 Jul" to "AWS Cloud Services — Annual" to "-\$1,200.00",
            "22 Jul" to "Transfer to Savings Account" to "-\$2,000.00",
            "05 Aug" to "Salary Deposit — Honeyfile Systems Ltd" to "+\$4,000.00",
            "14 Aug" to "Google Workspace Pro" to "-\$620.75"
        )
        for ((dateDesc, amount) in txns) {
            val (date, desc) = dateDesc
            canvas.drawText(date, 50f, y, label)
            canvas.drawText(desc, 120f, y, label)
            canvas.drawText(amount, 450f, y, value.apply { color = if (amount.startsWith("+")) Color.parseColor("#2e7d32") else Color.parseColor("#c62828") })
            y += 24f
        }

        canvas.drawText("CONFIDENTIAL — For Account Holder Use Only", 50f, 800f, label.apply { color = Color.RED; textSize = 10f })
    }

    private fun drawNda(canvas: Canvas, title: Paint, label: Paint, value: Paint, divider: Paint, redact: Paint) {
        canvas.drawText("NON-DISCLOSURE AGREEMENT", 100f, 70f, title)
        canvas.drawText("This Agreement is entered into as of 01 January 2026", 50f, 100f, label)
        canvas.drawLine(50f, 110f, 545f, 110f, divider)

        val paras = listOf(
            "PARTIES: This Non-Disclosure Agreement (the \"Agreement\") is entered into",
            "between Honeyfile Systems Ltd. (\"Disclosing Party\") and Mayuresh Nanal",
            "(\"Receiving Party\"), collectively referred to as the \"Parties\".",
            "",
            "CONFIDENTIAL INFORMATION: The Receiving Party agrees not to disclose,",
            "publish, or use any proprietary data, trade secrets, software source code,",
            "client records, financial records, or intellectual property belonging to the",
            "Disclosing Party without prior written consent.",
            "",
            "TERM: This Agreement shall remain in effect for a period of five (5) years",
            "from the Effective Date or until written termination by either Party.",
            "",
            "PENALTY FOR BREACH: Unauthorized disclosure shall result in liquidated",
            "damages of USD 500,000 and potential criminal liability under the applicable",
            "computer fraud and intellectual property statutes.",
            "",
            "GOVERNING LAW: This Agreement shall be governed by the laws of the",
            "State of New York, United States of America.",
            "",
            "Signed:  ________________________        Date: ______________",
            "         Mayuresh Nanal (Receiving Party)",
            "",
            "Signed:  ________________________        Date: ______________",
            "         Authorized Signatory — Honeyfile Systems Ltd."
        )
        var y = 140f
        for (line in paras) {
            canvas.drawText(line, 50f, y, label)
            y += 22f
        }
        canvas.drawText("CONFIDENTIAL LEGAL DOCUMENT — DO NOT DISTRIBUTE", 50f, 810f, label.apply { color = Color.RED; textSize = 10f })
    }

    private fun drawTaxReturn(canvas: Canvas, title: Paint, label: Paint, value: Paint, divider: Paint) {
        canvas.drawText("INCOME TAX RETURN — ASSESSMENT YEAR 2025-26", 50f, 65f, title.apply { textSize = 18f })
        canvas.drawText("Form ITR-2 | Individual & HUF (Not having income from Business)", 50f, 88f, label)
        canvas.drawLine(50f, 98f, 545f, 98f, divider)

        val rows = listOf(
            "Taxpayer Name"      to "Mayuresh Nanal",
            "PAN"                to "ABCPN1234K",
            "Assessment Year"    to "2025-26",
            "Filing Status"      to "Original Return",
            "Residential Status" to "Resident Individual",
            "Gross Total Income" to "₹ 18,40,000",
            "Total Deductions"   to "₹  1,50,000",
            "Taxable Income"     to "₹ 16,90,000",
            "Tax Payable"        to "₹  2,87,500",
            "TDS Deducted"       to "₹  2,80,000",
            "Tax Refund Due"     to "₹      7,500",
            "Bank A/c (Refund)"  to "HDFC Bank — ••••4892"
        )
        var y = 130f
        for ((l, v) in rows) {
            canvas.drawText(l, 50f, y, label)
            canvas.drawText(v, 280f, y, value)
            y += 28f
        }
        canvas.drawLine(50f, y + 5f, 545f, y + 5f, divider)
        canvas.drawText("Acknowledgement No: 382841920250815    E-Filed: 15-Aug-2025", 50f, y + 25f, label)
        canvas.drawText("CONFIDENTIAL TAX DOCUMENT", 50f, 810f, label.apply { color = Color.RED; textSize = 10f })
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DOCX Generator — Minimal OpenXML Zip
    // ──────────────────────────────────────────────────────────────────────────

    private fun generateDocx(template: DecoyTemplate): ByteArray {
        val content = buildString {
            appendLine("CRYPTO SEED BACKUP — LEDGER NANO X")
            appendLine("STRICTLY CONFIDENTIAL — DO NOT PHOTOGRAPH OR SHARE")
            appendLine()
            appendLine("Wallet 1: Bitcoin (BTC)")
            appendLine("  Seed Phrase: abandon ability able about above absent absorb abstract absurd abuse access accident")
            appendLine("  Derivation Path: m/44'/0'/0'")
            appendLine("  Address: 1A1zP1eP5QGefi2DMPTfTL5SLmv7Divf7Na")
            appendLine()
            appendLine("Wallet 2: Ethereum (ETH)")
            appendLine("  Seed Phrase: legal winner thank year wave sausage worth useful legal winner thank yellow")
            appendLine("  Derivation Path: m/44'/60'/0'")
            appendLine("  Address: 0x71C7656EC7ab88b098defB751B7401B5f6d8976F")
            appendLine()
            appendLine("Wallet 3: Solana (SOL)")
            appendLine("  Seed Phrase: letter advice cage absurd amount doctor acoustic avoid letter advice cage above")
            appendLine("  Derivation Path: m/44'/501'/0'")
            appendLine()
            appendLine("Hardware PIN: 4829")
            appendLine("Recovery PIN: 7731")
            appendLine()
            appendLine("Last Updated: 01-Aug-2026")
            appendLine("Backup Created By: Mayuresh Nanal")
        }
        return buildDocx(content)
    }

    private fun buildDocx(textContent: String): ByteArray {
        val paragraphs = textContent.lines().joinToString("") { line ->
            "<w:p><w:r><w:t xml:space=\"preserve\">${escapeXml(line)}</w:t></w:r></w:p>"
        }
        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>$paragraphs<w:sectPr/></w:body>
</w:document>"""

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun addEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            addEntry("[Content_Types].xml", contentTypesXml)
            addEntry("_rels/.rels", relsXml)
            addEntry("word/document.xml", documentXml)
        }
        return out.toByteArray()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // XLSX Generator — Minimal OpenXML Zip
    // ──────────────────────────────────────────────────────────────────────────

    private fun generateXlsx(template: DecoyTemplate): ByteArray {
        val employees = listOf(
            listOf("Mayuresh Nanal", "Engineering Lead", "₹18,00,000", "₹1,50,000", "₹19,50,000"),
            listOf("Anirudh Kewat", "Sr. Developer", "₹14,00,000", "₹1,20,000", "₹15,20,000"),
            listOf("Rohan Sharma", "Product Manager", "₹16,50,000", "₹1,40,000", "₹17,90,000"),
            listOf("Priya Desai", "UI/UX Designer", "₹12,00,000", "₹1,00,000", "₹13,00,000"),
            listOf("Arjun Mehta", "DevOps Engineer", "₹15,00,000", "₹1,25,000", "₹16,25,000")
        )

        val headers = listOf("Employee Name", "Designation", "Base Salary", "Allowances", "CTC")
        fun row(rowNum: Int, cells: List<String>): String = buildString {
            append("<row r=\"$rowNum\">")
            cells.forEachIndexed { i, v ->
                val col = ('A' + i)
                append("<c r=\"$col$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(v)}</t></is></c>")
            }
            append("</row>")
        }

        val sheetData = buildString {
            append("<sheetData>")
            append(row(1, headers))
            employees.forEachIndexed { i, emp -> append(row(i + 2, emp)) }
            append("</sheetData>")
        }

        val sheetXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$sheetData</worksheet>"""

        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Payroll Q3 2026" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val wbRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun addEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            addEntry("[Content_Types].xml", contentTypesXml)
            addEntry("_rels/.rels", relsXml)
            addEntry("xl/workbook.xml", workbookXml)
            addEntry("xl/_rels/workbook.xml.rels", wbRelsXml)
            addEntry("xl/worksheets/sheet1.xml", sheetXml)
        }
        return out.toByteArray()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // JSON — GCP Service Account
    // ──────────────────────────────────────────────────────────────────────────

    private fun generateJson(template: DecoyTemplate): ByteArray = """
{
  "type": "service_account",
  "project_id": "honeyfile-prod-secure-2026",
  "private_key_id": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
  "private_key": "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA2a2rwplBQLF29amygykEMmYz0+Kcj3bKBp29Si3qRQHCTSF\nnrZECKDFBcWGaGVJHPtFxNJPmgOp6HIm+GThNJ0M1zMT7hOhI0HKGAhDFgEFHBa\nXPKWBKwVFDENqiqbbQLV0oNMxMZ4PVBnUKeQnHSc6CWhbSqzUoMlmGKNFKbWAMLT\n0000000000000000000000000000000000000000000000000000000000000000\nAQIDBAUGBwgBAgMEBQYHCAECAwQFBgcIAQIDBAUGBwgBAgMEBQYHCA==\n-----END RSA PRIVATE KEY-----\n",
  "client_email": "honeyfile-sa@honeyfile-prod-secure-2026.iam.gserviceaccount.com",
  "client_id": "102938475610293847561",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/honeyfile-sa%40honeyfile-prod-secure-2026.iam.gserviceaccount.com",
  "universe_domain": "googleapis.com",
  "stripe_secret_key": "sk_live_51HbN3rKZ9v7TxGg9bQpTFjkr8PLrKBrn4dA3MhZ5qLT7EJHYe8Jv9QLBC2gL",
  "aws_access_key_id": "AKIAIOSFODNN7EXAMPLE",
  "aws_secret_access_key": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
}
""".trimIndent().toByteArray()

    // ──────────────────────────────────────────────────────────────────────────
    // ENV — App Secrets
    // ──────────────────────────────────────────────────────────────────────────

    private fun generateEnv(template: DecoyTemplate): ByteArray = """
# Honeyfile Systems — Production Environment Secrets
# CONFIDENTIAL: Do NOT commit this file to version control

APP_ENV=production
APP_SECRET_KEY=hf_sk_prod_9f2a3c5e7b1d4f6a8c0e2a4b6c8d0e2f4a6b8c0d

# Database
DB_HOST=db.internal.honeyfile.systems
DB_PORT=5432
DB_NAME=honeyfile_prod
DB_USER=hf_admin
DB_PASSWORD=Hf@Pr0d!2026#Secure

# Firebase
FIREBASE_API_KEY=AIzaSyD-9tSrKe_hkJ_gBqP0FAKEKEY12345
FIREBASE_PROJECT_ID=honeyfile-prod-secure-2026
FIREBASE_AUTH_DOMAIN=honeyfile-prod-secure-2026.firebaseapp.com

# AWS S3
AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
AWS_REGION=ap-south-1
S3_BUCKET=honeyfile-prod-vault-backup

# Stripe
STRIPE_SECRET_KEY=sk_live_51HbN3rKZ9v7TxGg9bQpTFjkr8PLrK
STRIPE_WEBHOOK_SECRET=whsec_fakewebhookkey1234567890abcdef

# JWT
JWT_SECRET=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.FAKE_SECRET_PAYLOAD.FAKE_SIGNATURE
JWT_EXPIRES_IN=7d

# SMTP Alert
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=alerts@honeyfile.systems
SMTP_PASS=Hf!Smtp2026${'$'}Pass
""".trimIndent().toByteArray()

    // ──────────────────────────────────────────────────────────────────────────
    // SQL Dump — Database Backup
    // ──────────────────────────────────────────────────────────────────────────

    private fun generateSqlDump(template: DecoyTemplate): ByteArray {
        val content = """
            -- ==========================================================
            -- Honeyfile Enterprise Security - Database Dump
            -- Export Date: 2026-08-15 03:00:00 UTC
            -- Database: internal_production_vault
            -- ==========================================================

            SET FOREIGN_KEY_CHECKS = 0;
            DROP TABLE IF EXISTS `system_credentials`;

            CREATE TABLE `system_credentials` (
                `id` INT AUTO_INCREMENT PRIMARY KEY,
                `service` VARCHAR(100) NOT NULL,
                `username` VARCHAR(150) NOT NULL,
                `password_hash` VARCHAR(255) NOT NULL,
                `api_secret` VARCHAR(255) DEFAULT NULL,
                `environment` VARCHAR(50) NOT NULL,
                `notes` TEXT
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

            INSERT INTO `system_credentials` (`service`, `username`, `password_hash`, `api_secret`, `environment`, `notes`) VALUES
            ('AWS Root Management', 'honeyfile-admin', 'Aws!R00t2026#Secure', 'AKIAIOSFODNN7EXAMPLE', 'production', 'MFA Hardware Token Bound'),
            ('Production PostgreSQL Master', 'hf_admin', 'Hf@Pr0d!2026#Secure', 'pg_live_key_99812401', 'production', 'Primary Cluster IP: 10.0.1.100'),
            ('Stripe Payment Gateway', 'billing@honeyfile.systems', 'Str1pe!2026${'$'}Prod', 'sk_live_51Mz9901849182347182934', 'production', 'Live charge webhook active'),
            ('Cloudflare Edge Security', 'admin@honeyfile.systems', 'Cf!Dns2026#Zone', 'cf_api_991827461928401928374619', 'production', 'Full SSL Strict & WAF'),
            ('Firebase Admin Console', 'mayuresh@honeyfile.systems', 'Fb!C0ns0le2026${'$'}', 'sec_sa_99812401827361928374', 'production', 'Cloud Firestore Vault Sync'),
            ('GitHub Enterprise Bot', 'honeyfile-ci-bot', 'GhB0t!2026#Token', 'ghp_9918237461928374619283746192', 'production', 'CI/CD Deployment Token');

            -- Dump completed on 2026-08-15 03:00:01 UTC
        """.trimIndent()
        return content.toByteArray(Charsets.UTF_8)
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    companion object {
        private const val TAG = "DecoyGeneratorEngine"

        val KNOWN_DECOY_FILENAMES = setOf(
            "Chase_Premier_Statement_Q3_2026.pdf",
            "NDA_Confidential_Agreement_2026.pdf",
            "ITR_2025_Tax_Assessment.pdf",
            "Crypto_Seed_Backup_Ledger.docx",
            "Payroll_Q3_2026_Confidential.xlsx",
            "gcp_service_account_prod.json",
            "app_secrets.env",
            "database_backup.sql",
            "internal_credentials.db"
        )

        fun isDecoyFileName(fileName: String): Boolean {
            return KNOWN_DECOY_FILENAMES.any { it.equals(fileName, ignoreCase = true) }
        }
    }
}
