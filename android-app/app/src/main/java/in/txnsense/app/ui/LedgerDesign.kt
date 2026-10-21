package `in`.txnsense.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.txnsense.app.data.TransactionEntity
import `in`.txnsense.app.feature.friendly
import `in`.txnsense.app.feature.transactionFlowLabel
import `in`.txnsense.app.money
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FinanceLedgerTheme(dark: Boolean=androidx.compose.foundation.isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors=if(dark) darkColorScheme(
        primary=Color(0xFFA7B9FF),onPrimary=Color(0xFF17214F),primaryContainer=Color(0xFF293557),onPrimaryContainer=Color(0xFFF1F2F6),
        background=Color(0xFF14161B),onBackground=Color(0xFFF1F2F6),surface=Color(0xFF14161B),onSurface=Color(0xFFF1F2F6),
        surfaceContainer=Color(0xFF20232B),surfaceContainerLow=Color(0xFF20232B),surfaceContainerHigh=Color(0xFF292D36),surfaceContainerHighest=Color(0xFF292D36),
        surfaceVariant=Color(0xFF292D36),onSurfaceVariant=Color(0xFFB4BAC6),secondaryContainer=Color(0xFF293557),onSecondaryContainer=Color(0xFFA7B9FF),
        tertiaryContainer=Color(0xFF3F311F),onTertiaryContainer=Color(0xFFEFC682),outline=Color(0xFF606775),outlineVariant=Color(0xFF373C47),error=Color(0xFFFFB4AB)
    ) else lightColorScheme(
        primary=Color(0xFF3455DB),onPrimary=Color.White,primaryContainer=Color(0xFFE8EDFF),onPrimaryContainer=Color(0xFF20232A),
        background=Color(0xFFF7F6F2),onBackground=Color(0xFF20232A),surface=Color(0xFFF7F6F2),onSurface=Color(0xFF20232A),
        surfaceContainer=Color.White,surfaceContainerLow=Color.White,surfaceContainerHigh=Color(0xFFECEBE6),surfaceContainerHighest=Color(0xFFECEBE6),
        surfaceVariant=Color(0xFFECEBE6),onSurfaceVariant=Color(0xFF606671),secondaryContainer=Color(0xFFE8EDFF),onSecondaryContainer=Color(0xFF3455DB),
        tertiaryContainer=Color(0xFFFBEDD7),onTertiaryContainer=Color(0xFF82551C),outline=Color(0xFF92979F),outlineVariant=Color(0xFFDDDED9),error=Color(0xFF9F3327)
    )
    fun type(size:Int,line:Int,weight:FontWeight=FontWeight.Normal,tracking:Float=0f)=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=size.sp,lineHeight=line.sp,fontWeight=weight,letterSpacing=tracking.sp)
    MaterialTheme(colorScheme=colors,
        shapes=Shapes(small=RoundedCornerShape(10.dp),medium=RoundedCornerShape(12.dp),large=RoundedCornerShape(18.dp),extraLarge=RoundedCornerShape(24.dp)),
        typography=Typography(headlineLarge=type(44,50,FontWeight.SemiBold,-1.7f),headlineMedium=type(27,33,FontWeight.SemiBold,-.8f),
            headlineSmall=type(24,30,FontWeight.SemiBold,-.6f),titleLarge=type(21,27,FontWeight.SemiBold,-.5f),titleMedium=type(18,24,FontWeight.SemiBold,-.35f),
            titleSmall=type(15,21,FontWeight.SemiBold),bodyLarge=type(15,22),bodyMedium=type(14,21),bodySmall=type(12,18),
            labelLarge=type(14,20,FontWeight.SemiBold),labelMedium=type(12,18,FontWeight.SemiBold),labelSmall=type(11,16)),content=content)
}

