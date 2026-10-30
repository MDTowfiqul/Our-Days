package app.ourdays.chat

import android.content.Context
import java.io.File

/**
 * Chat history on this phone: one message JSON per line. Kept separate from backups
 * on purpose — chat history is not part of the data backup and never leaves the phone
 * except encrypted through the shared channel.
 */
object ChatStore {
    private const val FILE_NAME = "our_chat_messages.jsonl"
    private const val MAX_MESSAGES = 500

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
    }

    private fun save(context: Context, messages: List<ChatMessage>) {
        file(context).writeText(messages.joinToString("\n") { it.toJson() })
    }
}
