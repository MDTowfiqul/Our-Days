package app.sinceus.cloud

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

    /** Vergisst ein ungültig gewordenes Token, damit das nächste Mal ein neues geholt wird. */
    fun invalidate(account: String)
}

object DriveBackends {
    fun create(context: android.content.Context): DriveBackend? = runCatching {
        Class.forName("app.sinceus.cloud.play.GmsDriveBackend")
            .getDeclaredConstructor(android.content.Context::class.java)
            .newInstance(context.applicationContext) as DriveBackend
    }.getOrNull()
}
