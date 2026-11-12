package app.ourdays.chat

import android.content.Context
import app.ourdays.data.Pairing
import app.ourdays.data.PairingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * One refresh of Our Chat, shared by the chat screen and the background
 * worker so both see exactly the same messages. Text messages are decrypted
 * in place; media messages are downloaded, decrypted, and stored under
 * chat_media before they enter history, so history only ever holds readable
 * messages plus a count of the ones that could not be opened.
 *
 * Two transports, same history: Telegram polls the channel, Firebase polls
 * the shared Firestore room. Both carry the same sealed payloads, so a
 * couple can switch transports without losing history.
 */
object ChatSync {
    data class Outcome(val messages: List<ChatMessage>, val undecryptable: Int)

    suspend fun refresh(context: Context): Outcome = withContext(Dispatchers.IO) {
        val config = ChatConfigStore.load(context)
        val pairing = PairingStore.load(context)
        if (!config.enabled || !config.isConfigured || pairing == null) {
            return@withContext Outcome(ChatStore.load(context), 0)
        }
        when (config.transport) {
            ChatTransport.TELEGRAM -> refreshTelegram(context, config, pairing)
            ChatTransport.FIREBASE -> refreshFirebase(context, config, pairing)
        }
    }

    private fun refreshTelegram(context: Context, config: ChatConfig, pairing: Pairing): Outcome {
        val offset = if (config.lastUpdateId > 0) config.lastUpdateId + 1 else null
        val root = TelegramChat.fetch(config.botToken, offset)
        var undecryptable = 0
        val incoming = mutableListOf<ChatMessage>()
        for (raw in TelegramChat.parseUpdates(root)) {
            val msg = ChatCrypto.decrypt(pairing.key, raw.payload)
            if (msg == null) undecryptable++ else incoming += msg
        }
        for (raw in TelegramChat.parseMedia(root)) {
            val meta = ChatCrypto.decryptMeta(pairing.key, raw.captionPayload)
            if (meta == null) {
                undecryptable++
                continue
            }
            val plain = runCatching {
                val cipher = TelegramChat.downloadFile(config.botToken, raw.fileId)
                val out = ByteArrayOutputStream()
                ChatCrypto.decryptBytes(pairing.key, ByteArrayInputStream(cipher), out)
                out.toByteArray()
            }.getOrNull() ?: continue
            val ext = extFor(meta.mime, meta.fileName)
            val local = ChatStore.saveMedia(context, meta.id, ext, plain)
            incoming += ChatMessage(meta.id, meta.from, meta.at, meta.caption, meta.kind, local, meta.mime)
        }
        val maxId = TelegramChat.maxUpdateId(root)
        if (maxId > config.lastUpdateId) {
            ChatConfigStore.save(context, config.copy(lastUpdateId = maxId))
        }
        return Outcome(storeIncoming(context, incoming), undecryptable)
    }

    /** Firebase refresh: text only, sealed payloads, old messages pruned. */
    private fun refreshFirebase(context: Context, config: ChatConfig, pairing: Pairing): Outcome {
        // Only ask for what is newer than the newest message already stored:
        // a full list on every poll would burn the shared free quota in reads.
        // A minute of overlap covers phones whose clocks run a little slow;
        // merging drops duplicates by id, so re-delivery is harmless.
        val since = ChatStore.load(context).maxOfOrNull { it.at }
            ?.let { (it - 60_000).coerceAtLeast(0) } ?: 0
        val docs = FirebaseChat.fetchMessages(context, config.roomId, since)
        var undecryptable = 0
        val incoming = mutableListOf<ChatMessage>()
        for (doc in docs) {
            val msg = ChatCrypto.decrypt(pairing.key, doc.payload)
            if (msg == null) undecryptable++ else incoming += msg
        }
        // Messages older than 90 days are deleted from the room so the shared
        // 1 GiB free storage never fills up; local history keeps them.
        runCatching {
            FirebaseChat.pruneIfDue(context, config.roomId, System.currentTimeMillis() - FirebaseChat.RETENTION_MS)
        }
        return Outcome(storeIncoming(context, incoming), undecryptable)
    }

    /**
     * Stores what arrived: ordinary messages merge into history, edit/delete
     * events are applied to it instead of being shown as messages. Events are
     * applied after the merge, so an event for a message that arrived in the
     * same refresh still finds its target.
     */
    private fun storeIncoming(context: Context, incoming: List<ChatMessage>): List<ChatMessage> {
        val (events, normal) = incoming.partition { it.isEvent }
        val merged = ChatStore.merge(context, normal)
        if (events.isEmpty()) return merged
        return ChatStore.applyEvents(context, events)
    }

    private fun extFor(mime: String, fileName: String): String {
        val fromMime = when {
            mime.startsWith("image/jpeg") -> "jpg"
            mime.startsWith("image/png") -> "png"
            mime.startsWith("image/webp") -> "webp"
            mime.startsWith("video/mp4") -> "mp4"
            mime.startsWith("video/") -> "mp4"
            mime.startsWith("image/") -> "jpg"
            else -> null
        }
        if (fromMime != null) return fromMime
        val dot = fileName.lastIndexOf('.')
        if (dot >= 0 && dot < fileName.length - 1) {
            val ext = fileName.substring(dot + 1).filter { it.isLetterOrDigit() }.take(5)
            if (ext.isNotEmpty()) return ext
        }
        return "bin"
    }
}
