@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package `in`.financeministry.app.feature

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.financeministry.app.data.MethodSpend
import `in`.financeministry.app.data.SpendOverview
import `in`.financeministry.app.data.TransactionEntity
import `in`.financeministry.app.data.TransactionRepository
import `in`.financeministry.app.money
import `in`.financeministry.app.ui.Brand
import `in`.financeministry.app.ui.LocalBrand
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Shared-element key so a tapped method card morphs into the detail screen's background.
 *  Keyed per instrument (channel + bank + masked last-4) so each card has its own morph. */
internal fun methodShareKey(channel: String, bank: String?, last4: String?): String =
    "method-bg-$channel-${bank.orEmpty()}-${last4.orEmpty()}"

/** Bank + masked last-4 label that distinguishes one physical card/account from another. */
internal fun instrumentLabel(bank: String?, last4: String?): String =
    listOfNotNull(bank, last4).joinToString(" ").ifBlank { "Unidentified" }

internal data class MethodVisual(val label: String, val color: Color, val onColor: Color, val logo: String)

internal fun visualFor(channel: String, brand: Brand): MethodVisual = when (channel) {
    "CreditCard" -> MethodVisual("Credit Card", brand.lime, brand.onAccentDark, "CARD")
    "Card" -> MethodVisual("Card", brand.lime, brand.onAccentDark, "CARD")
    "UPI" -> MethodVisual("UPI", brand.yellow, brand.onAccentDark, "UPI")
    "DebitCard" -> MethodVisual("Debit Card", brand.navy, brand.onAccentLight, "CARD")
    "Wallet" -> MethodVisual("Wallet", brand.teal, brand.onAccentDark, "WALLET")
    "CashManual" -> MethodVisual("Cash", brand.teal, brand.onAccentDark, "CASH")
    "NetBanking" -> MethodVisual("Net Banking", brand.navy, brand.onAccentLight, "NET")
    "BankTransfer" -> MethodVisual("Bank Transfer", brand.navy, brand.onAccentLight, "BANK")
    "ATM" -> MethodVisual("ATM", brand.navy, brand.onAccentLight, "ATM")
    "IMPS", "NEFT", "RTGS" -> MethodVisual(channel, brand.navy, brand.onAccentLight, channel)
    else -> MethodVisual(friendly(channel), brand.navy, brand.onAccentLight, "•")
}

/** Single-letter badge per payment-method type, shown in the transaction row icon tile. */
internal fun methodLetter(channel: String): String = when (channel) {
    "CreditCard", "Card" -> "C"
    "DebitCard" -> "D"
    "UPI" -> "U"
    "BankTransfer" -> "B"
    "Wallet" -> "W"
    "NetBanking" -> "N"
    "ATM" -> "A"
    "IMPS" -> "I"
    "NEFT" -> "F"
    "RTGS" -> "R"
    "CashManual" -> "$"
    else -> "•"
}

@Composable
fun SpendTrackerScreen(
    repository: TransactionRepository,
    month: String,
    overview: SpendOverview?,
    onMonthChange: (String) -> Unit,
    timeFilter: String,
    onTimeFilter: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMethod: (channel: String, bank: String?, last4: String?, month: String) -> Unit = { _, _, _, _ -> },
    sharedScope: SharedTransitionScope? = null,
    animatedScope: AnimatedVisibilityScope? = null,
    carouselState: androidx.compose.foundation.lazy.LazyListState? = null,
) {
    val brand = LocalBrand.current
    // Fixed full-screen gradient (light green bottom -> lighter top); the list scrolls over it.
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(brand.bgTop, brand.bgBottom)))) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { Header(onOpenSettings) }
            item { MethodCarousel(overview?.methods.orEmpty(), onOpenSettings, sharedScope, animatedScope, carouselState ?: rememberLazyListState()) { m -> onOpenMethod(m.channel, m.bankName, m.maskedAccountHint, month) } }
            item { MonthSpendPager(repository, month, onMonthChange) }
            // White sheet sized to its content (min one screen tall) so the whole list scrolls with
            // the page instead of being clipped to a single fixed-height, non-scrolling panel.
            item { TransactionSheet(Modifier.heightIn(min = LocalConfiguration.current.screenHeightDp.dp), overview, timeFilter, onTimeFilter, onOpenTransaction) }
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("Hi", style = MaterialTheme.typography.headlineLarge, color = scheme.onBackground)
            Text("Bhaskar", style = MaterialTheme.typography.headlineLarge, color = scheme.onBackground)
            Text("Your spending by payment method", fontSize = 12.sp, color = brand.textSecondary)
        }
        val interaction = remember { MutableInteractionSource() }
        Text("⚙", fontSize = 24.sp, color = scheme.onBackground,
            modifier = Modifier.clickable(interactionSource = interaction, indication = null) { onOpenSettings() }.padding(4.dp))
    }
}

