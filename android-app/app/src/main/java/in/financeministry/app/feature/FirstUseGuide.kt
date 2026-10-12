package `in`.financeministry.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class TourChapter(val title: String, val summary: String, val details: String)

private val tourChapters = listOf(
    TourChapter("Your private ledger", "Track money on this device. No account, bank connection or internet access is needed.",
        "The app is independent, not a government or bank service. Your ledger is encrypted locally. SMS access and notifications are optional; manual entry works without them. It is an early alpha, so keep your bank records and check detected transactions."),
    TourChapter("Record it your way", "Use + Add for cash or missing payments. Optionally enable supported bank SMS in Settings.",
        "Enter money out, money in or a transfer, amount and date. More details includes payment method, source and notes. Edit / confirm corrects mistakes and keeps a correction history. Uncertain SMS goes to Review, not confirmed totals. Supported English INR alerts can still be missed; dates use the SMS record’s timestamp, not dates written in its text. Recording notifications offer View and Categorize / Edit."),
    TourChapter("Understand your month", "Home separates your spending, money out and money in. Tap the month to explore another period.",
        "Your spending includes personal and family expenses, gifts and your own group share. Money out / in is confirmed cash flow, not a bank balance. Self transfers, card bill settlements and confirmed duplicates are excluded. Refunds remain separate. Tap Breakdown to see the maths and spending by category. Still owed shows all-date unpaid amounts plus the remaining amount from expenses added in the selected month. People also shows newly owed amounts and repayments received in that month."),
    TourChapter("Find every transaction", "Activity has search, combined filters and totals using the same accounting rules as Home.",
        "Search labels and notes; use the money in / out buttons or open Filters for categories. More filters adds purpose, payment source, manual or edited records, card bill payments and card credits to check. Show transactions applies the choices; Close keeps your previous results. Tap a row for quick category and purpose changes, or press and hold to open details, notes, linked records, repayment history, correction or deletion. Newer / Older pages browse large ledgers. More filters also offers Select transactions: preview a category or compatible source update, then Apply or Undo."),
    TourChapter("Category and purpose", "Food tells you what it was. Personal, Family, For someone else, Group or Self transfer tells you who it was for.",
        "Categories: Other, Food, Travel, Shopping, Bills, Flat expenses, Health, Education, Entertainment, Cash, Income and Investment. Add category in the form creates your own local label. Income / Investment labels do not change money direction. Family counts as your spending. Group records your own share; the rest can be owed to you. For someone else can be repayable or a gift / treat. Use a local person or group label to find these records later."),
    TourChapter("Breakdown and budgets", "Breakdown shows category spending and a comparison with the previous period. Tap a category to see its records.",
        "It uses your own share, not everybody's group expense. Current-month comparison uses equivalent elapsed days and warns when history is incomplete. Refunds are separate. Settings → More settings → Optional tools → Budgets lets you set category limits, see used / left and optionally receive an 80% alert. Budgets are targets, not bank balances."),
    TourChapter("Review with confidence", "Review separates missing details from possible duplicates or own-account transfers.",
        "Enter missing amount or check direction and status before confirming. Categorizing alone does not confirm an uncertain payment. Not a transaction excludes an alert without deleting it; open its Activity details to Restore to review. Possible matches show evidence: keep both, select one counted record, or confirm a self transfer. Original records remain available; match decisions can be undone. Check Category rules conflicts separately. Review is for all dates, even when Home shows one month."),
    TourChapter("Track money owed", "For someone else and Group expenses can track repayments without counting the same receipt twice.",
        "For Group expenses, Split with people includes You and local nicknames. Choose Equal or Custom; every paise must add up to the payment. Save a group default to reuse people and shares, then preview or override it for each expense. Open People to see outstanding or settled balances. Choose the expense if a person owes for more than one, then record a repayment using a recorded payment or cash. Possible receipt matches explain their evidence and require your confirmation. Partial settlements cannot exceed that person's unpaid amount. Linking a payment never counts it twice; unlink keeps the incoming transaction. Old unnamed repayments stay unassigned: unlink and relink them explicitly before assigning people. No contacts or invitations are used."),
    TourChapter("Sources and card bills", "Name your UPI, bank accounts and cards. Paying a credit-card bill is a settlement, not new income.",
        "Settings → Payment sources stores local nicknames and optional last four digits. Compatible messages can map automatically; ambiguous sources stay unassigned for you to choose. Used sources can be retired without losing history. Explicit card-bill alerts are excluded from spending and cash-flow totals; older records can be marked manually. Use Card credits to check carefully: cashback is not automatically a bill repayment."),
    TourChapter("Less repeated work", "Remember merchant categories, schedule reminders and add a home-screen widget.",
        "Settings → More settings → Category rules can apply exact merchant categories to future payments, optionally for one source. Rules never change purpose, amount or confirmation and do not rewrite history. Review reminders can run daily at your chosen time when review is pending. Optional tools has recurring payment reminders, pause / resume and Mark paid linking or manual entry—not automatic payments. The widget offers quick Add and an optional privacy-aware monthly summary."),
    TourChapter("History and safe recovery", "Import up to three months of existing SMS with a preview. Keep an encrypted backup before changing phones.",
        "Settings → More settings → SMS & notifications offers a separate, optional past-message import. Check ready, review and duplicate candidates before saving; you can cancel, discard or undo untouched rows from the last batch. Backup & restore creates a password-protected backup and validates restore before replacing—not merging—the ledger. Keep the password: it cannot be recovered. CSV is a readable report, not a backup. Cross-install imports can need duplicate review. Exports stay where you save them; erasing or uninstalling the app does not remove those files."),
    TourChapter("Make it yours", "You're ready. Add a transaction now, choose optional setup, or return to your ledger.",
        "Settings → Appearance offers System, Light or Dark. More settings → Personalization adds an optional Hi, Name greeting. Capture health shows recording status and recovery options; pausing capture keeps your records. SMS & notifications controls capture and alerts; Data and privacy explains local storage, erasure and reporting bugs. These advanced tools and App tour are under More settings. Finishing or skipping acknowledges this version; the tour is offered once again after an update."),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstUseGuide(captureEnabled: Boolean, hasSources: Boolean, reminderEnabled: Boolean,
    onDone: () -> Unit, onAdd: () -> Unit, onOpenSms: () -> Unit, onOpenSources: () -> Unit, onOpenReminder: () -> Unit,
    isUpdate: Boolean = false, onChapterChanged: () -> Unit = {}) {
    var step by rememberSaveable { mutableStateOf(0) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    val chapter = tourChapters[step.coerceIn(tourChapters.indices)]
    val last = step == tourChapters.lastIndex
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("App tour · ${step + 1} of ${tourChapters.size}", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = onDone) { Text("Skip for now") }
            }
            LinearProgressIndicator(progress = { (step + 1).toFloat() / tourChapters.size }, modifier = Modifier.fillMaxWidth())
            if (step == 0 && isUpdate) Text("After your update · rediscover your tools", style = MaterialTheme.typography.labelMedium)
            Text(chapter.title, style = MaterialTheme.typography.titleLarge)
            Text(chapter.summary, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { detailsExpanded = !detailsExpanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (detailsExpanded) "Less detail" else "More detail")
            }
            if (detailsExpanded) Text(chapter.details, style = MaterialTheme.typography.bodyMedium)
            if (last) {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("+ Add transaction") }
                Text("Optional setup", style = MaterialTheme.typography.labelLarge)
                GuideItem(captureEnabled, "Record supported bank SMS", onOpenSms)
                GuideItem(hasSources, "Name your UPI, accounts and cards", onOpenSources)
                GuideItem(reminderEnabled, "Choose a review reminder", onOpenReminder)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { step--; detailsExpanded = false; onChapterChanged() }, enabled = step > 0) { Text("Back") }
                Button(onClick = { if (last) onDone() else { step++; detailsExpanded = false; onChapterChanged() } }) {
                    Text(if (last) "Start using app" else "Next")
                }
            }
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