private val paths=mapOf(
    "plus" to "M12 5v14M5 12h14","close" to "m6 6 12 12M18 6 6 18","arrow" to "M5 12h14m-5-5 5 5-5 5",
    "back" to "M19 12H5m5-5-5 5 5 5","down" to "m7 10 5 5 5-5","chevron" to "m9 5 7 7-7 7",
    "home" to "m3 10 9-7 9 7M5 9v12h5v-7h4v7h5V9","list" to "M8 5h13M8 12h13M8 19h13M3 5h.01M3 12h.01M3 19h.01",
    "people" to "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M16 3a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-3.87M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
    "check" to "m5 12 4 4L19 6","review" to "M9 3h6l2 3h3v16H4V6h3l2-3M8 11h8M8 16h5",
    "gear" to "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1 1-3",
    "lock" to "M6 10h12v11H6V10M8 10V6a4 4 0 0 1 8 0v4","out" to "M7 17 17 7M7 7h10v10","incoming" to "M7 7 17 17M7 17h10V7",
    "search" to "M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0","filter" to "M4 7h16M7 12h10M10 17h4",
    "food" to "M4 3v7h5V3M6.5 10v11M17 3v18M17 3c-5 1-5 9 0 9","travel" to "M5 16h14V5c0-2-14-2-14 0v11ZM5 10h14M8 20l-3 2M16 20l3 2M8 14h.01M16 14h.01",
    "shopping" to "M5 7h14l2 14H3L5 7ZM8 7V5a4 4 0 0 1 8 0v2","bills" to "M5 21V3l3 2 4-2 4 2 3-2v18l-3-2-4 2-4-2-3 2ZM8 9h8M8 13h6",
    "income" to "M3 6h18v14H3V6ZM3 10h18M16 15h2","other" to "M5 5h14v14H5V5M9 9h6M9 13h4",
    "info" to "M12 11v6M12 7h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0",
    "moon" to "M20 15a8 8 0 0 1-11-11A9 9 0 1 0 20 15"
)

/** Quiet proposal typography without shrinking the native selectable touch target. */
@Composable
fun LedgerChoiceChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = RoundedCornerShape(10.dp)) {
    val colors = MaterialTheme.colorScheme
    FilterChip(selected = selected, onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp), shape = shape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent, labelColor = colors.onSurfaceVariant,
            selectedContainerColor = colors.secondaryContainer, selectedLabelColor = colors.primary),
        border = BorderStroke(1.dp, (if (selected) colors.secondaryContainer else colors.outlineVariant)
            .copy(alpha = if (enabled) 1f else .48f)),
        label = {
            ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)) {
                label()
            }
        })
}

