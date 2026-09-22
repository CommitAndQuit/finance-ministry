package `in`.txnsense.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import `in`.txnsense.app.ui.TxnSenseTheme
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    private val request = mutableStateOf<Pair<String, Boolean>?>(null)
    private val quickRequest = mutableStateOf(false)
    private val reviewRequestGeneration = androidx.compose.runtime.mutableIntStateOf(0)
    private val resumeGeneration = androidx.compose.runtime.mutableIntStateOf(0)
    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readRequest(intent)
        setContent {
            TxnSenseTheme {
                val repository = (application as TxnSenseApp).container.repository
                var showLedger by remember { mutableStateOf(false) }
                var openSettings by remember { mutableStateOf(false) }
                // Payment-method detail page (one instrument), reached by tapping a home card.
                var methodDetail by remember { mutableStateOf<MethodRef?>(null) }
                // Styled transaction detail page (transaction id), reached by tapping a transaction row.
                var detailTxn by remember { mutableStateOf<String?>(null) }
                // Home UI state hoisted above AnimatedContent so it survives navigating into the
                // detail page and back: the carousel scroll position, the selected month, and the
                // loaded overview (so the home never flashes empty on return, which reset the scroll).
                val carouselState = androidx.compose.foundation.lazy.rememberLazyListState()
                val homeRevision by repository.revision.collectAsState()
                val homeEraseGen by repository.eraseGeneration.collectAsState()
                var homeMonth by rememberSaveable { mutableStateOf(java.time.YearMonth.now().toString()) }
                var homeOverview by remember { mutableStateOf<`in`.txnsense.app.data.SpendOverview?>(null) }
                var homeTimeFilter by rememberSaveable { mutableStateOf("This Month") }
                LaunchedEffect(homeMonth, homeRevision, homeEraseGen) {
                    homeOverview = runCatching { repository.spendOverview(java.time.YearMonth.parse(homeMonth).atDay(1)) }.getOrNull()
                }
                // Deep links (notification tap, quick classify, review) go straight to the detailed ledger.
                LaunchedEffect(request.value, quickRequest.value, reviewRequestGeneration.intValue) {
                    if (request.value != null || quickRequest.value || reviewRequestGeneration.intValue > 0) {
                        openSettings = false; showLedger = true
                    }
                }
                // One SharedTransitionLayout spans all top-level screens so a tapped card expands to
                // fill the screen and a tapped transaction row expands into its detail view.
                val screenState: ScreenState = when {
                    showLedger -> ScreenState.Ledger
                    detailTxn != null -> ScreenState.Detail(detailTxn!!)
                    methodDetail != null -> ScreenState.Method(methodDetail!!)
                    else -> ScreenState.Home
                }
                SharedTransitionLayout {
                    AnimatedContent(
                        targetState = screenState,
                        transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                        label = "screen"
                    ) { s ->
                        CompositionLocalProvider(
                            `in`.txnsense.app.feature.LocalSharedTransitionScope provides this@SharedTransitionLayout,
                            `in`.txnsense.app.feature.LocalNavAnimatedScope provides this@AnimatedContent,
                        ) {
                            when (s) {
                                ScreenState.Ledger -> {
                                    BackHandler(enabled = true) { showLedger = false; openSettings = false }
                                    LedgerApp(repository, request.value, resumeGeneration.intValue,
                                        reviewRequestGeneration.intValue, quickRequest = quickRequest.value, openSettings = openSettings,
                                        onExitHome = { showLedger = false; openSettings = false }) {
                                        request.value = null
                                        intent.removeExtra("transaction_id"); intent.removeExtra("edit"); intent.removeExtra("quick_classify")
                                    }
                                }
                                is ScreenState.Method -> {
                                    `in`.txnsense.app.feature.MethodDetailScreen(repository,
                                        s.ref.channel, s.ref.bankName, s.ref.maskedAccountHint, s.ref.month,
                                        onBack = { methodDetail = null },
                                        onOpenTransaction = { detailTxn = it },
                                        sharedScope = this@SharedTransitionLayout, animatedScope = this@AnimatedContent)
                                }
                                is ScreenState.Detail -> {
                                    `in`.txnsense.app.feature.TransactionDetailScreen(repository, s.id,
                                        onBack = { detailTxn = null },
                                        onEdit = { request.value = it to true; openSettings = false; showLedger = true },
                                        onOpenTransaction = { detailTxn = it })
                                }
                                ScreenState.Home -> {
                                    `in`.txnsense.app.feature.SpendTrackerScreen(repository,
                                        month = homeMonth, overview = homeOverview, onMonthChange = { homeMonth = it },
                                        timeFilter = homeTimeFilter, onTimeFilter = { homeTimeFilter = it },
                                        onOpenTransaction = { detailTxn = it },
                                        onOpenSettings = { openSettings = true; showLedger = true },
                                        onOpenMethod = { channel, bank, last4, month -> methodDetail = MethodRef(channel, bank, last4, month) },
                                        sharedScope = this@SharedTransitionLayout, animatedScope = this@AnimatedContent,
                                        carouselState = carouselState)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); resumeGeneration.intValue++ }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readRequest(intent) }
    fun requestReview() { reviewRequestGeneration.intValue++ }
    private fun readRequest(intent: Intent) {
        quickRequest.value = intent.getBooleanExtra("quick_classify", false)
        request.value = intent.getStringExtra("transaction_id")?.let { it to intent.getBooleanExtra("edit", false) }
        if (intent.getBooleanExtra("open_review", false)) {
            requestReview()
            intent.removeExtra("open_review")
        }
    }
}

/** Top-level navigation states. Method carries its data so outgoing content survives the transition. */
/** Identifies one payment instrument (channel + bank + masked last-4) for a given month. */
data class MethodRef(val channel: String, val bankName: String?, val maskedAccountHint: String?, val month: String)

private sealed interface ScreenState {
    data object Home : ScreenState
    data object Ledger : ScreenState
    data class Method(val ref: MethodRef) : ScreenState
    data class Detail(val id: String) : ScreenState
}

@Composable
fun TxnSenseNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "shell") {
        composable("shell") {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = "Finance Ministry", style = MaterialTheme.typography.headlineMedium)
                    Text(text = "Private alpha shell")
                }
            }
        }
    }
}
