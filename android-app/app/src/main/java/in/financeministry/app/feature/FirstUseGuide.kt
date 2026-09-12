package `in`.financeministry.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FirstUseGuide(captureEnabled: Boolean, hasSources: Boolean, reminderEnabled: Boolean,
    onDone: () -> Unit, onOpenSms: () -> Unit, onOpenSources: () -> Unit, onOpenReminder: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Finish setup", style = MaterialTheme.typography.titleLarge)
            Text("Private and on-device. Nothing is uploaded.", style = MaterialTheme.typography.bodyMedium)
            GuideItem(captureEnabled, "Record supported bank SMS", onOpenSms)
            GuideItem(hasSources, "Name your UPI, accounts and cards", onOpenSources)
            GuideItem(reminderEnabled, "Choose a review reminder", onOpenReminder)
            Text("You can add transactions yourself and return here later.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDone, contentPadding = PaddingValues(0.dp)) { Text("Skip for now") }
        }
    }
}

@Composable private fun GuideItem(done: Boolean, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Text("${if (done) "✓" else "○"} $label", Modifier.align(Alignment.CenterStart))
        }
    }
}