@Composable fun LedgerIcon(name:String,modifier:Modifier=Modifier,tint:Color=LocalContentColor.current) {
    val path=remember(name) { PathParser().parsePathString(paths[name] ?: paths.getValue("other")).toPath() }
    Canvas(modifier.size(22.dp)) { scale(size.width/24f,size.height/24f,pivot=androidx.compose.ui.geometry.Offset.Zero) {
        drawPath(path,tint,style=Stroke(width=1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
    } }
}
fun compactMoney(minor:Long?):String=minor?.let { compactMoney(BigInteger.valueOf(it)) } ?: "Amount unknown"
fun compactMoney(minor:BigInteger):String=money(minor).removeSuffix(".00")
@Composable fun LedgerAmount(amount:BigInteger,modifier:Modifier=Modifier) {
    val formatted=money(amount);val point=formatted.lastIndexOf('.')
    Text(buildAnnotatedString {
        withStyle(SpanStyle(fontSize=32.sp,fontWeight=FontWeight.Normal,color=MaterialTheme.colorScheme.onSurfaceVariant)) { append(formatted.take(1)) }
        append(formatted.substring(1,point))
        withStyle(SpanStyle(fontSize=22.sp,fontWeight=FontWeight.Normal,color=MaterialTheme.colorScheme.onSurfaceVariant,letterSpacing=(-.3).sp)) { append(formatted.substring(point)) }
    },modifier,style=MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings="tnum"))
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable fun LedgerTransactionRow(row:TransactionEntity,sourceName:String?,onOpen:()->Unit,onClassify:()->Unit,selecting:Boolean=false,selected:Boolean=false) {
    val colors=MaterialTheme.colorScheme;val dark=colors.background.red<.5f
    val incomingMoney=row.direction=="Credit" && row.transactionType!="CardRepayment"
    val kind=when {row.transactionType=="CardRepayment"->"bills";incomingMoney->"income";row.category=="Food"->"food";row.category=="Travel"->"travel";row.category=="Shopping"->"shopping";row.category in listOf("Bills","Flat expenses")->"bills";else->"other"}
    val tile=when(kind) {"income"->if(dark)Color(0xFF223D30)else Color(0xFFE5F1E9);"food"->if(dark)Color(0xFF3E2F2C)else Color(0xFFFAE4DA);"shopping"->if(dark)Color(0xFF332E43)else Color(0xFFECE6F7);"bills"->if(dark)Color(0xFF3C3427)else Color(0xFFF4ECD9);else->if(dark)Color(0xFF273345)else Color(0xFFE5EDFA)}
    val positive=if(dark)Color(0xFFA0D8B8)else Color(0xFF236549)
    val title=row.counterpartyLabel ?: if(row.sourceType=="Manual") "Manual transaction" else "${friendly(row.channel)} transaction"
    Column {
        Row(Modifier.fillMaxWidth().combinedClickable(
            onClick=if(selecting || row.reviewState in listOf("NeedsReview","Dismissed")) onOpen else onClassify,
            onLongClick=if(selecting) null else onOpen,
            onLongClickLabel="View transaction details")
            .semantics {
                contentDescription=if(selecting) "Select $title" else if(row.reviewState=="NeedsReview") "Review $title" else if(row.reviewState=="Dismissed") "View excluded alert $title" else "Categorize $title"
                stateDescription=buildList {
                    add(transactionFlowLabel(row));add(friendly(row.status))
                    add(if(row.reviewState=="Dismissed") "Not a transaction" else friendly(row.reviewState))
                    add(friendly(row.ownership))
                    if(row.duplicateOfId!=null)add("Excluded duplicate")
                    if(selecting)add(if(selected) "Selected" else "Not selected")
                }.joinToString(" · ")
            }
            .padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(selecting) Checkbox(selected,onCheckedChange=null)
            Box(Modifier.size(40.dp).background(tile,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) { LedgerIcon(kind,Modifier.size(19.dp),if(kind=="income")positive else colors.onSurface) }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold),maxLines=1,overflow=TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(3.dp),
                    itemVerticalAlignment=Alignment.CenterVertically) {
                    Text(row.category,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                    if(row.ownership!="Personal") Text(friendly(row.ownership),
                        Modifier.background(colors.secondaryContainer,RoundedCornerShape(5.dp)).padding(horizontal=5.dp,vertical=2.dp),
                        style=MaterialTheme.typography.labelSmall.copy(fontSize=10.sp),color=colors.onSecondaryContainer)
                }
                val details=buildList {
                    if(row.status!="Successful")add(friendly(row.status))
                    if(row.reviewState=="NeedsReview")add("Needs review")
                    if(row.reviewState=="Dismissed")add("Not a transaction")
                    if(row.transactionType=="CardRepayment")add(transactionFlowLabel(row))
                    if(row.duplicateOfId!=null)add("Excluded duplicate")
                    sourceName?.let(::add)
                }
                if(details.isNotEmpty())Text(details.joinToString(" · "),style=MaterialTheme.typography.labelSmall,
                    color=if(row.status=="Failed")colors.error else colors.onSurfaceVariant)
                if(row.categoryNeedsReview)Text("Category needs your choice",style=MaterialTheme.typography.labelSmall,color=colors.error)
            }
            Column(Modifier.align(Alignment.Top).padding(top=1.dp),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("${if(incomingMoney)"+" else ""}${compactMoney(row.amountMinor)}",style=MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.SemiBold,fontFeatureSettings="tnum"),color=if(incomingMoney)positive else colors.onSurface)
                Text(if(row.ownership=="Group")"Your share ${compactMoney(row.personalShareMinor ?: row.amountMinor)}" else DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(row.effectiveTimestamp)),style=MaterialTheme.typography.labelSmall.copy(fontSize=10.sp),color=colors.onSurfaceVariant)
            }
        }
        HorizontalDivider(color=colors.outlineVariant)
    }
}
