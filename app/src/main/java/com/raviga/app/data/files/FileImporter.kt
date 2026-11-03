package com.raviga.app.data.files

import kotlinx.coroutines.ensureActive
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.raviga.app.data.api.FileTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copies a document the client picked (a content:// Uri from the system picker)
 * into app cache, so its exact size is known before it is read on the phone. The
 * copy is deleted as soon as it has been read, or at the next start ([clearStale]).
 */
class FileImporter(private val context: Context) {

    data class Picked(val file: File, val name: String, val bytes: Long, val contentType: String)

    sealed class Problem(message: String) : com.raviga.app.data.UserFacingException(message) {
        class Unsupported : Problem("Raviga can read PDF, Word (.docx), text, Markdown and RTF files. Save it as one of those and try again.")
        class TooLarge(maxBytes: Long) : Problem("That file is over ${maxBytes / 1_048_576} MB. Upload a shorter version, or paste the important parts.")
        class Unreadable : Problem("Couldn't open that file. Try again, or choose another copy.")
    }

    /** [onName]: the file's name, as soon as it's known (R-094 P2-16: "Getting <name>" while a cloud copy downloads). */
    suspend fun import(uri: Uri, maxBytes: Long, accepted: List<String>, onName: (String) -> Unit = {}): Picked = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "Document"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null).use { c ->
                if (c != null && c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { i -> size = c.getLong(i) }
                }
            }
        }
        onName(name)
        val type = FileTypes.resolve(name, resolver.getType(uri), accepted) ?: throw Problem.Unsupported()
        if (size > maxBytes) throw Problem.TooLarge(maxBytes)

        val dir = File(context.cacheDir, UPLOADS).apply { mkdirs() }
        val out = File(dir, UUID.randomUUID().toString())
        try {
            (resolver.openInputStream(uri) ?: throw Problem.Unreadable()).use { src ->
                out.outputStream().use { dst ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        ensureActive()   // R-094 P2-16: Stop takes effect between chunks
                        val n = src.read(buffer)
                        if (n < 0) break
                        total += n
                        // Providers can under-report size; stop copying once it's clearly too big.
                        if (total > maxBytes) throw Problem.TooLarge(maxBytes)
                        dst.write(buffer, 0, n)
                    }
                }
            }
        } catch (e: Problem) {
            out.delete(); throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            out.delete(); throw e   // stopped: no copy kept, and not "unreadable"
        } catch (e: Exception) {
            out.delete(); throw Problem.Unreadable()
        }
        if (out.length() == 0L) { out.delete(); throw Problem.Unreadable() }
        Picked(out, name, out.length(), type)
    }

    /**
     * Copies left behind when the app was ended while a document was being read (the read's own `finally` never ran),
     * so the file doesn't stay on the phone. [before]: only copies made before then; at start-up that is the process
     * start, so a copy a picker result is making right now is never touched. "Delete my data" clears them all.
     */
    fun clearStale(before: Long = Long.MAX_VALUE) = clearCopies(File(context.cacheDir, UPLOADS), before)

    companion object {
        private const val UPLOADS = "uploads"

        /** Deletes every file in [dir] last written before [before]; the number deleted. */
        fun clearCopies(dir: File, before: Long): Int =
            dir.listFiles()?.count { it.isFile && it.lastModified() < before && it.delete() } ?: 0
    }
}
