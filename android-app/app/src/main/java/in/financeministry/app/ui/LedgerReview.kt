package `in`.financeministry.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.financeministry.app.data.TransactionEntity
import `in`.financeministry.app.money
import `in`.financeministry.app.transactionTime

internal fun reviewExplanation(row:TransactionEntity):String = when {
    row.amountMinor==null -> "The amount could not be identified reliably. Check the alert and enter the payment amount."
    row.currency!="INR" -> "The currency needs checking. Confirm an INR amount before including this payment."
    row.direction=="Unknown" -> "Choose whether money went out, came in, or moved between your accounts."
    row.status=="Unknown" -> "Check whether this payment completed before confirming it."
    row.status=="Pending" -> "This payment appears pending. Check its final status before confirming."
    row.transactionType in listOf("Refund","Reversal") && row.linkedOriginalId==null -> "A returned payment was detected, but its original payment could not be matched safely. Check its type and status."
    row.channel=="Unknown" -> "The payment method could not be identified reliably. Choose the method and check the details."
    else -> "The amount was found, but the payment needs your confirmation."
}

/** One clear decision per pending payment, using the same surface and spacing as the proposal. */
@Composable internal fun LedgerReviewCard(row:TransactionEntity,onConfirm:()->Unit,onExclude:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().padding(top=16.dp),shape=RoundedCornerShape(18.dp),color=colors.surfaceContainer) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                LedgerIcon("info",Modifier.size(15.dp),colors.onTertiaryContainer)
                Text("Details need checking",style=MaterialTheme.typography.labelSmall,color=colors.onTertiaryContainer)
            }
            Spacer(Modifier.height(17.dp))
            Text(row.counterpartyLabel ?: "${`in`.financeministry.app.feature.friendly(row.channel)} payment",style=MaterialTheme.typography.titleMedium)
            Text(money(row.amountMinor),Modifier.padding(top=8.dp,bottom=12.dp),style=MaterialTheme.typography.headlineMedium.copy(fontSize=29.sp))
            Text(reviewExplanation(row),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            ReviewDetail("Detected as",when(row.direction) {"Debit"->"Money out";"Credit"->"Money in";"Transfer"->"Transfer";else->"Please check"})
            ReviewDetail("Date",transactionTime(row.effectiveTimestamp))
            Button(onClick=onConfirm,modifier=Modifier.fillMaxWidth().padding(top=20.dp).heightIn(min=50.dp),shape=RoundedCornerShape(13.dp)) {
                Text("Check & confirm");Spacer(Modifier.width(8.dp));LedgerIcon("arrow",Modifier.size(18.dp))
            }
            FilledTonalButton(onClick=onExclude,modifier=Modifier.fillMaxWidth().padding(top=9.dp).heightIn(min=50.dp),
                shape=RoundedCornerShape(13.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=colors.surfaceVariant,contentColor=colors.onSurface)) {
                Text("Not a transaction")
            }
        }
    }
}
@Composable private fun ReviewDetail(label:String,value:String) {
    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,Modifier.widthIn(max=200.dp),style=MaterialTheme.typography.labelLarge)
    }
}
