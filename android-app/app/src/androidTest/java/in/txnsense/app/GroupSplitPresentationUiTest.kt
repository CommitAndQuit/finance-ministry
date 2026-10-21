package `in`.txnsense.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.txnsense.app.core.model.*
import `in`.txnsense.app.data.*
import `in`.txnsense.app.feature.GroupSplitEditor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real split draft/editor against an isolated ledger; no contacts or personal records. */
class GroupSplitPresentationUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private data class Fixture(val repository: TransactionRepository, val expenseId: String, val shares: List<MemberShareInput>)
    private fun fixture(): Fixture = runBlocking {
        val r = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "split_presentation_${UUID.randomUUID()}")
        val alice = r.addLocalMember("Alice"); val bob = r.addLocalMember("Bob")
        val id = r.save(ManualInput("900", Direction.Debit, System.currentTimeMillis(), TransactionType.Other,
            label="Friends dinner", category="Food", ownership=SpendingOwnership.Group, groupLabel="Friends", personalShare="300"))
        val shares = listOf(MemberShareInput(alice.id, 30000), MemberShareInput(bob.id, 30000))
        r.saveGroupSplit(id, 30000, shares, r.get(id)!!.updatedAt)
        Fixture(r, id, shares)
    }
    private fun open(f: Fixture, showing: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(true)) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        val updated = runBlocking { f.repository.get(f.expenseId)!!.updatedAt }
        rule.setContent { FinanceMinistryTheme {
            if (showing.value) GroupSplitEditor(f.repository, 90000, 30000, f.shares,
                { own, shares -> f.repository.saveGroupSplit(f.expenseId, own, shares, updated) }, { showing.value=false })
        } }
        rule.waitUntil(15000) { rule.onAllNodes(hasText("Apply split") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun capture(name: String) {
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        // Compose's virtual clock can finish before Android commits the window.
        // Wait for an actual rendered frame so a blank launch window is not
        // mistaken for visual evidence of a passing semantics assertion.
        val committed = CountDownLatch(1)
        rule.runOnUiThread {
            val decor = rule.activity.window.decorView
            decor.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            decor.invalidate()
        }
        assertTrue("The split window did not commit a rendered frame", committed.await(5, TimeUnit.SECONDS))
        val i = InstrumentationRegistry.getInstrumentation()
        val variant = InstrumentationRegistry.getArguments().getString("visualVariant")
            ?.takeIf { it in setOf("light", "dark", "compact-light", "compact-dark", "large-text-dark") } ?: "light"
        File(i.targetContext.getExternalFilesDir("group-ux"), "$name-$variant.png").outputStream().use {
            check(i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }
    private fun cleanup(f: Fixture) = runBlocking { f.repository.eraseAll(); f.repository.close() }

    // Inheriting the larger default chip caption must not undo the approved split hierarchy.
    @Test fun split_choices_keep_quiet_captions_and_accessible_targets_without_applying_a_draft() {
        val f = fixture()
        try {
            open(f)
            listOf("Split equally", "Custom amounts").forEach { label ->
                rule.onNodeWithText(label, useUnmergedTree = true).assertQuietChoiceCaption(label)
                val bounds = rule.onNode(hasText(label) and isSelectable()).fetchSemanticsNode().boundsInWindow
                assertTrue("$label must retain its48dp target", bounds.height >= with(rule.density) { 48.dp.toPx() })
            }
            val equally = rule.onNode(hasText("Split equally") and isSelectable()).fetchSemanticsNode().boundsInWindow
            val custom = rule.onNode(hasText("Custom amounts") and isSelectable()).fetchSemanticsNode().boundsInWindow
            val gap = if (custom.top > equally.top) custom.top - equally.bottom else custom.left - equally.right
            assertTrue("Split choices must not join their borders when native text wraps; gap=$gap px",
                gap >= with(rule.density) { 6.dp.toPx() })
            rule.onNode(hasText("Custom amounts") and isSelectable()).performClick().assertIsSelected()
            rule.onNodeWithText("Custom amounts", useUnmergedTree = true).assertQuietChoiceCaption("Custom amounts")
            assertEquals(30000L, runBlocking { f.repository.get(f.expenseId)!!.personalShareMinor })
            assertEquals(listOf(30000L, 30000L), runBlocking { f.repository.groupSharesFor(f.expenseId).map { it.amountMinor } })
            assertTrue(runBlocking { f.repository.repaymentsFor(f.expenseId).isEmpty() })
        } finally { cleanup(f) }
    }

    @Test fun equal_preview_distinguishes_own_spending_from_the_amount_owed_before_apply() {
        val f = fixture()
        val showing = mutableStateOf(true)
        try {
            open(f, showing)
            rule.onNode(hasText("Split equally") and isSelectable()).assertIsSelected()
            rule.onNode(hasText("Your spending") and hasText("₹300")).assertIsDisplayed()
            rule.onNode(hasText("Owed back to you") and hasText("₹600")).assertIsDisplayed()
            rule.onNodeWithText("Apply split").assertIsDisplayed()
            capture("equal-preview")
            assertEquals(30000L, runBlocking { f.repository.get(f.expenseId)!!.personalShareMinor })
            assertEquals(listOf(30000L,30000L), runBlocking { f.repository.groupSharesFor(f.expenseId).map { it.amountMinor } })
            rule.onNodeWithContentDescription("Close group split").performClick()
            rule.onNodeWithText("Discard split changes?").assertDoesNotExist()
            rule.runOnIdle { assertFalse(showing.value) }
        } finally { cleanup(f) }
    }

    @Test fun custom_preview_tracks_the_draft_and_blocks_an_unassigned_paise_total() {
        val f = fixture()
        try {
            open(f)
            rule.onNodeWithText("Custom amounts").performClick()
            rule.onNodeWithContentDescription("Share for You (INR)").performScrollTo().performTextReplacement("200")
            rule.onNodeWithContentDescription("Share for Alice (INR)").performScrollTo().performTextReplacement("400")
            rule.onNodeWithContentDescription("Share for Bob (INR)").performScrollTo().performTextReplacement("299")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.onNodeWithText("Apply split").assertIsNotEnabled()
            rule.onNodeWithText("Remaining to assign: ₹1.00").assertIsDisplayed()
            rule.onNode(hasText("Your spending") and hasText("₹200")).performScrollTo().assertIsDisplayed()
            rule.onNode(hasText("Owed back to you") and hasText("₹699")).assertIsDisplayed()
            assertEquals(30000L, runBlocking { f.repository.get(f.expenseId)!!.personalShareMinor })
            rule.onNodeWithContentDescription("Share for Bob (INR)").performScrollTo().performTextReplacement("300")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.onNode(hasText("Owed back to you") and hasText("₹700")).performScrollTo().assertIsDisplayed()
            capture("custom-preview")
            rule.onNodeWithText("Apply split").assertIsDisplayed().performClick()
            rule.waitUntil(15000) { runBlocking { f.repository.get(f.expenseId)!!.personalShareMinor == 20000L } }
            assertEquals(listOf(30000L,40000L), runBlocking { f.repository.groupSharesFor(f.expenseId).map { it.amountMinor }.sorted() })
            assertEquals(20000L, runBlocking { f.repository.snapshot().personalSpend.toLong() })
        } finally { cleanup(f) }
    }

    @Test fun header_close_keeps_dirty_confirmation_and_discard_does_not_change_the_expense() {
        val f = fixture(); val showing = mutableStateOf(true)
        try {
            open(f, showing)
            rule.onNodeWithText("Custom amounts").performClick()
            rule.onNodeWithContentDescription("Share for You (INR)").performScrollTo().performTextReplacement("200")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.onNodeWithContentDescription("Close group split").assertIsDisplayed().performClick()
            rule.onNodeWithText("Keep editing").performClick()
            rule.onNodeWithContentDescription("Share for You (INR)").assertTextContains("200")
            rule.onNodeWithContentDescription("Close group split").performClick()
            rule.onNodeWithText("Discard changes").performClick()
            rule.runOnIdle { assertFalse(showing.value) }
            assertEquals(30000L, runBlocking { f.repository.get(f.expenseId)!!.personalShareMinor })
            assertEquals(listOf(30000L,30000L), runBlocking { f.repository.groupSharesFor(f.expenseId).map { it.amountMinor } })
        } finally { cleanup(f) }
    }
}
