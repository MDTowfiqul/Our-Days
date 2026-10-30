package app.ourdays.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.ourdays.cloud.CloudBackupStore
import app.ourdays.cloud.CloudProvider
import app.ourdays.data.PairingStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    val isPaired: Boolean get() = PairingStore.load(getApplication()) != null

    /** Cloud backup Telegram settings, if set — handy for prefilling the shared channel */
    val cloudTelegram: Pair<String, String>? get() {
        val c = CloudBackupStore.load(getApplication())
        return if (c.provider == CloudProvider.TELEGRAM && c.botToken.isNotBlank() && c.chatId.isNotBlank()) {
            c.botToken to c.chatId
        } else null
    }

    init {
        if (_config.value.enabled && isPaired) refresh()
    }

    fun refresh() {
        val app = getApplication<Application>()
        val config = _config.value
        val pairing = PairingStore.load(app) ?: return
        if (!config.isConfigured) return
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = null
            try {
                val decoded = withContext(Dispatchers.IO) {
                    TelegramChat.fetch(config.botToken)
                        .mapNotNull { ChatCrypto.decrypt(pairing.key, it.payload) }
                }
                _messages.value = ChatStore.merge(app, decoded)
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

    fun saveConfig(botToken: String, chatId: String) {
        val app = getApplication<Application>()
        val next = ChatConfig(botToken.trim(), chatId.trim(), enabled = true)
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
}
