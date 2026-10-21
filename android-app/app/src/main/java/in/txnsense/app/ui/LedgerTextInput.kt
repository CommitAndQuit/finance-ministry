package `in`.txnsense.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Proposal field: a stable caption above a quiet rounded input, not a floating label.
 * BasicTextField retains native editing/selection/IME actions and the accessible name.
 */
@Composable
fun LedgerTextInput(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, amountStyle: Boolean = false,
    showLabel: Boolean = true, textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    supportingText: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (showLabel) Text(label, style = MaterialTheme.typography.labelMedium,
            color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
            // The editable node owns its label, so TalkBack does not read it twice.
            modifier = Modifier.semantics { hideFromAccessibility() })
        Surface(color = colors.background, shape = shape) {
            BasicTextField(value, onValueChange, enabled = enabled, singleLine = singleLine,
                interactionSource = interaction, keyboardOptions = keyboardOptions,
                cursorBrush = SolidColor(colors.primary),
                textStyle = (if (amountStyle) MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-.6).sp, fontFeatureSettings = "tnum") else textStyle)
                    .copy(color = if (enabled) colors.onSurface else colors.onSurfaceVariant),
                modifier = Modifier.fillMaxWidth().heightIn(min = 49.dp)
                    .border(if (focused) 2.dp else 1.dp, if (focused) colors.primary else colors.outlineVariant, shape)
                    .semantics { contentDescription = label },
                decorationBox = { field ->
                    Box(Modifier.fillMaxWidth().padding(12.dp)) {
                        if (amountStyle && value.isEmpty()) Text("0.00", style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-.6).sp),
                            color = colors.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics {})
                        field()
                    }
                })
        }
        supportingText?.invoke()
    }
}
