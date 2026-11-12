package app.ourdays.chat

import android.content.Context

/** How this phone reaches the partner: Telegram (own bot + shared channel) or Firebase (paired room). */
enum class ChatTransport { TELEGRAM, FIREBASE }

/**
 * Our Chat setup. Two transports, one choice:
 * - TELEGRAM: one shared Telegram channel, but a bot per phone — see
 *   TelegramChat for why one shared bot cannot deliver messages.
 * - FIREBASE: text only, one shared Firestore room, joined with a 6-digit
 *   code. No Google account needed on the phone (anonymous sign-in).
 */
data class ChatConfig(
    /** THIS phone's bot token (its own BotFather bot, admin of the shared channel) */
    val botToken: String = "",
    /** Shared channel chat id — the SAME value on both phones */
    val chatId: String = "",
    val enabled: Boolean = false,
    /** Highest Telegram update id already processed by this phone's bot */
    val lastUpdateId: Long = 0L,
    val transport: ChatTransport = ChatTransport.TELEGRAM,
    /** Shared Firestore room id (Firebase transport) — the SAME value on both phones */
    val roomId: String = "",
) {
    val isConfigured: Boolean get() = when (transport) {
        ChatTransport.TELEGRAM -> botToken.isNotBlank() && chatId.isNotBlank()
        ChatTransport.FIREBASE -> roomId.isNotBlank()
    }
}

object ChatConfigStore {
    private const val PREFS = "our_chat"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): ChatConfig {
        val p = prefs(context)
        // Old installs saved no transport: they were Telegram setups, so that stays the default.
        val transport = runCatching {
            ChatTransport.valueOf(p.getString("transport", ChatTransport.TELEGRAM.name)!!)
        }.getOrDefault(ChatTransport.TELEGRAM)
        return ChatConfig(
            botToken = p.getString("bot_token", "").orEmpty(),
            chatId = p.getString("chat_id", "").orEmpty(),
            enabled = p.getBoolean("enabled", false),
            lastUpdateId = p.getLong("last_update_id", 0L),
            transport = transport,
            roomId = p.getString("room_id", "").orEmpty(),
        )
    }

    fun save(context: Context, config: ChatConfig) {
        prefs(context).edit()
            .putString("bot_token", config.botToken)
            .putString("chat_id", config.chatId)
            .putBoolean("enabled", config.enabled)
            .putLong("last_update_id", config.lastUpdateId)
            .putString("transport", config.transport.name)
            .putString("room_id", config.roomId)
            .apply()
    }

    fun clear(context: Context) = prefs(context).edit().clear().apply()
}
