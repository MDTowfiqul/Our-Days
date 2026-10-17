package app.ourdays

import android.app.Application
import app.ourdays.data.LoveRepository
import app.ourdays.notify.DailyScheduler
import app.ourdays.notify.Notifier
import app.ourdays.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LoveApp : Application() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            DailyScheduler.schedule(this@LoveApp, LoveRepository(this@LoveApp).current())
            runCatching { UpdateChecker.checkIfDue(this@LoveApp) }
        }
    }
}
