package `in`.txnsense.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import `in`.txnsense.app.ui.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.txnsense.app.data.*
import `in`.txnsense.app.core.model.transactionCategories
import `in`.txnsense.app.feature.TransactionForm
import `in`.txnsense.app.feature.friendly
import `in`.txnsense.app.feature.expandFromRow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.math.BigInteger

private fun groupedIndian(digits: String): String {
    if (digits.length <= 3) return digits
    val tail = digits.takeLast(3)
    val head = digits.dropLast(3)
    return head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
}
fun money(minor: Long?): String = minor?.let { money(BigInteger.valueOf(it)) } ?: "Amount unknown"
fun money(minor: BigInteger): String {
    val negative = minor.signum() < 0
    val absolute = minor.abs()
    val units = absolute.divide(BigInteger.valueOf(100)).toString()
    val paise = absolute.mod(BigInteger.valueOf(100)).toString().padStart(2, '0')
    return "${if (negative) "-" else ""}₹${groupedIndian(units)}.$paise"
}
fun transactionTime(millis: Long): String = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LedgerApp(repository: TransactionRepository, request: Pair<String, Boolean>?, refreshGeneration: Int = 0,
    reviewRequestGeneration: Int = 0, addRequestGeneration: Int = 0, optionalToolRequest: String? = null,
    quickRequest: Boolean = false, openSettings: Boolean = false, onExitHome: (() -> Unit)? = null,
    consumeOptionalToolRequest: () -> Unit = {}, consumeRequest: () -> Unit) {
    LocalLedgerTheme(repository.preferences) {
        LedgerContent(repository, request, refreshGeneration, reviewRequestGeneration, addRequestGeneration,
            optionalToolRequest, quickRequest, openSettings, onExitHome, consumeOptionalToolRequest, consumeRequest)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LedgerContent(repository: TransactionRepository, request: Pair<String, Boolean>?, refreshGeneration: Int,
    reviewRequestGeneration: Int, addRequestGeneration: Int, optionalToolRequest: String?,
    quickRequest: Boolean, openSettings: Boolean = false, onExitHome: (() -> Unit)? = null,
    consumeOptionalToolRequest: () -> Unit, consumeRequest: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val displayLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val revision by repository.revision.collectAsState()
    var snapshot by remember { mutableStateOf<LedgerSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var form by rememberSaveable { mutableStateOf(false) }
    var quickForm by rememberSaveable { mutableStateOf(false) }
    var dirtyForm by rememberSaveable { mutableStateOf(false) }
    val latestDirtyForm by rememberUpdatedState(dirtyForm)
    val formSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden || !latestDirtyForm })
    var requestWarning by remember { mutableStateOf(false) }
    var requestApproval by remember { mutableIntStateOf(0) }
    var approvedRequest by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var selected by remember { mutableStateOf<TransactionEntity?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var disclosure by remember { mutableStateOf(false) }
    var eraseDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var excludeReview by remember { mutableStateOf<TransactionEntity?>(null) }
    var cardBillDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var handledReviewRequest by remember { mutableIntStateOf(0) }
    var handledAddRequest by remember { mutableIntStateOf(0) }
    var addWarning by remember { mutableStateOf(false) }
    var formDismissWarning by remember { mutableStateOf(false) }
    var optionalToolWarning by remember { mutableStateOf(false) }
    var optionalToolsInitialTab by rememberSaveable { mutableStateOf("Budgets") }
    var notifications by remember { mutableStateOf(repository.preferences.getBoolean("notifications", true)) }
    var captureEnabled by remember { mutableStateOf(repository.captureAllowed()) }
    fun permissionNeedsSettings(): Boolean {
        var owner: android.content.Context = context
        while (owner is android.content.ContextWrapper && owner !is android.app.Activity) owner = owner.baseContext
        val activity = owner as? android.app.Activity ?: return false
        return smsNeedsSettings(
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
            repository.preferences.getBoolean("sms_permission_requested", false),
            androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECEIVE_SMS),
        )
    }
    var smsSettingsRequired by remember { mutableStateOf(permissionNeedsSettings()) }
    fun openSmsSettings() {
        try {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}")))
        } catch (_: Exception) { error = "Open Android Settings → Apps → TxnSense → Permissions → SMS." }
    }
    var notificationAvailable by remember { mutableStateOf(`in`.txnsense.app.sms.TransactionNotifications.available(context)) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var destination by rememberSaveable { mutableStateOf("Overview") }
    var search by rememberSaveable { mutableStateOf("") }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(openSettings) }
    var settingsSection by rememberSaveable { mutableStateOf<String?>(null) }
    var advancedSettings by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings) { if (!settings) advancedSettings = false }
    var showInsights by rememberSaveable { mutableStateOf(false) }
    var showRepayments by rememberSaveable { mutableStateOf(false) }
    var showBatch by rememberSaveable { mutableStateOf(false) }
    var batchSelectedIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var showCategoryTip by rememberSaveable { mutableStateOf(false) }
    var showMatches by rememberSaveable { mutableStateOf(false) }
    var matchHistory by rememberSaveable { mutableStateOf(false) }
    var selectedMatch by remember { mutableStateOf<MatchDecisionEntity?>(null) }
    var categoryConflictCount by remember { mutableIntStateOf(0) }
    var reviewAvailable by remember { mutableStateOf(false) }
    var reviewCount by remember { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableStateOf(java.time.YearMonth.now().toString()) }
    var showGuide by key(BuildConfig.VERSION_CODE) { rememberSaveable { mutableStateOf(`in`.txnsense.app.feature.shouldShowAppTour(
        repository.preferences.getInt(`in`.txnsense.app.feature.APP_TOUR_VERSION_KEY, 0), BuildConfig.VERSION_CODE)) } }
    val tourIsUpdate = repository.preferences.getBoolean("onboarding_complete", false)
    fun finishGuide() {
        repository.preferences.edit().putBoolean("onboarding_complete", true)
            .putInt(`in`.txnsense.app.feature.APP_TOUR_VERSION_KEY, BuildConfig.VERSION_CODE).apply()
        showGuide = false
    }
    var paymentSources by remember { mutableStateOf(emptyList<PaymentSourceEntity>()) }
    var sourceKindFilter by rememberSaveable { mutableStateOf("All") }
    var availableCategories by remember { mutableStateOf(transactionCategories) }
    var showSummaryDetails by rememberSaveable { mutableStateOf(false) }
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    var owedCountLabel by remember { mutableStateOf("0 people") }
    var saveFeedback by remember { mutableStateOf<String?>(null) }
    var backupDismissedAt by remember { mutableLongStateOf(repository.preferences.getLong("backup_reminder_dismissed_at",0)) }
    LaunchedEffect(saveFeedback) { if(saveFeedback!=null) { kotlinx.coroutines.delay(3500);saveFeedback=null } }
    var showFilters by remember { mutableStateOf(false) }
    var resultsTotalsSnapshot by remember { mutableStateOf<LedgerSnapshot?>(null) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var reviewWarning by remember { mutableStateOf(false) }
    var draftPurposeFilter by remember { mutableStateOf("All") }
    var draftOriginFilter by remember { mutableStateOf("All") }
    var draftDirectionFilter by remember { mutableStateOf("All") }
    var draftPaymentKindFilter by remember { mutableStateOf("All") }
    var draftCategories by remember { mutableStateOf(emptyList<String>()) }
    var draftSourceIds by remember { mutableStateOf(emptyList<String>()) }
    var draftSearch by remember { mutableStateOf("") }
    var preferredName by remember { mutableStateOf(repository.preferences.getString("preferred_name", "").orEmpty()) }
    var preferredNameDraft by rememberSaveable { mutableStateOf(preferredName) }
    val homeListState = rememberLazyListState()
    val activityListState = rememberLazyListState()
    val reviewListState = rememberLazyListState()
    val ledgerListState = when(destination) { "Transactions" -> activityListState; "Review" -> reviewListState; else -> homeListState }
    BackHandler(enabled = settings && !form && selectedId == null) {
        if (!busy) { if (settingsSection != null) settingsSection = null else settings = false }
    }
    BackHandler(enabled = !settings && !form && selectedId == null && destination != "Overview") {
        destination = "Overview"; offset = 0; snapshot = null
    }
    BackHandler(enabled = showBatch && destination == "Transactions" && !form) {
        showBatch = false; batchSelectedIds = arrayListOf()
    }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        captureEnabled = repository.captureAllowed()
        smsSettingsRequired = permissionNeedsSettings()
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationAvailable = `in`.txnsense.app.sms.TransactionNotifications.available(context)
    }
    fun changeCapture() {
        if (captureEnabled) { repository.preferences.edit().putBoolean("sms_disclosure", false).apply(); captureEnabled = false }
        else if (smsSettingsRequired && repository.preferences.getBoolean("sms_disclosure", false)) openSmsSettings()
        else disclosure = true
    }
    fun changeNotifications(enabled: Boolean) {
        notifications = enabled; repository.preferences.edit().putBoolean("notifications", enabled).apply()
        if (enabled && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(refreshGeneration) {
        captureEnabled = repository.captureAllowed()
        smsSettingsRequired = permissionNeedsSettings()
        notifications = repository.preferences.getBoolean("notifications", true)
        notificationAvailable = `in`.txnsense.app.sms.TransactionNotifications.available(context)
    }
    BackHandler(enabled = selected != null && !form) { if (!busy) { selected = null; selectedId = null } }
    LaunchedEffect(selectedId, revision, form) {
        if (selectedId != null && (!form || selected?.id != selectedId)) {
            try { selected = repository.get(selectedId!!); if (selected == null) { selectedId = null; form = false; error = "This transaction no longer exists." } }
            catch (_: Exception) { error = "Cannot open this transaction."; selectedId = null; form = false }
        }
        try {
            selectedMatch = selectedId?.let { id -> repository.matchDecisions().firstOrNull {
                it.undoneAt == null && it.action != "KeepBoth" && (it.firstId == id || it.secondId == id)
            } }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { selectedMatch = null; error = "Could not load match history. Your ledger is unchanged." }
    }
    LaunchedEffect(revision, offset, filter, destination, search, refreshGeneration, selectedMonth, sourceKindFilter) {
        loading = true
        try {
            val monthSummary = repository.snapshot(0, "All", java.time.YearMonth.parse(selectedMonth).atDay(1), LocalDate.now())
            val result = if (destination == "Review" || filter == "Review") repository.reviewQueue(offset).copy(
                debit = monthSummary.debit, credit = monthSummary.credit,
                dailyDebit = monthSummary.dailyDebit, dailyCredit = monthSummary.dailyCredit,
                personalSpend = monthSummary.personalSpend, paidForOthers = monthSummary.paidForOthers,
                outstandingRepayments = monthSummary.outstandingRepayments,
                selectedMonth = monthSummary.selectedMonth,
                categoryBalances = monthSummary.categoryBalances)
                else repository.snapshot(if (destination != "Transactions") 0 else offset,
                    if (destination != "Transactions") "All" else filter,
                    java.time.YearMonth.parse(selectedMonth).atDay(1), LocalDate.now(),
                    if (destination == "Transactions") search else "",
                    sourceKindFilter)
            snapshot = result
            if (java.time.YearMonth.parse(selectedMonth) == java.time.YearMonth.now()) {
                // Optional surfaces must never turn a healthy local ledger into a storage error.
                runCatching { `in`.txnsense.app.widget.FinanceWidget.updateAll(context) }
                runCatching { `in`.txnsense.app.sms.BudgetAlerts.evaluate(context, repository) }
            }
            paymentSources = repository.paymentSources()
            availableCategories = repository.categories()
            reviewCount = repository.reviewCount()
            reviewAvailable = reviewCount > 0
            categoryConflictCount = repository.categoryConflicts().size
            owedCountLabel = `in`.txnsense.app.feature.owedBalanceCountLabel(
                RepaymentAccounting.people(repository.repaymentSummary(java.time.YearMonth.parse(selectedMonth).atDay(1))))
            if (result.rows.isEmpty() && offset > 0) offset = maxOf(0, offset - 100)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Cannot open encrypted storage. Existing data was preserved. Erase all only if you intend to delete it." }
        finally { loading = false }
    }
    LaunchedEffect(reviewRequestGeneration, busy) {
        if (busy) return@LaunchedEffect
        if (reviewRequestGeneration > 0 && reviewRequestGeneration != handledReviewRequest) {
            handledReviewRequest = reviewRequestGeneration
            if (form && dirtyForm) reviewWarning = true
            else {
                settings = false; settingsSection = null; selected = null; selectedId = null; form = false; showInsights = false; showRepayments = false; showBatch = false; showMatches = false
                filter = "All"; sourceKindFilter = "All"; destination = "Review"; offset = 0; snapshot = null
            }
        }
    }
    LaunchedEffect(addRequestGeneration, busy) {
        if (busy || addRequestGeneration == 0 || addRequestGeneration == handledAddRequest) return@LaunchedEffect
        handledAddRequest = addRequestGeneration
        if (form && dirtyForm) addWarning = true
        else {
            dirtyForm = false; quickForm = false; selected = null; selectedId = null; form = true
            settings = false; settingsSection = null; showInsights = false; showRepayments = false; showBatch = false; showMatches = false
            destination = "Overview"; error = null
        }
    }
    LaunchedEffect(optionalToolRequest, busy) {
        if (busy || optionalToolRequest == null) return@LaunchedEffect
        if (form && dirtyForm) optionalToolWarning = true
        else {
            optionalToolsInitialTab = optionalToolRequest
            selected = null; selectedId = null; form = false; settings = true; settingsSection = "Optional tools"
            showInsights = false; showRepayments = false; showBatch = false; showMatches = false; error = null
            consumeOptionalToolRequest()
        }
    }
    LaunchedEffect(selectedMonth) { ledgerListState.scrollToItem(0) }
    LaunchedEffect(selectedMonth, filter, search, offset, destination) {
        batchSelectedIds = arrayListOf()
        if (destination != "Transactions") showBatch = false
    }
    LaunchedEffect(request, requestApproval, busy) {
        if (busy) return@LaunchedEffect
        if (request != null) {
            if (form && dirtyForm && approvedRequest != request) { requestWarning = true; return@LaunchedEffect }
            approvedRequest = null
            dirtyForm = false
            quickForm = quickRequest
            selected = null; selectedId = null; form = false; settings = false; showInsights = false; showRepayments = false; showBatch = false; showMatches = false; destination = "Overview"
            try { selected = repository.get(request.first); if (selected == null) error = "This transaction no longer exists." else { selectedId = selected!!.id; form = request.second; error = null } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Cannot open this transaction. Data was preserved." }
            consumeRequest()
        }
    }
    // When launched as an overlay from the Spend Tracker home, never render the old ledger home:
    // pop back once nothing else is showing, and stay blank while a tapped transaction loads.
    val hasContent = settings || form || quickForm || selectedId != null || selected != null || destination != "Overview" || filter == "Review"
    if (onExitHome != null && !hasContent) {
        if (request == null) LaunchedEffect("exit-home") { onExitHome() }
        Surface(Modifier.fillMaxSize()) {}
        return
    }
    if (showMatches && !settings && selectedId == null && !form) {
        `in`.txnsense.app.feature.MatchingReviewScreen(repository, initialHistory = matchHistory,
            onDetails = { showMatches = false; destination = "Review"; offset = 0; snapshot = null },
            onNavigate = { showMatches = false; destination = it; offset = 0; snapshot = null },
            onSettings = { showMatches = false; settings = true; settingsSection = null },
            onTransaction = { selected = null; selectedId = it; matchHistory = true },
            onBusyChange = { busy = it })
        return
    }
    if (showRepayments && selectedId == null && !form) {
        `in`.txnsense.app.feature.RepaymentsScreen(repository, java.time.YearMonth.parse(selectedMonth).atDay(1),
            onBack = { showRepayments = false }, onExpense = { selected = null; selectedId = it })
        return
    }
    if (showInsights) {
        `in`.txnsense.app.feature.SpendingInsightsScreen(repository, java.time.YearMonth.parse(selectedMonth).atDay(1),
            onBack = { showInsights = false },
            onCategory = { category ->
                showInsights = false; filter = combinedFilter("All", "All", "Debit", listOf(category)); search = ""
                destination = "Transactions"; offset = 0; snapshot = null
            },
            onReview = { showInsights = false; destination = "Review"; offset = 0; snapshot = null },
            onBudget = { showInsights = false; optionalToolsInitialTab = "Budgets"; settings = true; settingsSection = "Optional tools" })
        return
    }
    Surface(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize()) {
        val pageModifier = Modifier.safeDrawingPadding().fillMaxSize()
        Column(pageModifier) {
            Row(Modifier.fillMaxWidth().heightIn(min=72.dp).padding(start=20.dp,end=20.dp,top=12.dp,bottom=8.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    if(destination=="Overview") Text("TxnSense",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(when(destination) {
                        "Overview" -> preferredName.trim().takeIf {it.isNotEmpty()}?.let {"Hi, $it"} ?: "Your ledger"
                        "Transactions" -> "Activity"
                        "People" -> "People"
                        else -> "Review"
                    },style=MaterialTheme.typography.headlineMedium)
                }
                run {
                    IconButton(onClick={settings=true;settingsSection=null},modifier=Modifier.semantics {contentDescription="Settings"}) {LedgerIcon("gear")}
                    FilledIconButton(onClick={selected=null;selectedId=null;quickForm=false;form=true},modifier=Modifier.size(48.dp).semantics {contentDescription="Add transaction"},shape=RoundedCornerShape(14.dp)) {LedgerIcon("plus")}
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (selectedId != null && selected == null) { Text("Loading transaction…") }
            if(destination=="People") {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    `in`.txnsense.app.feature.PeopleContent(repository,java.time.YearMonth.parse(selectedMonth).atDay(1),
                        onChanged={saveFeedback="Repayment recorded. Your spending is unchanged."},
                        onOpenTransaction={selected=null;selectedId=it})
                }
                LedgerNavigation(destination,reviewCount) {next->if(next!=destination){destination=next;offset=0;snapshot=null}}
            } else {
                // Lazy layout can evaluate its item provider during measurement. Capture every
                // structural input together so its count and keys cannot see different revisions.
                val listDestination = destination
                val listSnapshot = snapshot
                val listLoading = loading
                val listGuide = showGuide
                val listOffset = offset
                val rows = if (listDestination == "Overview") listSnapshot?.rows.orEmpty().take(4)
                    else listSnapshot?.rows.orEmpty()
                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = ledgerListState,
                    contentPadding = PaddingValues(start=20.dp,end=20.dp,bottom=24.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (listDestination == "Overview" && listGuide) item {
                    `in`.txnsense.app.feature.FirstUseGuide(captureEnabled, paymentSources.any { it.active },
                        repository.preferences.getBoolean("review_reminder", false),
                        onDone = { finishGuide() },
                        onAdd = { finishGuide(); selected = null; selectedId = null; form = true },
                        onOpenSms = { finishGuide(); settings = true; settingsSection = "SMS & messages" },
                        onOpenSources = { finishGuide(); settings = true; settingsSection = "Payment sources" },
                        onOpenReminder = { finishGuide(); settings = true; settingsSection = "Review reminders" },
                        isUpdate = tourIsUpdate,
                        onChapterChanged = { scope.launch { ledgerListState.scrollToItem(0) } })
                }
                if (listDestination == "Overview") item {
                    listSnapshot?.let { data ->
                        LedgerHomeSummary(data,owedCountLabel,onMonth={showMonthPicker=true},onBreakdown={showSummaryDetails=true},
                            onPeople={destination="People";offset=0;snapshot=null})
                    }
                    if(repository.preferences.getBoolean("capture_error",false)) LedgerReviewCue("A message could not be recorded. Add it manually if needed.",{selected=null;selectedId=null;form=true},"info")
                    else if(!captureEnabled && repository.preferences.getBoolean("sms_permission_requested",false)) {
                        val smsAllowed = context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        LedgerReviewCue(if (smsAllowed) "SMS capture is paused. Manage settings" else "SMS permission blocked. Manage settings",
                            {settings=true;settingsSection="SMS & messages"},"info")
                    }
                    if(reviewAvailable) LedgerReviewCue("$reviewCount payment${if(reviewCount==1)"" else "s"} needs a quick check",{destination="Review";offset=0;snapshot=null})
                    val now=System.currentTimeMillis()
                    val lastBackup=repository.preferences.getLong("last_backup_at",0)
                    if((listSnapshot?.resultCount ?: 0)>=20 && now-lastBackup>30L*24*60*60*1000 && now-backupDismissedAt>7L*24*60*60*1000) {
                        Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                            TextButton(onClick={settings=true;settingsSection="Backup & export"},modifier=Modifier.weight(1f),contentPadding=PaddingValues(0.dp)) {Text("Keep a backup of your ledger",style=MaterialTheme.typography.bodySmall)}
                            IconButton(onClick={backupDismissedAt=now;repository.preferences.edit().putLong("backup_reminder_dismissed_at",now).apply()},modifier=Modifier.semantics {contentDescription="Dismiss backup reminder"}) {LedgerIcon("close",Modifier.size(16.dp))}
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top=19.dp,bottom=5.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text("Recent activity",style=MaterialTheme.typography.titleMedium.copy(fontSize=17.sp))
                        TextButton(onClick={destination="Transactions";offset=0;snapshot=null},contentPadding=PaddingValues(0.dp)) {Text("View all",style=MaterialTheme.typography.labelMedium);Spacer(Modifier.width(4.dp));LedgerIcon("arrow",Modifier.size(15.dp))}
                    }
                }
                if (listDestination == "Transactions") item {
                    if (showCategoryTip) Text("Open a transaction, then choose Category & purpose to save it or remember the merchant for future payments.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        LedgerMonthButton(java.time.YearMonth.parse(selectedMonth).format(DateTimeFormatter.ofPattern("MMMM uuuu")), {showMonthPicker=true})
                        TextButton(onClick = {
                            focusManager.clearFocus(); keyboardController?.hide()
                            draftPurposeFilter = purposePart(filter); draftOriginFilter = originPart(filter); draftDirectionFilter = directionPart(filter); draftPaymentKindFilter = paymentKindPart(filter); draftCategories = categoryParts(filter); draftSourceIds = sourceParts(filter); draftSearch = search; showFilters = true
                        }, contentPadding=PaddingValues(0.dp), modifier = Modifier.semantics { contentDescription = "Filter transactions" }) { LedgerIcon("filter",Modifier.size(15.dp));Spacer(Modifier.width(4.dp));Text("Filters",style=MaterialTheme.typography.labelMedium) }
                    }
                    LedgerSearchField(search, { search = it.take(80); offset = 0; snapshot = null },
                        onSearch = { focusManager.clearFocus(); keyboardController?.hide() },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 13.dp))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {listOf("All" to "All","Debit" to "Money out","Credit" to "Money in").forEach { (value,label) ->
                        LedgerChoiceChip(directionPart(filter)==value,{filter=combinedFilter(purposePart(filter),originPart(filter),value,categoryParts(filter),sourceParts(filter),paymentKindPart(filter));offset=0;snapshot=null},label={Text(label)},shape=RoundedCornerShape(10.dp),modifier=Modifier.heightIn(min=48.dp))
                    }}
                    if(showBatch) TextButton(onClick={showBatch=false;batchSelectedIds=arrayListOf()},contentPadding=PaddingValues(0.dp)) {Text("Done selecting",style=MaterialTheme.typography.labelMedium)}
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val detailFilters = combinedFilter(purposePart(filter), originPart(filter), "All", categoryParts(filter), sourceParts(filter), paymentKindPart(filter))
                        if (detailFilters != "All") {
                            LedgerChoiceChip(selected = true, onClick = {
                                focusManager.clearFocus(); keyboardController?.hide()
                                draftPurposeFilter = purposePart(filter); draftOriginFilter = originPart(filter); draftDirectionFilter = directionPart(filter); draftPaymentKindFilter = paymentKindPart(filter); draftCategories = categoryParts(filter); draftSourceIds = sourceParts(filter); draftSearch = search; showFilters = true
                            }, label = { Text(filterLabel(filter, paymentSources)) })
                        }
                        if (filter != "All" || search.isNotBlank()) TextButton(onClick = { filter = "All"; search = ""; offset = 0; snapshot = null },
                            modifier = Modifier.semantics { contentDescription = "Clear transaction filters" }) { Text("Clear") }
                    }
                    snapshot?.let {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("${it.resultCount} transaction${if (it.resultCount == 1L) "" else "s"}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = {
                                focusManager.clearFocus();keyboardController?.hide();resultsTotalsSnapshot=it
                            },contentPadding=PaddingValues(0.dp)) { Text("Totals",style=MaterialTheme.typography.labelMedium);Spacer(Modifier.width(4.dp));LedgerIcon("down",Modifier.size(15.dp)) }
                        }
                    }
                    if (showBatch) TextButton(onClick = {
                        batchSelectedIds = ArrayList(snapshot?.rows.orEmpty().map { it.id })
                    }) { Text("Select visible records") }
                }
                if (listDestination == "Review") item {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text("All dates · $reviewCount remaining",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick={matchHistory=false;showMatches=true}) {Text("Possible matches")}
                    }
                    Text("A quick check keeps your totals accurate. These payments are excluded until you confirm.", style = MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if (categoryConflictCount > 0) TextButton(onClick = { settings = true; settingsSection = "Category rules" }) {
                        Text("$categoryConflictCount category choices needed · separate from financial review")
                    }
                }
                if (listLoading) item { Text("Loading transactions…") }
                else if (rows.isEmpty()) item {
                    if (destination == "Transactions") Column(Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val filtered = filter != "All" || search.isNotBlank()
                        Text(if (filtered) "No matching transactions" else "No transactions yet", style = MaterialTheme.typography.titleMedium)
                        Text(if (filtered) "Try another month or clear the filters." else "Add one yourself, or enable SMS capture in Settings.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (filtered) TextButton(onClick = { filter = "All"; search = ""; offset = 0; snapshot = null }) { Text("Clear filters") }
                        else TextButton(onClick = { selected = null; selectedId = null; form = true }) { Text("Add transaction") }
                    } else if(destination=="Review") Column(Modifier.fillMaxWidth().padding(vertical=48.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {LedgerIcon("check",Modifier.size(36.dp),MaterialTheme.colorScheme.primary);Text("All caught up",style=MaterialTheme.typography.titleLarge);Text("No saved payments need checking.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick={destination="Overview";offset=0;snapshot=null}) {Text("Back to Home")}}
                    else Text("No transactions here yet. Add one manually or enable new SMS capture in Settings.",style=MaterialTheme.typography.bodyMedium)
                }
                rows.groupBy { Instant.ofEpochMilli(it.effectiveTimestamp).atZone(ZoneId.systemDefault()).toLocalDate() }.forEach { (day, rows) ->
                    if(listDestination!="Review") item("day-$day") { Text(day.format(DateTimeFormatter.ofPattern("d MMMM",displayLocale)).uppercase(displayLocale), modifier=Modifier.padding(top=14.dp,bottom=2.dp),style = MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(rows, key = { it.id }) { row ->
                        if(listDestination=="Review") LedgerReviewCard(row,
                            onConfirm={selected=row;selectedId=row.id;quickForm=false;form=true;showCategoryTip=false},onExclude={excludeReview=row})
                        else {
                        val selecting=showBatch && destination=="Transactions"
                        LedgerTransactionRow(row,paymentSources.firstOrNull {it.id==row.paymentSourceId}?.nickname,
                            onOpen={if(selecting) batchSelectedIds=ArrayList(if(row.id in batchSelectedIds)batchSelectedIds-row.id else batchSelectedIds+row.id)
                                else {selected=row;selectedId=row.id;showCategoryTip=false}},
                            onClassify={selected=row;selectedId=row.id;quickForm=row.reviewState!="NeedsReview";form=true;showCategoryTip=false},
                            selecting=selecting,selected=row.id in batchSelectedIds)
                        }
                    }
                }
                if (listDestination != "Overview" && (listOffset > 0 || listSnapshot?.hasOlder == true)) item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { offset = maxOf(0, offset - 100); snapshot = null }, enabled = !loading && offset > 0) { Text("Newer") }
                    Text("Page ${offset / 100 + 1}", Modifier.padding(top = 12.dp))
                    TextButton(onClick = { offset += 100; snapshot = null }, enabled = !loading && snapshot?.hasOlder == true && offset <= Int.MAX_VALUE - 201) { Text("Older") }
                }
                }
                }
                if (showBatch && destination == "Transactions") {
                    `in`.txnsense.app.feature.BatchCleanupActions(repository, snapshot?.rows.orEmpty(), batchSelectedIds) {
                        batchSelectedIds = arrayListOf()
                    }
                }
                LedgerNavigation(destination,reviewCount) { next ->
                    if (next != destination) { destination = next; offset = 0; snapshot = null }
                }
            }
        }
        saveFeedback?.let {message->
            Surface(Modifier.align(Alignment.BottomCenter).padding(start=16.dp,end=16.dp,bottom=92.dp),shape=RoundedCornerShape(13.dp),color=MaterialTheme.colorScheme.onSurface,contentColor=MaterialTheme.colorScheme.background,shadowElevation=4.dp) {
                Text(message,Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)
            }
        }
      }
    }
    if (settings && !form && selectedId == null) {
        val latestSettingsBusy by rememberUpdatedState(busy)
        val settingsSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !latestSettingsBusy })
        fun backFromSettings() {
            if (!busy) {
                if (settingsSection == null) settings = false
                else { settingsSection = null; scope.launch { settingsSheet.show() } }
            }
        }
        val settingsScroll = key(settingsSection) { rememberScrollState() }
        ModalBottomSheet(onDismissRequest = ::backFromSettings, sheetState = settingsSheet, dragHandle = null,
            sheetGesturesEnabled = !busy, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            LedgerSheetSystemBars()
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 9.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (settingsSection != null) IconButton(onClick = { settingsSection = null }, enabled = !busy,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Back to settings" }) {
                        LedgerIcon("back")
                    }
                    Text(when (settingsSection) {
                        null -> "Your app"
                        "Backup & export" -> "Backup & restore"
                        "SMS & messages" -> "SMS & notifications"
                        else -> settingsSection.orEmpty()
                    }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { settings = false; settingsSection = null }, enabled = !busy,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Close settings" }) { LedgerIcon("close") }
                }
                Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(settingsScroll)
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(if (settingsSection == null) 0.dp else 8.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                when (settingsSection) {
                    null -> {
                        Text("A simple ledger, on your terms.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 20.dp))
                        SettingsToggleRow("SMS capture", when {
                            captureEnabled -> "On · supported bank alerts"
                            smsSettingsRequired -> "SMS permission blocked · manual entry still works"
                            else -> "Paused · manual entry still works"
                        }, captureEnabled, !busy) { changeCapture() }
                        SettingsToggleRow("Recording notifications", if (notifications && !notificationAvailable)
                            "Blocked by Android · manage below" else "Quick access to category and purpose", notifications, !busy, ::changeNotifications)
                        val appearance = LocalLedgerAppearance.current
                        SettingsRow("Appearance", "${appearance.name} theme", icon = "moon") { settingsSection = "Appearance" }
                        SettingsRow("Backup & restore", "Protect your local ledger") { settingsSection = "Backup & export" }
                        SettingsRow("Payment sources", "Bank accounts, cards & UPI") { settingsSection = "Payment sources" }
                        SettingsRow("Help & feedback", "Report a bug or suggest a feature") { settingsSection = "Help & feedback" }
                        SettingsRow("More settings", "Rules, reminders, privacy and other tools", icon = if (advancedSettings) "back" else "down") {
                            advancedSettings = !advancedSettings
                        }
                        if (advancedSettings) {
                        SettingsRow("SMS & notifications", "Permissions, capture health and history import") { settingsSection = "SMS & messages" }
                        SettingsRow("Category rules", "Remember merchant categories for future payments") { settingsSection = "Category rules" }
                        SettingsRow("Review reminders", if (repository.preferences.getBoolean("review_reminder", false)) "Daily reminder is on" else "Off") { settingsSection = "Review reminders" }
                        SettingsRow("Optional tools", "Budgets, payment reminders and home-screen widget") { settingsSection = "Optional tools" }
                        SettingsRow("New message formats", if (repository.learningEnabled()) "Learning from unreadable alerts" else "Off") { settingsSection = "New message formats" }
                        SettingsRow("Personalization", if (preferredName.isBlank()) "Set an optional home greeting" else "Home says Hi, $preferredName") {
                            preferredNameDraft = preferredName; settingsSection = "Personalization"
                        }
                        SettingsRow("Data and privacy", "Local storage, erasure and recovery") { settingsSection = "Data & privacy" }
                        SettingsRow("App tour", "Explore features and optional setup again") {
                            showGuide = true; settings = false; settingsSection = null; destination = "Overview"
                            scope.launch { ledgerListState.scrollToItem(0) }
                        }
                        }
                        Text("TxnSense is an independent app.\nYour ledger stays on this phone.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 20.dp))
                    }
                    "Appearance" -> {
                        Text("Choose a theme, or follow your phone. This choice stays on your device and is included in encrypted backups.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val appearance = LocalLedgerAppearance.current
                        LedgerAppearance.entries.forEach { mode ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(
                                role = androidx.compose.ui.semantics.Role.RadioButton,
                                onClick = { repository.preferences.edit().putString(APPEARANCE_KEY, mode.name).apply() })
                                .semantics { this.selected = appearance == mode }, verticalAlignment = Alignment.CenterVertically) {
                                Text(mode.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                RadioButton(appearance == mode, onClick = null)
                            }
                        }
                    }
                    "Help & feedback" -> `in`.txnsense.app.feature.SupportPanel()
                    "Personalization" -> {
                        Text("A name is optional and stays on this device.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = preferredNameDraft, onValueChange = { preferredNameDraft = it.take(40) },
                            label = { Text("Preferred name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            preferredName = preferredNameDraft.trim()
                            repository.preferences.edit().putString("preferred_name", preferredName).apply()
                            settingsSection = null
                        }, modifier = Modifier.fillMaxWidth()) { Text("Save name") }
                    }
                    "SMS & messages" -> {
                        Text("Record supported bank alerts as transactions. You can always add or correct a transaction yourself.")
                        TextButton(onClick = { settingsSection = "Capture health" }) { Text("Capture health") }
                        Button(onClick = ::changeCapture, enabled = !busy) { Text(if (captureEnabled) "Pause SMS capture" else if (smsSettingsRequired) "Open SMS permission settings" else "Enable SMS capture") }
                        Text(when {
                            captureEnabled -> "SMS capture active"
                            context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED -> "SMS permission not allowed · Manual entry still works"
                            else -> "SMS capture paused · You can enable it above"
                        }, style = MaterialTheme.typography.bodySmall)
                        if (smsSettingsRequired) Text("Android is no longer showing the SMS permission prompt. Allow SMS in Android settings, then return here. Manual entry still works.", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Recording notifications", Modifier.padding(top = 12.dp))
                            Switch(checked = notifications, onCheckedChange = ::changeNotifications, enabled = !busy)
                        }
                        if (notifications && !notificationAvailable) {
                            Text("Android notifications are blocked. SMS capture can still record transactions.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Open notification settings") }
                        }
                        `in`.txnsense.app.feature.HistoricalImportPanel(repository)
                    }
                    "Capture health" -> `in`.txnsense.app.feature.CaptureHealthPanel(repository, refreshGeneration,
                        onAdd = { settings = false; settingsSection = null; selected = null; selectedId = null; quickForm = false; form = true },
                        onImport = { settingsSection = "Import past messages" },
                        onManageCapture = { settingsSection = "SMS & messages" },
                        onReminder = { settingsSection = "Review reminders" })
                    "Import past messages" -> `in`.txnsense.app.feature.HistoricalImportPanel(repository)
                    "Payment sources" -> {
                        `in`.txnsense.app.feature.PaymentSourcesPanel(repository)
                        Spacer(Modifier.height(16.dp))
                        `in`.txnsense.app.feature.PaymentMethodsPanel(repository)
                    }
                    "Backup & export" -> `in`.txnsense.app.feature.BackupPanel(repository, java.time.YearMonth.parse(selectedMonth).atDay(1),
                        onBusyChange = { busy = it }, onRestored = {
                            preferredName = repository.preferences.getString("preferred_name", "").orEmpty()
                            captureEnabled = repository.captureAllowed(); notifications = repository.preferences.getBoolean("notifications", true)
                            showGuide = `in`.txnsense.app.feature.shouldShowAppTour(
                                repository.preferences.getInt(`in`.txnsense.app.feature.APP_TOUR_VERSION_KEY, 0), BuildConfig.VERSION_CODE)
                        })
                    "Category rules" -> `in`.txnsense.app.feature.CategoryRulesPanel(repository,
                        onConflict = { id -> settings = false; selected = null; selectedId = id; quickForm = true; form = true },
                        onFromTransaction = {
                            settings = false; settingsSection = null; destination = "Transactions"; showCategoryTip = true; offset = 0; snapshot = null
                        })
                    "Review reminders" -> `in`.txnsense.app.feature.ReviewReminderPanel(repository, refreshGeneration)
                    "New message formats" -> `in`.txnsense.app.feature.PatternLearningPanel(repository, refreshGeneration)
                    "Optional tools" -> `in`.txnsense.app.feature.OptionalToolsPanel(repository,
                        java.time.YearMonth.parse(selectedMonth).atDay(1), initialTab = optionalToolsInitialTab, onAddPayment = {
                            settings = false; settingsSection = null; selected = null; selectedId = null; quickForm = false; form = true
                        })
                    "Data & privacy" -> {
                        `in`.txnsense.app.feature.PrivacyPanel(onErase = { eraseDialog = true }, busy = busy)
                    }
                }
                }
            }
        }
    }
    if(showSummaryDetails && snapshot!=null) MonthlyMathSheet(repository,snapshot!!,{showSummaryDetails=false},{showSummaryDetails=false;showInsights=true})
    excludeReview?.let {row->AlertDialog(onDismissRequest={if(!busy)excludeReview=null},title={Text("Exclude this alert?")},
        text={Text("It will leave Review and stay excluded from totals. The record is kept in Activity; open its details to restore it to Review.")},
        confirmButton={TextButton(enabled=!busy,onClick={
            busy=true;scope.launch {
                try {repository.dismissReview(row.id,row.updatedAt);excludeReview=null;snapshot=null;saveFeedback="Alert excluded. Totals are unchanged."}
                catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
                catch(e:IllegalArgumentException) {error=e.message;excludeReview=null}
                catch(_:Exception) {error="Could not exclude this alert. Close and retry.";excludeReview=null}
                finally {busy=false}
            }
        }) {Text(if(busy)"Saving…" else "Exclude alert")}},dismissButton={TextButton(enabled=!busy,onClick={excludeReview=null}) {Text("Keep in review")}})}
    if(showMonthPicker) ModalBottomSheet(onDismissRequest={showMonthPicker=false},dragHandle=null,containerColor=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Choose a month",style=MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={selectedMonth=java.time.YearMonth.parse(selectedMonth).minusMonths(1).toString();offset=0;snapshot=null},modifier=Modifier.semantics {contentDescription="Previous month"}) {LedgerIcon("back")}
                Text(java.time.YearMonth.parse(selectedMonth).format(DateTimeFormatter.ofPattern("MMMM uuuu")),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                IconButton(onClick={selectedMonth=java.time.YearMonth.parse(selectedMonth).plusMonths(1).toString();offset=0;snapshot=null},modifier=Modifier.semantics {contentDescription="Next month"}) {LedgerIcon("arrow")}
            }
            TextButton(onClick={selectedMonth=java.time.YearMonth.now().toString();offset=0;snapshot=null}) {Text("Current month")}
            Button(onClick={showMonthPicker=false},modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {Text("Use this month")}
        }
    }
    val detailsRow = selected
    if (detailsRow != null && !form) TransactionDetailsSheet(
        onDismiss = { if (!busy) { selected = null; selectedId = null } },
        primaryLabel = if (detailsRow.reviewState == "Dismissed") "Done" else "Edit / confirm",
        primaryEnabled = !busy && (detailsRow.reviewState == "Dismissed" || selectedMatch == null),
        dismissEnabled = !busy,
        onPrimary = {
            if (detailsRow.reviewState == "Dismissed" || selectedMatch != null) { selected = null; selectedId = null }
            else form = true
        },
    ) {
        val row = detailsRow
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        row.counterpartyLabel?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        Text(money(row.amountMinor), style = MaterialTheme.typography.headlineSmall)
        Text("${`in`.txnsense.app.feature.transactionFlowLabel(row)} · ${friendly(row.status)}")
        if (row.transactionType == "CardRepayment") Text("Pays an existing card balance. Excluded from spending and money-in/out totals; purchases count when they happen.", style = MaterialTheme.typography.bodySmall)
        Text("${friendly(row.transactionType)} · ${friendly(row.channel)} · ${friendly(row.sourceType)}")
        paymentSources.firstOrNull { it.id == row.paymentSourceId }?.let { Text("Payment source: ${it.nickname}${if (it.active) "" else " (inactive)"}", style = MaterialTheme.typography.bodySmall) }
        if (row.paymentSourceId == null && paymentSources.any { it.active && it.channel == row.channel })
            TextButton(onClick = { showSourcePicker = true }) { Text("Choose payment source") }
        transactionLabels(row)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (row.duplicateOfId != null) Text("Excluded duplicate · the other record is counted", style = MaterialTheme.typography.bodyMedium)
        if (row.reviewState == "Dismissed") {
            Text("Marked not a transaction · excluded from all money totals. The original evidence is retained.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={
                busy=true
                scope.launch {
                    try {repository.restoreDismissedReview(row.id,row.updatedAt);selected=null;selectedId=null;destination="Review";offset=0;snapshot=null;saveFeedback="Returned to review. Totals are unchanged."}
                    catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
                    catch(e:IllegalArgumentException) {error=e.message}
                    catch(_:Exception) {error="Could not restore this alert. Close and retry."}
                    finally {busy=false}
                }
            },enabled=!busy) {Text("Restore to review")}
        }
        selectedMatch?.let { decision ->
            Text(if (decision.action == "SelfTransfer") "Confirmed own-account transfer · excluded from spending and money-in/out totals."
                else "Part of a confirmed duplicate pair. Both records are retained.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { selected = null; selectedId = null; showMatches = true; matchHistory = true }) { Text("Review match decision") }
            Text("Undo the match decision before editing this record.", style = MaterialTheme.typography.bodySmall)
        }
        row.groupLabel?.let { Text("${if (row.ownership == "Group") "Group" else "For"}: $it", style = MaterialTheme.typography.bodySmall) }
        repaymentSummary(row, snapshot?.reversedOriginalIds.orEmpty())?.let { (label, owed) ->
            Text("$label: ${money(owed)}", style = MaterialTheme.typography.bodySmall)
        }
        if (row.reviewState == "NeedsReview") Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Check this transaction", style = MaterialTheme.typography.titleMedium)
                Text(when {
                    row.amountMinor == null -> "We couldn’t identify a reliable amount. Enter it to finish recording."
                    row.direction == "Unknown" -> "Choose whether money went out, came in, or moved between accounts."
                    row.status == "Unknown" -> "Check whether this payment completed before confirming."
                    else -> "Check the details before including this transaction in your totals."
                })
            }
        } else Text(friendly(row.reviewState), style = MaterialTheme.typography.bodySmall)
        Text(transactionTime(row.effectiveTimestamp))
        if (row.sourceType == "SMS" && !row.isUserCorrected) Text("Date from the SMS record’s timestamp, not the date written in its text. If the date is wrong, Edit lets you correct it.", style = MaterialTheme.typography.bodySmall)
        row.linkedOriginalId?.let { originalId ->
            TextButton(onClick = { selected = null; selectedId = originalId }) { Text("View linked original transaction") }
            Text("Linked by matching reference, account, channel and full amount.", style = MaterialTheme.typography.bodySmall)
        }
        row.maskedAccountHint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        row.userNotes?.let { Text("Description", style = MaterialTheme.typography.labelMedium); Text(it) }
        `in`.txnsense.app.feature.RepaymentHistoryPanel(repository, row,
            RepaymentAccounting.eligible(row, snapshot?.reversedOriginalIds.orEmpty()))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (row.reviewState != "Dismissed") {
            if (selectedMatch == null && row.direction in listOf("Debit", "Credit") && row.ownership !in listOf("SelfTransfer", "ForOther", "Group") && row.transactionType !in listOf("CardRepayment", "Refund", "Reversal", "SelfTransfer"))
                OutlinedButton(onClick = { cardBillDialog = true }) { Text("Mark as card bill payment") }
            OutlinedButton(onClick = { quickForm = true; form = true }, enabled = selectedMatch == null) { Text("Category & purpose") }
            }
        }
        TextButton(onClick = { deleteDialog = true }) { Text("Delete transaction") }
    }
    if (form && (selectedId == null || selected != null)) ModalBottomSheet(
        onDismissRequest = { if (!busy) { if (dirtyForm) formDismissWarning = true else { form = false; selected = null; selectedId = null } } },
        sheetState = formSheetState,
        // Scrolling the form at its top must not drag/dismiss the editor window.
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        TransactionForm(repository, selected,
            onDone = { saveFeedback=if(quickForm)"Category and purpose saved." else if(selected?.reviewState=="NeedsReview")"Payment confirmed. Your totals are updated." else "Payment saved.";dirtyForm = false; quickForm = false; form = false; selected = null; selectedId = null; offset = 0; error = null },
            onDirtyChange = { dirtyForm = it }, quickOnly = quickForm,
            onCancel = {dirtyForm=false;quickForm=false;form=false;selected=null;selectedId=null})
    }
    if (requestWarning) AlertDialog(onDismissRequest = { requestWarning = false; consumeRequest() },
        title = { Text("Open another transaction?") }, text = { Text("Your current changes have not been saved.") },
        confirmButton = { TextButton(onClick = { approvedRequest = request; requestWarning = false; requestApproval++ }) { Text("Discard and open") } },
        dismissButton = { TextButton(onClick = { requestWarning = false; consumeRequest() }) { Text("Keep editing") } })
    if (formDismissWarning) AlertDialog(onDismissRequest = { formDismissWarning = false },
        title = { Text("Leave without saving?") }, text = { Text("Your transaction changes have not been saved.") },
        confirmButton = { TextButton(onClick = {
            formDismissWarning = false; dirtyForm = false; quickForm = false; form = false; selected = null; selectedId = null
        }) { Text("Discard changes") } },
        dismissButton = { TextButton(onClick = { formDismissWarning = false }) { Text("Keep editing") } })
    if (reviewWarning) AlertDialog(onDismissRequest = { reviewWarning = false },
        title = { Text("Open review queue?") }, text = { Text("Your current transaction changes have not been saved.") },
        confirmButton = { TextButton(onClick = {
            reviewWarning = false; dirtyForm = false; form = false; selected = null; selectedId = null
            settings = false; settingsSection = null; destination = "Review"; offset = 0; snapshot = null
        }) { Text("Discard and review") } },
        dismissButton = { TextButton(onClick = { reviewWarning = false }) { Text("Keep editing") } })
    if (addWarning) AlertDialog(onDismissRequest = { addWarning = false },
        title = { Text("Keep your unsaved changes?") }, text = { Text("Quick add was requested while this transaction has unsaved changes.") },
        confirmButton = { TextButton(onClick = {
            addWarning = false; dirtyForm = false; quickForm = false; selected = null; selectedId = null; form = true
            settings = false; settingsSection = null; showInsights = false; showRepayments = false; showBatch = false; showMatches = false; destination = "Overview"
        }) { Text("Discard and add") } }, dismissButton = { TextButton(onClick = { addWarning = false }) { Text("Keep editing") } })
    if (optionalToolWarning) AlertDialog(onDismissRequest = { optionalToolWarning = false; consumeOptionalToolRequest() },
        title = { Text("Open optional tools?") }, text = { Text("Your current transaction changes have not been saved.") },
        confirmButton = { TextButton(onClick = {
            optionalToolWarning = false; dirtyForm = false; selected = null; selectedId = null; form = false
            optionalToolsInitialTab = optionalToolRequest ?: "Budgets"; settings = true; settingsSection = "Optional tools"
            showInsights = false; showRepayments = false; showBatch = false; showMatches = false
            consumeOptionalToolRequest()
        }) { Text("Discard and open") } }, dismissButton = { TextButton(onClick = {
            optionalToolWarning = false; consumeOptionalToolRequest()
        }) { Text("Keep editing") } })
    if (disclosure) AlertDialog(onDismissRequest = { disclosure = false }, title = { Text("Read new SMS on this device?") },
        text = { Text("Android gives this app access to incoming SMS, including non-financial messages. Processing stays on this device. We reject OTPs and non-transactions and store normalized financial fields in encrypted storage. Raw messages and senders are not stored or uploaded. SMS permission is optional; manual entry always works. No payment or bank connection is involved.") },
        confirmButton = { TextButton(onClick = {
            repository.preferences.edit().putBoolean("sms_disclosure", true).apply(); disclosure = false
            if (permissionNeedsSettings()) openSmsSettings() else {
                repository.preferences.edit().putBoolean("sms_permission_requested", true).apply()
                smsPermission.launch(Manifest.permission.RECEIVE_SMS)
            }
        }) { Text("I understand — continue") } },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Not now") } })
    if (eraseDialog) AlertDialog(onDismissRequest = { if (!busy) eraseDialog = false }, title = { Text("Erase all local data?") },
        text = { Text("Permanently deletes this device's transactions, corrections, encryption keys and settings. No automatic backup or undo is created. Previously exported backups are not deleted. SMS capture will be off.") },
        confirmButton = { TextButton(onClick = { busy = true; scope.launch {
            try { repository.eraseAll(); selected = null; selectedId = null; form = false; snapshot = null; captureEnabled = false; notifications = true; error = null }
            catch (_: Exception) { error = "Erasure did not fully finish. Capture is off; retry before re-enabling it." }
            finally { busy = false; eraseDialog = false }
        } }, enabled = !busy) { Text("Erase permanently") } },
        dismissButton = { TextButton(onClick = { eraseDialog = false }, enabled = !busy) { Text("Cancel") } })
    if (deleteDialog) AlertDialog(onDismissRequest = { if (!busy) deleteDialog = false }, title = { Text("Delete this transaction?") },
        text = { Text("The record and correction history will be permanently removed.") },
        confirmButton = { TextButton(onClick = { if (!busy) { busy = true; scope.launch {
            try { repository.delete(selected!!.id); selected = null; selectedId = null } catch (_: Exception) { error = "Delete failed. Please retry." }
            finally { busy = false; deleteDialog = false }
        } } }, enabled = !busy) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteDialog = false }, enabled = !busy) { Text("Cancel") } })
    resultsTotalsSnapshot?.let { totals -> MatchingTotalsSheet(totals) {resultsTotalsSnapshot=null} }
    if (cardBillDialog && selected != null) AlertDialog(onDismissRequest = { if (!busy) cardBillDialog = false },
        title = { Text("Mark as card bill payment?") },
        text = { Text("Only use this for paying your own credit card bill, not a purchase, refund or cashback. This record will be excluded from spending and money-in/out totals. Its amount, status and review state stay unchanged. You can change the type back in Edit / confirm.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { busy = true; scope.launch {
            try { selected = repository.markCardBillPayment(selected!!.id); cardBillDialog = false; error = null }
            catch (e: IllegalArgumentException) { error = e.message; cardBillDialog = false }
            catch (_: Exception) { error = "Could not update this payment. Please retry."; cardBillDialog = false }
            finally { busy = false }
        } }) { Text("Mark bill payment") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { cardBillDialog = false }) { Text("Cancel") } })
    if (showFilters) LedgerFiltersSheet(
        categorySummary = draftCategories.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "All categories",
        moreFiltersInitially = draftPurposeFilter != "All" || draftOriginFilter != "All" ||
            draftPaymentKindFilter != "All" || draftSourceIds.isNotEmpty(),
        onDismiss = { showFilters = false },
        onApply = {
            focusManager.clearFocus(); keyboardController?.hide()
            val knownSourceIds = paymentSources.map { it.id }.toSet()
            val next = combinedFilter(draftPurposeFilter, draftOriginFilter, draftDirectionFilter, draftCategories,
                draftSourceIds.filter { it in knownSourceIds }, draftPaymentKindFilter)
            if (next != filter || draftSearch != search || offset != 0) { filter = next; search = draftSearch; offset = 0; snapshot = null }
            showFilters = false
        },
        onClear = {
            focusManager.clearFocus(); keyboardController?.hide()
            draftPurposeFilter = "All"; draftOriginFilter = "All"; draftDirectionFilter = "All"; draftPaymentKindFilter = "All"; draftCategories = emptyList(); draftSourceIds = emptyList(); draftSearch = ""
            if (filter != "All" || search.isNotBlank() || offset != 0) { filter = "All"; search = ""; offset = 0; snapshot = null }
            showFilters = false
        },
        categoryChoices = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                availableCategories.forEach { category ->
                    LedgerChoiceChip(selected = category in draftCategories, onClick = {
                        draftCategories = if (category in draftCategories) draftCategories - category else draftCategories + category
                    }, label = { Text(category) }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp))
                }
            }
        }, advancedChoices = {
            TextButton(onClick={focusManager.clearFocus();keyboardController?.hide();showFilters=false;showBatch=true;batchSelectedIds=arrayListOf()}) {Text("Select transactions")}
            OutlinedTextField(draftSearch, { draftSearch = it.take(80) }, label = { Text("Search transactions") },
                placeholder = { Text("Merchant or label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            HorizontalDivider()
            Text("Money flow", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Debit", "Credit").forEach { option ->
                    LedgerChoiceChip(selected = draftDirectionFilter == option, onClick = {
                        draftDirectionFilter = if (draftDirectionFilter == option) "All" else option
                    }, label = { Text(if (option == "Debit") "Money out" else "Money in") }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp))
                }
            }
            HorizontalDivider()
            Text("Payment type", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("CardBills", "CardCreditsToCheck").forEach { option ->
                    LedgerChoiceChip(selected = draftPaymentKindFilter == option, onClick = {
                        draftPaymentKindFilter = if (draftPaymentKindFilter == option) "All" else option
                    }, label = { Text(friendlyFilter(option)) }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp))
                }
            }
            if (draftPaymentKindFilter == "CardCreditsToCheck") Text("Unclassified card credits in this month. Check each record: some may be cashback or other genuine credits. Nothing is changed automatically.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Purpose", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Personal", "Family", "ForOthers", "Group", "SelfTransfer").forEach { option ->
                    LedgerChoiceChip(selected = draftPurposeFilter == option, onClick = {
                        draftPurposeFilter = if (draftPurposeFilter == option) "All" else option
                    }, label = { Text(friendlyFilter(option)) }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp))
                }
            }
            HorizontalDivider()
            Text("Origin and changes", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Manual", "Edited").forEach { option ->
                    LedgerChoiceChip(selected = draftOriginFilter == option, onClick = {
                        draftOriginFilter = if (draftOriginFilter == option) "All" else option
                    }, label = { Text(friendlyFilter(option)) }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp))
                }
            }
            HorizontalDivider()
            Text("Payment source", style = MaterialTheme.typography.labelLarge)
            if (paymentSources.isEmpty()) Text("Add a payment source in Settings to filter by it.", style = MaterialTheme.typography.bodySmall)
            else {
                Text("Choose one or more registered sources.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    paymentSources.sortedWith(compareByDescending<PaymentSourceEntity> { it.active }.thenBy { it.nickname.lowercase() }).forEach { source ->
                        LedgerChoiceChip(selected = source.id in draftSourceIds, onClick = {
                            draftSourceIds = if (source.id in draftSourceIds) draftSourceIds - source.id else draftSourceIds + source.id
                        }, label = { Text(source.nickname + if (source.active) "" else " · inactive") }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp))
                    }
                }
            }
        })
    if (showSourcePicker && selected != null) AlertDialog(
        onDismissRequest = { if (!busy) showSourcePicker = false },
        title = { Text("Choose payment source") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("This only assigns a source. It won’t confirm or change the transaction.", style = MaterialTheme.typography.bodySmall)
                paymentSources.filter { it.active && it.channel == selected!!.channel }.forEach { source ->
                    TextButton(onClick = { if (!busy) { busy = true; scope.launch {
                        try {
                            selected = repository.updateTransactionPaymentSource(selected!!.id, source.id)
                            showSourcePicker = false; error = null
                        } catch (_: Exception) { error = "Could not assign that payment source. Please retry." }
                        finally { busy = false }
                    } } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(source.nickname) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { showSourcePicker = false }, enabled = !busy) { Text("Cancel") } })
}

private fun purposePart(value: String): String = value.split("+").firstOrNull {
    it in listOf("Personal", "Family", "ForOthers", "Group", "SelfTransfer")
} ?: "All"

private fun originPart(value: String): String = value.split("+").firstOrNull {
    it in listOf("Manual", "Edited")
} ?: "All"

private fun directionPart(value: String): String = value.split("+").firstOrNull {
    it in listOf("Debit", "Credit")
} ?: "All"

private fun paymentKindPart(value: String): String = value.split("+").firstOrNull { it in listOf("CardBills", "CardCreditsToCheck") } ?: "All"

private fun categoryParts(value: String): List<String> = value.split("+").filter { it.startsWith("Category:") }.map { it.removePrefix("Category:") }

private fun sourceParts(value: String): List<String> = value.split("+").filter { it.startsWith("Source:") }.map { it.removePrefix("Source:") }

private fun combinedFilter(purpose: String, origin: String, direction: String = "All", categories: List<String> = emptyList(),
    sourceIds: List<String> = emptyList(), paymentKind: String = "All"): String = (listOf(purpose, origin, direction, paymentKind) +
    categories.distinct().map { "Category:$it" } + sourceIds.distinct().map { "Source:$it" })
    .filter { it != "All" }.joinToString("+").ifBlank { "All" }

private fun filterLabel(value: String, paymentSources: List<PaymentSourceEntity>): String {
    val sourceNames = paymentSources.associate { it.id to it.nickname }
    return value.split("+").joinToString(" · ") {
        when {
            it.startsWith("Category:") -> it.removePrefix("Category:")
            it.startsWith("Source:") -> sourceNames[it.removePrefix("Source:")] ?: "Payment source"
            else -> friendlyFilter(it)
        }
    }
}

private fun friendlyFilter(value: String): String = when (value) {
    "CardBills" -> "Card bill payments"
    "CardCreditsToCheck" -> "Card credits to check"
    "Review" -> "Needs review"
    "Debit" -> "Money out"
    "Credit" -> "Money in"
    "ForOthers" -> "For someone else"
    "SelfTransfer" -> "Self transfer"
    else -> friendly(value)
}

internal fun repaymentSummary(row: TransactionEntity, reversedOriginalIds: Set<String> = emptySet()): Pair<String, Long>? {
    if (!row.repaymentExpected || row.duplicateOfId != null || row.reviewState == "Dismissed") return null
    if (row.ownership !in listOf("ForOther", "Group") || row.direction != "Debit" || row.amountMinor == null) return null
    if (row.status in listOf("Failed", "Reversed") || row.transactionType in listOf("SelfTransfer", "CardRepayment", "Refund", "Reversal") || row.id in reversedOriginalIds) return null
    val personal = row.personalShareMinor ?: row.amountMinor
    val owed = (row.amountMinor - personal - row.repaidMinor).coerceAtLeast(0)
    val settled = row.status == "Successful" && row.reviewState != "NeedsReview"
    return (if (settled) "Still owed" else "Potentially owed after review") to owed
}

private fun transactionLabels(row: TransactionEntity): String? = buildList {
    if (row.category != "Other") add(friendly(row.category))
    if (row.ownership != "Personal") add(friendly(row.ownership))
    row.groupLabel?.takeIf { it.isNotBlank() }?.let(::add)
}.joinToString(" · ").ifBlank { null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LedgerNavigation(destination:String,reviewCount:Int=0,onNavigate:(String)->Unit) {
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().heightIn(min=72.dp).padding(start=10.dp,end=10.dp,top=9.dp,bottom=4.dp)) {
        listOf(Triple("Overview","Home","home"),Triple("Transactions","Activity","list"),Triple("People","People","people"),Triple("Review","Review","review")).forEach { (key,label,icon)->
            TextButton(onClick={onNavigate(key)},modifier=Modifier.weight(1f).heightIn(min=56.dp).semantics {
                contentDescription=when(key){"Overview"->"Open home";"Transactions"->"Open activity";"People"->"Open people";else->"Open review tab"}
                selected=destination==key
            },contentPadding=PaddingValues(0.dp)) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(width=38.dp,height=28.dp).background(if(destination==key)MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center) {
                        LedgerIcon(icon,Modifier.size(22.dp),if(destination==key)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        if(key=="Review" && reviewCount>0) Badge(Modifier.align(Alignment.TopEnd),
                            containerColor=MaterialTheme.colorScheme.onTertiaryContainer,
                            contentColor=MaterialTheme.colorScheme.background) {Text(reviewCount.toString(),fontSize=9.sp)}
                    }
                    Text(label,style=MaterialTheme.typography.labelSmall.copy(fontSize=10.sp,
                        fontWeight=if(destination==key)FontWeight.Bold else FontWeight.Normal),color=if(destination==key)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun PaymentCategoryCard(category: String, balanceMinor: Long, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) androidx.compose.ui.graphics.Color(0xFFCCFF00) else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = backgroundColor, contentColor = contentColor),
        modifier = Modifier.width(240.dp).height(140.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(friendly(category), style = MaterialTheme.typography.titleMedium, color = contentColor)
            Text(money(balanceMinor), style = MaterialTheme.typography.headlineMedium, color = contentColor)
        }
    }
}

@Composable
private fun SettingsRow(title: String, summary: String, icon: String = "chevron", onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button,
            onClick = onClick).padding(vertical = 17.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LedgerIcon(icon, Modifier.size(if (icon == "chevron") 16.dp else 22.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SettingsToggleRow(title: String, summary: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled,
            role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = title }.padding(vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(40.dp, 24.dp).background(if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))) {
                Box(Modifier.padding(start = if (checked) 19.dp else 3.dp, top = 3.dp).size(18.dp)
                    .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surfaceContainer,
                        androidx.compose.foundation.shape.CircleShape))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
