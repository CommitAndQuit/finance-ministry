package `in`.financeministry.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import `in`.financeministry.app.feature.TransactionForm
import `in`.financeministry.app.ui.FinanceLedgerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Actual field layout and editing, not a grep of style constants or a fake input. */
class LedgerInputPresentationUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun withForm(check: (TransactionRepository) -> Unit) {
        val repo = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "input_public_${UUID.randomUUID()}")
        try {
            rule.setContent { FinanceLedgerTheme(dark = false) { Surface(Modifier.fillMaxSize()) {
                TransactionForm(repo, null, onDone = {})
            } } }
            rule.waitUntil(15000) { rule.onAllNodes(publicEditorSaveButton() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            check(repo)
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    // Floating a caption into the input border must fail for both empty and populated amounts.
    @Test fun amount_caption_stays_above_the_editable_input_before_and_after_typing() = withForm { _ ->
        fun assertCaptionAboveInput() {
            val caption = rule.onNode(hasText("Amount (INR)") and !hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
            val input = rule.onNode(editableLedgerField("Amount (INR)")).fetchSemanticsNode().boundsInWindow
            assertTrue("Amount caption belongs above the input, not inside its border", caption.bottom <= input.top)
        }
        assertCaptionAboveInput()
        rule.onNode(editableLedgerField("Amount (INR)")).performTextInput("125.50")
        assertCaptionAboveInput()
    }

    // Reverting the amount to default body-size text must fail against the approved amountfield.
    @Test fun entered_amount_uses_large_readable_typography() = withForm { _ ->
        rule.onNode(editableLedgerField("Amount (INR)")).performTextInput("125.50")
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNode(editableLedgerField("Amount (INR)")).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("125.50", layouts.single().layoutInput.text.text)
        assertEquals(28f, layouts.single().layoutInput.style.fontSize.value, .1f)
    }

    // Oversized dropdown captions undo the proposal's quiet field hierarchy.
    @Test fun category_caption_uses_the_same_small_hierarchy_as_input_captions() = withForm { _ ->
        rule.onNode(publicEditorChoice("Category", "Other")).performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Category", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(12f, layouts.single().layoutInput.style.fontSize.value, .1f)
        rule.onNode(publicEditorChoice("Category", "Other")).performClick()
        rule.onNodeWithText("Food").performClick()
        rule.onNode(publicEditorChoice("Category", "Food")).assertIsDisplayed()
    }

    // The proposal uses the same 12sp hierarchy for purpose captions and choice labels.
    @Test fun purpose_caption_and_choices_use_a_consistent_small_hierarchy_without_small_touch_targets() = withForm { _ ->
        rule.onNode(hasText("Family") and isSelectable()).performScrollTo()
        listOf("Who was it for?", "Family", "Money in").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("$label typography", 12f, layouts.single().layoutInput.style.fontSize.value, .1f)
        }
        val choice = rule.onNode(hasText("Family") and isSelectable())
        assertTrue("Compact typography must not reduce the touch target", choice.fetchSemanticsNode().boundsInWindow.height >= with(rule.density) { 48.dp.toPx() })
        choice.performClick().assertIsSelected()
    }

    // Touch targets must not cause adjacent wrapped rows to share a border.
    @Test fun wrapped_purpose_choices_keep_visible_vertical_separation() = withForm { _ ->
        val personal = rule.onNode(hasText("Personal") and isSelectable())
        val transfer = rule.onNode(hasText("Self transfer") and isSelectable())
        transfer.performScrollTo().assertIsDisplayed()
        personal.assertIsDisplayed()
        val gap = transfer.fetchSemanticsNode().boundsInWindow.top - personal.fetchSemanticsNode().boundsInWindow.bottom
        assertTrue("Wrapped choices must retain the proposal's row gap", gap >= with(rule.density) { 6.dp.toPx() })
    }

    // Legacy group-share inputs must not revert to a label inside the border.
    @Test fun group_share_caption_stays_above_the_input_and_allocation_is_saved() = withForm { repo ->
        rule.onNode(editableLedgerField("Amount (INR)")).performTextInput("900")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNode(hasText("Group") and isSelectable()).performScrollTo().performClick()
        rule.onNode(editableLedgerField("Group name")).performScrollTo().performTextInput("Friends")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNode(editableLedgerField("Your share (INR)")).performScrollTo()
        val caption = rule.onNode(hasText("Your share (INR)") and !hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
        val input = rule.onNode(editableLedgerField("Your share (INR)"))
        assertTrue("Group-share caption belongs above its input", caption.bottom <= input.fetchSemanticsNode().boundsInWindow.top)
        input.performTextReplacement("300")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNode(publicEditorSaveButton()).performClick()
        rule.waitUntil(15000) { runBlocking { repo.snapshot().rows.size == 1 } }
        val row = runBlocking { repo.snapshot().rows.single() }
        assertEquals(90000L, row.amountMinor)
        assertEquals(30000L, row.personalShareMinor)
        assertEquals("Group", row.ownership)
        assertEquals(30000L, runBlocking { repo.snapshot().personalSpend.toLong() })
        assertEquals(60000L, RepaymentAccounting.owed(row))
    }

    // A transparent/default white field loses the proposal's surface hierarchy.
    @Test fun inactive_input_has_a_distinct_warm_background_and_saves_exact_paise() = withForm { repo ->
        val input = rule.onNode(editableLedgerField("Amount (INR)"))
        val bounds = input.fetchSemanticsNode().boundsInWindow
        val inset = with(rule.density) { 8.dp.toPx() }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        val shot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            assertEquals("Input background must be warm ivory, not a second white card", 0xFFF7F6F2.toInt(),
                shot.getPixel((bounds.right - inset).toInt(), bounds.center.y.toInt()))
        } finally { shot.recycle() }
        input.performTextInput("125.50")
        rule.onNode(editableLedgerField("Merchant or short label (optional)")).performTextInput("Sample cafe")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNode(publicEditorSaveButton()).performClick()
        rule.waitUntil(15000) { runBlocking { repo.snapshot().rows.size == 1 } }
        val row = runBlocking { repo.snapshot().rows.single() }
        assertEquals(12550L, row.amountMinor)
        assertEquals("Sample cafe", row.counterpartyLabel)
        assertEquals(12550L, runBlocking { repo.snapshot().personalSpend.toLong() })
    }
}
