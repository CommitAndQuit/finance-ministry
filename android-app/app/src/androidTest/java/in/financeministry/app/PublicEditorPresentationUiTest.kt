package `in`.financeministry.app

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Presentation must not silently change the payment's original financial evidence. */
class PublicEditorPresentationUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var keyboardPreference: String? = null
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }
    @Before fun showKeyboard() {
        keyboardPreference = shell("settings get secure show_ime_with_hard_keyboard").also { check(it in setOf("0", "1", "null")) }
        shell("settings put secure show_ime_with_hard_keyboard 1")
    }
    @After fun restoreKeyboard() {
        keyboardPreference?.let {
            shell(if (it == "null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $it")
        }
    }

    private fun withQuickEditor(check: (TransactionRepository, TransactionEntity) -> Unit) {
        val r = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "editor_public_${UUID.randomUUID()}")
        try {
            val row = runBlocking {
                r.preferences.edit().putBoolean("onboarding_complete", true)
                    .putInt("app_tour_seen_version", BuildConfig.VERSION_CODE).commit()
                val source = r.addPaymentSource("Everyday UPI", "UPI", Channel.UPI, "Test bank", "7111")
                val id = r.save(ManualInput("125.50", Direction.Debit, System.currentTimeMillis(), TransactionType.Other,
                    channel = Channel.UPI, label = "Weekly groceries", category = "Food", paymentSourceId = source.id))
                r.get(id)!!
            }
            rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
            rule.setContent { LedgerApp(r, null, consumeRequest = {}) }
            rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("Categorize Weekly groceries").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Categorize Weekly groceries").performClick()
            rule.waitUntil(15000) { rule.onAllNodes(publicEditorSaveButton() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            check(r, row)
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }

    private fun capture(name: String) {
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        val frame = CountDownLatch(1)
        rule.runOnUiThread { rule.activity.window.decorView.apply {
            viewTreeObserver.registerFrameCommitCallback { frame.countDown() }; invalidate()
        } }
        assertTrue("Editor did not commit a rendered frame", frame.await(5, TimeUnit.SECONDS))
        val i = InstrumentationRegistry.getInstrumentation()
        val variant = InstrumentationRegistry.getArguments().getString("visualVariant") ?: "light"
        val shot = requireNotNull(i.uiAutomation.takeScreenshot())
        try {
            File(i.targetContext.getExternalFilesDir("editor-ux"), "$name-$variant.png").outputStream().use {
                assertTrue(shot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { shot.recycle() }
    }

    // Dropping source context, or replacing the source nickname with a generic method,
    // must fail while the original persisted row remains exactly unchanged.
    @Test fun quick_editor_identifies_the_payment_amount_and_registered_source_without_editing_it() = withQuickEditor { r, row ->
        rule.onNodeWithText("Category & purpose").assertIsDisplayed()
        rule.onNode(hasText("Weekly groceries") and hasText("₹125.50 · Everyday UPI") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        rule.onNode(publicEditorChoice("Category", "Food")).assertIsDisplayed()
        rule.onNode(publicEditorSaveButton()).assertIsDisplayed()
        capture("quick-context")
        rule.onNodeWithContentDescription("Close transaction editor").performClick()
        assertEquals(row, runBlocking { r.get(row.id) })
    }

    // Heading, Close and Save must remain reachable at the last advanced field.
    @Test fun editor_heading_close_and_save_stay_reachable_when_advanced_fields_scroll() = withQuickEditor { r, row ->
        rule.onNodeWithText("More details").performScrollTo().performClick()
        rule.onNodeWithText("Advanced details").performScrollTo().performClick()
        rule.onNode(editableLedgerField("Account last 4 digits (optional)")).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Payment details").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close transaction editor").assertIsDisplayed()
        rule.onNode(publicEditorSaveButton()).assertIsDisplayed()
        capture("advanced-pinned-header")
        rule.onNode(editableLedgerField("Account last 4 digits (optional)")).performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        rule.waitUntil(10000) { automation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
        val keyboard = android.graphics.Rect()
        automation.windows.first { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }.getBoundsInScreen(keyboard)
        rule.onNodeWithText("Payment details").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close transaction editor").assertIsDisplayed()
        assertTrue("Save must remain above the keyboard", rule.onNode(publicEditorSaveButton()).fetchSemanticsNode().boundsInWindow.bottom <= keyboard.top)
        capture("advanced-pinned-header-keyboard")
        rule.onNodeWithContentDescription("Close transaction editor").performClick()
        assertEquals(row, runBlocking { r.get(row.id) })
    }

    @Test fun keeping_a_quick_purpose_draft_then_saving_does_not_change_financial_fields() = withQuickEditor { r, row ->
        rule.onNode(hasText("Family") and isSelectable()).performScrollTo().performClick()
        rule.onNodeWithContentDescription("Close transaction editor").performClick()
        rule.onNodeWithText("Keep editing").performClick()
        rule.onNode(hasText("Family") and isSelectable()).assertIsSelected()
        assertEquals(row, runBlocking { r.get(row.id) })
        rule.onNode(publicEditorSaveButton()).assertIsDisplayed().performClick()
        rule.waitUntil(15000) { runBlocking { r.get(row.id)?.ownership == "Family" } }
        val saved = runBlocking { r.get(row.id)!! }
        assertEquals(12550L, saved.amountMinor)
        assertEquals("Food", saved.category)
        assertEquals(row.effectiveTimestamp, saved.effectiveTimestamp)
        assertEquals(row.direction, saved.direction)
        assertEquals(row.status, saved.status)
        assertEquals(row.transactionType, saved.transactionType)
        assertEquals(row.channel, saved.channel)
        assertEquals(row.paymentSourceId, saved.paymentSourceId)
        assertEquals(row.referenceHash, saved.referenceHash)
        assertEquals(row.linkedOriginalId, saved.linkedOriginalId)
        assertEquals(12550L, runBlocking { r.snapshot().personalSpend.toLong() })
        assertEquals(12550L, runBlocking { r.snapshot().debit.toLong() })
        assertEquals(0L, runBlocking { r.snapshot().credit.toLong() })
    }
}
