package `in`.financeministry.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.financeministry.app.data.TransactionEntity
import `in`.financeministry.app.feature.friendly
import `in`.financeministry.app.feature.transactionFlowLabel
import `in`.financeministry.app.money

/** The compact editor identifies its existing payment before changing labels. */
@Composable
fun LedgerPaymentContext(row: TransactionEntity, sourceName: String?) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.red < .5f
    val kind = when {
        row.transactionType == "CardRepayment" -> "bills"
        row.direction == "Credit" -> "income"
        row.category == "Food" -> "food"
        row.category == "Travel" -> "travel"
        row.category == "Shopping" -> "shopping"
        row.category in listOf("Bills", "Flat expenses") -> "bills"
        else -> "other"
    }
    val tile = when (kind) {
        "income" -> if (dark) Color(0xFF223D30) else Color(0xFFE5F1E9)
        "food" -> if (dark) Color(0xFF3E2F2C) else Color(0xFFFAE4DA)
        "shopping" -> if (dark) Color(0xFF332E43) else Color(0xFFECE6F7)
        "bills" -> if (dark) Color(0xFF3C3427) else Color(0xFFF4ECD9)
        else -> if (dark) Color(0xFF273345) else Color(0xFFE5EDFA)
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(tile, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            LedgerIcon(kind, Modifier.size(19.dp), if (kind == "income") {
                if (dark) Color(0xFFA0D8B8) else Color(0xFF236549)
            } else colors.onSurface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.counterpartyLabel ?: transactionFlowLabel(row), style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 19.sp, lineHeight = 26.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold))
            Text("${money(row.amountMinor)} · ${sourceName ?: friendly(row.channel)}",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}
