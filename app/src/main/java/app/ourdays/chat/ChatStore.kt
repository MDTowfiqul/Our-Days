package app.ourdays.chat

import android.content.Context
import java.io.File

/**
 * Chat history on this phone: one message JSON per line. Kept separate from backups
 * on purpose — chat history is not part of the data backup and never leaves the phone
 * except encrypted through the shared channel. Media files live under chat_media and
 * are named after their message id, so history and media are deleted together.
 */
object ChatStore {
    private const val FILE_NAME = "our_chat_messages.jsonl"
    private const val MAX_MESSAGES = 500
    private const val MEDIA_DIR = "chat_media"

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun load(context: Context): List<ChatMessage> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            f.readLines().mapNotNull { ChatMessage.fromJson(it) }
        }.getOrDefault(emptyList())
            .distinctBy { it.id }.sortedBy { it.at }.takeLast(MAX_MESSAGES)
    }

    /** Merges [incoming] into history (newest wins per id) and saves. Returns merged list. */
    fun merge(context: Context, incoming: List<ChatMessage>): List<ChatMessage> {
        val merged = (load(context) + incoming).distinctBy { it.id }
            .sortedBy { it.at }.takeLast(MAX_MESSAGES)
        save(context, merged)
        return merged
    }

    fun clear(context: Context) {
        file(context).delete()
        File(context.filesDir, MEDIA_DIR).deleteRecursively()
    }

    private fun save(context: Context, messages: List<ChatMessage>) {
        file(context).writeText(messages.joinToString("\n") { it.toJson() })
    }

    fun mediaDir(context: Context): File =
        File(context.filesDir, MEDIA_DIR).apply { if (!exists()) mkdirs() }

    /** Saves decrypted media bytes; returns the file name stored in the message. */
    fun saveMedia(context: Context, id: String, ext: String, bytes: ByteArray): String {
        val name = "$id.$ext"
        File(mediaDir(context), name).writeBytes(bytes)
        return name
    }

    fun mediaFile(context: Context, name: String): File = File(mediaDir(context), name)
}
