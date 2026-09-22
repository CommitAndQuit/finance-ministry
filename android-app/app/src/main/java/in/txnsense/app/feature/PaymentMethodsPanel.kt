package `in`.txnsense.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.txnsense.app.core.model.Channel
import `in`.txnsense.app.data.DiscoveredMethod
import `in`.txnsense.app.data.PaymentSourceEntity
import `in`.txnsense.app.data.TransactionRepository
import `in`.txnsense.app.money
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Payment methods derived from stored transactions: the distinct instruments the user
 * actually uses, with per-method spend. Read-only summary plus one-tap registration so a
 * discovered instrument can become a named [PaymentSourceEntity].
 */
@Composable
fun PaymentMethodsPanel(repository: TransactionRepository) {
    val generation by repository.eraseGeneration.collectAsState()
    key(repository, generation) { PaymentMethodsContent(repository) }
}

@Composable
private fun PaymentMethodsContent(repository: TransactionRepository) {
    var methods by remember { mutableStateOf<List<DiscoveredMethod>>(emptyList()) }
    var sources by remember { mutableStateOf<List<PaymentSourceEntity>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    suspend fun reload() { methods = repository.discoveredMethods(); sources = repository.activePaymentSources() }
    LaunchedEffect(Unit) { reload() }

    Text("Payment methods you use", style = MaterialTheme.typography.titleMedium)
    Text("Derived from your recorded SMS and manual entries. Grouped by method, bank and card/account.",
        style = MaterialTheme.typography.bodySmall)

    if (methods.isEmpty()) {
        Text("No payment methods detected yet. They appear here as transactions are recorded.",
            style = MaterialTheme.typography.bodySmall)
    }

    methods.forEach { method ->
        val registered = sources.any { source ->
            channelMatchesKind(source.channel, method.channel) &&
                (method.maskedAccountHint == null || source.last4 == null || source.last4 == method.maskedAccountHint.takeLast(4)) &&
                (method.bankName == null || source.bankName == null || source.bankName.equals(method.bankName, ignoreCase = true))
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(methodTitle(method), fontWeight = FontWeight.Medium)
            Text(buildString {
                append("${method.count} txn")
                if (method.count != 1) append("s")
                append(" · last ${dateFormat.format(Date(method.lastSeen))}")
            }, style = MaterialTheme.typography.bodySmall)
            Text("Out ${money(method.outMinor)} · In ${money(method.inMinor)}", style = MaterialTheme.typography.bodySmall)
            if (registered) {
                Text("Saved as a payment source", style = MaterialTheme.typography.labelSmall)
            } else {
                TextButton(onClick = {
                    scope.launch {
                        busy = true; error = null
                        try {
                            repository.addPaymentSource(
                                nickname = uniqueNickname(methodTitle(method), sources),
                                kind = kindFor(method.channel),
                                channel = runCatching { Channel.valueOf(method.channel) }.getOrDefault(Channel.Other),
                                bankName = method.bankName ?: "",
                                last4 = method.maskedAccountHint?.takeLast(4)?.takeIf { it.matches(Regex("[0-9]{4}")) } ?: "",
                            )
                            reload()
                        } catch (e: IllegalArgumentException) { error = e.message ?: "Could not add this source." }
                        catch (_: Exception) { error = "Could not add this source. Try again." }
                        finally { busy = false }
                    }
                }, enabled = !busy) { Text("Add as payment source") }
            }
        }
        HorizontalDivider()
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

private fun methodTitle(method: DiscoveredMethod): String = listOfNotNull(
    method.bankName,
    friendly(method.channel),
    method.maskedAccountHint,
).joinToString(" · ")

private fun kindFor(channel: String): String = when (channel) {
    "CreditCard" -> "Credit card"
    "DebitCard" -> "Debit card"
    "Card" -> "Card"
    "Wallet" -> "Wallet"
    "UPI" -> "UPI"
    else -> "Bank account"
}

private val cardFamily = setOf("Card", "CreditCard", "DebitCard")
private fun channelMatchesKind(sourceChannel: String, methodChannel: String): Boolean =
    sourceChannel == methodChannel || (sourceChannel in cardFamily && methodChannel in cardFamily)

private fun uniqueNickname(base: String, sources: List<PaymentSourceEntity>): String {
    val trimmed = base.take(40).ifBlank { "Payment method" }
    if (sources.none { it.nickname.equals(trimmed, ignoreCase = true) }) return trimmed
    var index = 2
    while (sources.any { it.nickname.equals("${trimmed.take(36)} $index", ignoreCase = true) }) index++
    return "${trimmed.take(36)} $index"
}
