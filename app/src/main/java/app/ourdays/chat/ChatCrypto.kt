package app.ourdays.chat

import android.util.Base64
import app.ourdays.data.SyncCrypto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Chat encryption: the message JSON is sealed with the pairing key (AES-256-GCM,
 * [SyncCrypto]) — the same key only the two paired phones hold. Telegram only ever
 * sees Base64 ciphertext, never the text.
 */
object ChatCrypto {
    /** Telegram message text starts with this, so chat messages are easy to tell apart */
    const val PREFIX = "our-days-chat "

    fun encrypt(key: ByteArray, message: ChatMessage): String {
        val plain = message.toJson().toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        SyncCrypto.encrypt(ByteArrayInputStream(plain), out, key)
        return PREFIX + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** null when the payload is not a chat message, was changed, or uses another key */
    fun decrypt(key: ByteArray, text: String): ChatMessage? {
        if (!text.startsWith(PREFIX)) return null
        val cipher = runCatching {
            Base64.decode(text.removePrefix(PREFIX), Base64.DEFAULT)
        }.getOrNull() ?: return null
        val out = ByteArrayOutputStream()
        return runCatching {
            SyncCrypto.decrypt(ByteArrayInputStream(cipher), out, key)
            ChatMessage.fromJson(out.toString(Charsets.UTF_8.name()))
        }.getOrNull()
    }
}
