package `in`.financeministry.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals

/** Check the consumed native text layout, not a grep of a typography constant. */
internal fun SemanticsNodeInteraction.assertQuietChoiceCaption(label: String, singleLine: Boolean = false) {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    assertEquals("$label should have one actual text layout", 1, layouts.size)
    assertEquals("$label should retain the approved quiet choice hierarchy", 12f,
        layouts.single().layoutInput.style.fontSize.value, .1f)
    if (singleLine) assertEquals("$label should wrap its chip to another row, not squeeze the caption", 1,
        layouts.single().lineCount)
}
