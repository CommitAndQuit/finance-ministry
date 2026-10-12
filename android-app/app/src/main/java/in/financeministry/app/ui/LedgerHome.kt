package `in`.financeministry.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.financeministry.app.data.*
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun LedgerMonthButton(label:String,onClick:()->Unit) {
    TextButton(onClick=onClick,contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp)) {
        Text(label,color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.labelMedium.copy(fontSize=13.sp))
        Spacer(Modifier.width(7.dp));LedgerIcon("down",Modifier.size(16.dp),MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun LedgerHomeSummary(snapshot:LedgerSnapshot,owedCountLabel:String,onMonth:()->Unit,onBreakdown:()->Unit,onPeople:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=19.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        LedgerMonthButton(snapshot.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM uuuu")),onMonth)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            LedgerIcon("lock",Modifier.size(13.dp),colors.onSurfaceVariant)
            Text("Only on your phone",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
        }
    }
    Text("Your spending",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
    LedgerAmount(snapshot.personalSpend,Modifier.padding(top=5.dp))
    Row(Modifier.fillMaxWidth().padding(top=9.dp,bottom=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Personal, family & your group share",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
        TextButton(onClick=onBreakdown,modifier=Modifier.semantics {contentDescription="How totals work"},contentPadding=PaddingValues(0.dp)) {
            Text("Breakdown",style=MaterialTheme.typography.labelMedium);Spacer(Modifier.width(4.dp));LedgerIcon("arrow",Modifier.size(15.dp))
        }
    }
    Row(Modifier.fillMaxWidth().background(colors.surfaceContainer,RoundedCornerShape(17.dp)).padding(horizontal=16.dp,vertical=15.dp)) {
        FlowMetric("Money out","out",snapshot.debit,Modifier.weight(1f))
        VerticalDivider(Modifier.height(45.dp),color=colors.outlineVariant)
        FlowMetric("Money in","incoming",snapshot.credit,Modifier.weight(1f).padding(start=20.dp))
    }
    Row(Modifier.fillMaxWidth().padding(top=14.dp).clickable(onClick=onPeople).semantics {contentDescription="Open repayments"}
        .padding(vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(39.dp).background(colors.secondaryContainer,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) {
            LedgerIcon("people",tint=colors.primary)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Still owed to you",style=MaterialTheme.typography.labelLarge.copy(fontSize=13.sp))
            Text("${compactMoney(snapshot.selectedMonthOutstandingRepayments)} from this month · $owedCountLabel",
                style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
        }
        Text(compactMoney(snapshot.outstandingRepayments),style=MaterialTheme.typography.titleMedium.copy(fontSize=19.sp,fontFeatureSettings="tnum"))
        LedgerIcon("chevron",Modifier.size(16.dp),colors.onSurfaceVariant)
    }
    HorizontalDivider(color=colors.outlineVariant)
}

@Composable private fun FlowMetric(label:String,icon:String,amount:BigInteger,modifier:Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            LedgerIcon(icon,Modifier.size(13.dp),MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(compactMoney(amount),style=MaterialTheme.typography.titleMedium.copy(fontSize=19.sp,letterSpacing=(-.4).sp,fontFeatureSettings="tnum"))
    }
}

@Composable fun LedgerReviewCue(text:String,onClick:()->Unit,icon:String="review") {
    Row(Modifier.fillMaxWidth().padding(top=14.dp,bottom=5.dp).background(MaterialTheme.colorScheme.tertiaryContainer,RoundedCornerShape(10.dp))
        .clickable(onClick=onClick).heightIn(min=48.dp).padding(horizontal=12.dp,vertical=11.dp),
        horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        LedgerIcon(icon,Modifier.size(17.dp),MaterialTheme.colorScheme.onTertiaryContainer)
        Text(text,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onTertiaryContainer)
        LedgerIcon("chevron",Modifier.size(16.dp),MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MonthlyMathSheet(repository:TransactionRepository,snapshot:LedgerSnapshot,onDismiss:()->Unit,onCategories:()->Unit) {
    val revision by repository.revision.collectAsState()
    var refunds by remember { mutableStateOf<BigInteger?>(null) }
    var refundLoadFailed by remember { mutableStateOf(false) }
    var refundRetry by remember { mutableIntStateOf(0) }
    LaunchedEffect(snapshot.selectedMonth,revision,refundRetry) {
        refunds = null; refundLoadFailed = false
        try {
            refunds=repository.spendingInsights(snapshot.selectedMonth, snapshot.selectedMonth.plusMonths(1).minusDays(1)).refundsReceived
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { refundLoadFailed = true }
    }
    ModalBottomSheet(onDismissRequest=onDismiss,dragHandle=null,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top=17.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Your month, explained",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                IconButton(onClick=onDismiss,modifier=Modifier.semantics {contentDescription="Close breakdown"}) {LedgerIcon("close")}
            }
            Column(Modifier.weight(1f,false).verticalScroll(androidx.compose.foundation.rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(snapshot.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM uuuu"))+" · One ledger. Different views of your money.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Your spending",style=MaterialTheme.typography.bodyMedium);LedgerAmount(snapshot.personalSpend)
                Column(Modifier.background(MaterialTheme.colorScheme.background,RoundedCornerShape(15.dp)).padding(15.dp)) {
                    MathRow("Recorded money out",compactMoney(snapshot.debit))
                    MathRow("Less: other people’s shares","− "+compactMoney(snapshot.paidForOthers))
                    HorizontalDivider();MathRow("Your spending",compactMoney(snapshot.personalSpend))
                }
                TextButton(onClick=onCategories,contentPadding=PaddingValues(0.dp)) {Text("See spending by category");Spacer(Modifier.width(4.dp));LedgerIcon("arrow",Modifier.size(15.dp))}
                MathRow("Still owed · all dates",compactMoney(snapshot.outstandingRepayments))
                Text("Repayments reduce what people owe you. They don’t change your share of the original expense.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                MathRow("Refunds received",refunds?.let(::compactMoney) ?: if (refundLoadFailed) "Unavailable" else "Loading…")
                if (refundLoadFailed) {
                    Text("Could not load refunds. Your other totals are unchanged.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { refundRetry++ }) { Text("Retry refunds") }
                }
                Text("Refunds are included in money in and shown separately from spending. Self transfers, card bill payments and unconfirmed payments are excluded from these totals. SMS dates use the message record’s timestamp unless corrected. This is a recorded ledger, not your bank balance.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Button(onClick=onDismiss,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp).padding(bottom=18.dp)) {Text("Got it")}
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MatchingTotalsSheet(snapshot:LedgerSnapshot,onDismiss:()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss,dragHandle=null,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(top=17.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Matching totals",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                IconButton(onClick=onDismiss,modifier=Modifier.semantics {contentDescription="Close matching totals"}) {
                    LedgerIcon("close")
                }
            }
            Column(Modifier.weight(1f,false).verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("${snapshot.resultCount} matching transaction${if(snapshot.resultCount==1L)"" else "s"} · " +
                    snapshot.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM uuuu")),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                MathRow("Money out",compactMoney(snapshot.resultDebit))
                MathRow("Money in",compactMoney(snapshot.resultCredit))
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                MathRow("Your spending",compactMoney(snapshot.resultPersonalSpend))
                Text("The same accounting rules as Home, applied to your current filters. Payments needing review remain excluded. These totals are not your bank balance.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable private fun MathRow(label:String,amount:String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {}.padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        Text(amount,style=MaterialTheme.typography.titleSmall)
    }
}
