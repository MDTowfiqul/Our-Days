package app.ourdays.cloud

import android.content.Context

/**
 * Cloud-Sicherung: verschlüsselte Backups wahlweise per Telegram-Bot oder Google Drive.
 * Die Zugangsdaten liegen in eigenen Einstellungen (nicht in der Datensicherung),
 * damit ein wiederhergestelltes Backup nicht aus Versehen weiter in die Cloud schreibt.
 */
enum class CloudProvider { NONE, TELEGRAM, DRIVE }

data class CloudConfig(
    val provider: CloudProvider = CloudProvider.NONE,
    // Telegram
    val botToken: String = "",
    val chatId: String = "",
    // Google Drive
    val driveAccount: String = "",
    // Gemeinsam
    val password: String = "",
    val frequencyDays: Int = 7,
    val wifiOnly: Boolean = true,
    val lastBackup: Long = 0L,
    val lastError: String? = null,
)

object CloudBackupStore {
    private const val PREFS = "cloud_backup"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): CloudConfig {
        val p = prefs(context)
        return CloudConfig(
            provider = runCatching { CloudProvider.valueOf(p.getString("provider", "NONE")!!) }
                .getOrDefault(CloudProvider.NONE),
            botToken = p.getString("bot_token", "").orEmpty(),
            chatId = p.getString("chat_id", "").orEmpty(),
            driveAccount = p.getString("drive_account", "").orEmpty(),
            password = p.getString("password", "").orEmpty(),
            frequencyDays = p.getInt("frequency_days", 7),
            wifiOnly = p.getBoolean("wifi_only", true),
            lastBackup = p.getLong("last_backup", 0L),
            lastError = p.getString("last_error", null),
        )
    }

    fun save(context: Context, config: CloudConfig) {
        prefs(context).edit()
            .putString("provider", config.provider.name)
            .putString("bot_token", config.botToken)
            .putString("chat_id", config.chatId)
            .putString("drive_account", config.driveAccount)
            .putString("password", config.password)
            .putInt("frequency_days", config.frequencyDays)
            .putBoolean("wifi_only", config.wifiOnly)
            .putLong("last_backup", config.lastBackup)
            .putString("last_error", config.lastError)
            .apply()
    }

    fun recordResult(context: Context, success: Boolean, error: String? = null) {
        val c = load(context)
        save(
            context,
            c.copy(
                lastBackup = if (success) System.currentTimeMillis() else c.lastBackup,
                lastError = if (success) null else error,
            ),
        )
    }

    /** Lokales Verzeichnis hochgeladener Sicherungen (id = Telegram-Stempel oder Drive-Datei-ID). */
    data class BackupRecord(val provider: CloudProvider, val id: String, val date: Long, val label: String)

    fun ledger(context: Context): List<BackupRecord> {
        return prefs(context).getString("ledger", "").orEmpty().lines()
            .mapNotNull { line ->
                val p = line.split(";", limit = 4)
                if (p.size < 4) return@mapNotNull null
                val provider = runCatching { CloudProvider.valueOf(p[0]) }.getOrNull() ?: return@mapNotNull null
                BackupRecord(provider, p[1], p[2].toLongOrNull() ?: 0L, p[3])
            }
            .sortedByDescending { it.date }
    }

    fun addRecord(context: Context, provider: CloudProvider, id: String, label: String) {
        val line = "${provider.name};$id;${System.currentTimeMillis()};${label.replace(";", " ")}"
        val existing = prefs(context).getString("ledger", "").orEmpty()
        // Höchstens 50 Einträge behalten
        val lines = (listOf(line) + existing.lines()).filter { it.isNotBlank() }.take(50)
        prefs(context).edit().putString("ledger", lines.joinToString("\n")).apply()
    }

    fun clear(context: Context) = prefs(context).edit().clear().apply()
}
