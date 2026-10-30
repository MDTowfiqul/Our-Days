package app.ourdays.chat

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ourdays.data.PairingStore
import java.util.concurrent.TimeUnit

/**
 * Quiet background poll: fetches new chat messages into local history.
 * No notification yet — new messages are visible when the chat is opened.
 */
class ChatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val config = ChatConfigStore.load(context)
        val pairing = PairingStore.load(context)
        if (!config.enabled || !config.isConfigured || pairing == null) return Result.success()
        return try {
            val raw = TelegramChat.fetch(config.botToken)
            val decoded = raw.mapNotNull { ChatCrypto.decrypt(pairing.key, it.payload) }
            ChatStore.merge(context, decoded)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

object ChatScheduler {
    private const val TAG = "our-chat-sync"

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(TAG)
            return
        }
        val request = PeriodicWorkRequestBuilder<ChatWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(TAG)
            .build()
        wm.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
