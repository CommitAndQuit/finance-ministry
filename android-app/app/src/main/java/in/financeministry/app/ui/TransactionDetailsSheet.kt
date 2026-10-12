package `in`.financeministry.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Reading history is a focused sheet; dismissing it never edits the ledger. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailsSheet(onDismiss: () -> Unit, primaryLabel: String, onPrimary: () -> Unit,
    primaryEnabled: Boolean, dismissEnabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxHeight = (with(LocalDensity.current) { windowHeight.toDp() } - 60.dp).coerceAtLeast(48.dp)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = {
        if (dismissEnabled) onDismiss() else scope.launch { sheetState.show() }
    }, dragHandle = null, sheetGesturesEnabled = false,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 17.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Payment details", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss, enabled = dismissEnabled, modifier = Modifier.semantics { contentDescription = "Close transaction details" }) {
                    LedgerIcon("close")
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(onClick = onPrimary, enabled = primaryEnabled, shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).heightIn(min = 50.dp)) {
                Text(primaryLabel)
            }
        }
    }
}
