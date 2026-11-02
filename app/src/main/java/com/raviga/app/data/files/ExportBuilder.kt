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
    suspend fun write(serverZipUrl: String, target: Uri) = withContext(Dispatchers.IO) {
        (context.contentResolver.openOutputStream(target, "w") ?: throw IOException("Couldn't open the file to save into.")).use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                if (!serverZipUrl.startsWith("demo:")) http.newCall(Request.Builder().url(serverZipUrl).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("The download link has expired. Ask for a new one.")
                    val body = response.body ?: throw IOException("The download came back empty. Ask for a new link.")
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
            }
        }
    }

    private companion object {
        const val README = "These drafts were kept only on your phone and were never sent to Raviga's servers.\n" +
            "Each file holds a draft's notes (the text of what you said, typed or uploaded), every version of its brief, and its quote if it had one.\n"
    }
}