@Composable
private fun MethodCarousel(
    methods: List<MethodSpend>, onAddPaymentMethod: () -> Unit,
    sharedScope: SharedTransitionScope?, animatedScope: AnimatedVisibilityScope?,
    listState: androidx.compose.foundation.lazy.LazyListState, onOpenMethod: (MethodSpend) -> Unit
) {
    val brand = LocalBrand.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = screenWidth - 40.dp
    val fling = rememberSnapFlingBehavior(listState)
    val pageCount = methods.size + 1
    val total = methods.sumOf { it.netSpendMinor }.coerceAtLeast(1L)
    val activeIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyRow(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(methods.size) { i -> MethodCard(methods[i], cardWidth, methods[i].netSpendMinor.toFloat() / total, sharedScope, animatedScope, onOpenMethod) }
            item { AddMethodCard(cardWidth, onAddPaymentMethod) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(pageCount) { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(if (i == activeIndex) 9.dp else 7.dp)
                        .clip(CircleShape).background(if (i == activeIndex) brand.lime else brand.hairline)
                )
            }
        }
    }
}

@Composable
private fun MethodCard(
    method: MethodSpend, width: Dp, shareOfTotal: Float,
    sharedScope: SharedTransitionScope?, animatedScope: AnimatedVisibilityScope?, onOpenMethod: (MethodSpend) -> Unit
) {
    val brand = LocalBrand.current
    val v = visualFor(method.channel, brand)
    val sharedMod = if (sharedScope != null && animatedScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(rememberSharedContentState(key = methodShareKey(method.channel, method.bankName, method.maskedAccountHint)), animatedVisibilityScope = animatedScope)
        }
    } else Modifier
    Box(Modifier.width(width).height(200.dp).then(sharedMod).clip(RoundedCornerShape(24.dp)).background(v.color)
        .clickableMenu { onOpenMethod(method) }.padding(20.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(v.logo, fontSize = 18.sp, fontWeight = FontWeight.Black, color = v.onColor)
                Column {
                    Text(v.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = v.onColor)
                    Text(instrumentLabel(method.bankName, method.maskedAccountHint),
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = v.onColor.copy(alpha = 0.85f), maxLines = 1)
                    Text("${method.spendCount} transaction${if (method.spendCount == 1) "" else "s"}",
                        fontSize = 12.sp, color = v.onColor.copy(alpha = 0.7f))
                    if (method.refundMinor > 0) Text("Refunds ${money(method.refundMinor)} netted",
                        fontSize = 11.sp, color = v.onColor.copy(alpha = 0.6f))
                }
                Column {
                    Text(money(method.netSpendMinor), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = v.onColor)
                    Text("this month", fontSize = 12.sp, color = v.onColor.copy(alpha = 0.7f))
                }
            }
            MethodPie(shareOfTotal, v.onColor, v.onColor.copy(alpha = 0.2f), 120.dp)
        }
    }
}

