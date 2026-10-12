package `in`.financeministry.app

import android.app.Application
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real filter drafts, resulting rows and reachable controls on an isolated local ledger. */
class PublicFiltersUiTest {
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
    private fun repository() = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "filter_ui_${UUID.randomUUID()}")
    private fun show(r: TransactionRepository, extraCategories: Boolean = false) {
        runBlocking {
            r.preferences.edit().putInt("app_tour_seen_version", BuildConfig.VERSION_CODE).putBoolean("onboarding_complete", true).commit()
            val source = r.addPaymentSource("Everyday UPI", "UPI", Channel.UPI, "Test bank", "7111")
            r.save(ManualInput("125", Direction.Debit, System.currentTimeMillis(), TransactionType.Other,
                label = "Needle family", category = "Food", ownership = SpendingOwnership.Family, channel = Channel.UPI, paymentSourceId = source.id))
            r.save(ManualInput("170", Direction.Debit, System.currentTimeMillis(), TransactionType.Other,
                label = "Needle travel", category = "Travel", channel = Channel.UPI, paymentSourceId = source.id))
            r.save(ManualInput("40", Direction.Debit, System.currentTimeMillis(), TransactionType.Other,
                label = "Other family", category = "Food", ownership = SpendingOwnership.Family, channel = Channel.UPI, paymentSourceId = source.id))
            r.save(ManualInput("300", Direction.Credit, System.currentTimeMillis(), TransactionType.Other,
                label = "Needle income", category = "Income"))
            if (extraCategories) repeat(24) { r.addCategory("Local category ${it.toString().padStart(2, '0')}") }
        }
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent { LedgerApp(r, null, consumeRequest = {}) }
        rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("Open activity").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Open activity").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithText("4 transactions").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openFilters() {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Filter transactions"))
        rule.onNodeWithContentDescription("Filter transactions").performClick()
    }
    private fun chooseCategory(name: String) {
        rule.onNode(hasText(name) and isSelectable() and hasAnyAncestor(isDialog())).performScrollTo().performClick()
    }
    private fun capture(name: String) {
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        val frame = CountDownLatch(1)
        rule.runOnUiThread {
            rule.activity.window.decorView.apply {
                viewTreeObserver.registerFrameCommitCallback { frame.countDown() }; invalidate()
            }
        }
        assertTrue("Activity did not commit a rendered frame", frame.await(5, TimeUnit.SECONDS))
        val i = InstrumentationRegistry.getInstrumentation()
        val variant = InstrumentationRegistry.getArguments().getString("visualVariant") ?: "light"
        val shot = requireNotNull(i.uiAutomation.takeScreenshot())
        try {
            File(i.targetContext.getExternalFilesDir("activity-ux"), "$name-$variant.png").outputStream().use {
                assertTrue(shot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { shot.recycle() }
    }
    private fun assertUnchanged(r: TransactionRepository) = runBlocking {
        val data = r.snapshot()
        assertEquals(4, data.rows.size)
        assertEquals(33500L, data.debit.toLong())
        assertEquals(30000L, data.credit.toLong())
        assertEquals(33500L, data.personalSpend.toLong())
        assertTrue(data.rows.none { it.isUserCorrected })
    }
    private fun cleanup(r: TransactionRepository) = runBlocking { r.eraseAll(); r.close() }

    @Test fun category_filter_keeps_header_and_apply_visible_when_the_category_list_scrolls() {
        val r = repository()
        try {
            show(r, extraCategories = true); openFilters()
            rule.onNodeWithContentDescription("Close filters").assertIsDisplayed()
            rule.onNodeWithText("Search transactions").assertDoesNotExist()
            capture("filters-initial")
            rule.onNodeWithContentDescription("Choose filter categories").performClick()
            chooseCategory("Local category 23")
            rule.onNodeWithText("Filter activity").assertIsDisplayed()
            rule.onNodeWithContentDescription("Close filters").assertIsDisplayed()
            rule.onNodeWithText("Show transactions").assertIsDisplayed()
            capture("filters-long-categories")
            rule.onNodeWithText("Show transactions").performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithText("No matching transactions").fetchSemanticsNodes().isNotEmpty() }
            assertUnchanged(r)
        } finally { cleanup(r) }
    }

    @Test fun closing_a_category_draft_preserves_the_active_query_and_money_direction() {
        val r = repository()
        try {
            show(r)
            rule.onNodeWithText("Search your transactions").performTextInput("Needle")
            rule.onNode(hasSetTextAction() and hasText("Needle")).performImeAction()
            rule.onNode(hasText("Money out") and hasClickAction()).performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithText("2 transactions").fetchSemanticsNodes().isNotEmpty() }
            openFilters()
            rule.onNodeWithContentDescription("Choose filter categories").performClick(); chooseCategory("Food")
            rule.onNodeWithContentDescription("Close filters").performClick()
            rule.onNode(hasSetTextAction() and hasText("Needle")).assertExists()
            rule.onNode(hasText("Money out") and isSelectable()).assertIsSelected()
            rule.onNodeWithText("2 transactions").assertExists()
            openFilters()
            rule.onNodeWithContentDescription("Choose filter categories").performClick()
            rule.onNode(hasText("Food") and isSelectable() and hasAnyAncestor(isDialog())).assertIsNotSelected()
            rule.onNodeWithContentDescription("Close filters").performClick()
            assertUnchanged(r)
        } finally { cleanup(r) }
    }

    @Test fun combined_category_source_purpose_origin_direction_and_search_filter_without_mutating_records() {
        val r = repository()
        try {
            show(r); openFilters()
            rule.onNodeWithContentDescription("Choose filter categories").performClick()
            chooseCategory("Food"); chooseCategory("Travel")
            rule.onNodeWithContentDescription("Choose filter categories").performScrollTo().performClick()
            rule.onNodeWithText("More filters").performScrollTo().performClick()
            for (name in listOf("Money out", "Family", "Added manually", "Everyday UPI")) {
                rule.onNode(hasText(name) and isSelectable() and hasAnyAncestor(isDialog())).performScrollTo().performClick()
            }
            rule.onNodeWithText("Search transactions").performScrollTo().performTextInput("Needle")
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.serviceInfo = automation.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
            rule.waitUntil(10000) { automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
            val keyboard = android.graphics.Rect()
            automation.windows.first { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }.getBoundsInScreen(keyboard)
            assertTrue("Apply must remain above the keyboard", rule.onNodeWithText("Show transactions").fetchSemanticsNode().boundsInWindow.bottom <= keyboard.top)
            rule.onNodeWithText("Filter activity").assertIsDisplayed()
            capture("filters-keyboard")
            rule.onNodeWithText("Show transactions").assertIsDisplayed().performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithText("1 transaction").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Categorize Needle family").assertExists()
            rule.onNodeWithContentDescription("Categorize Needle travel").assertDoesNotExist()
            rule.onNodeWithContentDescription("Categorize Other family").assertDoesNotExist()
            openFilters()
            rule.onNode(hasText("Family") and isSelectable() and hasAnyAncestor(isDialog())).performScrollTo().assertIsSelected()
            rule.onNode(hasText("Everyday UPI") and isSelectable() and hasAnyAncestor(isDialog())).performScrollTo().assertIsSelected()
            rule.onNodeWithText("Clear all").assertIsDisplayed().performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithText("4 transactions").fetchSemanticsNodes().isNotEmpty() }
            assertUnchanged(r)
        } finally { cleanup(r) }
    }

    @Test fun compact_search_can_clear_only_the_query_without_losing_category_or_money_direction() {
        val r = repository()
        try {
            show(r)
            rule.onNode(hasSetTextAction()).assertHeightIsAtLeast(48.dp)
            val maximumSearchHeight = with(rule.density) { (if (fontScale > 1.1f) 60.dp else 50.dp).toPx() }
            assertTrue("Search should keep its compact 48dp rhythm", rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.height <= maximumSearchHeight)
            rule.onNodeWithText("Search your transactions").performTextInput("Needle")
            rule.onNode(hasSetTextAction() and hasText("Needle")).performImeAction()
            rule.onNode(hasText("Money out") and hasClickAction()).performClick()
            openFilters()
            rule.onNodeWithContentDescription("Choose filter categories").performClick(); chooseCategory("Food")
            rule.onNodeWithText("Show transactions").performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithText("1 transaction").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Clear search").performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithText("2 transactions").fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasText("Money out") and isSelectable()).assertIsSelected()
            rule.onNodeWithContentDescription("Categorize Needle travel").assertDoesNotExist()
            rule.onNodeWithContentDescription("Categorize Needle family").assertExists()
            rule.onNodeWithContentDescription("Categorize Other family").assertExists()
            capture("activity-filtered-search-cleared")
            assertUnchanged(r)
        } finally { cleanup(r) }
    }

    @Test fun search_has_a_stable_accessible_name_and_a_visible_focus_outline() {
        val r = repository()
        try {
            show(r)
            val search = rule.onNodeWithContentDescription("Search transactions")
            search.assertIsDisplayed().performClick()
            val pixels = search.captureToImage().toPixelMap()
            val dark = (rule.activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val accent = if (dark) 0xFFA7B9FF.toInt() else 0xFF3455DB.toInt()
            assertEquals("The focused search outline should be clearly visible", accent, pixels[1, pixels.height / 2].toArgb())
            search.performTextInput("Needle")
            search.assertExists() // Typing must not remove its accessible name.
            search.performImeAction()
            rule.waitUntil(15000) { rule.onAllNodesWithText("3 transactions").fetchSemanticsNodes().isNotEmpty() }
            assertUnchanged(r)
        } finally { cleanup(r) }
    }
}
