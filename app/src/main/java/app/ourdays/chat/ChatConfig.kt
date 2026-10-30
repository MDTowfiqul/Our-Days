package app.ourdays.chat

import android.content.Context

/** Our Chat setup: one shared Telegram channel for both partners. */
data class ChatConfig(
    val botToken: String = "",
    /** Shared channel chat id — the SAME value on both phones */
    val chatId: String = "",
    val enabled: Boolean = false,
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
        )
    }

    fun save(context: Context, config: ChatConfig) {
        prefs(context).edit()
            .putString("bot_token", config.botToken)
            .putString("chat_id", config.chatId)
            .putBoolean("enabled", config.enabled)
            .apply()
    }

    fun clear(context: Context) = prefs(context).edit().clear().apply()
}
