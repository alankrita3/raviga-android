package com.raviga.app.data.files

import androidx.core.graphics.createBitmap
import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.raviga.app.data.api.FileTypes
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads a document's text on the phone (contract v0.6: files never leave it).
 * Text PDFs through PDFBox; scanned PDFs through ML Kit's on-device text
 * recogniser; Word, RTF, text and Markdown by [PlainFormats]. The file itself
 * is deleted by the caller as soon as it has been read.
 */
class TextExtractor(private val context: Context) {

    data class Result(val text: String, val pageCount: Int?, val truncated: Boolean, val notice: String?)

    class Unreadable(message: String) : Exception(message)

    @Volatile private var pdfBoxReady = false

    suspend fun extract(
        file: File,
        contentType: String,
        maxChars: Int,
        maxPages: Int,
        onProgress: (String) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        val read = when (contentType) {
            FileTypes.PDF -> readPdf(file, maxPages, onProgress)
            FileTypes.DOCX -> PlainFormats.Read(PlainFormats.docx(file), null, false)
            FileTypes.RTF -> PlainFormats.Read(PlainFormats.rtf(PlainFormats.decode(file.readBytes())), null, false)
            else -> PlainFormats.Read(PlainFormats.decode(file.readBytes()), null, false)
        }
        val text = PlainFormats.tidy(read.text)
        if (text.isBlank()) {
            throw Unreadable("We couldn't find any text in that file. If it's a photo or locked with a password, type or paste the important parts instead.")
        }
        val cut = text.length > maxChars
        val kept = if (cut) text.take(maxChars).substringBeforeLast('\n', text.take(maxChars)) else text
        val truncated = cut || read.pagesCut
        Result(kept, read.pageCount, truncated, if (truncated) "Only the first part fit. Add the rest as another note." else null)
    }

    private suspend fun readPdf(file: File, maxPages: Int, onProgress: (String) -> Unit): PlainFormats.Read {
        if (!pdfBoxReady) { PDFBoxResourceLoader.init(context.applicationContext); pdfBoxReady = true }
        val (text, pages) = try {
            PDDocument.load(file).use { doc ->
                if (doc.isEncrypted) throw Unreadable("That PDF is locked with a password. Save an unlocked copy, or paste the text instead.")
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                    startPage = 1
                    endPage = minOf(doc.numberOfPages, maxPages)
                }
                stripper.getText(doc) to doc.numberOfPages
            }
        } catch (e: Unreadable) {
            throw e
        } catch (e: Exception) {
            throw Unreadable("Couldn't open that PDF. Try saving it again, or paste the text instead.")
        }
        // A scan has pages but (almost) no text layer: read the page images on the device.
        val letters = text.count { it.isLetter() }
        if (letters < 40 * minOf(pages, maxPages).coerceAtLeast(1) / 4) {
            val ocr = ocrPdf(file, maxPages, onProgress)
            if (ocr.isNotBlank()) return PlainFormats.Read(ocr, pages, pages > maxPages)
        }
        return PlainFormats.Read(text, pages, pages > maxPages)
    }

    /** ML Kit's on-device recogniser over rendered pages. Nothing is uploaded. */
    private suspend fun ocrPdf(file: File, maxPages: Int, onProgress: (String) -> Unit): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val count = minOf(renderer.pageCount, maxPages)
                    val out = StringBuilder()
                    for (i in 0 until count) {
                        onProgress("Reading page ${i + 1} of $count")
                        renderer.openPage(i).use { page ->
                            // About 150 dpi: enough for print, small enough for memory.
                            val scale = 150f / 72f
                            val w = (page.width * scale).toInt().coerceIn(1, 2400)
                            val h = (page.height * scale).toInt().coerceIn(1, 3400)
                            val bitmap = createBitmap(w, h)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val result = runCatching { recognizer.process(InputImage.fromBitmap(bitmap, 0)).await() }.getOrNull()
                            bitmap.recycle()
                            result?.textBlocks?.forEach { block -> out.append(block.text).append("\n\n") }
                        }
                    }
                    return out.toString()
                }
            }
        } catch (e: Exception) {
            return ""
        } finally {
            recognizer.close()
        }
    }
}
