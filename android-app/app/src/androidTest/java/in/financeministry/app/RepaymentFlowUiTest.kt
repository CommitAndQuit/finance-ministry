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
        rule.onNode(hasText("Record repayment") or hasText("Record money received")).performClick()
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
        rule.waitUntil(10000) { automation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
        val keyboard = android.graphics.Rect()
        automation.windows.first { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }.getBoundsInScreen(keyboard)
        val save = rule.onNodeWithText("Save repayment").fetchSemanticsNode().boundsInWindow
        assertTrue("Save overlaps the visible keyboard", save.bottom <= keyboard.top)
    }

    // A missing direct action must fail this: recording must not route through transaction details.
    @Test fun outstanding_expense_can_record_cash_without_opening_details() {
        val repo = repository()
        val id = expense(repo)
        var openedDetails = false
        try {
            rule.setContent { FinanceMinistryTheme { RepaymentsScreen(repo, LocalDate.now(), {}, { openedDetails = true }) } }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Still owed · all dates").fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Flatmates"))
            rule.onNodeWithText("Flatmates").performClick()
            rule.onNodeWithText("Record money received").assertIsDisplayed().performClick()
            rule.onNodeWithText("Received cash").performClick()
            rule.onNodeWithText("Repayment amount (INR)").performClick().performTextInput("500")
            assertSaveAboveKeyboard()
            capture("cash-keyboard.png")
            rule.onNodeWithText("Save repayment").assertIsDisplayed().performClick()
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
            rule.onNodeWithText("Save repayment").assertIsDisplayed()
            rule.onNode(hasText("Choose a recorded payment") or hasText("Link an existing money-in")).performClick()
            rule.onNodeWithText("Search recorded payments").performTextInput("Chosen receipt")
            capture("recorded-payment-search.png")
            rule.onNode(hasText("Chosen receipt") and !hasSetTextAction()).performScrollTo().performClick()
            rule.onNodeWithText("Repayment amount (INR)").performClick().performTextInput("500")
            assertSaveAboveKeyboard()
            rule.onNodeWithText("Still owed after saving: ₹1,500.00").assertExists()
            rule.onNodeWithText("Save repayment").assertIsDisplayed().performClick()
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
            rule.onNode(hasText("Received cash") or hasText("Record cash repayment")).performClick()
            rule.onNodeWithText("Repayment amount (INR)").performTextInput("2001")
            rule.onNodeWithText("Save repayment").assertIsDisplayed().performClick()
            rule.onNodeWithText("Repayment exceeds the amount still owed.").assertIsDisplayed()
            assertTrue(runBlocking { repo.repaymentsFor(id).isEmpty() })
            rule.onNodeWithText("Repayment amount (INR)").performTextReplacement("500")
            rule.onNodeWithText("Save repayment").assertIsDisplayed().performClick()
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
            rule.onNodeWithText("Repayment amount (INR)").performTextReplacement("250")
            rule.onNodeWithText("Still owed after saving: ₹1,750.00").assertExists()
            rule.onNodeWithText("Save repayment").assertIsDisplayed().performClick()
            rule.waitUntil(15000) { runBlocking { repo.repaymentsFor(id).single().amountMinor == 25000L } }
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
            rule.onNodeWithText("Repayment amount (INR)").performTextInput("500")
            rule.onNodeWithText("Choose a recorded payment").performScrollTo().performClick()
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            androidx.test.espresso.Espresso.pressBack()
            rule.onNodeWithText("Repayment amount (INR)").assertIsDisplayed()
            rule.onNodeWithText("500").assertExists()
            rule.onNodeWithText("Cancel").performClick()
            rule.onNodeWithText("Keep editing").performClick()
            rule.onNodeWithText("Save repayment").assertIsDisplayed().performClick()
            rule.waitUntil(15000) { runBlocking { repo.repaymentsFor(id).size == 1 } }
            assertEquals(50000L, runBlocking { repo.repaymentsFor(id).single().amountMinor })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }
}
