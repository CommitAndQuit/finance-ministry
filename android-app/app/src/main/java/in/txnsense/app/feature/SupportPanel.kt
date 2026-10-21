package `in`.txnsense.app.feature

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.txnsense.app.BuildConfig

internal const val SUPPORT_EMAIL = "financeministrysupport@gmail.com"

internal fun supportEmailIntent(featureRequest: Boolean): Intent {
    val topic = if (featureRequest) "Feature request" else "Bug report"
    val body = if (featureRequest) "What would you like to do?\n\nHow would this help you?\n\n"
        else "What happened?\n\nSteps to reproduce:\n\nWhat did you expect?\n\n"
    val message = body + "App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
        "\nPlease remove private information before sending. Do not include real SMS, OTPs or account details."
    val subject = "Finance Ministry — $topic"
    return Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(message)}"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, message)
}

@Composable
internal fun SupportPanel(launchEmail: ((Intent) -> Unit)? = null) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    fun draft(featureRequest: Boolean) {
        try {
            val intent = supportEmailIntent(featureRequest)
            if (launchEmail != null) launchEmail(intent) else context.startActivity(intent)
            message = "Email draft opened. You can review it before sending."
        } catch (_: ActivityNotFoundException) {
            message = "No email app is available. Copy the address below and use your email service."
        } catch (_: SecurityException) {
            message = "Could not open your email app. Copy the address below and use your email service."
        }
    }
    Text("Help us improve Finance Ministry", style = MaterialTheme.typography.titleMedium)
    Text("Report a problem or suggest a feature. Your email app opens a draft for you to review and send. No transactions, messages, logs or attachments are added.")
    Button(onClick = { draft(false) }, modifier = Modifier.fillMaxWidth()) { Text("Report a bug") }
    OutlinedButton(onClick = { draft(true) }, modifier = Modifier.fillMaxWidth()) { Text("Suggest a feature") }
    Spacer(Modifier.height(4.dp))
    SelectionContainer { Text(SUPPORT_EMAIL, style = MaterialTheme.typography.bodyMedium) }
    TextButton(onClick = {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Support email", SUPPORT_EMAIL))
        message = "Support email copied."
    }) { Text("Copy email address") }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Text("Only share information you are comfortable emailing. Remove private details from any screenshots you choose to attach. Your email provider handles the message under its own policies.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
