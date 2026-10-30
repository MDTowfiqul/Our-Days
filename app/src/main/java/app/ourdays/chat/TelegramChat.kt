package app.ourdays.chat

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Our Chat over the Telegram Bot API — the same kind of channel the cloud backup
 * already uses, but for short text messages instead of backup files. Updates are
 * deliberately NOT confirmed (like the backup listing), so polling stays repeatable
 * and nothing is lost when the app is closed between polls. The bot must be an
 * admin of the shared channel, otherwise it never sees the channel posts.
 */
object TelegramChat {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** One raw chat message from the channel, still encrypted */
    data class RawChat(val messageId: Long, val dateSeconds: Long, val payload: String)

    private fun api(token: String, method: String) = "https://api.telegram.org/bot$token/$method"

    private fun checkOk(body: String): JSONObject {
        val json = JSONObject(body)
        if (!json.optBoolean("ok", false)) throw java.io.IOException(json.optString("description", "telegram error"))
        return json
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

    /** Reads the channel without changing anything; throws when token or channel is wrong */
    fun checkConnection(token: String): Int {
        val url = api(token, "getUpdates") + "?allowed_updates=%5B%22message%22%2C%22channel_post%22%5D&limit=1"
        val json = http.newCall(Request.Builder().url(url).get().build())
            .execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("telegram http ${resp.code}")
                checkOk(resp.body!!.string())
            }
        return json.getJSONArray("result").length()
    }

    fun fetch(token: String): List<RawChat> {
        val url = api(token, "getUpdates") + "?allowed_updates=%5B%22message%22%2C%22channel_post%22%5D&limit=100"
        val json = http.newCall(Request.Builder().url(url).get().build())
            .execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("telegram http ${resp.code}")
                checkOk(resp.body!!.string())
            }
        return parseUpdates(json)
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
                messageId = msg.optLong("message_id", 0L),
                dateSeconds = msg.optLong("date", 0L),
                payload = text,
            )
        }
        return out.sortedBy { it.messageId }
    }
}
