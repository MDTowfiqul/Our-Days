package app.sinceus.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.sinceus.data.LoveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Führt eine Cloud-Sicherung aus: Daten als ZIP exportieren, mit dem Passwort
 * verschlüsseln, beim eingestellten Anbieter hochladen.
 */
suspend fun runCloudBackup(
    context: Context,
    config: CloudConfig,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
) {
    require(config.provider != CloudProvider.NONE) { "no provider" }
    require(config.password.isNotBlank()) { "no password" }
    val repo = LoveRepository(context.applicationContext)
    val stamp = System.currentTimeMillis().toString()
    val plain = File.createTempFile("cb-plain", ".zip", context.cacheDir)
    val encrypted = File.createTempFile("cb-enc", ".bin", context.cacheDir)
    try {
        withContext(Dispatchers.IO) {
            plain.outputStream().use { repo.exportBackup(it) }
            encrypted.outputStream().use { out ->
                plain.inputStream().use { input -> CloudCrypto.encrypt(input, out, config.password) }
            }
        }
        when (config.provider) {
            CloudProvider.TELEGRAM -> {
                require(config.botToken.isNotBlank() && config.chatId.isNotBlank()) { "telegram not configured" }
                TelegramBackup.uploadBackup(config.botToken, config.chatId, encrypted, stamp, onProgress)
            }
            CloudProvider.DRIVE -> {
                require(config.driveAccount.isNotBlank()) { "drive not configured" }
                val backend = DriveBackends.create(context)
                    ?: throw java.io.IOException("drive not available in this build")
                backend.upload(config.driveAccount, "our-days-backup-$stamp.bin", encrypted)
                onProgress(1, 1)
            }
            CloudProvider.NONE -> Unit
        }
    } finally {
        plain.delete()
        encrypted.delete()
    }
}

/** Lädt eine Sicherung herunter, entschlüsselt sie und stellt sie wieder her. */
suspend fun restoreCloudBackup(
    context: Context,
    config: CloudConfig,
    remoteId: String,
    password: String,
) {
    val app = context.applicationContext
    val encrypted = File.createTempFile("cb-dl", ".bin", app.cacheDir)
    val plain = File.createTempFile("cb-dl", ".zip", app.cacheDir)
    try {
        withContext(Dispatchers.IO) {
            encrypted.outputStream().use { out ->
                when (config.provider) {
                    CloudProvider.TELEGRAM -> {
                        val backup = TelegramBackup.listBackups(config.botToken)
                            .firstOrNull { it.stamp == remoteId }
                            ?: throw java.io.IOException("backup not found")
                        TelegramBackup.downloadBackup(config.botToken, backup, out)
                    }
                    CloudProvider.DRIVE -> {
                        val backend = DriveBackends.create(app)
                            ?: throw java.io.IOException("drive not available in this build")
                        backend.download(config.driveAccount, remoteId, out)
                    }
                    CloudProvider.NONE -> throw java.io.IOException("no provider")
                }
            }
            plain.outputStream().use { out ->
                encrypted.inputStream().use { input -> CloudCrypto.decrypt(input, out, password) }
            }
        }
        LoveRepository(app).importBackup(plain.inputStream())
    } finally {
        encrypted.delete()
        plain.delete()
    }
}

/** Hintergrund-Arbeiter für die geplante Sicherung. */
class CloudBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val config = CloudBackupStore.load(applicationContext)
        if (config.provider == CloudProvider.NONE || config.password.isBlank()) {
            return Result.success()
        }
        return try {
            runCloudBackup(applicationContext, config)
            CloudBackupStore.recordResult(applicationContext, true)
            Result.success()
        } catch (e: Exception) {
            CloudBackupStore.recordResult(applicationContext, false, e.message?.take(200))
            Result.retry()
        }
    }
}

object CloudBackupScheduler {
    private const val TAG = "cloud-backup"

    fun schedule(context: Context, config: CloudConfig) {
        val wm = WorkManager.getInstance(context)
        if (config.provider == CloudProvider.NONE) {
            wm.cancelUniqueWork(TAG)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (config.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(
            config.frequencyDays.toLong().coerceAtLeast(1),
            TimeUnit.DAYS,
        ).setConstraints(constraints).addTag(TAG).build()
        wm.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Sofortige Sicherung, z. B. über den Knopf in den Einstellungen. */
    fun runNow(context: Context): UUID {
        val request = OneTimeWorkRequestBuilder<CloudBackupWorker>().addTag(TAG).build()
        WorkManager.getInstance(context).enqueue(request)
        return request.id
    }

    fun workInfo(context: Context, id: UUID) = WorkManager.getInstance(context).getWorkInfoById(id)
}
