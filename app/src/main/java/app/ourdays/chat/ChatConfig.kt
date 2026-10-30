package app.ourdays.chat

import android.content.Context

/**
 * Our Chat setup: one shared Telegram channel for both partners, but a bot per
 * phone — see TelegramChat for why one shared bot cannot deliver messages.
 */
data class ChatConfig(
    /** THIS phone's bot token (its own BotFather bot, admin of the shared channel) */
    val botToken: String = "",
    /** Shared channel chat id — the SAME value on both phones */
    val chatId: String = "",
    val enabled: Boolean = false,
    /** Highest Telegram update id already processed by this phone's bot */
    val lastUpdateId: Long = 0L,
) {
    val isConfigured: Boolean get() = botToken.isNotBlank() && chatId.isNotBlank()
}

object ChatConfigStore {
    private const val PREFS = "our_chat"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): ChatConfig {
        val p = prefs(context)
        return ChatConfig(
            botToken = p.getString("bot_token", "").orEmpty(),
            chatId = p.getString("chat_id", "").orEmpty(),
            enabled = p.getBoolean("enabled", false),
            lastUpdateId = p.getLong("last_update_id", 0L),
        )
    }

    fun save(context: Context, config: ChatConfig) {
        prefs(context).edit()
            .putString("bot_token", config.botToken)
            .putString("chat_id", config.chatId)
            .putBoolean("enabled", config.enabled)
            .putLong("last_update_id", config.lastUpdateId)
            .apply()
    }

    fun clear(context: Context) = prefs(context).edit().clear().apply()
}
