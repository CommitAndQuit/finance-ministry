package `in`.txnsense.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import `in`.txnsense.app.data.LearnedState
import `in`.txnsense.app.data.LearnedTemplateEntity
import `in`.txnsense.app.data.LearningSummary
import `in`.txnsense.app.data.TemplateLearning
import `in`.txnsense.app.data.TransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PatternLearningPanel(repository: TransactionRepository, refreshGeneration: Int = 0) {
    val generation by repository.eraseGeneration.collectAsState()
    key(repository, generation) { PatternLearningContent(repository, refreshGeneration) }
}

@Composable private fun PatternLearningContent(repository: TransactionRepository, refreshGeneration: Int) {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(repository.learningEnabled()) }
    var summary by remember { mutableStateOf(LearningSummary(0, 0, 0, 0)) }
    var learned by remember { mutableStateOf(emptyList<LearnedTemplateEntity>()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshGeneration, reload) {
        enabled = repository.learningEnabled()
        try {
            summary = repository.learningSummary()
            learned = repository.learnedTemplates()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { summary = LearningSummary(0, 0, 0, 0); learned = emptyList() }
    }
    HorizontalDivider()
    Text("Learn new message formats", style = MaterialTheme.typography.titleMedium)
    Text(
        "When an alert arrives in a format the app cannot read, it keeps an outline of the format — the " +
            "banking words, with a blank where every value was — and not the message. After the same " +
            "outline turns up a few times it works out a rule for it.",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Learn from unreadable alerts")
        Switch(checked = enabled, enabled = !busy, modifier = Modifier.semantics {
            contentDescription = "Learn new message formats"
            stateDescription = if (enabled) "On" else "Off"
        }, onCheckedChange = { checked ->
            busy = true; message = null; enabled = checked
            scope.launch {
                try {
                    repository.setLearningEnabled(checked)
                    message = if (checked) null else "Switched off. Everything learned so far was deleted."
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { enabled = repository.learningEnabled(); message = "That could not be changed." }
                finally { busy = false; reload++ }
            }
        })
    }
    Text(
        "A learned rule never records a transaction by itself. It reads live alerts silently until it has " +
            "managed ${TemplateLearning.MIN_SHADOW_AGREEMENTS} of them cleanly, and after that it only ever " +
            "adds transactions for you to review. If it ever disagrees with a format the app already knew, " +
            "it is dropped for good.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (enabled) {
        Text(
            "${summary.layoutsRemembered} outlines kept · ${summary.onProbation} on trial · " +
                "${summary.inUse} in use · ${summary.retired} dropped",
        )
        Text(
            "Outlines are forgotten after 90 days without being seen, and go entirely when you switch this " +
                "off or erase your data.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(enabled = !busy, onClick = {
            busy = true; message = null
            scope.launch {
                try {
                    val report = repository.induceTemplates()
                    message = when {
                        report.layoutsExamined == 0 -> "No unreadable alerts have arrived yet."
                        report.learned.isNotEmpty() -> "Worked out ${report.learned.size} new rule(s). Each starts on trial."
                        else -> "Nothing new to learn from ${report.layoutsExamined} outline(s) yet."
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "That could not finish. Nothing was changed." }
                finally { busy = false; reload++ }
            }
        }) { Text("Look for new formats now") }
        Text("This otherwise happens on its own overnight, while charging.", style = MaterialTheme.typography.bodySmall)
    }
    message?.let { Text(it) }
    learned.forEach { row ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${bank(row.bankId)} · ${row.direction.lowercase()}", style = MaterialTheme.typography.titleSmall)
                Text(stateLabel(row), style = MaterialTheme.typography.bodySmall)
                Text("Worked out from ${row.sampleCount} matching outlines.", style = MaterialTheme.typography.bodySmall)
                if (row.state != LearnedState.Retired.name) {
                    TextButton(enabled = !busy, onClick = {
                        busy = true; message = null
                        scope.launch {
                            try { repository.forgetLearnedTemplate(row.templateId); message = "Rule removed." }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { message = "That could not be removed." }
                            finally { busy = false; reload++ }
                        }
                    }) { Text("Remove this rule") }
                }
            }
        }
    }
}

private fun bank(bankId: String) = if (bankId == "UNKNOWN") "Unrecognised sender" else bankId

private fun stateLabel(row: LearnedTemplateEntity): String = when (row.state) {
    LearnedState.Shadow.name ->
        "On trial — reads alerts, records nothing (${row.agreements} of ${TemplateLearning.MIN_SHADOW_AGREEMENTS})"
    LearnedState.Active.name -> "In use — adds transactions for you to review"
    else -> "Dropped — it disagreed with a format the app already knew"
}
