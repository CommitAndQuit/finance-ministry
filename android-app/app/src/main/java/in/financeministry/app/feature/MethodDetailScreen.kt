@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package `in`.financeministry.app.feature

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.financeministry.app.data.TransactionEntity
import `in`.financeministry.app.data.TransactionRepository
import `in`.financeministry.app.money
import `in`.financeministry.app.ui.LocalBrand
import java.time.YearMonth

/**
 * Full-screen page reached by tapping a payment-method card on the home screen. The card expands
 * into this screen via a shared-element transition, keeping its colour as the background. Below the
 * summary sits a white sheet with every spend for that method in the selected month (scrollable).
 * Additional per-method features will be added here later.
 */
@Composable
fun MethodDetailScreen(
    repository: TransactionRepository,
    channel: String,
    bankName: String?,
    maskedAccountHint: String?,
    month: String,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    sharedScope: SharedTransitionScope? = null,
    animatedScope: AnimatedVisibilityScope? = null,
) {
    val revision by repository.revision.collectAsState()
    val eraseGen by repository.eraseGeneration.collectAsState()
    var rows by remember(channel, bankName, maskedAccountHint, month) { mutableStateOf<List<TransactionEntity>?>(null) }

    LaunchedEffect(channel, bankName, maskedAccountHint, month, revision, eraseGen) {
        rows = runCatching { repository.methodTransactions(channel, bankName, maskedAccountHint, YearMonth.parse(month).atDay(1)) }.getOrNull()
    }

    BackHandler(enabled = true) { onBack() }

    val scheme = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val v = visualFor(channel, brand)
    val list = rows.orEmpty()
    val total = list.sumOf { it.amountMinor ?: 0L }

    // The background is the same colour as the tapped card and shares its bounds, so the card
    // appears to expand to fill the screen.
    val sharedMod = if (sharedScope != null && animatedScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(rememberSharedContentState(key = methodShareKey(channel, bankName, maskedAccountHint)), animatedVisibilityScope = animatedScope)
        }
    } else Modifier

    Box(Modifier.fillMaxSize().then(sharedMod).background(v.color)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar: back + method name (on the card colour).
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("←", fontSize = 26.sp, color = v.onColor,
                    modifier = Modifier.clickableMenu(onBack).padding(8.dp))
                Column {
                    Text(v.label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = v.onColor)
                    Text(instrumentLabel(bankName, maskedAccountHint), fontSize = 13.sp, color = v.onColor.copy(alpha = 0.85f))
                }
            }
            // Month summary.
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(money(total), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = v.onColor)
                Text("${list.size} transaction${if (list.size == 1) "" else "s"} this month",
                    fontSize = 13.sp, color = v.onColor.copy(alpha = 0.7f))
            }
            // Scrollable list in a white sheet that takes over the rest of the screen.
            Column(
                Modifier.fillMaxSize().padding(top = 8.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(scheme.surface)
            ) {
                if (list.isEmpty()) {
                    Text("No spends for this payment method this month.", color = brand.textSecondary,
                        modifier = Modifier.padding(24.dp))
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        itemsIndexed(list) { i, row ->
                            TransactionRow(row) { onOpenTransaction(row.id) }
                            if (i < list.lastIndex) HorizontalDivider(color = brand.hairline)
                        }
                    }
                }
            }
        }
    }
}
