package `in`.financeministry.app

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import `in`.financeministry.app.feature.RepaymentEditor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** A scrolling title or destructive picker close must fail without mutating a personal ledger. */
class PublicRepaymentPresentationUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun withEditor(splitNames: List<String> = emptyList(), check: (TransactionRepository, String) -> Unit) {
        val repo = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "repay_public_${UUID.randomUUID()}")
        try {
            val id = runBlocking { repo.save(ManualInput("400", Direction.Debit, System.currentTimeMillis(),
                TransactionType.Other, label = "Lunch for Alice",
                ownership = if (splitNames.isEmpty()) SpendingOwnership.ForOther else SpendingOwnership.Group,
                groupLabel = "Alice", personalShare = "0")) }
            if (splitNames.isNotEmpty()) runBlocking {
                require(splitNames.size == 2)
                val shares = splitNames.map { MemberShareInput(repo.addLocalMember(it).id, 20000L) }
                repo.saveGroupSplit(id, 0, shares, repo.get(id)!!.updatedAt)
            }
            val expense = runBlocking { repo.get(id)!! }
            val open = mutableStateOf(true)
            rule.setContent { FinanceMinistryTheme { Surface {
                if (open.value) RepaymentEditor(repo, expense, null, onDismiss = { open.value = false })
            } } }
            rule.waitUntil(15000) { rule.onAllNodes(hasText("Received cash") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            check(repo, id)
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    private fun capture(name: String) {
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        val variant = InstrumentationRegistry.getArguments().getString("visualVariant") ?: "light"
        val image = requireNotNull(i.uiAutomation.takeScreenshot())
        try { File(i.targetContext.getExternalFilesDir("repayment-public-ux"), "$name-$variant.png").outputStream().use {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        } } finally { image.recycle() }
    }

    @Test fun cash_mode_does_not_offer_a_second_recorded_payment_selector() = withEditor { _, _ ->
        rule.onNodeWithText("Received cash").performClick()
        rule.onNode(hasText("Choose a recorded payment") and hasClickAction()).assertDoesNotExist()
        rule.onNodeWithText("Recorded payment").assertIsDisplayed()
    }

    // Quiet method captions must not mean small targets or recording money on selection.
    @Test fun repayment_choices_keep_quiet_captions_and_accessible_targets_without_recording_a_receipt() =
        withEditor(splitNames = listOf("Alice", "Bob")) { repo, id ->
            rule.waitUntil(15000) { rule.onAllNodes(hasText("Bob") and isSelectable()).fetchSemanticsNodes().isNotEmpty() }
            listOf("Recorded payment", "Received cash", "Alice", "Bob").forEach { label ->
                rule.onNodeWithText(label, useUnmergedTree = true).assertQuietChoiceCaption(label)
                val bounds = rule.onNode(hasText(label) and isSelectable()).fetchSemanticsNode().boundsInWindow
                assertTrue("$label must retain its48dp target", bounds.height >= with(rule.density) { 48.dp.toPx() })
            }
            rule.onNode(hasText("Received cash") and isSelectable()).performClick().assertIsSelected()
            rule.onNodeWithText("Received cash", useUnmergedTree = true).assertQuietChoiceCaption("Received cash")
            assertTrue(runBlocking { repo.repaymentsFor(id).isEmpty() })
            assertEquals(40000L, runBlocking { RepaymentAccounting.owed(repo.get(id)!!) })
            assertEquals(listOf(20000L, 20000L), runBlocking { repo.groupSharesFor(id).map { it.amountMinor } })
            assertEquals(0L, runBlocking { repo.snapshot().credit.toLong() })
        }

    // Large native text wraps the methods. Missing row spacing must not join their borders.
    @Test fun wrapped_repayment_methods_keep_visible_separation_and_do_not_record_a_receipt() = withEditor { repo, id ->
        val recorded = rule.onNode(hasText("Recorded payment") and isSelectable())
        val cash = rule.onNode(hasText("Received cash") and isSelectable())
        cash.performScrollTo().assertIsDisplayed()
        recorded.assertIsDisplayed()
        val cashBounds = cash.fetchSemanticsNode().boundsInWindow
        val recordedBounds = recorded.fetchSemanticsNode().boundsInWindow
        val wrapped = cashBounds.top > recordedBounds.top
        if (InstrumentationRegistry.getArguments().getString("requireWrappedChoices") == "true") {
            assertTrue("Compact large-text fixture must wrap the two repayment methods", wrapped)
        }
        val gap = if (wrapped) cashBounds.top - recordedBounds.bottom else cashBounds.left - recordedBounds.right
        assertTrue("Wrapped repayment methods need a visible row gap; actual gap=$gap px", gap >= with(rule.density) { 6.dp.toPx() })
        cash.performClick().assertIsSelected()
        recorded.assertIsNotSelected()
        rule.onNodeWithText("Record cash repayment").assertIsDisplayed()
        assertTrue(runBlocking { repo.repaymentsFor(id).isEmpty() })
        assertEquals(40000L, runBlocking { RepaymentAccounting.owed(repo.get(id)!!) })
        assertEquals(0L, runBlocking { repo.snapshot().credit.toLong() })
        capture("method-choice-separation")
    }

    // Long local names must remain separately tappable when the participant choices wrap.
    @Test fun wrapped_repayment_people_keep_visible_separation_without_changing_saved_shares() =
        withEditor(splitNames = listOf("Alice Johnson Riverside", "Bob Williams Northside")) { repo, id ->
            rule.waitUntil(15000) { rule.onAllNodes(hasText("Bob Williams Northside") and isSelectable()).fetchSemanticsNodes().isNotEmpty() }
            val alice = rule.onNode(hasText("Alice Johnson Riverside") and isSelectable())
            val bob = rule.onNode(hasText("Bob Williams Northside") and isSelectable())
            bob.performScrollTo().assertIsDisplayed()
            alice.assertIsDisplayed()
            val bobBounds = bob.fetchSemanticsNode().boundsInWindow
            val aliceBounds = alice.fetchSemanticsNode().boundsInWindow
            val wrapped = bobBounds.top > aliceBounds.top
            if (InstrumentationRegistry.getArguments().getString("requireWrappedChoices") == "true") {
                assertTrue("Compact large-text fixture must wrap the two local names", wrapped)
            }
            val gap = if (wrapped) bobBounds.top - aliceBounds.bottom else bobBounds.left - aliceBounds.right
            assertTrue("Wrapped people need a visible row gap; actual gap=$gap px", gap >= with(rule.density) { 6.dp.toPx() })
            bob.performClick().assertIsSelected()
            alice.assertIsNotSelected()
            rule.onNodeWithText("Bob Williams Northside · ₹200.00 available to settle").assertExists()
            assertEquals(listOf(20000L, 20000L), runBlocking { repo.groupSharesFor(id).map { it.amountMinor } })
            assertTrue(runBlocking { repo.repaymentsFor(id).isEmpty() })
            assertEquals(40000L, runBlocking { RepaymentAccounting.owed(repo.get(id)!!) })
            capture("participant-choice-separation")
        }

    // Reserving a full-screen scroll body for a short form must fail: the primary
    // action belongs directly below its content, not after hundreds of blank pixels.
    @Test fun short_cash_form_does_not_reserve_a_blank_screen_below_its_last_field() = withEditor { _, _ ->
        rule.onNodeWithText("Received cash").performClick()
        val explanation = rule.onNodeWithText("Saving creates one manual cash receipt and links it to this expense.")
            .performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val action = rule.onNodeWithText("Record cash repayment").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val maxGap = with(rule.density) { 48.dp.toPx() }
        assertTrue("Cash form reserves a large blank body above its primary action", action.top - explanation.bottom <= maxGap)
        capture("cash-compact-content")
    }

    // Putting the title/Close in the body must fail after scrolling to the receipt date.
    @Test fun cash_sheet_keeps_its_heading_close_and_primary_action_when_the_body_scrolls() = withEditor { repo, id ->
        rule.onNodeWithText("Received cash").performScrollTo().performClick()
        rule.onNodeWithText("Received:", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Record repayment").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close repayment editor").assertIsDisplayed()
        rule.onNodeWithText("Record cash repayment").assertIsDisplayed()
        capture("cash-pinned-header")
        rule.onNodeWithContentDescription("Close repayment editor").performClick()
        // Switching mode is a draft change, not a recorded receipt.
        rule.onNodeWithText("Discard changes").performClick()
        assertTrue(runBlocking { repo.repaymentsFor(id).isEmpty() })
        assertEquals(40000L, runBlocking { RepaymentAccounting.owed(repo.get(id)!!) })
        assertEquals(0L, runBlocking { repo.snapshot().credit.toLong() })
    }

    // Close must navigate back within the picker, not discard an unsaved cash amount.
    @Test fun payment_picker_has_a_pinned_return_action_that_preserves_the_cash_draft() = withEditor { repo, id ->
        rule.onNodeWithText("Received cash").performClick()
        rule.onNode(editableLedgerField("Repayment amount (INR)")).performTextInput("125")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Recorded payment").performScrollTo().performClick()
        rule.onNode(hasText("Choose a recorded payment") and hasClickAction()).performScrollTo().performClick()
        rule.onNodeWithText("Choose a recorded payment").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back to repayment editor").assertIsDisplayed()
        capture("picker-pinned-header")
        rule.onNodeWithContentDescription("Back to repayment editor").performClick()
        rule.onNodeWithText("125").assertExists()
        rule.onNodeWithText("Received cash").performScrollTo().performClick()
        rule.onNodeWithText("Record cash repayment").assertIsDisplayed()
        assertTrue(runBlocking { repo.repaymentsFor(id).isEmpty() })
        rule.onNodeWithContentDescription("Close repayment editor").performClick()
        rule.onNodeWithText("Keep editing").performClick()
        rule.onNodeWithText("125").assertExists()
        rule.onNodeWithText("Record cash repayment").performClick()
        rule.waitUntil(15000) { runBlocking { repo.repaymentsFor(id).size == 1 } }
        assertEquals(12500L, runBlocking { repo.snapshot().credit.toLong() })
        assertEquals(27500L, runBlocking { RepaymentAccounting.owed(repo.get(id)!!) })
        assertEquals(0L, runBlocking { repo.snapshot().personalSpend.toLong() })
    }
}
