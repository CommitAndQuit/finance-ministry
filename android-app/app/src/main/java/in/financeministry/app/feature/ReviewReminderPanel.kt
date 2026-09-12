package `in`.financeministry.app.feature

import android.app.TimePickerDialog
import android.Manifest
import android.os.Build
import android.provider.Settings
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import `in`.financeministry.app.data.TransactionRepository
import `in`.financeministry.app.sms.ReviewReminder

@Composable
fun ReviewReminderPanel(repository: TransactionRepository, refreshGeneration: Int = 0) {
    val generation by repository.eraseGeneration.collectAsState()
    key(repository, generation) { ReviewReminderContent(repository, refreshGeneration) }
}

@Composable private fun ReviewReminderContent(repository: TransactionRepository, refreshGeneration: Int) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(repository.preferences.getBoolean("review_reminder", false)) }
    var hour by remember { mutableIntStateOf(repository.preferences.getInt("review_reminder_hour", 20)) }
    var minute by remember { mutableIntStateOf(repository.preferences.getInt("review_reminder_minute", 0)) }
    var available by remember { mutableStateOf(ReviewReminder.available(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        available = ReviewReminder.available(context)
    }
    LaunchedEffect(refreshGeneration) {
        enabled = repository.preferences.getBoolean("review_reminder", false)
        hour = repository.preferences.getInt("review_reminder_hour", 20)
        minute = repository.preferences.getInt("review_reminder_minute", 0)
        available = ReviewReminder.available(context)
    }
    Text("Daily review reminder", style = MaterialTheme.typography.titleMedium)
    Text("Only notifies when saved transactions need your review.", style = MaterialTheme.typography.bodySmall)
    Row {
        Switch(checked = enabled, modifier = Modifier.semantics {
            contentDescription = "Daily review reminder"
            stateDescription = if (enabled) "On" else "Off"
        }, onCheckedChange = { checked ->
            enabled = checked
            if (checked) {
                ReviewReminder.schedule(context, hour, minute)
                if (Build.VERSION.SDK_INT >= 33 && !available) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else ReviewReminder.cancel(context)
        })
        TextButton(onClick = { TimePickerDialog(context, { _, h, m ->
            hour = h; minute = m; ReviewReminder.updateTime(context, h, m)
        }, hour, minute, android.text.format.DateFormat.is24HourFormat(context)).show() }) { Text(String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute)) }
    }
    if (enabled && !available) {
        Text("Android notifications are blocked, so this reminder cannot appear.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Open notification settings") }
    } else if (enabled) Text("Scheduled around ${String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute)} when transactions need review.", style = MaterialTheme.typography.bodySmall)
}