/** Donut showing this method's share of the month's total spend. */
@Composable
private fun MethodPie(fraction: Float, color: Color, trackColor: Color, size: Dp) {
    val safe = fraction.coerceIn(0f, 1f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = this.size.minDimension * 0.16f
            val inset = strokeWidth / 2f
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            drawArc(color = trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            drawArc(color = color, startAngle = -90f, sweepAngle = 360f * safe, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
        Text("${(safe * 100).roundToInt()}%", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun AddMethodCard(width: Dp, onClick: () -> Unit) {
    val brand = LocalBrand.current
    Box(
        Modifier.width(width).height(200.dp).clip(RoundedCornerShape(24.dp)).background(brand.navy).clickableMenu(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("+ Add Payment Method", color = brand.onAccentLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Swipeable month total: pages are months oldest→current; swiping right slides in the previous month. */
@Composable
private fun MonthSpendPager(repository: TransactionRepository, month: String, onMonthChange: (String) -> Unit) {
    val months = remember { (23 downTo 0).map { YearMonth.now().minusMonths(it.toLong()) } }
    val startIndex = remember(month) { months.indexOf(YearMonth.parse(month)).let { if (it < 0) months.lastIndex else it } }
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { months.size })
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { onMonthChange(months[it].toString()) }
    }
    HorizontalPager(state = pagerState, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp) { page ->
        MonthTotalCard(repository, months[page])
    }
}

@Composable
private fun MonthTotalCard(repository: TransactionRepository, ym: YearMonth) {
    val scheme = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    var total by remember(ym) { mutableStateOf<Long?>(null) }
    LaunchedEffect(ym) { total = runCatching { repository.monthSpendTotal(ym.atDay(1)) }.getOrNull() }
    // Read the locale from the configuration so the label recomposes on a locale change.
    val label = "${ym.month.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])} ${ym.year}"
    val glassShape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(glassShape)
            .background(Brush.verticalGradient(listOf(brand.glassTop, brand.glassBottom)))
            .border(1.dp, brand.glassBorder, glassShape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Total Spends", fontSize = 14.sp, color = brand.textSecondary)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface)
        Text(money(total ?: 0L), fontSize = 40.sp, fontWeight = FontWeight.Bold,
            color = scheme.onSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TransactionSheet(
    modifier: Modifier, overview: SpendOverview?, timeFilter: String,
    onFilter: (String) -> Unit, onOpenTransaction: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val rows = overview?.recentSpends.orEmpty().filter { row ->
        val date = Instant.ofEpochMilli(row.effectiveTimestamp).atZone(zone).toLocalDate()
        when (timeFilter) {
            "Today" -> date == today
            "This Week" -> !date.isBefore(today.minusDays(6))
            else -> true
        }
    }
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(scheme.surface).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 24.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { menu = true }) { Text("$timeFilter ▾", fontSize = 14.sp, color = scheme.onSurface) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf("Today", "This Week", "This Month").forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { onFilter(option); menu = false })
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            Text("No spends in this period.", color = brand.textSecondary, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            rows.forEachIndexed { i, row ->
                TransactionRow(row) { onOpenTransaction(row.id) }
                if (i < rows.lastIndex) HorizontalDivider(color = brand.hairline)
            }
        }
    }
}

@Composable
internal fun TransactionRow(row: TransactionEntity, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val brand = LocalBrand.current
    val v = visualFor(row.channel, brand)
    val time = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault())
    Row(
        Modifier.fillMaxWidth().expandFromRow("txn-${row.id}").clickableMenu(onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(v.color), contentAlignment = Alignment.Center) {
            Text(methodLetter(row.channel), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = v.onColor)
        }
        Column(Modifier.weight(1f)) {
            Text(row.counterpartyLabel ?: "${v.label} spend",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, maxLines = 1)
            Text("${row.category} · ${time.format(Instant.ofEpochMilli(row.effectiveTimestamp))}",
                fontSize = 12.sp, color = brand.textSecondary, maxLines = 1)
        }
        Text(money(row.amountMinor), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
    }
}

internal fun Modifier.clickableMenu(onClick: () -> Unit): Modifier = this.clickable { onClick() }
