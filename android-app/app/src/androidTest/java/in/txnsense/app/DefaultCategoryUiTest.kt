package `in`.txnsense.app

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.txnsense.app.data.*
import `in`.txnsense.app.feature.TransactionForm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class DefaultCategoryUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun a_fresh_form_can_choose_grocery_without_old_category_clutter() = choose("Grocery")
    @Test fun a_fresh_form_can_choose_transport_without_old_category_clutter() = choose("Transport")

    private fun choose(category: String) {
        val r = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "default_ui_${UUID.randomUUID()}")
        try {
            var saved = false
            rule.setContent { FinanceMinistryTheme { Surface { TransactionForm(r, null, onDone = { saved = true }) } } }
            rule.onNode(editableLedgerField("Amount (INR)")).performTextInput("25")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.onNode(publicEditorChoice("Category", "Other")).performScrollTo().performClick()
            listOf("Grocery", "Food", "Entertainment", "Bills", "Transport", "Investment").forEach {
                rule.onNodeWithText(it, useUnmergedTree = true).assertExists()
            }
            listOf("Travel", "Shopping", "Flat expenses", "Health", "Education", "Income").forEach {
                rule.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist()
            }
            if (category == "Grocery") {
                rule.waitForIdle()
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val shot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                try {
                    File(instrumentation.targetContext.getExternalFilesDir("play-prep"), "default-categories.png")
                        .outputStream().use { assertTrue(shot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { shot.recycle() }
            }
            rule.onNodeWithText(category, useUnmergedTree = true).performClick()
            rule.onNode(publicEditorChoice("Category", category)).assertExists()
            rule.onNode(publicEditorSaveButton()).performClick()
            rule.waitUntil(15000) { saved }
            assertEquals(category, runBlocking { r.snapshot().rows.single().category })
            assertEquals(2500L, runBlocking { r.snapshot().debit.toLong() })
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }
}
