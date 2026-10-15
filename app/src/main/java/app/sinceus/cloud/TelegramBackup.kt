package app.sinceus.cloud

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Telegram-Bot als Cloud-Speicher: Die Sicherung (verschlüsselt) wird als Dokument
 * an einen eigenen Chat geschickt. Bots dürfen 50 MB pro Datei, darum wird Größeres
 * in 45-MB-Teile zerlegt. Wiederherstellen listet die eigenen Backup-Nachrichten
 * über getUpdates und lädt sie herunter.
 */
object TelegramBackup {
    const val CAPTION_PREFIX = "our-days-backup"
    private const val PART_BYTES = 45L * 1024 * 1024

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun api(token: String, method: String) = "https://api.telegram.org/bot$token/$method"

    private fun checkOk(body: String): JSONObject {
        val json = JSONObject(body)
        if (!json.optBoolean("ok", false)) {
            throw java.io.IOException(json.optString("description", "telegram error"))
        }
        return json
    }

    /** Schickt eine Textnachricht (für den Verbindungstest). */
    fun sendTestMessage(token: String, chatId: String) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("text", "Our-Days: Cloud-Sicherung ist eingerichtet. ✅")
            .build()
        http.newCall(Request.Builder().url(api(token, "sendMessage")).post(body).build())
            .execute().use { resp ->
                val text = resp.body!!.string()
                if (!resp.isSuccessful) throw java.io.IOException("telegram http ${resp.code}")
                checkOk(text)
            }
    }

    /**
     * Lädt [file] (bereits verschlüsselt) hoch. Gibt die Anzahl der Teile zurück.
     * [onProgress] erhält (fertige Teile, Teile gesamt).
     */
    fun uploadBackup(
        token: String,
        chatId: String,
        file: File,
        stamp: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Int {
        val total = file.length()
        val parts = ((total + PART_BYTES - 1) / PART_BYTES).toInt().coerceAtLeast(1)
        var done = 0
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(PART_BYTES.toInt())
            var index = 0
            while (true) {
                var read = 0
                while (read < buffer.size) {
                    val n = input.read(buffer, read, buffer.size - read)
                    if (n < 0) break
                    read += n
                }
                if (read == 0) break
                index++
                val part = File.createTempFile("tgpart", ".bin")
                try {
                    part.outputStream().use { it.write(buffer, 0, read) }
                    sendDocument(
                        token, chatId, part,
                        "$CAPTION_PREFIX $stamp part $index/$parts",
                        "our-days-backup-$stamp-part$index.bin",
                    )
                } finally {
                    part.delete()
                }
                done++
                onProgress(done, parts)
                if (read < buffer.size) break
            }
        }
        return parts
    }

    private fun sendDocument(token: String, chatId: String, file: File, caption: String, fileName: String) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", caption)
            .addFormDataPart(
                "document", fileName,
                file.asRequestBody("application/octet-stream".toMediaType()),
            )
            .build()
        http.newCall(Request.Builder().url(api(token, "sendDocument")).post(body).build())
            .execute().use { resp ->
                val text = resp.body!!.string()
                if (!resp.isSuccessful) throw java.io.IOException("telegram http ${resp.code}: $text".take(300))
                checkOk(text)
            }
    }

    data class RemoteBackup(val stamp: String, val parts: List<String>, val date: Long)

    /**
     * Listet eigene Backups aus den unbestätigten Updates. Die Updates werden bewusst
     * NICHT bestätigt, damit die Liste bei jedem Aufruf wieder da ist.
     */
    fun listBackups(token: String): List<RemoteBackup> {
        val url = api(token, "getUpdates") + "?allowed_updates=%5B%22message%22%2C%22channel_post%22%5D&limit=100"
        val text = http.newCall(Request.Builder().url(url).get().build())
            .execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("telegram http ${resp.code}")
                checkOk(resp.body!!.string())
            }.toString()
        val byStamp = mutableMapOf<String, MutableList<Pair<Int, String>>>()
        val result = JSONObject(text).getJSONArray("result")
        for (i in 0 until result.length()) {
            val update = result.getJSONObject(i)
            // Eigene Nachrichten kommen als "message", in Kanälen als "channel_post"
            val msg = update.optJSONObject("message") ?: update.optJSONObject("channel_post") ?: continue
            val doc = msg.optJSONObject("document") ?: continue
            val caption = msg.optString("caption", "")
            if (!caption.startsWith(CAPTION_PREFIX)) continue
            // "our-days-backup <stamp> part <i>/<n>"
            val tail = caption.removePrefix(CAPTION_PREFIX).trim().split(" ")
            if (tail.size < 3) continue
            val stamp = tail[0]
            val index = tail[2].substringBefore("/").toIntOrNull() ?: continue
            val fileId = doc.getString("file_id")
            byStamp.getOrPut(stamp) { mutableListOf() }.add(index to fileId)
        }
        return byStamp.mapNotNull { (stamp, parts) ->
            val ordered = parts.sortedBy { it.first }.map { it.second }
            if (ordered.isEmpty()) return@mapNotNull null
            // Nur vollständige Sätze (1..n lückenlos) anbieten
            val indexes = parts.map { it.first }.sorted()
            if (indexes != (1..indexes.size).toList()) return@mapNotNull null
            RemoteBackup(stamp, ordered, msgDateOf(stamp))
        }.sortedByDescending { it.date }
    }

    private fun msgDateOf(stamp: String): Long = stamp.toLongOrNull() ?: 0L

    /** Lädt alle Teile eines Backups herunter und schreibt sie aneinandergehängt nach [out]. */
    fun downloadBackup(token: String, backup: RemoteBackup, out: OutputStream) {
        out.buffered().use { sink ->
            backup.parts.forEach { fileId ->
                val path = getFilePath(token, fileId)
                downloadFile(token, path, sink)
            }
            sink.flush()
        }
    }

    private fun getFilePath(token: String, fileId: String): String {
        val url = api(token, "getFile") + "?file_id=" + fileId
        val json = http.newCall(Request.Builder().url(url).get().build())
            .execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("telegram http ${resp.code}")
                checkOk(resp.body!!.string())
            }
        return json.getJSONObject("result").getString("file_path")
    }

    private fun downloadFile(token: String, filePath: String, out: OutputStream) {
        val url = "https://api.telegram.org/file/bot$token/$filePath"
        http.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("telegram http ${resp.code}")
            resp.body!!.byteStream().use { it.copyTo(out) }
        }
    }
}
