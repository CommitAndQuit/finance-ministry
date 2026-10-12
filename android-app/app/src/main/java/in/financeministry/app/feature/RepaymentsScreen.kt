package `in`.financeministry.app.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.financeministry.app.data.*
import `in`.financeministry.app.money
import `in`.financeministry.app.transactionTime
import `in`.financeministry.app.ui.LedgerIcon
import `in`.financeministry.app.ui.LedgerChoiceChip
import `in`.financeministry.app.ui.compactMoney
import java.math.BigInteger
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Standalone entry point retained for transaction-details navigation and UI tests. */
@Composable
fun RepaymentsScreen(repository: TransactionRepository, month: LocalDate, onBack: () -> Unit,
    onExpense: (String) -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("People", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onBack) { Text("Back") }
            }
            PeopleContent(repository, month, {}, onExpense)
        }
    }
}

/** The host owns the toolbar and bottom navigation; this owns only People content and its sheets. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PeopleContent(repository: TransactionRepository, month: LocalDate, onChanged: () -> Unit,
    onOpenTransaction: (String) -> Unit) {
    val revision by repository.revision.collectAsState()
    var summary by remember { mutableStateOf<RepaymentSummary?>(null) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var settled by rememberSaveable { mutableStateOf(false) }
    var personId by rememberSaveable { mutableStateOf<String?>(null) }
    var recordingId by rememberSaveable { mutableStateOf<String?>(null) }
    var recordingMemberId by rememberSaveable { mutableStateOf<String?>(null) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var monthDetails by rememberSaveable { mutableStateOf(false) }
    var peopleView by rememberSaveable { mutableStateOf(true) }
    var monthOnly by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(month, revision, retry) {
        error = false
        try { summary = repository.repaymentSummary(month) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    val data = summary
    val balancesData = data?.let { value ->
        value.copy(expenses = value.expenses.filter { !monthOnly || Instant.ofEpochMilli(it.effectiveTimestamp)
            .atZone(ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1) == month.withDayOfMonth(1) },
            shares = if (peopleView) value.shares else emptyList())
    }
    val people = balancesData?.let(RepaymentAccounting::people).orEmpty()
    val visible = people.filter { (it.outstanding.signum() == 0) == settled }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp)) {
        if (error) item {
            Text("Could not load repayments. Your ledger has not changed.")
            TextButton(onClick = { retry++ }) { Text("Try again") }
        } else if (data == null) item { CircularProgressIndicator(Modifier.padding(24.dp)) }
        else {
            item {
                Text("Still owed to you · all dates", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val amount = money(data.outstanding)
                val decimal = amount.lastIndexOf('.')
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(amount.take(1)) }
                    append(amount.substring(1, decimal))
                    withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(amount.substring(decimal)) }
                }, Modifier.padding(top = 7.dp, bottom = 7.dp),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 39.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                val allDateBalances = RepaymentAccounting.people(data.copy(shares = if (peopleView) data.shares else emptyList()))
                Text("${owedBalanceCountLabel(allDateBalances)} · every repayment accounted for",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(22.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val monthName = month.format(DateTimeFormatter.ofPattern("MMMM"))
                Row(Modifier.fillMaxWidth().padding(vertical = 17.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    RepaymentMetric("Newly owed · $monthName", data.newlyOwed, Modifier.weight(1f))
                    RepaymentMetric("Received back · $monthName", data.received, Modifier.weight(1f))
                }
                TextButton(onClick = { monthDetails = !monthDetails }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (monthDetails) "Hide month breakdown" else "Month breakdown")
                }
                if (monthDetails) {
                    RepaymentMetric("Still unpaid from this month's payments", data.monthOutstanding)
                    Text("Receipts may settle older expenses. Undated legacy repayments stay outside monthly receipts.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LedgerChoiceChip(peopleView, { peopleView = true; personId = null }, label = { Text("By person") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp))
                        LedgerChoiceChip(!peopleView, { peopleView = false; personId = null }, label = { Text("By group") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp))
                    }
                    Row(Modifier.fillMaxWidth().clickable { monthOnly = !monthOnly; personId = null }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(monthOnly, { monthOnly = it; personId = null })
                        Text("Only payments made in ${month.format(DateTimeFormatter.ofPattern("MMMM uuuu"))}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (monthOnly || !peopleView) Text("${if (peopleView) "By person" else "By group"}${if (monthOnly) " · expenses from $monthName" else " · all dates"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerChoiceChip(!settled, { settled = false }, label = { Text("Outstanding") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp))
                    LedgerChoiceChip(settled, { settled = true }, label = { Text("Settled") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 48.dp))
                }
                feedback?.let { Text(it, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium) }
            }
            items(visible, key = { it.id }) { person ->
                Row(Modifier.fillMaxWidth().clickable { personId = person.id }.padding(vertical = 17.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PersonInitial(person.name)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(person.name, style = MaterialTheme.typography.titleSmall)
                        val size = person.expenses.size
                        Text("$size shared expense${if (size == 1) "" else "s"}${if (person.received.signum() > 0) " · ${compactMoney(person.received)} received" else ""}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (person.outstanding.signum() == 0) "Settled" else compactMoney(person.outstanding),
                        Modifier.widthIn(max = 150.dp), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontFeatureSettings = "tnum"))
                    LedgerIcon("chevron", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (visible.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No balances here", style = MaterialTheme.typography.titleLarge)
                    Text(if (settled) "People move to Settled once their recorded share is repaid."
                        else "Nothing is outstanding. Recorded repayments remain in Settled.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Text("Names stay on your phone. Nobody is contacted or invited.", Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    val person = people.firstOrNull { it.id == personId }
    if (person != null && recordingId == null) PersonBalanceSheet(person,
        onDismiss = { personId = null }, onOpenTransaction = onOpenTransaction,
        onRecord = { entry -> recordingId = entry.expense.id; recordingMemberId = entry.memberId })
    data?.expenses?.firstOrNull { it.id == recordingId }?.let { expense ->
        RepaymentEditor(repository, expense, null, onDismiss = { recordingId = null; recordingMemberId = null },
            initialMemberId = recordingMemberId, onSaved = {
                feedback = "Repayment recorded. Your spending is unchanged."; onChanged()
            })
    }
}

@Composable
private fun PersonInitial(name: String, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.red < .5f
    Surface(modifier.size(44.dp), shape = CircleShape, color = if (dark) Color(0xFF332E43) else Color(0xFFECE6F7),
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Box(contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonBalanceSheet(person: PersonRepaymentBalance, onDismiss: () -> Unit,
    onOpenTransaction: (String) -> Unit, onRecord: (PersonExpenseBalance) -> Unit) {
    val unpaid = person.expenses.filter { it.outstandingMinor > 0 }
    var selectedExpense by rememberSaveable(person.id) { mutableStateOf(unpaid.singleOrNull()?.expense?.id) }
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = null,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${person.name}’s balance", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PersonInitial(person.name)
                        Column {
                            Text(money(person.outstanding), style = MaterialTheme.typography.titleLarge)
                            Text(if (person.outstanding.signum() == 0) "All settled" else "Still owed to you",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(person.expenses, key = { it.expense.id }) { entry ->
                    Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(15.dp)) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp)) {
                            RepaymentMathRow(entry.expense.counterpartyLabel ?: "Expense", money(entry.expense.amountMinor))
                            Text("${transactionTime(entry.expense.effectiveTimestamp)} · you paid", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            RepaymentMathRow("${person.name}’s share", money(entry.shareMinor))
                            RepaymentMathRow("Received", money(entry.shareMinor - entry.outstandingMinor))
                            RepaymentMathRow("Still unpaid", money(entry.outstandingMinor))
                            TextButton(onClick = { onDismiss(); onOpenTransaction(entry.expense.id) }) { Text("Expense & repayment history") }
                            if (unpaid.size > 1 && entry.outstandingMinor > 0) LedgerChoiceChip(selectedExpense == entry.expense.id,
                                { selectedExpense = entry.expense.id }, label = { Text("Repay this expense") })
                        }
                    }
                }
                item {
                    RepaymentMathRow("Received so far", money(person.received))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    RepaymentMathRow("Still owed", money(person.outstanding))
                    Text("Recording a repayment updates this balance. It does not change your spending.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (unpaid.size > 1) Text("Choose the expense this repayment settles. Any remaining receipt can be linked to another expense.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(onClick = {
                if (unpaid.isEmpty()) onDismiss() else unpaid.firstOrNull { it.expense.id == selectedExpense }?.let(onRecord)
            }, enabled = unpaid.isEmpty() || unpaid.any { it.expense.id == selectedExpense }, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).heightIn(min = 50.dp)) {
                Text(if (unpaid.isEmpty()) "Done" else "Record repayment")
            }
        }
    }
}

@Composable
private fun RepaymentMetric(label: String, amount: BigInteger, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(compactMoney(amount), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun RepaymentMathRow(label: String, amount: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(amount, Modifier.widthIn(max = 160.dp), style = MaterialTheme.typography.titleSmall)
    }
}
