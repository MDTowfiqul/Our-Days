package app.ourdays.chat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Our Chat over the Telegram Bot API.
 *
 * Delivery model — read this before changing anything:
 * Telegram NEVER delivers a bot's own messages back to it as updates. One shared
 * bot for both phones therefore cannot work: phone A sends through the bot, and
 * phone B polling the same bot will never see that post. Each phone uses its
 * OWN bot instead (both bots are admins of the same channel): phone A sends
 * with bot A and polls with bot A, which delivers bot B's posts, and vice
 * versa. The setup screen says this in plain words; do not regress it to
 * "use the same bot token on both phones".
 *
 * Polling confirms progress with an offset: fetch() takes the last processed
 * update id and asks only for newer updates, so a busy bot can no longer hide
 * chat messages behind the 100-update window. The backup feature never
 * confirms offsets, so the two features do not consume each other's stream.
 */
object TelegramChat {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** One raw text chat message from the channel, still encrypted */
    data class RawChat(val updateId: Long, val messageId: Long, val dateSeconds: Long, val payload: String)

    /** One raw media chat message: the caption holds encrypted metadata, the document holds encrypted bytes */
    data class RawMedia(
        val updateId: Long,
        val messageId: Long,
        val dateSeconds: Long,
        val captionPayload: String,
        val fileId: String,
        val fileName: String,
        val mimeType: String,
    )

    private fun api(token: String, method: String) = "https://api.telegram.org/bot$token/$method"

    private fun checkOk(body: String): JSONObject {
        val json = JSONObject(body)
        if (!json.optBoolean("ok", false)) throw java.io.IOException(json.optString("description", "telegram error"))
        return json
    }

    private fun getJson(url: String): JSONObject {
        return http.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("telegram http ${resp.code}")
            checkOk(resp.body!!.string())
        }
    }

    fun sendMessage(token: String, chatId: String, text: String) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("text", text)
            .build()
        http.newCall(Request.Builder().url(api(token, "sendMessage")).post(body).build())
            .execute().use { resp ->
                val answer = resp.body!!.string()
                if (!resp.isSuccessful) throw java.io.IOException("telegram http ${resp.code}")
                checkOk(answer)
            }
    }

    /** Sends encrypted media bytes as a document; the caption carries encrypted metadata. */
    fun sendDocument(token: String, chatId: String, fileName: String, bytes: ByteArray, caption: String) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", caption)
            .addFormDataPart("document", fileName, bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        http.newCall(Request.Builder().url(api(token, "sendDocument")).post(body).build())
            .execute().use { resp ->
                val answer = resp.body!!.string()
                if (!resp.isSuccessful) throw java.io.IOException("telegram http ${resp.code}")
                checkOk(answer)
            }
    }

    /** Checks only that the token talks to Telegram; nothing is sent to the channel */
    fun checkConnection(token: String): Int {
        val url = api(token, "getUpdates") + "?allowed_updates=%5B%22message%22%2C%22channel_post%22%5D&limit=1"
        return getJson(url).getJSONArray("result").length()
    }

    /**
     * Reads updates; the caller persists the highest update id and passes it back
     * as [offset] next time. [offset] is the first update id wanted (last
     * processed id + 1); null means "recent history", used on first setup.
     */
    fun fetch(token: String, offset: Long? = null): JSONObject {
        var url = api(token, "getUpdates") + "?allowed_updates=%5B%22message%22%2C%22channel_post%22%5D&limit=100"
        if (offset != null && offset > 0) url += "&offset=$offset"
        return getJson(url)
    }

    /** Pure parsing, so it can be unit-tested without network */
    fun parseUpdates(root: JSONObject): List<RawChat> {
        val result = root.optJSONArray("result") ?: return emptyList()
        val out = mutableListOf<RawChat>()
        for (i in 0 until result.length()) {
            val update = result.optJSONObject(i) ?: continue
            val msg = update.optJSONObject("message") ?: update.optJSONObject("channel_post") ?: continue
            val text = msg.optString("text", "")
            if (!text.startsWith(ChatCrypto.PREFIX)) continue
            out += RawChat(
                updateId = update.optLong("update_id", 0L),
                messageId = msg.optLong("message_id", 0L),
                dateSeconds = msg.optLong("date", 0L),
                payload = text,
            )
        }
        return out.sortedBy { it.messageId }
    }

    /** Media messages: a document whose caption is an encrypted metadata envelope. */
    fun parseMedia(root: JSONObject): List<RawMedia> {
        val result = root.optJSONArray("result") ?: return emptyList()
        val out = mutableListOf<RawMedia>()
        for (i in 0 until result.length()) {
            val update = result.optJSONObject(i) ?: continue
            val msg = update.optJSONObject("message") ?: update.optJSONObject("channel_post") ?: continue
            val doc = msg.optJSONObject("document") ?: continue
            val caption = msg.optString("caption", "")
            if (!caption.startsWith(ChatCrypto.PREFIX)) continue
            out += RawMedia(
                updateId = update.optLong("update_id", 0L),
                messageId = msg.optLong("message_id", 0L),
                dateSeconds = msg.optLong("date", 0L),
                captionPayload = caption,
                fileId = doc.optString("file_id", ""),
                fileName = doc.optString("file_name", ""),
                mimeType = doc.optString("mime_type", ""),
            )
        }
        return out.filter { it.fileId.isNotBlank() }.sortedBy { it.messageId }
    }

    /** Highest update id in a getUpdates response, 0 when there is none. */
    fun maxUpdateId(root: JSONObject): Long {
        val result = root.optJSONArray("result") ?: return 0L
        var max = 0L
        for (i in 0 until result.length()) {
            val id = result.optJSONObject(i)?.optLong("update_id", 0L) ?: 0L
            if (id > max) max = id
        }
        return max
    }

    /** Resolves a file_id to its download path, then downloads the raw bytes. */
    fun downloadFile(token: String, fileId: String): ByteArray {
        val info = getJson(api(token, "getFile") + "?file_id=" + fileId)
        val path = info.optJSONObject("result")?.optString("file_path", "")?.takeIf { it.isNotBlank() }
            ?: throw java.io.IOException("telegram file path missing")
        return http.newCall(Request.Builder().url("https://api.telegram.org/file/bot$token/$path").get().build())
            .execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("telegram http ${resp.code}")
                resp.body!!.bytes()
            }
    }
}
