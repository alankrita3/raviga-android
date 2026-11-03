package com.raviga.app.data.files

import android.content.Context
import android.net.Uri
import com.raviga.app.data.drafts.DraftStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * "Download my data" (DPDP), local-first: the server's zip holds only what the
 * server holds, so the drafts that exist only on this phone are added here as
 * drafts/<id>.json before the file is saved where the client chooses.
 */
class ExportBuilder(
    private val context: Context,
    private val http: OkHttpClient,
    private val drafts: DraftStore,
) {
    /** Removes a file the save picker made that will not be filled (no link, or the save failed). */
    suspend fun discard(target: Uri) = withContext(Dispatchers.IO) {
        runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, target) }
        Unit
    }

    /** A save that fails part way leaves no empty or half-written zip behind ([discard]). */
    suspend fun write(serverZipUrl: String, target: Uri) = try {
        writeZip(serverZipUrl, target)
    } catch (e: Exception) {
        discard(target)
        throw e
    }

    private suspend fun writeZip(serverZipUrl: String, target: Uri) = withContext(Dispatchers.IO) {
        (context.contentResolver.openOutputStream(target, "w") ?: throw com.raviga.app.data.UserFacingException("Couldn't open the file to save into.")).use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                if (!serverZipUrl.startsWith("demo:")) http.newCall(Request.Builder().url(serverZipUrl).build()).execute().use { response ->
                    if (!response.isSuccessful) throw com.raviga.app.data.UserFacingException("The download link has expired. Ask for a new one.")
                    val body = response.body ?: throw com.raviga.app.data.UserFacingException("The download came back empty. Ask for a new link.")
                    ZipInputStream(body.byteStream()).use { input ->
                        while (true) {
                            val entry = input.nextEntry ?: break
                            if (entry.name.startsWith("drafts/")) continue
                            zip.putNextEntry(ZipEntry(entry.name))
                            if (!entry.isDirectory) input.copyTo(zip)
                            zip.closeEntry()
                        }
                    }
                }
                val local = drafts.exportJson()
                zip.putNextEntry(ZipEntry("drafts/README.txt"))
                zip.write(README.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                local.forEach { (id, json) ->
                    zip.putNextEntry(ZipEntry("drafts/$id.json"))
                    zip.write(json.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                // R-094 P2-13: drafts this build couldn't open go in too, exactly as stored.
                drafts.exportUnreadable().forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry("drafts/unreadable/$name"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    private companion object {
        const val README = "These drafts were kept only on your phone and were never sent to Raviga's servers.\n" +
            "Each file holds a draft's notes (the text of what you said, typed or uploaded), every version of its brief, and its quote if it had one.\n" +
            "The unreadable folder, if there is one, holds drafts this version of the app couldn't open, exactly as they were stored " +
            "(.sealed files are still encrypted with this phone's key).\n"
    }
}
