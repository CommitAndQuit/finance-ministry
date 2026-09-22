@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package `in`.txnsense.app.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.txnsense.app.data.TransactionEntity
import `in`.txnsense.app.data.TransactionRepository
import `in`.txnsense.app.money
import `in`.txnsense.app.repaymentSummary
import `in`.txnsense.app.transactionTime
import `in`.txnsense.app.ui.LocalBrand
import kotlinx.coroutines.launch

/**
 * Full-screen transaction detail reached by tapping a row, which expands into this screen via the
 * shared-element transition. Styled like the method-detail page: a light-green gradient carrying the
 * transaction's headline info, with a white card on top holding the remaining details and actions.
 */
@Composable
fun TransactionDetailScreen(
    repository: TransactionRepository,
    transactionId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
) {
    val revision by repository.revision.collectAsState()
    val eraseGen by repository.eraseGeneration.collectAsState()
    var row by remember(transactionId) { mutableStateOf<TransactionEntity?>(null) }
    var loaded by remember(transactionId) { mutableStateOf(false) }
    LaunchedEffect(transactionId, revision, eraseGen) {
        row = runCatching { repository.get(transactionId) }.getOrNull(); loaded = true
    }
    BackHandler(enabled = true) { onBack() }

    val scheme = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val scope = rememberCoroutineScope()
    var deleteDialog by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().expandFromRow("txn-$transactionId")
            .background(Brush.verticalGradient(listOf(brand.bgTop, brand.bgBottom)))
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("←", fontSize = 26.sp, color = scheme.onBackground,
                    modifier = Modifier.clickableMenu(onBack).padding(8.dp))
            }
            val r = row
            if (r == null) {
                Text(if (loaded) "This transaction no longer exists." else "Loading…",
                    color = brand.textSecondary, modifier = Modifier.padding(24.dp))
            } else {
                // Headline info on the gradient.
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(money(r.amountMinor), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = scheme.onBackground)
                    Text("${friendly(r.direction)} · ${friendly(r.status)}", fontSize = 14.sp, color = brand.textSecondary)
                    r.counterpartyLabel?.let { Text(it, fontSize = 16.sp, color = scheme.onBackground) }
                    Text(buildString { append(friendly(r.channel)); r.bankName?.let { append(" · $it") } },
                        fontSize = 13.sp, color = brand.textSecondary)
                    Text(transactionTime(r.effectiveTimestamp), fontSize = 12.sp, color = brand.textSecondary)
                }
                // White card with the remaining details.
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(scheme.surface)
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (r.reviewState == "NeedsReview") Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Check this transaction", style = MaterialTheme.typography.titleMedium)
                            Text(when {
                                r.amountMinor == null -> "We couldn’t identify a reliable amount. Enter it to finish recording."
                                r.direction == "Unknown" -> "Choose whether money went out, came in, or moved between accounts."
                                r.status == "Unknown" -> "Check whether this payment completed before confirming."
                                else -> "Check the details before including this transaction in your totals."
                            })
                        }
                    } else Text(friendly(r.reviewState), style = MaterialTheme.typography.bodySmall, color = brand.textSecondary)

                    Text("Category: ${r.category}", style = MaterialTheme.typography.bodyMedium)
                    if (r.ownership != "Personal") Text("For: ${friendly(r.ownership)}", style = MaterialTheme.typography.bodyMedium)
                    r.groupLabel?.let { Text("Group: $it", style = MaterialTheme.typography.bodyMedium) }
                    repaymentSummary(r)?.let { (label, owed) -> Text("$label: ${money(owed)}", style = MaterialTheme.typography.bodyMedium) }
                    r.userNotes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    r.linkedOriginalId?.let { originalId ->
                        TextButton(onClick = { onOpenTransaction(originalId) }, contentPadding = PaddingValues(0.dp)) {
                            Text("View linked original transaction")
                        }
                    }

                    SmsProvenance(repository, r)

                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onEdit(r.id) }) { Text("Edit / confirm") }
                        OutlinedButton(onClick = { deleteDialog = true }) { Text("Delete") }
                    }
                }
            }
        }
    }

    if (deleteDialog) AlertDialog(
        onDismissRequest = { deleteDialog = false },
        title = { Text("Delete this transaction?") },
        text = { Text("It will be removed from this device. This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = {
                deleteDialog = false
                scope.launch { runCatching { repository.delete(transactionId) }; onBack() }
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancel") } }
    )
}
