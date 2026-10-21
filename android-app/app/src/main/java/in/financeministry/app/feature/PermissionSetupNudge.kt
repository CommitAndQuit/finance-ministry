package `in`.financeministry.app.feature

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import `in`.financeministry.app.BuildConfig
import `in`.financeministry.app.data.TransactionRepository
import `in`.financeministry.app.permissionSetupNeeds
import `in`.financeministry.app.permissionSetupSnoozed
import `in`.financeministry.app.sms.TransactionNotifications

/** Optional setup only. Permission dialogs are launched exclusively by a tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PermissionSetupNudge(repository: TransactionRepository, refreshGeneration: Int, permissionResultGeneration: Int,
    onEnableSms: () -> Unit, onEnableNotifications: () -> Unit) {
    val context = LocalContext.current
    val preferences = repository.preferences
    var preferenceGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> preferenceGeneration++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val needs = remember(preferences, refreshGeneration, permissionResultGeneration, preferenceGeneration) {
        permissionSetupNeeds(
            context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED,
            preferences.getBoolean("sms_disclosure", false), preferences.getBoolean("sms_permission_requested", false),
            preferences.getBoolean("notifications", true), TransactionNotifications.available(context))
    }
    val snoozed = permissionSetupSnoozed(preferences.getLong("permission_setup_dismissed_at", 0),
        preferences.getInt("permission_setup_dismissed_version", 0), BuildConfig.VERSION_CODE, System.currentTimeMillis())
    if (snoozed || (!needs.sms && !needs.notifications)) return

    Card(Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Set up automatic tracking", style = MaterialTheme.typography.titleSmall)
            Text(when {
                needs.sms && needs.notifications -> "Record supported bank alerts and get notified when they're saved. Optional — manual entry always works."
                needs.sms -> "Record supported bank alerts automatically. Optional — manual entry always works."
                else -> "Get notified when supported bank SMS is recorded. Recording still works without notifications."
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (needs.sms) FilledTonalButton(onClick = onEnableSms) { Text("Enable SMS") }
                if (needs.notifications) TextButton(onClick = onEnableNotifications) { Text("Enable notifications") }
                TextButton(onClick = {
                    preferences.edit {
                        putLong("permission_setup_dismissed_at", System.currentTimeMillis())
                        putInt("permission_setup_dismissed_version", BuildConfig.VERSION_CODE)
                    }
                }) { Text("Not now") }
            }
        }
    }
}
