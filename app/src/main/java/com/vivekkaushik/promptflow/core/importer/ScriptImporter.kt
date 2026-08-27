package com.vivekkaushik.promptflow.core.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.zip.ZipInputStream

/**
 * Import (spec §04): ACTION_OPEN_DOCUMENT — .txt / .md read directly,
 * .pdf via pdfbox-android text strip, .docx via zip + word/document.xml parse.
 */
object ScriptImporter {

    val MIME_TYPES = arrayOf(
        "text/plain",
        "text/markdown",
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    data class Imported(val title: String, val body: String)

    suspend fun import(context: Context, uri: Uri): Imported = withContext(Dispatchers.IO) {
        val name = displayName(context, uri) ?: "Imported script"
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = context.contentResolver.getType(uri) ?: ""
        val body = when {
            mime == "application/pdf" || ext == "pdf" -> readPdf(context, uri)
            mime.endsWith("wordprocessingml.document") || ext == "docx" -> readDocx(context, uri)
            else -> readText(context, uri)
        }
        Imported(title = name.substringBeforeLast('.').ifBlank { "Imported script" }, body = body.trim())
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun readText(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""

    private fun readPdf(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc -> PDFTextStripper().getText(doc) }
        } ?: ""

    /** Minimal .docx text extraction: w:t runs joined, w:p as paragraph breaks. */
    private fun readDocx(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val sb = StringBuilder()
                        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
                        parser.setInput(zip, "UTF-8")
                        var event = parser.eventType
                        var inText = false
                        while (event != XmlPullParser.END_DOCUMENT) {
                            when (event) {
                                XmlPullParser.START_TAG -> if (parser.name == "t") inText = true
                                XmlPullParser.TEXT -> if (inText) sb.append(parser.text)
                                XmlPullParser.END_TAG -> when (parser.name) {
                                    "t" -> inText = false
                                    "p" -> sb.append('\n')
                                }
                            }
                            event = parser.next()
                        }
                        return sb.toString()
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return ""
    }
}
