package app.ourdays.chat

import org.json.JSONObject

/**
 * One message in Our Chat. The payload is plain, versioned JSON before it is sealed
 * with the pairing key, so fields can be added later without breaking old messages.
 * v1: text only. v2: adds kind + media fields; v1 messages still read as text.
 */
data class ChatMessage(
    /** Random id, so the same message is never stored twice */
    val id: String,
    /** Person id of the sender (Pairing.me), null = unknown */
    val from: String?,
    /** Epoch millis, set by the sending phone */
    val at: Long,
    /** Text of a text message, or the caption of a media message (may be blank) */
    val text: String,
    val kind: String = KIND_TEXT,
    /** Local file name under the chat_media directory, for media messages */
    val mediaLocal: String? = null,
    val mediaMime: String? = null,
) {
    val isMedia: Boolean get() = kind != KIND_TEXT

    fun toJson(): String {
        val o = JSONObject()
            .put("v", 2)
            .put("id", id)
            .put("from", from ?: JSONObject.NULL)
            .put("at", at)
            .put("text", text)
            .put("kind", kind)
        if (mediaLocal != null) o.put("media", mediaLocal)
        if (mediaMime != null) o.put("mime", mediaMime)
        return o.toString()
    }

    companion object {
        const val KIND_TEXT = "text"
        const val KIND_PHOTO = "photo"
        const val KIND_VIDEO = "video"

        fun fromJson(json: String): ChatMessage? = runCatching {
            val o = JSONObject(json)
            val v = o.optInt("v", 0)
            require(v == 1 || v == 2) { "unsupported chat version" }
            ChatMessage(
                id = o.getString("id"),
                from = if (o.isNull("from")) null else o.optString("from").ifBlank { null },
                at = o.getLong("at"),
                text = o.getString("text"),
                kind = o.optString("kind", KIND_TEXT).ifBlank { KIND_TEXT },
                mediaLocal = o.optString("media", "").ifBlank { null },
                mediaMime = o.optString("mime", "").ifBlank { null },
            )
        }.getOrNull()
    }
}

/** Transport envelope for a media message: encrypted into the document caption. */
data class ChatMediaMeta(
    val id: String,
    val from: String?,
    val at: Long,
    val kind: String,
    val caption: String,
    val fileName: String,
    val mime: String,
) {
    fun toJson(): String = JSONObject()
        .put("v", 1)
        .put("id", id)
        .put("from", from ?: JSONObject.NULL)
        .put("at", at)
        .put("kind", kind)
        .put("caption", caption)
        .put("file", fileName)
        .put("mime", mime)
        .toString()

    companion object {
        fun fromJson(json: String): ChatMediaMeta? = runCatching {
            val o = JSONObject(json)
            require(o.optInt("v", 0) == 1) { "unsupported media version" }
            ChatMediaMeta(
                id = o.getString("id"),
                from = if (o.isNull("from")) null else o.optString("from").ifBlank { null },
                at = o.getLong("at"),
                kind = o.getString("kind"),
                caption = o.optString("caption", ""),
                fileName = o.getString("file"),
                mime = o.optString("mime", ""),
            )
        }.getOrNull()
    }
}
