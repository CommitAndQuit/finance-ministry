package `in`.financeministry.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.financeministry.app.core.model.Channel
import `in`.financeministry.app.data.PaymentSourceEntity
import `in`.financeministry.app.data.TransactionRepository
import kotlinx.coroutines.launch

@Composable
fun PaymentSourcesPanel(repository: TransactionRepository) {
    val generation by repository.eraseGeneration.collectAsState()
    key(repository, generation) { PaymentSourcesContent(repository) }
}

@Composable private fun PaymentSourcesContent(repository: TransactionRepository) {
    var sources by remember { mutableStateOf<List<PaymentSourceEntity>>(emptyList()) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PaymentSourceEntity?>(null) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { sources = repository.activePaymentSources() }
    Text("My payment sources", style = MaterialTheme.typography.titleMedium)
    Text("Name the accounts and cards you use. Clear SMS matches get the right name; uncertain ones stay unassigned.", style = MaterialTheme.typography.bodySmall)
    sources.forEach { source ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(source.nickname)
                val details = listOfNotNull(
                    friendly(source.kind),
                    friendly(source.channel).takeUnless { it.equals(friendly(source.kind), ignoreCase = true) },
                    source.bankName,
                    source.last4?.let { "••••$it" }
                ).distinct().joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodySmall)
            }
            Row {
                TextButton(onClick = { sourceError = null; editing = source }, enabled = !busy) { Text("Edit") }
                TextButton(onClick = { scope.launch { busy = true; try { repository.deletePaymentSource(source.id); sources = repository.activePaymentSources() }
                    catch (_: Exception) { sourceError = "Could not remove this source. Try again." } finally { busy = false } } }, enabled = !busy) { Text("Remove") }
            }
        }
    }
    sourceError?.takeIf { !adding && editing == null }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    OutlinedButton(onClick = { sourceError = null; adding = true }, enabled = !busy) { Text("Add payment source") }
    val dialogSource = editing
    if (adding || dialogSource != null) PaymentSourceDialog(existing = dialogSource, error = sourceError, busy = busy,
        onDismiss = { if (!busy) { adding = false; editing = null } }, onSave = { nickname, kind, channel, bank, last4 ->
        scope.launch {
            busy = true
            try {
                if (dialogSource == null) repository.addPaymentSource(nickname, kind, Channel.valueOf(channel), bank, last4)
                else repository.updatePaymentSource(dialogSource.id, nickname, kind, Channel.valueOf(channel), bank, last4)
                sources = repository.activePaymentSources(); adding = false; editing = null
            } catch (error: IllegalArgumentException) { sourceError = error.message ?: "Check the source details." }
            catch (_: Exception) { sourceError = "Could not save this source. Try again." }
            finally { busy = false }
        }
    })
}

@Composable private fun PaymentSourceDialog(existing: PaymentSourceEntity?, error: String?, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String, String, String) -> Unit) {
    var nickname by remember(existing?.id) { mutableStateOf(existing?.nickname ?: "") }
    var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: "UPI") }
    var channel by remember(existing?.id) { mutableStateOf(existing?.channel ?: "UPI") }
    var bank by remember(existing?.id) { mutableStateOf(existing?.bankName ?: "") }
    var last4 by remember(existing?.id) { mutableStateOf(existing?.last4 ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "Add payment source" else "Edit payment source") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(nickname, { nickname = it.take(40) }, label = { Text("Name, e.g. Personal UPI") }, singleLine = true)
            SourceDropdown("Type", kind, listOf("UPI", "Bank account", "Credit card", "Debit card")) { kind = it; channel = if (it == "UPI") "UPI" else if (it.contains("card", true)) "Card" else "BankTransfer" }
            SourceDropdown("Used for", channel, listOf("UPI", "Card", "BankTransfer", "IMPS", "NEFT", "RTGS")) { channel = it }
            OutlinedTextField(bank, { bank = it.take(40) }, label = { Text("Bank or institution (optional)") }, supportingText = { Text("For example, HDFC or ICICI.") }, singleLine = true)
            OutlinedTextField(last4, { last4 = it.filter(Char::isDigit).take(4) }, label = { Text("Last 4 digits (optional)") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { onSave(nickname, kind, channel, bank, last4) }, enabled = !busy) { Text(if (busy) "Saving…" else "Save") } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } })
}

@Composable private fun SourceDropdown(label: String, value: String, options: List<String>, onChoose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { expanded = true }) { Text("$label: ${friendly(value)}") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) { options.forEach { item -> DropdownMenuItem(text = { Text(friendly(item)) }, onClick = { onChoose(item); expanded = false }) } }
    }
}
