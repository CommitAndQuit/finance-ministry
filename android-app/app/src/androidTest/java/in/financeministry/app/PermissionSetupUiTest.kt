package `in`.financeministry.app

import android.app.Application
import android.app.Activity
import android.app.Instrumentation
import android.Manifest
import android.content.ContextWrapper
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
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
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import java.util.UUID

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PermissionSetupUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun repository(context: Context = ApplicationProvider.getApplicationContext<Application>()) =
        TransactionRepository(context, "setup_ui_${UUID.randomUUID()}")
    private fun skipTour(r: TransactionRepository) {
        r.preferences.edit().putInt("app_tour_seen_version", BuildConfig.VERSION_CODE)
            .putBoolean("onboarding_complete", true).commit()
    }

    /** Opt in only on a disposable emulator with POST_NOTIFICATIONS denied and its flags reset. */
    @Test fun a_actual_notification_prompt_grant_hides_the_action_without_a_resume_signal() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("runPermissionPrompt") == "true")
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        val r = repository()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            assertEquals("Host must reset notification permission before this opt-in test", PackageManager.PERMISSION_DENIED,
                rule.activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            skipTour(r)
            r.preferences.edit().putBoolean("sms_permission_requested", true).putBoolean("sms_disclosure", false)
                .putBoolean("notifications", true).commit()
            rule.setContent { FinanceMinistryTheme { LedgerApp(r, null, consumeRequest = {}) } }
            assertFalse(r.preferences.getBoolean("notification_permission_requested", false))
            rule.onNodeWithText("Enable notifications").assertIsDisplayed().performClick()
            rule.waitUntil(15000) {
                instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_allow_button")
                    ?.any { it.isVisibleToUser } == true
            }
            val allow = instrumentation.uiAutomation.rootInActiveWindow
                .findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_allow_button").first { it.isVisibleToUser }
            assertTrue(allow.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            rule.waitUntil(15000) { rule.onAllNodesWithText("Enable notifications").fetchSemanticsNodes().isEmpty() }
            assertEquals(PackageManager.PERMISSION_GRANTED, rule.activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            assertTrue(r.preferences.getBoolean("notification_permission_requested", false))
            assertFalse(r.preferences.getBoolean("sms_disclosure", true))
            rule.onNodeWithText("Set up automatic tracking").assertDoesNotExist()
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }

    @Test fun first_use_sms_action_explains_access_and_not_now_persists_without_changing_the_ledger() {
        val r = repository()
        val launch = mutableIntStateOf(0)
        try {
            r.preferences.edit().putInt("app_tour_seen_version", BuildConfig.VERSION_CODE)
                .putBoolean("onboarding_complete", true).putBoolean("notifications", false).commit()
            runBlocking { r.save(ManualInput("42.50", Direction.Debit, System.currentTimeMillis(), TransactionType.Other, label = "Demo payment")) }
            rule.setContent { key(launch.intValue) { FinanceMinistryTheme { LedgerApp(r, null, consumeRequest = {}) } } }
            rule.onNodeWithText("Enable SMS").assertIsDisplayed().performClick()
            rule.onNodeWithText("Read new SMS on this device?").assertIsDisplayed()
            assertFalse(r.preferences.getBoolean("sms_disclosure", false))
            assertFalse(r.preferences.getBoolean("sms_permission_requested", false))
            rule.onNode(hasText("Not now") and hasAnyAncestor(isDialog())).performClick() // Decline disclosure, not the card.
            rule.onNodeWithText("Enable SMS").assertIsDisplayed()
            rule.onNodeWithText("Not now").performClick() // Snooze the card.
            rule.onNodeWithText("Enable SMS").assertDoesNotExist()
            rule.runOnIdle { launch.intValue++ }
            rule.onNodeWithText("Enable SMS").assertDoesNotExist()
            assertFalse(r.captureAllowed())
            assertEquals(4250L, runBlocking { r.snapshot().debit.toLong() })
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }

    @Test fun intentionally_paused_capture_and_alerts_do_not_show_enable_actions() {
        val r = repository()
        try {
            skipTour(r)
            r.preferences.edit().putBoolean("sms_permission_requested", true).putBoolean("sms_disclosure", false)
                .putBoolean("notifications", false).commit()
            rule.setContent { FinanceMinistryTheme { LedgerApp(r, null, consumeRequest = {}) } }
            rule.onNodeWithText("Set up automatic tracking").assertDoesNotExist()
            rule.onNodeWithText("Enable SMS").assertDoesNotExist()
            rule.onNodeWithText("Enable notifications").assertDoesNotExist()
            rule.onNodeWithContentDescription("Add transaction").assertIsDisplayed().performClick()
            rule.onNode(editableLedgerField("Amount (INR)")).assertExists()
            assertFalse(r.preferences.getBoolean("sms_disclosure", true))
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }

    @Test fun sms_settings_link_and_return_refresh_remove_the_completed_action() {
        val refresh = mutableIntStateOf(0)
        var simulateRevoked = true
        val platform = object : ContextWrapper(rule.activity) {
            override fun checkSelfPermission(permission: String): Int =
                if (permission == Manifest.permission.RECEIVE_SMS && simulateRevoked) PackageManager.PERMISSION_DENIED
                else super.checkSelfPermission(permission)
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (permission == Manifest.permission.RECEIVE_SMS && simulateRevoked) PackageManager.PERMISSION_DENIED
                else super.checkPermission(permission, pid, uid)
        }
        val r = repository(platform)
        var launched: Intent? = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Settings.ACTION_APPLICATION_DETAILS_SETTINGS) return null
                launched = intent
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            skipTour(r)
            instrumentation.uiAutomation.grantRuntimePermission(rule.activity.packageName, Manifest.permission.RECEIVE_SMS)
            r.preferences.edit().putBoolean("sms_permission_requested", true).putBoolean("sms_disclosure", true)
                .putBoolean("notifications", false).commit()
            rule.setContent { CompositionLocalProvider(LocalContext provides platform) {
                FinanceMinistryTheme { LedgerApp(r, null, refreshGeneration = refresh.intValue, consumeRequest = {}) }
            } }
            rule.onNodeWithText("Enable SMS").performClick()
            rule.runOnIdle {
                assertEquals("package:${rule.activity.packageName}", requireNotNull(launched).data.toString())
                assertNull(launched!!.clipData)
                assertNull(launched!!.extras)
                simulateRevoked = false
                refresh.intValue++ // Same refresh signal MainActivity.onResume supplies.
            }
            rule.onNodeWithText("Enable SMS").assertDoesNotExist()
            rule.onNodeWithText("Set up automatic tracking").assertDoesNotExist()
            assertTrue(r.captureAllowed())
            assertTrue(runBlocking { r.snapshot().rows.isEmpty() })
        } finally { instrumentation.removeMonitor(monitor); runBlocking { r.eraseAll(); r.close() } }
    }

    @Test fun notification_recovery_opens_this_apps_settings_without_financial_payload() {
        val r = repository()
        val platform = object : ContextWrapper(rule.activity) {
            override fun checkSelfPermission(permission: String): Int =
                if (permission == Manifest.permission.POST_NOTIFICATIONS) PackageManager.PERMISSION_DENIED
                else super.checkSelfPermission(permission)
        }
        var launched: Intent? = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action !in listOf(Settings.ACTION_APP_NOTIFICATION_SETTINGS, Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)) return null
                launched = intent
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            skipTour(r)
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(rule.activity.packageName, Manifest.permission.POST_NOTIFICATIONS)
            r.preferences.edit().putBoolean("sms_permission_requested", true).putBoolean("sms_disclosure", false)
                .putBoolean("notifications", true).putBoolean("notification_permission_requested", true).commit()
            rule.setContent { CompositionLocalProvider(LocalContext provides platform) {
                FinanceMinistryTheme { LedgerApp(r, null, consumeRequest = {}) }
            } }
            rule.onNodeWithText("Enable SMS").assertDoesNotExist()
            rule.onNodeWithText("Enable notifications").assertIsDisplayed().performClick()
            rule.runOnIdle {
                val intent = requireNotNull(launched)
                assertEquals(rule.activity.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
                assertTrue(intent.extras!!.keySet().all { it in listOf(Settings.EXTRA_APP_PACKAGE, Settings.EXTRA_CHANNEL_ID) })
                assertNull(intent.data)
                assertNull(intent.clipData)
            }
            assertFalse(r.preferences.getBoolean("sms_disclosure", true))
        } finally { instrumentation.removeMonitor(monitor); runBlocking { r.eraseAll(); r.close() } }
    }

    @Test fun new_version_reoffers_previously_dismissed_setup() {
        val r = repository()
        try {
            skipTour(r)
            r.preferences.edit().putBoolean("notifications", false)
                .putLong("permission_setup_dismissed_at", System.currentTimeMillis())
                .putInt("permission_setup_dismissed_version", BuildConfig.VERSION_CODE - 1).commit()
            rule.setContent { FinanceMinistryTheme { LedgerApp(r, null, consumeRequest = {}) } }
            rule.onNodeWithText("Enable SMS").assertIsDisplayed()
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }
}
