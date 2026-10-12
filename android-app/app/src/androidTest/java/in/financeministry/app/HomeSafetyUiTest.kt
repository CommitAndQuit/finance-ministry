package `in`.financeministry.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Real Home actions and persisted safety preferences in a disposable ledger. */
class HomeSafetyUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val generation = mutableIntStateOf(0)

    private fun withHome(entries: Int = 0, configure: (TransactionRepository) -> Unit = {},
        check: (TransactionRepository) -> Unit) {
        val repo = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "home_safety_${UUID.randomUUID()}")
        try {
            repo.preferences.edit().putBoolean("onboarding_complete", true)
                .putInt("app_tour_seen_version", BuildConfig.VERSION_CODE).commit()
            runBlocking { repeat(entries) { repo.save(ManualInput("1", Direction.Debit, System.currentTimeMillis(),
                TransactionType.Other, label = "Entry $it")) } }
            configure(repo)
            rule.setContent { key(generation.intValue) { LedgerApp(repo, null, consumeRequest = {}) } }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Recent activity").fetchSemanticsNodes().isNotEmpty() }
            check(repo)
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    private fun reopen() { rule.runOnIdle { generation.intValue++ } }
    private fun backupCue() = rule.onNodeWithText("Keep a backup of your ledger")

    // A warning that does not reach a usable manual form must fail on actual persisted recovery.
    @Test fun capture_error_opens_manual_entry_and_records_the_missing_payment() = withHome(configure = {
        it.preferences.edit().putBoolean("capture_error", true).commit()
    }) { repo ->
        rule.onNodeWithText("A message could not be recorded. Add it manually if needed.").performScrollTo().performClick()
        rule.waitUntil(15000) { rule.onAllNodes(publicEditorSaveButton() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(editableLedgerField("Amount (INR)")).performTextInput("12.34")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNode(publicEditorSaveButton()).performClick()
        rule.waitUntil(15000) { runBlocking { repo.snapshot().rows.size == 1 } }
        assertEquals(1234L, runBlocking { repo.snapshot().debit.toLong() })
        assertTrue("A manual correction must not pretend SMS capture has recovered", repo.preferences.getBoolean("capture_error", false))
    }

    @Test fun capture_status_warning_reaches_sms_settings_without_losing_the_ledger() = withHome(entries = 1, configure = {
        it.preferences.edit().putBoolean("sms_permission_requested", true).putBoolean("sms_disclosure", false).commit()
    }) { repo ->
        rule.onNode(hasText("Manage settings", substring = true) and hasClickAction()).performScrollTo().performClick()
        rule.onNodeWithText("SMS & notifications").assertIsDisplayed()
        rule.onNodeWithText("You can always add or correct a transaction yourself.", substring = true).assertExists()
        assertEquals(100L, runBlocking { repo.snapshot().debit.toLong() })
    }

    @Test fun a_stale_or_missing_backup_reminder_opens_encrypted_backup_tools() = withHome(entries = 20) { repo ->
        backupCue().performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText("Backup & restore").assertIsDisplayed()
        rule.onNodeWithText("Create encrypted backup").assertExists()
        rule.onNodeWithText("No completed backup yet.").assertExists()
        assertEquals(20, runBlocking { repo.snapshot().rows.size })
    }

    @Test fun recent_backup_does_not_prompt_again() = withHome(entries = 20, configure = {
        it.preferences.edit().putLong("last_backup_at", System.currentTimeMillis()).commit()
    }) { repo ->
        backupCue().assertDoesNotExist()
        assertEquals(20, runBlocking { repo.snapshot().rows.size })
    }

    @Test fun a_small_new_ledger_is_not_immediately_nagged_for_backup() = withHome(entries = 19) { repo ->
        backupCue().assertDoesNotExist()
        assertEquals(19, runBlocking { repo.snapshot().rows.size })
    }

    // Forgetting the snooze preference, or ignoring it on a fresh composition, must fail.
    @Test fun dismissing_backup_reminder_survives_reopening_then_expires_after_the_snooze() = withHome(entries = 20) { repo ->
        backupCue().performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Dismiss backup reminder").performClick()
        assertTrue(repo.preferences.getLong("backup_reminder_dismissed_at", 0) > 0)
        backupCue().assertDoesNotExist()
        reopen()
        rule.waitUntil(15000) { rule.onAllNodesWithText("Recent activity").fetchSemanticsNodes().isNotEmpty() }
        backupCue().assertDoesNotExist()
        // Stored timestamps are input to the real reminder logic; no production clock is mocked.
        repo.preferences.edit().putLong("backup_reminder_dismissed_at", System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000).commit()
        reopen()
        rule.waitUntil(15000) { rule.onAllNodesWithText("Keep a backup of your ledger").fetchSemanticsNodes().isNotEmpty() }
        backupCue().performScrollTo().assertIsDisplayed()
        assertEquals(20, runBlocking { repo.snapshot().rows.size })
    }

    /** Host must revoke SMS BEFORE starting instrumentation; revocation during a test kills its process. */
    @Test fun denied_sms_permission_is_identified_on_home_and_routes_to_permission_recovery() {
        assumeTrue("Requires host-prepared denied SMS permission", InstrumentationRegistry.getArguments().getString("runHomeDeniedPermission") == "true")
        assertEquals(PackageManager.PERMISSION_DENIED, rule.activity.checkSelfPermission(Manifest.permission.RECEIVE_SMS))
        withHome(entries = 1, configure = {
            it.preferences.edit().putBoolean("sms_permission_requested", true).putBoolean("sms_disclosure", true).commit()
        }) { repo ->
            rule.onNodeWithText("SMS permission blocked. Manage settings").performScrollTo().assertIsDisplayed().performClick()
            rule.onNodeWithText("SMS & notifications").assertIsDisplayed()
            rule.onNodeWithText("SMS permission not allowed · Manual entry still works").assertExists()
            assertFalse(repo.captureAllowed())
            assertEquals(100L, runBlocking { repo.snapshot().debit.toLong() })
        }
    }
}
