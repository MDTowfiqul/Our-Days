package app.ourdays.cloud

import android.content.Intent
import java.io.File
import java.io.OutputStream

/**
 * Google-Drive-Anbindung. Die echte Umsetzung liegt aus Lizenzgründen nur in der
 * Play-Variante (app/src/play); andere Varianten bekommen null und blenden Drive aus.
 */
interface DriveBackend {
    data class DriveFile(val id: String, val name: String, val modified: Long)

    /** Lädt die verschlüsselte Sicherung hoch, gibt die Datei-ID zurück. */
    suspend fun upload(account: String, name: String, file: File): String

    /** Neueste zuerst. */
    suspend fun listBackups(account: String): List<DriveFile>

    suspend fun download(account: String, id: String, out: OutputStream)

    /** Benennt eine Sicherung um (nur Drive — Telegram-Sicherungen haben keinen Namen). */
    suspend fun rename(account: String, id: String, newName: String)

    /** Löscht eine Sicherung endgültig. */
    suspend fun delete(account: String, id: String)

    /** Vergisst ein ungültig gewordenes Token, damit das nächste Mal ein neues geholt wird. */
    fun invalidate(account: String)
}

class DriveConsentNeeded(val consentIntent: Intent?) : Exception("Drive consent needed")
object DriveBackends {
    fun create(context: android.content.Context): DriveBackend? = runCatching {
        Class.forName("app.ourdays.cloud.play.GmsDriveBackend")
            .getDeclaredConstructor(android.content.Context::class.java)
            .newInstance(context.applicationContext) as DriveBackend
    }.getOrNull()
}
