package app.ourdays.chat

import org.json.JSONObject

/**
 * One message in Our Chat. The payload is plain, versioned JSON before it is sealed
 * with the pairing key, so fields can be added later without breaking old messages.
 */
data class ChatMessage(
    /** Random id, so the same message is never stored twice */
    val id: String,
    /** Person id of the sender (Pairing.me), null = unknown */
    val from: String?,
    /** Epoch millis, set by the sending phone */
    val at: Long,
    val text: String,
) {
    fun toJson(): String = JSONObject()
        .put("v", 1)
        .put("id", id)
        .put("from", from ?: JSONObject.NULL)
        .put("at", at)
        .put("text", text)
        .toString()

    companion object {
        fun fromJson(json: String): ChatMessage? = runCatching {
            val o = JSONObject(json)
            require(o.optInt("v", 0) == 1) { "unsupported chat version" }
            ChatMessage(
                id = o.getString("id"),
                from = if (o.isNull("from")) null else o.optString("from").ifBlank { null },
                at = o.getLong("at"),
                text = o.getString("text"),
            )
        }.getOrNull()
    }
}
