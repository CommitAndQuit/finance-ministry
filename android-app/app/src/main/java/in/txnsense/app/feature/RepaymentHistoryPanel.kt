package `in`.txnsense.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import `in`.txnsense.app.ui.LedgerTextInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.txnsense.app.data.*
import `in`.txnsense.app.money
import `in`.txnsense.app.transactionTime
import `in`.txnsense.app.ui.LedgerIcon
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

@Composable
fun RepaymentHistoryPanel(repository: TransactionRepository, expense: TransactionEntity, eligible: Boolean) {
    val revision by repository.revision.collectAsState()
    var history by remember(expense.id) { mutableStateOf<List<RepaymentEntity>>(emptyList()) }
    var memberNames by remember(expense.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var add by rememberSaveable(expense.id) { mutableStateOf(false) }
    var editId by rememberSaveable(expense.id) { mutableStateOf<String?>(null) }
    var unlinkId by rememberSaveable(expense.id) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(expense.id, revision) {
        try { history = repository.repaymentsFor(expense.id); memberNames = repository.localMembers().associate { it.id to it.name }; error = null }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not load repayment history." }
    }
    if (expense.direction != "Debit") return
    if (expense.ownership == "Group" && expense.repaymentExpected) GroupSharesPanel(repository, expense, eligible)
    if (expense.repaymentExpected && expense.ownership in listOf("ForOther", "Group") || history.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text("Repayment history", style = MaterialTheme.typography.titleMedium)
        if (eligible && RepaymentAccounting.principal(expense) > 0) {
            Text("Expected ${money(RepaymentAccounting.principal(expense))} · Received ${money(expense.repaidMinor)}", style = MaterialTheme.typography.bodySmall)
            if (RepaymentAccounting.owed(expense) > 0) Button(onClick = { add = true }) { Text("Record repayment") }
            else Text("Settled", style = MaterialTheme.typography.bodySmall)
        } else Text("Confirm an eligible expense before recording a repayment.", style = MaterialTheme.typography.bodySmall)
        if (history.isEmpty()) Text("No repayments recorded yet.", style = MaterialTheme.typography.bodySmall)
        history.forEach { entry ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(money(entry.amountMinor), style = MaterialTheme.typography.titleSmall)
                Text(entry.receivedAt?.let(::transactionTime) ?: "Date unknown · legacy repayment", style = MaterialTheme.typography.bodySmall)
                Text("${memberNames[entry.memberId] ?: entry.payer.ifBlank { "No name" }} · ${if (entry.method == "Legacy") "Previously recorded" else if (entry.method == "Cash") "Cash receipt" else "Linked incoming payment"}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { editId = entry.id }, enabled = !busy && eligible) { Text("Edit repayment") }
                    TextButton(onClick = { unlinkId = entry.id }, enabled = !busy) { Text("Unlink") }
                }
            }
        }
    } else if (!expense.repaymentExpected && expense.ownership in listOf("ForOther", "Group")) {
        Text("Gift or treat · no repayment expected", style = MaterialTheme.typography.bodySmall)
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (add || editId != null) {
        val entry = history.firstOrNull { it.id == editId }
        if (add || entry != null) RepaymentEditor(repository, expense, entry, onDismiss = { add = false; editId = null })
    }
    unlinkId?.let { id ->
        AlertDialog(onDismissRequest = { if (!busy) unlinkId = null }, title = { Text("Unlink repayment?") },
            text = { Text("This amount becomes owed again. The incoming transaction is kept; no money is deleted or moved.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try { repository.removeRepayment(id); unlinkId = null; error = null }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (e: Exception) { error = e.message ?: "Could not unlink repayment." }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Unlinking…" else "Unlink repayment") } },
            dismissButton = { TextButton(onClick = { unlinkId = null }, enabled = !busy) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RepaymentEditor(repository: TransactionRepository, expense: TransactionEntity, existing: RepaymentEntity?, onDismiss: () -> Unit,
    initialMemberId: String? = null, onSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var amount by rememberSaveable(existing?.id) { mutableStateOf(existing?.amountMinor?.let { BigDecimal.valueOf(it, 2).toPlainString() } ?: "") }
    var payer by rememberSaveable(existing?.id) { mutableStateOf(existing?.payer ?: expense.groupLabel.orEmpty()) }
    var date by rememberSaveable(existing?.id) { mutableStateOf(existing?.receivedAt ?: System.currentTimeMillis()) }
    var dateKnown by rememberSaveable(existing?.id) { mutableStateOf(existing == null || existing.receivedAt != null) }
    var method by rememberSaveable(existing?.id) { mutableStateOf(if (existing == null) "Linked" else existing.method) }
    var incomingId by rememberSaveable(existing?.id) { mutableStateOf(existing?.incomingId) }
    var credits by remember { mutableStateOf<List<Pair<TransactionEntity, Long>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var choosingPayment by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var memberId by rememberSaveable(expense.id, existing?.id) { mutableStateOf(existing?.memberId ?: initialMemberId) }
    var shares by remember { mutableStateOf<List<GroupShareEntity>>(emptyList()) }
    var members by remember { mutableStateOf<List<LocalMemberEntity>>(emptyList()) }
    var repayments by remember { mutableStateOf<List<RepaymentEntity>>(emptyList()) }
    var search by rememberSaveable(existing?.id) { mutableStateOf("") }
    val focus = LocalFocusManager.current
    var initialAmount by rememberSaveable(expense.id, existing?.id) { mutableStateOf(amount) }
    var initialPayer by rememberSaveable(expense.id, existing?.id) { mutableStateOf(payer) }
    var initialized by rememberSaveable(expense.id, existing?.id) { mutableStateOf(false) }
    val initialDate = rememberSaveable(existing?.id) { date }
    val dirty = amount != initialAmount || payer != initialPayer ||
        incomingId != existing?.incomingId || method != (existing?.method ?: "Linked") ||
        dateKnown != (existing == null || existing.receivedAt != null) || dateKnown && date != initialDate || memberId != (existing?.memberId ?: initialMemberId)
    val close = { if (!busy) { if (dirty) discard = true else onDismiss() }; Unit }
    LaunchedEffect(expense.id) {
        try {
            credits = repository.availableRepaymentCredits()
            shares = repository.groupSharesFor(expense.id); members = repository.localMembers(); repayments = repository.repaymentsFor(expense.id)
            if (!initialized) {
                if (memberId != null) {
                    payer = members.firstOrNull { it.id == memberId }?.name ?: payer
                    if (existing == null && amount.isBlank()) shares.firstOrNull { it.memberId == memberId }?.let {
                        amount = shareDecimal(GroupSplitAccounting.owed(it, repayments))
                    }
                }
                initialAmount = amount; initialPayer = payer; initialized = true
            }
            loaded = true
        }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not load incoming payments. Close and retry." }
    }
    val pickerTheme = `in`.txnsense.app.ui.ledgerPickerTheme()
    val leavePicker = { focus.clearFocus(); choosingPayment = false; search = "" }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val personName = members.firstOrNull { it.id == memberId }?.name
    val remainingForPerson = shares.firstOrNull { it.memberId == memberId }?.let { share ->
        GroupSplitAccounting.owed(share, repayments.filter { it.id != existing?.id })
    }
    val unpaid = remainingForPerson ?: Math.addExact(RepaymentAccounting.owed(expense), existing?.amountMinor ?: 0)
    val ranked = RepaymentAccounting.rankCredits(expense, personName ?: payer, unpaid, credits)
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxSheetHeight = (with(LocalDensity.current) { windowHeight.toDp() } - 60.dp).coerceAtLeast(48.dp)
    val chooseCredit: (RepaymentCreditMatch) -> Unit = { match ->
        incomingId = match.row.id; date = match.row.effectiveTimestamp; dateKnown = true; method = "Linked"
        amount = shareDecimal(minOf(unpaid, match.availableMinor)); error = null; leavePicker()
    }
    ModalBottomSheet(onDismissRequest = {
        // Material hides before this callback. Restore it when navigating within the
        // editor, or while a save/discard decision still owns the draft.
        if (choosingPayment) {
            leavePicker()
            scope.launch { sheetState.show() }
        } else {
            close()
            if (busy || dirty) scope.launch { sheetState.show() }
        }
    }, sheetState = sheetState, sheetGesturesEnabled = false, dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.imePadding()) {
      Column(Modifier.fillMaxWidth().heightIn(max = maxSheetHeight)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 9.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (choosingPayment) "Choose a recorded payment" else if (existing == null) "Record repayment" else "Edit repayment",
                Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { if (choosingPayment) leavePicker() else close() }, enabled = !busy,
                modifier = Modifier.semantics {
                    contentDescription = if (choosingPayment) "Back to repayment editor" else "Close repayment editor"
                }) { LedgerIcon(if (choosingPayment) "back" else "close") }
        }
        if (choosingPayment) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Use money already received. It won't be counted twice.", style = MaterialTheme.typography.bodySmall)
                LedgerTextInput(search, { search = it.take(60) }, label = "Search recorded payments", singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            val matches = ranked.filter { search.isBlank() || it.row.counterpartyLabel.orEmpty().contains(search.trim(), ignoreCase = true) }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                if (!loaded) item { Text(if (error == null) "Loading recorded payments…" else "Could not load payments. Close and retry.") }
                else if (matches.isEmpty()) item { Text(if (credits.isEmpty()) "No recorded payments with money available. Record the incoming transaction first, or choose Received cash." else "No matching payments.") }
                items(matches, key = { it.row.id }) { match ->
                    RepaymentMatchRow(match, incomingId == match.row.id, onClick = { chooseCredit(match) })
                }
            }
        } else {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${expense.counterpartyLabel ?: "Expense"}${expense.groupLabel?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
            Text("Still owed ${money(RepaymentAccounting.owed(expense))}", style = MaterialTheme.typography.titleMedium)
            if (shares.isNotEmpty()) {
                if (personName != null) Text("$personName · ${money(remainingForPerson)} available to settle", style = MaterialTheme.typography.titleMedium)
                if (existing == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    shares.filter { GroupSplitAccounting.owed(it, repayments) > 0 }.forEach { share ->
                        val name = members.firstOrNull { it.id == share.memberId }?.name ?: "Local person"
                        `in`.txnsense.app.ui.LedgerChoiceChip(memberId == share.memberId, {
                            memberId = share.memberId; payer = name
                            amount = shareDecimal(GroupSplitAccounting.owed(share, repayments)); error = null
                        }, label = { Text(name) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp), enabled = !busy)
                    }
                }
                if (memberId == null) Text("Choose whose unpaid share this settles.", style = MaterialTheme.typography.bodySmall)
            }
            if (existing == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                `in`.txnsense.app.ui.LedgerChoiceChip(selected = method == "Linked", onClick = { method = "Linked"; error = null }, label = { Text("Recorded payment") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp), enabled = !busy)
                `in`.txnsense.app.ui.LedgerChoiceChip(selected = method == "Cash", onClick = { method = "Cash"; incomingId = null; error = null }, label = { Text("Received cash") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp), enabled = !busy)
            }
            if (existing == null && method == "Linked") {
                Text("Possible matches", style = MaterialTheme.typography.titleSmall)
                if (loaded && ranked.isEmpty()) Text("No recorded payments with money available. Add an incoming payment in Activity first, or choose Received cash.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val shown = ranked.firstOrNull { it.row.id == incomingId }?.let { listOf(it) } ?: ranked.take(3)
                shown.forEach { match ->
                    RepaymentMatchRow(match, incomingId == match.row.id, enabled = !busy, onClick = { chooseCredit(match) })
                }
            }
            if (existing == null && method == "Linked") TextButton(onClick = { choosingPayment = true; focus.clearFocus() }, enabled = loaded && !busy) { Text("Choose a recorded payment") }
            LedgerTextInput(amount, { amount = it; error = null }, label = "Repayment amount (INR)", amountStyle = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            if (shares.isEmpty()) LedgerTextInput(payer, { payer = it.take(40) }, label = "Who paid you? (optional)", singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            if (existing == null) {
                if (method == "Linked") {
                    val selected = credits.firstOrNull { it.first.id == incomingId }
                    if (selected != null) {
                        Text("Recorded payment: ${selected.first.counterpartyLabel ?: "Incoming payment"}", style = MaterialTheme.typography.bodyMedium)
                        Text("${money(selected.second)} available to use. No new money-in will be added.", style = MaterialTheme.typography.bodySmall)
                    } else Text("Choose the incoming payment that repays this expense.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (existing?.incomingId != null || existing == null && method == "Linked") {
                if (existing != null || incomingId != null) Text("Receipt date: ${transactionTime(date)}", style = MaterialTheme.typography.bodySmall)
                if (existing != null) Text("Only this allocation changes. To correct the incoming amount or date, unlink its repayments first and edit that transaction.", style = MaterialTheme.typography.bodySmall)
            } else {
                if (existing?.method == "Legacy") Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = dateKnown, onCheckedChange = { dateKnown = it })
                    Text("I know the repayment date", Modifier.weight(1f))
                }
                if (dateKnown) TextButton(onClick = {
                    val local = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault())
                    android.app.DatePickerDialog(context, pickerTheme, { _, y, m, d ->
                        date = local.withYear(y).withMonth(1).withDayOfMonth(1).withMonth(m + 1).withDayOfMonth(d).toInstant().toEpochMilli()
                    }, local.year, local.monthValue - 1, local.dayOfMonth).show()
                }) { Text("Received: ${transactionTime(date)}") }
                Text(if (existing?.method == "Legacy") "Updating this history does not create an incoming transaction."
                    else "Saving creates one manual cash receipt and links it to this expense.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
          Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val remaining = try {
                val minor = ManualInput(amount, `in`.txnsense.app.core.model.Direction.Credit, date, `in`.txnsense.app.core.model.TransactionType.Other).amountMinor()
                val available = remainingForPerson ?: Math.addExact(RepaymentAccounting.owed(expense), existing?.amountMinor ?: 0)
                (available - minor).takeIf { minor <= available }
            } catch (_: IllegalArgumentException) { null } catch (_: ArithmeticException) { null }
            remaining?.let { Text("${personName?.let { name -> "$name · " }.orEmpty()}Still owed after saving: ${money(it)}", style = MaterialTheme.typography.bodySmall) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(enabled = loaded && !busy && (shares.isEmpty() || memberId != null) && (existing != null || method == "Cash" || incomingId != null),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), onClick = {
                if (busy || existing == null && method != "Cash" && incomingId == null) return@Button
                busy = true
                scope.launch {
                    try {
                        if (existing != null) repository.editRepayment(existing.id, amount, date.takeIf { dateKnown }, payer)
                        else repository.recordRepayment(expense.id, amount, date, payer, incomingId.takeIf { method == "Linked" }, memberId)
                        onSaved()
                        onDismiss()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (e: IllegalArgumentException) { error = e.message ?: "Check the repayment details." }
                    catch (_: Exception) { error = "Could not save repayment. Close and check the history before retrying." }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Saving…" else if (existing != null) "Save repayment" else if (method == "Cash") "Record cash repayment" else "Confirm & link repayment") }
            }
          }
        }
        }
      }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard repayment changes?") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard changes") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}

@Composable
private fun RepaymentMatchRow(match: RepaymentCreditMatch, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(13.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(match.row.counterpartyLabel ?: "Incoming payment", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (selected) Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Text("${money(match.availableMinor)} available · ${transactionTime(match.row.effectiveTimestamp)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${match.reasons.joinToString(" · ").ifBlank { "Other recorded incoming payment" }}. Please confirm.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
