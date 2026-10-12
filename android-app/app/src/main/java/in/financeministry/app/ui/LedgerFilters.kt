package `in`.financeministry.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Search owns only the query; clearing it must not clear the other Activity filters. */
@Composable
fun LedgerSearchField(value: String, onValueChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(modifier, color = colors.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
        BasicTextField(value, onValueChange, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .border(if (focused) 2.dp else 1.dp, if (focused) colors.primary else colors.outlineVariant, RoundedCornerShape(12.dp))
                .semantics { contentDescription = "Search transactions" },
            decorationBox = { field ->
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    LedgerIcon("search", Modifier.size(18.dp), colors.onSurfaceVariant)
                    Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        if (value.isEmpty()) Text("Search your transactions", style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant)
                        field()
                    }
                    if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") },
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Clear search" }) {
                        LedgerIcon("close", Modifier.size(18.dp), colors.onSurfaceVariant)
                    } else Spacer(Modifier.width(12.dp))
                }
            })
    }
}

/** A focused category-first sheet. Existing advanced choices remain available, not removed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerFiltersSheet(categorySummary: String, moreFiltersInitially: Boolean,
    onDismiss: () -> Unit, onApply: () -> Unit, onClear: () -> Unit,
    categoryChoices: @Composable () -> Unit, advancedChoices: @Composable () -> Unit) {
    var choosingCategories by remember { mutableStateOf(false) }
    var moreFilters by remember { mutableStateOf(moreFiltersInitially) }
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = null,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.imePadding()) {
        LedgerSheetSystemBars()
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 9.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Filter activity", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).semantics { contentDescription = "Close filters" }) {
                    LedgerIcon("close")
                }
            }
            Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Category", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = { choosingCategories = !choosingCategories },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 49.dp).semantics { contentDescription = "Choose filter categories" },
                    shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onSurface), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                    Text(categorySummary, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    LedgerIcon("down", Modifier.size(18.dp))
                }
                if (choosingCategories) {
                    Text("Choose one or more. None selected includes all categories.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    categoryChoices()
                }
                Text("Combine this with money in / out and search.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { moreFilters = !moreFilters }, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(if (moreFilters) "Fewer filters" else "More filters") }
                if (moreFilters) advancedChoices()
                Spacer(Modifier.height(12.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onApply, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(13.dp)) {
                    Text("Show transactions")
                }
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { contentDescription = "Clear transaction filters" }) { Text("Clear all") }
            }
        }
    }
}
