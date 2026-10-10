package com.melta.app.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * A deliberately tiny HTTP helper. Melta only ever downloads a handful of small sprite files,
 * so pulling in a full networking stack would cost more RAM and APK size than it saves.
 */
object Http {

    /** Downloads [url] into [target] atomically. Returns the file, or null on any failure. */
    suspend fun download(url: String, target: File): File? = withContext(Dispatchers.IO) {
        val tmp = File(target.parentFile, target.name + ".part")
        var conn: HttpURLConnection? = null
        try {
            target.parentFile?.mkdirs()
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Melta-Android")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target
        } catch (e: IOException) {
            tmp.delete()
            null
        } finally {
            conn?.disconnect()
        }
    }
}
