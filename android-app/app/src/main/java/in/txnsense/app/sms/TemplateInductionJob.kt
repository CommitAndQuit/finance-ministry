package `in`.txnsense.app.sms

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import `in`.txnsense.app.TxnSenseApp
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Schedules the background pass that turns remembered layouts into templates.
 *
 * Induction aligns every remembered layout against every other, so it is the one part of this app that
 * is worth real CPU. It runs only when the device is idle, charging and not low on battery: the work is
 * never urgent — a layout that becomes learnable tonight is just as learnable tomorrow — so there is no
 * reason for it to ever compete with the user or spend their battery.
 */
object TemplateInductionJob {

    private const val NAME = "template-induction"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<TemplateInductionWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        // KEEP, not UPDATE: re-registering on every launch would reset the period and mean a device
        // that is opened often never reaches the end of one.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}

class TemplateInductionWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as? TxnSenseApp)?.container?.repository ?: return Result.success()
        // Learning may have been switched off, or everything erased, since this was scheduled;
        // induceTemplates rechecks both under the same lock the rest of the repository uses.
        return try {
            // Bounded so a pathological corpus cannot hold a wakelock. The next run picks up where this
            // one left off, because induction always reconsiders the whole corpus.
            withTimeoutOrNull(4 * 60 * 1000L) { repository.induceTemplates() }
            Result.success()
        } catch (_: Exception) {
            // Nothing here is urgent enough to retry against; the next idle window will try again.
            Result.success()
        }
    }
}
