package `in`.txnsense.app

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.txnsense.app.core.model.*
import `in`.txnsense.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Detail navigation must preserve the Activity context and real repayment history. */
class TransactionDetailsPresentationUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun withDetails(check: (TransactionRepository, TransactionEntity) -> Unit) {
        val repo = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "details_public_${UUID.randomUUID()}")
        try {
            repo.preferences.edit().putBoolean("onboarding_complete", true)
                .putInt("app_tour_seen_version", BuildConfig.VERSION_CODE).commit()
            val row = runBlocking {
                val source = repo.addPaymentSource("Everyday UPI", "UPI", Channel.UPI)
                val id = repo.save(ManualInput("900", Direction.Debit, System.currentTimeMillis(), TransactionType.Other,
                    channel = Channel.UPI, label = "Sample expense", ownership = SpendingOwnership.ForOther,
                    groupLabel = "Alice", personalShare = "0", paymentSourceId = source.id))
                repeat(8) { repo.recordRepayment(id, "10", System.currentTimeMillis() + it, "Alice") }
                repo.get(id)!!
            }
            rule.setContent { LedgerApp(repo, null, consumeRequest = {}) }
            rule.onNodeWithContentDescription("Open activity").performClick()
            rule.onNodeWithContentDescription("Search transactions").performTextInput("Sample expense")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.waitUntil(15000) { rule.onAllNodesWithText("1 transaction").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Categorize Sample expense").performScrollTo().performTouchInput { longClick() }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Edit / confirm").fetchSemanticsNodes().isNotEmpty() }
            check(repo, row)
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    private fun capture(name: String) {
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        val variant = InstrumentationRegistry.getArguments().getString("visualVariant") ?: "light"
        val shot = requireNotNull(i.uiAutomation.takeScreenshot())
        try { File(i.targetContext.getExternalFilesDir("details-ux"), "$name-$variant.png").outputStream().use {
            assertTrue(shot.compress(Bitmap.CompressFormat.PNG, 100, it))
        } } finally { shot.recycle() }
    }

    // A full-page scrolling Close/Edit, or a sheet that clears the query on Close, must fail.
    @Test fun long_history_keeps_close_and_edit_reachable_and_returns_to_the_same_activity_query() = withDetails { repo, row ->
        rule.onNodeWithText("Delete transaction").performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("Payment details") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        rule.onNodeWithContentDescription("Close transaction details").assertIsDisplayed()
        rule.onNodeWithText("Edit / confirm").assertIsDisplayed()
        capture("details-long-history")
        rule.onNodeWithContentDescription("Close transaction details").performClick()
        rule.onNodeWithContentDescription("Search transactions").assertTextContains("Sample expense")
        rule.onNodeWithText("1 transaction").assertIsDisplayed()
        assertEquals(row.amountMinor, runBlocking { repo.get(row.id)!!.amountMinor })
        assertEquals(8, runBlocking { repo.repaymentsFor(row.id).size })
        assertEquals(90000L, runBlocking { repo.snapshot().debit.toLong() })
        assertEquals(8000L, runBlocking { repo.snapshot().credit.toLong() })
        assertEquals(82000L, runBlocking { RepaymentAccounting.owed(repo.get(row.id)!!) })
    }

    // A scrolling primary action or routing Edit to a different record must fail.
    @Test fun pinned_edit_preserves_repayment_guards_and_saves_nonfinancial_changes_to_same_payment() = withDetails { repo, row ->
        rule.onNodeWithText("Delete transaction").performScrollTo()
        rule.onNodeWithContentDescription("Close transaction details").assertIsDisplayed()
        rule.onNodeWithText("Edit / confirm").assertIsDisplayed().performClick()
        rule.onNode(editableLedgerField("Amount (INR)")).performTextReplacement("901")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        try {
            rule.waitUntil(15000) { rule.onAllNodes(publicEditorSaveButton() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            runCatching { capture("details-edit-readiness-failure") }
            println(rule.onAllNodes(isRoot()).printToString())
            throw failure
        }
        rule.onNode(publicEditorSaveButton()).performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithText("Review and unlink existing repayments before changing financial details or the spending split.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(90000L, runBlocking { repo.get(row.id)!!.amountMinor })
        assertEquals(8, runBlocking { repo.repaymentsFor(row.id).size })
        rule.onNode(editableLedgerField("Amount (INR)")).performTextReplacement("900")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNode(publicEditorChoice("Category", "Other")).performScrollTo().performClick()
        rule.onNodeWithText("Food").performClick()
        rule.onNode(publicEditorSaveButton()).assertIsDisplayed().performClick()
        rule.waitUntil(15000) { runBlocking { repo.get(row.id)?.category == "Food" } }
        val saved = runBlocking { repo.get(row.id)!! }
        assertEquals(90000L, saved.amountMinor)
        assertEquals("Food", saved.category)
        assertEquals(row.effectiveTimestamp, saved.effectiveTimestamp)
        assertEquals(row.paymentSourceId, saved.paymentSourceId)
        assertEquals(row.direction, saved.direction)
        assertEquals(row.channel, saved.channel)
        assertEquals(row.ownership, saved.ownership)
        assertEquals(8, runBlocking { repo.repaymentsFor(row.id).size })
        assertEquals(8000L, saved.repaidMinor)
        assertEquals(82000L, RepaymentAccounting.owed(saved))
        assertEquals(0L, runBlocking { repo.snapshot().personalSpend.toLong() })
    }
}
