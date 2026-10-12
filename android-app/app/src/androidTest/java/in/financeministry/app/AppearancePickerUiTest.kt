package `in`.financeministry.app

import android.app.Application
import android.graphics.Bitmap
import android.util.TypedValue
import android.view.View
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import `in`.financeministry.app.feature.RepaymentEditor
import `in`.financeministry.app.feature.ReviewReminderPanel
import `in`.financeministry.app.ui.LocalLedgerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** A phone's theme must not override the appearance selected inside the ledger. */
class AppearancePickerUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun repository() = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "picker_ui_${UUID.randomUUID()}")

    private fun assertPickerAppearance(kind: Class<out View>, dark: Boolean, name: String) {
        // The activity loses focus while a platform picker is open. Restrict the
        // lookup to the dialog so Espresso cannot wait on that inactive root.
        onView(isAssignableFrom(kind)).inRoot(isDialog()).check { view, failure ->
            if (failure != null) throw failure
            val value = TypedValue()
            assertTrue("Picker background must resolve", view.context.theme.resolveAttribute(android.R.attr.colorBackground, value, true))
            val color = if (value.resourceId != 0) view.context.getColor(value.resourceId) else value.data
            assertEquals("$name must follow app appearance, not phone appearance", dark, ColorUtils.calculateLuminance(color) < .5)
            assertTrue("Picker accent must resolve", view.context.theme.resolveAttribute(android.R.attr.colorAccent, value, true))
            val accent = if (value.resourceId != 0) view.context.getColor(value.resourceId) else value.data
            assertEquals("$name must use the approved ledger accent", if (dark) 0xFFA7B9FF.toInt() else 0xFF3455DB.toInt(), accent)
        }
        val i = InstrumentationRegistry.getInstrumentation()
        val shot = requireNotNull(i.uiAutomation.takeScreenshot())
        try {
            val variant = InstrumentationRegistry.getArguments().getString("visualVariant") ?: "light"
            File(i.targetContext.getExternalFilesDir("settings-ux"), "$name-$variant.png").outputStream().use {
                assertTrue(shot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { shot.recycle() }
        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
    }

    @Test fun transaction_date_and_time_pickers_follow_explicit_app_appearance() {
        val r = repository()
        try {
            r.preferences.edit().putInt("app_tour_seen_version", BuildConfig.VERSION_CODE)
                .putBoolean("onboarding_complete", true).putString("appearance", "Dark").commit()
            rule.setContent { LedgerApp(r, null, consumeRequest = {}) }
            rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("Add transaction").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Add transaction").performClick()
            rule.onNodeWithText("More details").performScrollTo().performClick()
            for ((mode, dark) in listOf("Dark" to true, "Light" to false)) {
                r.preferences.edit().putString("appearance", mode).commit()
                rule.waitForIdle()
                rule.onNode(hasText("Date:", substring = true)).performScrollTo().performClick()
                assertPickerAppearance(DatePicker::class.java, dark, "transaction-date-${mode.lowercase()}")
                rule.onNode(hasText("Time:", substring = true)).performScrollTo().performClick()
                assertPickerAppearance(TimePicker::class.java, dark, "transaction-time-${mode.lowercase()}")
            }
            rule.onNodeWithContentDescription("Close transaction editor").performClick()
            assertTrue("Opening and cancelling pickers must not create a payment", runBlocking { r.snapshot().rows.isEmpty() })
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }

    @Test fun cash_repayment_date_picker_follows_explicit_app_appearance_without_changing_debt() {
        val r = repository()
        try {
            val id = runBlocking { r.save(ManualInput("300", Direction.Debit, System.currentTimeMillis(),
                TransactionType.Other, label = "Picker expense", ownership = SpendingOwnership.ForOther, groupLabel = "Alice")) }
            val expense = runBlocking { requireNotNull(r.get(id)) }
            r.preferences.edit().putString("appearance", "Dark").commit()
            rule.setContent { LocalLedgerTheme(r.preferences) { RepaymentEditor(r, expense, null, onDismiss = {}) } }
            rule.waitUntil(15000) { rule.onAllNodes(hasText("Received cash") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Received cash").performScrollTo().performClick()
            for ((mode, dark) in listOf("Dark" to true, "Light" to false)) {
                r.preferences.edit().putString("appearance", mode).commit()
                rule.waitForIdle()
                rule.onNode(hasText("Received:", substring = true)).performScrollTo().performClick()
                assertPickerAppearance(DatePicker::class.java, dark, "repayment-date-${mode.lowercase()}")
            }
            assertTrue(runBlocking { r.repaymentsFor(id).isEmpty() })
            assertEquals(30000L, runBlocking { RepaymentAccounting.owed(requireNotNull(r.get(id))) })
            assertEquals(1, runBlocking { r.snapshot().rows.size })
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }

    @Test fun reminder_time_picker_follows_explicit_app_appearance_without_enabling_reminders() {
        val r = repository()
        try {
            r.preferences.edit().putString("appearance", "Dark").commit()
            rule.setContent { LocalLedgerTheme(r.preferences) { ReviewReminderPanel(r) } }
            for ((mode, dark) in listOf("Dark" to true, "Light" to false)) {
                r.preferences.edit().putString("appearance", mode).commit()
                rule.waitForIdle()
                rule.onNodeWithText("20:00").performClick()
                assertPickerAppearance(TimePicker::class.java, dark, "reminder-time-${mode.lowercase()}")
            }
            assertFalse(r.preferences.getBoolean("review_reminder", false))
            assertEquals(20, r.preferences.getInt("review_reminder_hour", 20))
            assertEquals(0, r.preferences.getInt("review_reminder_minute", 0))
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }
}
