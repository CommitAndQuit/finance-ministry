package `in`.financeministry.app

import android.app.Application
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real settings/navigation/appearance against an isolated ledger, not private user data. */
class PublicSettingsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun repository() = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "settings_ui_${UUID.randomUUID()}")
    private fun show(r: TransactionRepository) {
        runBlocking {
            r.preferences.edit().putInt("app_tour_seen_version", BuildConfig.VERSION_CODE)
                .putBoolean("onboarding_complete", true).putBoolean("notifications", true).commit()
            r.save(ManualInput("42", Direction.Debit, System.currentTimeMillis(), TransactionType.Other, label="Settings test", category="Food"))
        }
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent { FinanceMinistryTheme { LedgerApp(r, null, consumeRequest={}) } }
        rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("Open activity").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String? = null): Bitmap {
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        val frame = CountDownLatch(1)
        rule.runOnUiThread {
            rule.activity.window.decorView.apply {
                viewTreeObserver.registerFrameCommitCallback { frame.countDown() }; invalidate()
            }
        }
        assertTrue("Settings did not commit a rendered frame", frame.await(5, TimeUnit.SECONDS))
        val i = InstrumentationRegistry.getInstrumentation()
        val shot = requireNotNull(i.uiAutomation.takeScreenshot())
        if (name != null) {
            val variant = InstrumentationRegistry.getArguments().getString("visualVariant") ?: "light"
            File(i.targetContext.getExternalFilesDir("settings-ux"), "$name-$variant.png").outputStream().use {
                assertTrue(shot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        return shot
    }
    private fun cleanup(r: TransactionRepository) = runBlocking { r.eraseAll(); r.close() }

    @Test fun close_and_subpage_back_keep_the_activity_query_and_financial_records() {
        val r=repository()
        try {
            show(r)
            val before=runBlocking {r.snapshot()}
            rule.onNodeWithContentDescription("Open activity").performClick()
            rule.onNodeWithText("Search your transactions").performTextInput("Settings test")
            rule.onNode(hasSetTextAction() and hasText("Settings test")).performImeAction()
            rule.onNodeWithContentDescription("Settings").performClick()
            rule.onNodeWithContentDescription("Close settings").assertIsDisplayed()
            rule.onNodeWithText("SMS capture").assertIsDisplayed()
            rule.onNodeWithText("Appearance").assertIsDisplayed()
            screenshot("settings-main").recycle()
            rule.onNodeWithText("Backup & restore").performScrollTo().performClick()
            rule.onNodeWithText("Create encrypted backup").assertExists()
            rule.onNodeWithContentDescription("Back to settings").performClick()
            rule.onNodeWithText("Appearance").assertIsDisplayed()
            rule.onNodeWithContentDescription("Close settings").performClick()
            rule.onNode(hasSetTextAction() and hasText("Settings test")).assertIsDisplayed()
            rule.onNode(hasText("Activity") and !hasClickAction()).assertIsDisplayed()
            val after=runBlocking {r.snapshot()}
            assertEquals(before.rows.map {it.id}, after.rows.map {it.id})
            assertEquals(before.personalSpend, after.personalSpend)
            assertEquals(before.debit, after.debit)
        } finally {cleanup(r)}
    }

    @Test fun quick_sms_control_requires_consent_and_notification_control_persists() {
        val r=repository()
        try {
            show(r)
            rule.onNodeWithContentDescription("Settings").performClick()
            rule.onNodeWithContentDescription("SMS capture").assertIsOff().performClick()
            rule.onNodeWithText("Read new SMS on this device?").assertIsDisplayed()
            assertFalse(r.preferences.getBoolean("sms_disclosure",false))
            rule.onNodeWithText("Not now").performClick()
            rule.onNodeWithContentDescription("SMS capture").assertIsOff()
            rule.onNodeWithContentDescription("Recording notifications").assertIsOn().performClick()
            rule.onNodeWithContentDescription("Recording notifications").assertIsOff()
            assertFalse(r.preferences.getBoolean("notifications",true))
            rule.onNodeWithContentDescription("Close settings").performClick()
            rule.onNodeWithContentDescription("Settings").performClick()
            rule.onNodeWithContentDescription("Recording notifications").assertIsOff()
            assertEquals(4200L,runBlocking {r.snapshot().debit.toLong()})
        } finally {cleanup(r)}
    }

    @Test fun explicit_appearance_changes_the_rendered_ledger_and_system_bar_contrast() {
        val r=repository()
        try {
            show(r)
            for ((choice, background, lightIcons) in listOf(
                Triple("Dark",0xFF14161B.toInt(),false), Triple("Light",0xFFF7F6F2.toInt(),true))) {
                rule.onNodeWithContentDescription("Settings").performClick()
                rule.onNodeWithText("Appearance").performClick()
                rule.onNodeWithText(choice).performClick()
                assertEquals(choice,r.preferences.getString("appearance",null))
                rule.onNodeWithContentDescription("Back to settings").performClick()
                val sheetShot=screenshot("settings-${choice.lowercase()}")
                try {
                    // Inspect the system clock while the sheet owns a separate
                    // window. Checking only Activity.window misses this contrast bug.
                    val clockColors=(sheetShot.height*18/1000..sheetShot.height*40/1000).flatMap { y ->
                        (sheetShot.width*4/100..sheetShot.width*14/100).map { x -> android.graphics.Color.red(sheetShot.getPixel(x,y)) }
                    }
                    if(choice=="Dark") assertTrue("Dark sheet clock is unreadable: brightest pixel ${clockColors.max()}",clockColors.max()>=180)
                    else assertTrue("Light sheet clock is unreadable: darkest pixel ${clockColors.min()}",clockColors.min()<=80)
                } finally {sheetShot.recycle()}
                rule.onNodeWithContentDescription("Close settings").performClick()
                val shot=screenshot()
                try {assertEquals("Ledger background for $choice",background,shot.getPixel(2,shot.height/2))}
                finally {shot.recycle()}
                rule.runOnIdle {
                    assertEquals("Status icons for $choice",lightIcons,
                        WindowInsetsControllerCompat(rule.activity.window,rule.activity.window.decorView).isAppearanceLightStatusBars)
                }
            }
            rule.onNodeWithContentDescription("Settings").performClick()
            rule.onNodeWithText("Appearance").performClick()
            rule.onNodeWithText("System").performClick()
            assertEquals("System",r.preferences.getString("appearance",null))
            rule.onNodeWithContentDescription("Back to settings").performClick()
            rule.onNodeWithContentDescription("Close settings").performClick()
            assertEquals(4200L,runBlocking {r.snapshot().personalSpend.toLong()})
        } finally {cleanup(r)}
    }

    @Test fun android_back_returns_from_a_settings_subpage_before_closing_the_sheet() {
        val r=repository()
        try {
            show(r)
            rule.onNodeWithContentDescription("Open people").performClick()
            rule.onNodeWithContentDescription("Settings").performClick()
            rule.onNodeWithText("Payment sources").performScrollTo().performClick()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            rule.onNodeWithText("Appearance").assertIsDisplayed()
            rule.onNodeWithContentDescription("Close settings").assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            rule.onNodeWithContentDescription("Close settings").assertDoesNotExist()
            rule.onNodeWithText("Still owed to you · all dates").assertIsDisplayed()
        } finally {cleanup(r)}
    }

    @Test fun advanced_tools_remain_available_without_filling_the_initial_settings_sheet() {
        val r=repository()
        try {
            show(r)
            rule.onNodeWithContentDescription("Settings").performClick()
            rule.onNodeWithText("Optional tools").assertDoesNotExist()
            rule.onNodeWithText("More settings").performScrollTo().performClick()
            rule.onNodeWithText("Optional tools").performScrollTo().performClick()
            rule.onNodeWithText("Budgets").assertExists()
            rule.onNodeWithContentDescription("Back to settings").performClick()
            rule.onNodeWithText("More settings").performScrollTo().performClick()
            rule.onNodeWithText("Optional tools").assertDoesNotExist()
            rule.onNodeWithContentDescription("Close settings").performClick()
            assertEquals(4200L,runBlocking {r.snapshot().debit.toLong()})
        } finally {cleanup(r)}
    }
}
