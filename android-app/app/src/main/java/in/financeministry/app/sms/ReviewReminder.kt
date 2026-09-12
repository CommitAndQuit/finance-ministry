package `in`.financeministry.app.sms

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import `in`.financeministry.app.FinanceMinistryApp
import `in`.financeministry.app.MainActivity
import `in`.financeministry.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.UUID

object ReviewReminder {
    private const val ACTION = "in.financeministry.app.REVIEW_REMINDER"
    private const val REQUEST = 4701
    internal const val GENERATION_EXTRA = "review_reminder_generation"
    private const val GENERATION_PREF = "review_reminder_generation"
    private val gate = Any()
    const val CHANNEL = "review_reminders"
    fun available(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun schedule(context: Context, hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        synchronized(gate) {
            val preferences = context.getSharedPreferences("finance_settings", Context.MODE_PRIVATE)
            val generation = UUID.randomUUID().toString()
            check(preferences.edit().putBoolean("review_reminder", true).putInt("review_reminder_hour", hour)
                .putInt("review_reminder_minute", minute).putString(GENERATION_PREF, generation).commit())
            setAlarm(context, hour, minute, generation)
        }
    }
    private fun setAlarm(context: Context, hour: Int, minute: Int, generation: String) {
        val intent = Intent(context, ReviewReminderReceiver::class.java).setAction(ACTION).putExtra(GENERATION_EXTRA, generation)
        val pending = PendingIntent.getBroadcast(context, REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val next = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1) }
        // A repeating inexact alarm receives an 18-hour daily delivery window on modern
        // Android. Schedule one local, idle-tolerant occurrence instead and schedule the next
        // day from the receiver after it runs.
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
    }
    fun updateTime(context: Context, hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        synchronized(gate) {
            val preferences = context.getSharedPreferences("finance_settings", Context.MODE_PRIVATE)
            check(preferences.edit().putInt("review_reminder_hour", hour).putInt("review_reminder_minute", minute).commit())
            if (preferences.getBoolean("review_reminder", false)) schedule(context, hour, minute)
        }
    }
    internal fun generation(context: Context): String =
        context.getSharedPreferences("finance_settings", Context.MODE_PRIVATE).getString(GENERATION_PREF, "").orEmpty()
    internal fun isCurrent(context: Context, generation: String): Boolean = synchronized(gate) {
        val preferences = context.getSharedPreferences("finance_settings", Context.MODE_PRIVATE)
        generation.isNotEmpty() && preferences.getBoolean("review_reminder", false) && preferences.getString(GENERATION_PREF, "") == generation
    }
    internal fun scheduleNextIfCurrent(context: Context, generation: String): Boolean = synchronized(gate) {
        val preferences = context.getSharedPreferences("finance_settings", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("review_reminder", false) || preferences.getString(GENERATION_PREF, "") != generation) return@synchronized false
        setAlarm(context, preferences.getInt("review_reminder_hour", 20), preferences.getInt("review_reminder_minute", 0), generation)
        true
    }
    internal fun rescheduleEnabled(context: Context) = synchronized(gate) {
        val preferences = context.getSharedPreferences("finance_settings", Context.MODE_PRIVATE)
        if (preferences.getBoolean("review_reminder", false)) {
            val generation = preferences.getString(GENERATION_PREF, null) ?: UUID.randomUUID().toString().also {
                check(preferences.edit().putString(GENERATION_PREF, it).commit())
            }
            setAlarm(context, preferences.getInt("review_reminder_hour", 20), preferences.getInt("review_reminder_minute", 0), generation)
        }
    }
    fun cancel(context: Context) {
        synchronized(gate) {
            val preferences = context.getSharedPreferences("finance_settings", Context.MODE_PRIVATE)
            val generation = UUID.randomUUID().toString()
            check(preferences.edit().putBoolean("review_reminder", false).putString(GENERATION_PREF, generation).commit())
            val pending = PendingIntent.getBroadcast(context, REQUEST, Intent(context, ReviewReminderReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pending != null) {
                context.getSystemService(AlarmManager::class.java).cancel(pending)
                pending.cancel()
            }
            context.getSystemService(NotificationManager::class.java).cancel(REQUEST)
        }
    }
    fun isScheduled(context: Context): Boolean = PendingIntent.getBroadcast(context, REQUEST,
        Intent(context, ReviewReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null
    fun post(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Review reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Daily reminders when saved transactions need checking" })
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) return
        val intent = Intent(context, MainActivity::class.java).setData(Uri.parse("finance://review"))
            .putExtra("open_review", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val content = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(REQUEST, Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("Review your transactions").setContentText("You have transactions that need checking.").setAutoCancel(true).setContentIntent(content).build())
    }
    internal fun deliverIfCurrent(context: Context, generation: String, hasReview: Boolean): Boolean = synchronized(gate) {
        if (!isCurrent(context, generation)) return@synchronized false
        if (hasReview) post(context)
        true
    }
}

class ReviewReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            ReviewReminder.rescheduleEnabled(context)
            return
        }
        if (intent.action != "in.financeministry.app.REVIEW_REMINDER") return
        val generation = intent.getStringExtra(ReviewReminder.GENERATION_EXTRA) ?: return
        if (!ReviewReminder.isCurrent(context, generation)) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as FinanceMinistryApp).container.repository
                val count = try { withTimeoutOrNull(8_000) { repository.reviewCount() } } catch (_: Exception) { null }
                try { if (count != null) ReviewReminder.deliverIfCurrent(context, generation, count > 0) }
                catch (_: Exception) { /* Notification services are best-effort. */ }
            } finally {
                try { ReviewReminder.scheduleNextIfCurrent(context, generation) } catch (_: Exception) { /* Retry after reboot or the next user update. */ }
                finally { pendingResult.finish() }
            }
        }
    }
}
