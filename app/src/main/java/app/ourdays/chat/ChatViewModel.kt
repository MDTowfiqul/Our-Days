package app.ourdays.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.ourdays.R
import app.ourdays.cloud.CloudBackupStore
import app.ourdays.cloud.CloudProvider
import app.ourdays.data.PairingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val _messages = MutableStateFlow(ChatStore.load(app))
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _config = MutableStateFlow(ChatConfigStore.load(app))
    val config: StateFlow<ChatConfig> = _config.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Prefixed messages that arrived but could not be opened (wrong pairing key). */
    private val _undecryptable = MutableStateFlow(0)
    val undecryptable: StateFlow<Int> = _undecryptable.asStateFlow()

    val isPaired: Boolean get() = PairingStore.load(getApplication()) != null

    /** False when this build has no Firebase config (assets/firebase.json missing). */
    val firebaseAvailable: Boolean get() = FirebaseChat.isAvailable(getApplication())

    /**
     * Cloud backup channel id, if set — handy for prefilling the SHARED channel.
     * The bot token is deliberately NOT reused from here: chat needs its own bot
     * per phone (see TelegramChat), and copying the backup bot token onto both
     * phones is exactly the setup that can never deliver messages.
     */
    val cloudChannelId: String? get() {
        val c = CloudBackupStore.load(getApplication())
        return if (c.provider == CloudProvider.TELEGRAM && c.chatId.isNotBlank()) c.chatId else null
    }

    init {
        if (_config.value.enabled && isPaired) refresh()
    }

    fun refresh() {
        val app = getApplication<Application>()
        if (!_config.value.isConfigured || !isPaired) return
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = null
            try {
                val outcome = ChatSync.refresh(app)
                _messages.value = outcome.messages
                _undecryptable.value = outcome.undecryptable
            } catch (e: FirebaseChat.QuotaExceeded) {
                _error.value = app.getString(R.string.chat_firebase_quota)
            } catch (e: Exception) {
                _error.value = e.message?.take(200) ?: e.toString().take(200)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun send(text: String) {
        val app = getApplication<Application>()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val config = _config.value
        val pairing = PairingStore.load(app) ?: return
        if (!config.isConfigured) return
        val cap = when (config.transport) {
            ChatTransport.TELEGRAM -> TELEGRAM_MAX_TEXT
            ChatTransport.FIREBASE -> FIREBASE_MAX_TEXT
        }
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            from = pairing.me,
            at = System.currentTimeMillis(),
            text = trimmed.take(cap),
        )
        viewModelScope.launch {
            _error.value = null
            try {
                withContext(Dispatchers.IO) {
                    val sealed = ChatCrypto.encrypt(pairing.key, message)
                    when (config.transport) {
                        ChatTransport.TELEGRAM ->
                            TelegramChat.sendMessage(config.botToken, config.chatId, sealed)
                        ChatTransport.FIREBASE ->
                            FirebaseChat.sendMessage(app, config.roomId, pairing.me, sealed, message.id, message.at)
                    }
                }
                _messages.value = ChatStore.merge(app, listOf(message))
            } catch (e: FirebaseChat.QuotaExceeded) {
                _error.value = app.getString(R.string.chat_firebase_quota)
            } catch (e: Exception) {
                _error.value = e.message?.take(200) ?: e.toString().take(200)
            }
        }
    }

    /**
     * Changes the text of one of my own text messages, on both phones. The new
     * text travels as a sealed edit event (same sealed payload as a message, so
     * both transports carry it); the partner's phone applies it on refresh.
     * Media captions cannot be edited — only plain text.
     */
    fun editMessage(targetId: String, newText: String) {
        val app = getApplication<Application>()
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        val config = _config.value
        val pairing = PairingStore.load(app) ?: return
        if (!config.isConfigured) return
        val target = _messages.value.firstOrNull { it.id == targetId }
        if (target == null || pairing.me == null || target.from != pairing.me || target.kind != ChatMessage.KIND_TEXT) return
        val cap = when (config.transport) {
            ChatTransport.TELEGRAM -> TELEGRAM_MAX_TEXT
            ChatTransport.FIREBASE -> FIREBASE_MAX_TEXT
        }
        val event = ChatMessage(
            id = UUID.randomUUID().toString(),
            from = pairing.me,
            at = System.currentTimeMillis(),
            text = trimmed.take(cap),
            kind = ChatMessage.KIND_EDIT,
            targetId = targetId,
        )
        _messages.value = ChatStore.applyEvents(app, listOf(event))
        sendEvent(event)
    }

    /**
     * Removes a message. "For me" only deletes it on this phone; "for everyone"
     * also sends a sealed delete event so the partner's phone removes it too.
     * Only my own messages can be deleted for everyone.
     */
    fun deleteMessage(targetId: String, forEveryone: Boolean) {
        val app = getApplication<Application>()
        val config = _config.value
        val pairing = PairingStore.load(app) ?: return
        if (!config.isConfigured) return
        val target = _messages.value.firstOrNull { it.id == targetId } ?: return
        if (forEveryone && (pairing.me == null || target.from != pairing.me)) return
        val event = ChatMessage(
            id = UUID.randomUUID().toString(),
            from = pairing.me,
            at = System.currentTimeMillis(),
            text = "",
            kind = ChatMessage.KIND_DELETE,
            targetId = targetId,
        )
        _messages.value = ChatStore.applyEvents(app, listOf(event))
        if (forEveryone) sendEvent(event)
    }

    /** Sends an edit/delete event through the current transport. Events are never stored as messages. */
    private fun sendEvent(event: ChatMessage) {
        val app = getApplication<Application>()
        val config = _config.value
        val pairing = PairingStore.load(app) ?: return
        viewModelScope.launch {
            _error.value = null
            try {
                withContext(Dispatchers.IO) {
                    val sealed = ChatCrypto.encrypt(pairing.key, event)
                    when (config.transport) {
                        ChatTransport.TELEGRAM ->
                            TelegramChat.sendMessage(config.botToken, config.chatId, sealed)
                        ChatTransport.FIREBASE ->
                            FirebaseChat.sendMessage(app, config.roomId, pairing.me, sealed, event.id, event.at)
                    }
                }
            } catch (e: FirebaseChat.QuotaExceeded) {
                _error.value = app.getString(R.string.chat_firebase_quota)
            } catch (e: Exception) {
                _error.value = e.message?.take(200) ?: e.toString().take(200)
            }
        }
    }

    /** Sends a photo or video: encrypted with the pairing key, like text. Telegram only — Firebase chat is text only. */
    fun sendMedia(uri: Uri, caption: String) {
        val app = getApplication<Application>()
        val config = _config.value
        val pairing = PairingStore.load(app) ?: return
        if (!config.isConfigured || config.transport != ChatTransport.TELEGRAM) return
        viewModelScope.launch {
            _error.value = null
            try {
                val mime = app.contentResolver.getType(uri) ?: ""
                val kind = if (mime.startsWith("video/")) ChatMessage.KIND_VIDEO else ChatMessage.KIND_PHOTO
                val plain = try {
                    withContext(Dispatchers.IO) { prepareMedia(app, uri, kind) }
                } catch (e: Exception) {
                    _error.value = app.getString(R.string.chat_media_failed)
                    return@launch
                }
                if (plain.size > MAX_MEDIA_BYTES) {
                    _error.value = app.getString(R.string.chat_media_too_large)
                    return@launch
                }
                val id = UUID.randomUUID().toString()
                val ext = if (kind == ChatMessage.KIND_VIDEO) "mp4" else "jpg"
                val local = ChatStore.saveMedia(app, id, ext, plain)
                val encrypted = withContext(Dispatchers.IO) {
                    val out = ByteArrayOutputStream()
                    ChatCrypto.encryptBytes(pairing.key, ByteArrayInputStream(plain), out)
                    out.toByteArray()
                }
                val meta = ChatMediaMeta(
                    id = id,
                    from = pairing.me,
                    at = System.currentTimeMillis(),
                    kind = kind,
                    caption = caption.trim(),
                    fileName = "$id.$ext",
                    mime = mime,
                )
                withContext(Dispatchers.IO) {
                    TelegramChat.sendDocument(
                        config.botToken,
                        config.chatId,
                        "$id.$ext",
                        encrypted,
                        ChatCrypto.encryptMeta(pairing.key, meta),
                    )
                }
                _messages.value = ChatStore.merge(
                    app,
                    listOf(ChatMessage(id, pairing.me, meta.at, meta.caption, kind, local, mime)),
                )
            } catch (e: Exception) {
                _error.value = e.message?.take(200) ?: e.toString().take(200)
            }
        }
    }

    /**
     * Photos are downscaled to 1920px and re-encoded as JPEG so uploads stay small.
     * Videos are sent as-is (no transcoding on the phone); oversized files are
     * rejected by the caller through a sentinel larger than the limit.
     */
    private fun prepareMedia(app: Application, uri: Uri, kind: String): ByteArray {
        val resolver = app.contentResolver
        if (kind != ChatMessage.KIND_VIDEO) {
            val input = resolver.openInputStream(uri) ?: throw java.io.IOException("cannot open media")
            val bitmap = BitmapFactory.decodeStream(input)
                ?: throw java.io.IOException("not an image")
            input.close()
            val maxSide = maxOf(bitmap.width, bitmap.height)
            val scaled = if (maxSide > MAX_PHOTO_SIDE) {
                val scale = MAX_PHOTO_SIDE.toFloat() / maxSide
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            return out.toByteArray()
        }
        val stream = resolver.openInputStream(uri) ?: throw java.io.IOException("cannot open media")
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_MEDIA_BYTES) {
                stream.close()
                return ByteArray(MAX_MEDIA_BYTES + 1)
            }
            out.write(buf, 0, n)
        }
        stream.close()
        return out.toByteArray()
    }

    fun saveConfig(botToken: String, chatId: String) {
        val app = getApplication<Application>()
        // A new bot token means a new update stream, so polling restarts from scratch.
        val next = ChatConfig(
            botToken = botToken.trim(),
            chatId = chatId.trim(),
            enabled = true,
            lastUpdateId = 0L,
            transport = ChatTransport.TELEGRAM,
        )
        ChatConfigStore.save(app, next)
        _config.value = next
        ChatScheduler.schedule(app, true)
        if (isPaired) refresh()
    }

    /** Checks only that the token talks to Telegram; nothing is sent to the channel */
    fun testConnection(token: String, onResult: (Boolean) -> Unit) {
        if (token.isBlank()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { TelegramChat.checkConnection(token) } }.isSuccess
            onResult(ok)
        }
    }

    /** Finds the shared channel id from the bot's recent updates; null when no channel post is visible yet. */
    fun findChannelId(token: String, onResult: (String?) -> Unit) {
        if (token.isBlank()) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val id = runCatching { withContext(Dispatchers.IO) { TelegramChat.findChannelId(token) } }.getOrNull()
            onResult(id)
        }
    }

    /** Creates a Firebase room and returns its 6-digit code, or null on failure. The room is saved right away. */
    fun createFirebaseRoom(onCode: (String?) -> Unit) {
        val app = getApplication<Application>()
        if (!isPaired) {
            onCode(null)
            return
        }
        viewModelScope.launch {
            _error.value = null
            try {
                val (roomId, code) = withContext(Dispatchers.IO) { FirebaseChat.createRoom(app) }
                saveFirebaseConfig(roomId)
                onCode(code)
            } catch (e: FirebaseChat.QuotaExceeded) {
                _error.value = app.getString(R.string.chat_firebase_quota)
                onCode(null)
            } catch (e: Exception) {
                _error.value = e.message?.take(200) ?: e.toString().take(200)
                onCode(null)
            }
        }
    }

    /** Joins a Firebase room with the partner's 6-digit code. */
    fun joinFirebaseRoom(code: String, onResult: (Boolean) -> Unit) {
        val app = getApplication<Application>()
        val clean = code.trim()
        if (!FirebaseChat.isValidCode(clean)) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            _error.value = null
            try {
                val roomId = withContext(Dispatchers.IO) { FirebaseChat.lookupRoom(app, clean) }
                if (roomId == null) {
                    onResult(false)
                    return@launch
                }
                saveFirebaseConfig(roomId)
                onResult(true)
            } catch (e: FirebaseChat.QuotaExceeded) {
                _error.value = app.getString(R.string.chat_firebase_quota)
                onResult(false)
            } catch (e: Exception) {
                _error.value = e.message?.take(200) ?: e.toString().take(200)
                onResult(false)
            }
        }
    }

    fun saveFirebaseConfig(roomId: String) {
        val app = getApplication<Application>()
        val next = ChatConfig(
            transport = ChatTransport.FIREBASE,
            roomId = roomId.trim(),
            enabled = true,
        )
        ChatConfigStore.save(app, next)
        _config.value = next
        ChatScheduler.schedule(app, true)
        if (isPaired) refresh()
    }

    fun disable() {
        val app = getApplication<Application>()
        val next = _config.value.copy(enabled = false)
        ChatConfigStore.save(app, next)
        _config.value = next
        ChatScheduler.schedule(app, false)
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        /**
         * Telegram Bot API document limit is 50 MB to send, but the partner can
         * only download 20 MB through getFile — so a video over 20 MB would
         * send fine and then never arrive. The cap is the receive limit.
         */
        const val MAX_MEDIA_BYTES = 20 * 1024 * 1024
        const val MAX_PHOTO_SIDE = 1920

        /**
         * Telegram text limit is 4,096 characters, but that counts the sealed
         * payload (prefix + Base64 of the encrypted JSON), not the plain text:
         * ~50 bytes of crypto header + ~140 bytes of JSON around the text
         * (with a full-length sender id), then Base64 inflates by 4/3.
         * 2,800 plain characters stay under the limit with room to spare.
         */
        const val TELEGRAM_MAX_TEXT = 2800

        /** Firestore documents hold 1 MiB; 4,000 characters is far inside that. */
        const val FIREBASE_MAX_TEXT = 4000
    }
}
