package com.raviga.app.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import java.io.File

/**
 * JSON-on-disk cache for the last known config, project list and open
 * projects, so the app opens instantly and reads work offline. The backend is
 * the source of truth; nothing here is ever written back to it.
 */
class CacheStore(private val dir: File, private val json: Json) {

    constructor(context: Context, json: Json) : this(File(context.filesDir, "cache"), json)

    init { dir.mkdirs() }

    /** Writes are small and rare; one lock keeps two writers of the same file from interleaving. */
    private val writeLock = Mutex()

    suspend fun <T> read(name: String, deserializer: DeserializationStrategy<T>): T? =
        withContext(Dispatchers.IO) {
            val file = File(dir, "$name.json")
            if (!file.exists()) return@withContext null
            runCatching { json.decodeFromString(deserializer, file.readText()) }.getOrNull()
        }

    suspend fun <T> write(name: String, serializer: SerializationStrategy<T>, value: T) {
        val text = json.encodeToString(serializer, value)
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                val file = File(dir, "$name.json")
                val tmp = File(dir, "$name.json.${java.util.UUID.randomUUID()}.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) {
                    file.delete()
                    if (!tmp.renameTo(file)) tmp.delete()
                }
            }
        }
    }

    suspend fun delete(name: String) = writeLock.withLock {
        withContext(Dispatchers.IO) { File(dir, "$name.json").delete() }
    }

    suspend fun clearAll() = writeLock.withLock {
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }
    }

    /** Drops the project list, projects, documents and credits; keeps config, me and demo state. */
    suspend fun clearProjects() = writeLock.withLock {
        withContext(Dispatchers.IO) {
            dir.listFiles()?.filter { f ->
                val n = f.name
                n == "$PROJECTS.json" || n == "$CREDITS.json" || n.startsWith("project_") || n.startsWith("document_")
            }?.forEach { it.delete() }
        }
    }

    companion object {
        const val CONFIG = "config"
        const val ME = "me"
        const val PROJECTS = "projects"
        const val CREDITS = "credits"
        fun project(id: String) = "project_$id"
        fun document(id: String) = "document_$id"
    }
}
