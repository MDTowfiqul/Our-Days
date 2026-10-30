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
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            from = pairing.me,
            at = System.currentTimeMillis(),
            text = trimmed.take(4000),
        )
        viewModelScope.launch {
            _error.value = null
            try {
                withContext(Dispatchers.IO) {
                    TelegramChat.sendMessage(config.botToken, config.chatId, ChatCrypto.encrypt(pairing.key, message))
                }
                _messages.value = ChatStore.merge(app, listOf(message))
            } catch (e: Exception) {
                _error.value = e.message?.take(200) ?: e.toString().take(200)
            }
        }
    }

    /** Sends a photo or video: encrypted with the pairing key, like text. */
    fun sendMedia(uri: Uri, caption: String) {
        val app = getApplication<Application>()
        val config = _config.value
        val pairing = PairingStore.load(app) ?: return
        if (!config.isConfigured) return
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
        val next = ChatConfig(botToken.trim(), chatId.trim(), enabled = true, lastUpdateId = 0L)
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
        /** Telegram Bot API document limit. */
        const val MAX_MEDIA_BYTES = 50 * 1024 * 1024
        const val MAX_PHOTO_SIDE = 1920
    }
}
