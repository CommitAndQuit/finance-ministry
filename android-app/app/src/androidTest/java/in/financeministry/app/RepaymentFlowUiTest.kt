package `in`.financeministry.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import `in`.financeministry.app.feature.RepaymentHistoryPanel
import `in`.financeministry.app.feature.RepaymentsScreen
import `in`.financeministry.app.feature.PeopleContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.time.LocalDate
import java.util.UUID
import java.io.File

class RepaymentFlowUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var originalSoftKeyboard: String? = null
    private fun keyboardSetting(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }
    @Before fun showRealKeyboardOnDisposableTestDevice() {
        val original = keyboardSetting("settings get secure show_ime_with_hard_keyboard")
        check(original in setOf("0", "1", "null"))
        originalSoftKeyboard = original
        keyboardSetting("settings put secure show_ime_with_hard_keyboard 1")
    }
    @After fun restoreTestKeyboardSetting() {
        originalSoftKeyboard?.let {
            keyboardSetting(if (it == "null") "settings delete secure show_ime_with_hard_keyboard"
                else "settings put secure show_ime_with_hard_keyboard $it")
        }
    }
    private fun repository() = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "repayment_flow_${UUID.randomUUID()}")
    private fun expense(repo: TransactionRepository) = runBlocking {
        repo.save(ManualInput("3000", Direction.Debit, System.currentTimeMillis(), TransactionType.Other,
            label = "Dinner", ownership = SpendingOwnership.Group, groupLabel = "Flatmates", personalShare = "1000"))
    }
    private fun panel(repo: TransactionRepository, id: String) {
        val row = runBlocking { repo.get(id)!! }
        rule.setContent { FinanceMinistryTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp)) { RepaymentHistoryPanel(repo, row, true) }
        } } }
        rule.onNode(hasText("Record repayment") and hasClickAction()).performClick()
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir("repayment-ux"), name)
        file.outputStream().use { output ->
            check(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
    }
    private fun assertSaveAboveKeyboard() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        // The real IME and dialog-window resize are driven by Android, not the
        // Compose test clock. A tap using a moving footer's old coordinates can
        // miss Save even though its semantics is enabled. Wait for observable
        // native bounds to settle; never retry the save or invoke its callback.
        var previousBounds: Pair<androidx.compose.ui.geometry.Rect, android.graphics.Rect>? = null
        var lastMovementAt = android.os.SystemClock.elapsedRealtime()
        var movements = 0
        rule.waitUntil(10000) {
            val ime = automation.windows.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            if (ime == null) false else {
                val imeBounds = android.graphics.Rect().also(ime::getBoundsInScreen)
                val buttonBounds = rule.onNode((hasText("Confirm & link repayment") or hasText("Record cash repayment") or hasText("Save repayment")) and hasClickAction())
                    .fetchSemanticsNode().boundsInWindow
                val current = buttonBounds to imeBounds
                val now = android.os.SystemClock.elapsedRealtime()
                if (current != previousBounds) {
                    previousBounds = current; lastMovementAt = now; movements++; false
                } else now - lastMovementAt >= 200
            }
        }
        println("Repayment native footer/IME bounds settled after $movements observed positions: $previousBounds")
        val keyboard = android.graphics.Rect()
        automation.windows.first { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }.getBoundsInScreen(keyboard)
        val save = rule.onNode((hasText("Confirm & link repayment") or hasText("Record cash repayment") or hasText("Save repayment")) and hasClickAction()).fetchSemanticsNode().boundsInWindow
        assertTrue("Save overlaps the visible keyboard", save.bottom <= keyboard.top)
    }

    // Merely presenting or selecting a possible match must never mutate the ledger.
    @Test fun people_possible_match_requires_explicit_confirmation_and_preserves_recorded_income() {
        val repo = repository()
        val expense = runBlocking { repo.save(ManualInput("200", Direction.Debit, System.currentTimeMillis(),
            TransactionType.Other, label = "Paid for Alice", ownership = SpendingOwnership.ForOther,
            groupLabel = "Alice", personalShare = "0")) }
        val incoming = runBlocking { repo.save(ManualInput("200", Direction.Credit, System.currentTimeMillis(),
            TransactionType.Other, label = "Alice repayment")) }
        val before = runBlocking { repo.snapshot().credit }
        try {
            rule.setContent { FinanceMinistryTheme { Surface(Modifier.fillMaxSize()) {
                PeopleContent(repo, LocalDate.now(), {}, {})
            } } }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Alice").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Alice").performClick()
            rule.onNodeWithText("Record repayment").performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithText("Alice repayment").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(runBlocking { repo.repaymentsFor(expense).isEmpty() })
            rule.onNodeWithText("Confirm & link repayment").assertIsNotEnabled()
            rule.onNodeWithText("Alice repayment").performClick()
            assertTrue(runBlocking { repo.repaymentsFor(expense).isEmpty() })
            rule.onNodeWithText("Confirm & link repayment").performClick()
            rule.waitUntil(15000) { runBlocking { repo.repaymentsFor(expense).size == 1 } }
            assertEquals(incoming, runBlocking { repo.repaymentsFor(expense).single().incomingId })
            assertEquals(before, runBlocking { repo.snapshot().credit })
            assertEquals(0L, runBlocking { RepaymentAccounting.owed(repo.get(expense)!!) })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    // A missing direct action must fail this: recording must not route through transaction details.
    @Test fun outstanding_expense_can_record_cash_without_opening_details() {
        val repo = repository()
        val id = expense(repo)
        var openedDetails = false
        try {
            rule.setContent { FinanceMinistryTheme { RepaymentsScreen(repo, LocalDate.now(), {}, { openedDetails = true }) } }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Still owed to you · all dates").fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Flatmates"))
            rule.onNodeWithText("Flatmates").performClick()
            rule.onNodeWithText("Record repayment").assertIsDisplayed().performClick()
            rule.onNodeWithText("Received cash").performClick()
            rule.onNode(editableLedgerField("Repayment amount (INR)")).performClick().performTextInput("500")
            assertSaveAboveKeyboard()
            capture("cash-keyboard.png")
            rule.onNodeWithText("Record cash repayment").assertIsDisplayed().performClick()
            rule.waitUntil(15000) { runBlocking { repo.repaymentsFor(id).size == 1 } }
            assertFalse(openedDetails)
            assertEquals(150000L, runBlocking { repo.get(id)!!.let(RepaymentAccounting::owed) })
            assertEquals(50000L, runBlocking { repo.snapshot().credit.toLong() })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    // An inline unbounded credit list or scrolling footer must fail this; linking cannot duplicate income.
    @Test fun recorded_payment_picker_searches_long_history_and_save_stays_visible() {
        val repo = repository()
        val id = expense(repo)
        val receipt = runBlocking {
            repeat(30) { repo.save(ManualInput("1", Direction.Credit, System.currentTimeMillis(), TransactionType.Other, label = "Other receipt $it")) }
            repo.save(ManualInput("750", Direction.Credit, System.currentTimeMillis(), TransactionType.Other, label = "Chosen receipt"))
        }
        val before = runBlocking { repo.snapshot().credit }
        try {
            panel(repo, id)
            rule.onNodeWithText("Confirm & link repayment").assertIsDisplayed()
            rule.onNode(hasText("Choose a recorded payment") and hasClickAction()).performScrollTo().performClick()
            rule.onNode(editableLedgerField("Search recorded payments")).performTextInput("Chosen receipt")
            capture("recorded-payment-search.png")
            rule.onNode(hasText("Chosen receipt") and !hasSetTextAction()).performScrollTo().performClick()
            rule.onNode(editableLedgerField("Repayment amount (INR)")).performScrollTo().performClick().performTextReplacement("500")
            assertSaveAboveKeyboard()
            rule.onNodeWithText("Still owed after saving: ₹1,500.00").assertExists()
            rule.onNodeWithText("Confirm & link repayment").assertIsDisplayed().performClick()
            rule.waitUntil(15000) { runBlocking { repo.repaymentsFor(id).size == 1 } }
            assertEquals(receipt, runBlocking { repo.repaymentsFor(id).single().incomingId })
            assertEquals(before, runBlocking { repo.snapshot().credit })
            assertEquals(150000L, runBlocking { repo.get(id)!!.let(RepaymentAccounting::owed) })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    // Hidden Save while the keyboard is open must fail this, including the cash branch.
    @Test fun cash_save_is_visible_while_editing_amount_and_overpayment_is_rejected() {
        val repo = repository()
        val id = expense(repo)
        try {
            panel(repo, id)
            rule.onNodeWithText("Received cash").performClick()
            rule.onNode(editableLedgerField("Repayment amount (INR)")).performTextInput("2001")
            assertSaveAboveKeyboard()
            rule.onNodeWithText("Record cash repayment").assertIsDisplayed().assertIsEnabled().performClick()
            // Validation completes on the repository's IO dispatcher. Await the
            // actual feedback; do not retry Save or waive its visible-error check.
            rule.waitUntil(15000) {
                rule.onAllNodesWithText("Repayment exceeds the amount still owed.").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Repayment exceeds the amount still owed.").assertIsDisplayed()
            capture("cash-overpayment-keyboard.png")
            assertTrue(runBlocking { repo.repaymentsFor(id).isEmpty() })
            rule.onNode(editableLedgerField("Repayment amount (INR)")).performTextReplacement("500")
            assertSaveAboveKeyboard()
            rule.onNodeWithText("Record cash repayment").assertIsDisplayed().assertIsEnabled().performClick()
            rule.waitUntil(15000) { runBlocking { repo.repaymentsFor(id).size == 1 } }
            assertEquals(50000L, runBlocking { repo.repaymentsFor(id).single().amountMinor })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    @Test fun editing_and_unlinking_keep_the_original_incoming_payment() {
        val repo = repository()
        val id = expense(repo)
        val repayment = runBlocking { repo.recordRepayment(id, "500", System.currentTimeMillis(), "Friend") }
        val row = runBlocking { repo.get(id)!! }
        try {
            rule.setContent { FinanceMinistryTheme { Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding().padding(16.dp)) { RepaymentHistoryPanel(repo, row, true) }
            } } }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Edit repayment").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Edit repayment").performClick()
            rule.onNode(editableLedgerField("Repayment amount (INR)")).performTextReplacement("250")
            rule.onNodeWithText("Still owed after saving: ₹1,750.00").assertExists()
            // A visible footer can still be disabled while repayment data loads.
            // Catch that state directly instead of silently tapping a disabled action.
            assertSaveAboveKeyboard()
            val save = rule.onNodeWithText("Save repayment").assertIsDisplayed().assertIsEnabled()
            val tapBounds = save.fetchSemanticsNode().boundsInWindow
            println("Repayment save tap bounds: $tapBounds")
            save.performClick()
            try {
                rule.waitUntil(15000) { runBlocking { repo.repaymentsFor(id).single().amountMinor == 25000L } }
            } catch (failure: Throwable) {
                // Preserve the synthetic editor/error state before the activity is
                // disposed; isolated passes alone cannot explain suite-only failures.
                println("Repayment save original failure: $failure")
                runCatching { println("Repayment save bounds after wait: ${save.fetchSemanticsNode().boundsInWindow}; tapped at $tapBounds") }
                // A sheet has a separate semantics root. Diagnostics must not
                // replace the original failure when both windows are present.
                runCatching { capture("repayment-edit-save-failure.png") }
                    .onFailure { println("Could not capture repayment state: $it") }
                runCatching { println(rule.onAllNodes(isRoot()).printToString()) }
                    .onFailure { println("Could not print repayment state: $it") }
                throw failure
            }
            rule.onNodeWithText("Unlink").performClick()
            rule.onNodeWithText("Unlink repayment").performClick()
            rule.waitUntil(15000) { runBlocking { repo.repaymentsFor(id).isEmpty() } }
            assertEquals(200000L, runBlocking { repo.get(id)!!.let(RepaymentAccounting::owed) })
            assertEquals(50000L, runBlocking { repo.get(repayment.incomingId!!)!!.amountMinor })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    @Test fun picker_back_returns_to_visible_form_and_keep_editing_preserves_draft() {
        val repo = repository()
        val id = expense(repo)
        try {
            panel(repo, id)
            rule.onNodeWithText("Received cash").performClick()
            rule.onNode(editableLedgerField("Repayment amount (INR)")).performTextInput("500")
            rule.onNodeWithText("Recorded payment").performScrollTo().performClick()
            rule.onNodeWithText("Choose a recorded payment").performScrollTo().performClick()
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            androidx.test.espresso.Espresso.pressBack()
            rule.onNode(editableLedgerField("Repayment amount (INR)")).assertIsDisplayed()
            rule.onNodeWithText("500").assertExists()
            rule.onNodeWithText("Received cash").performScrollTo().performClick()
            rule.onNodeWithContentDescription("Close repayment editor").performClick()
            rule.onNodeWithText("Keep editing").performClick()
            rule.onNodeWithText("Record cash repayment").assertIsDisplayed().performClick()
            rule.waitUntil(15000) { runBlocking { repo.repaymentsFor(id).size == 1 } }
            assertEquals(50000L, runBlocking { repo.repaymentsFor(id).single().amountMinor })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }
}
