package `in`.txnsense.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class NotificationNavigationUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Test fun notification_actions_target_correct_records_and_do_not_replay_after_recreation() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<TxnSenseApp>()
        val repository = app.container.repository
        val onboardingComplete = repository.preferences.getBoolean("onboarding_complete", false)
        val originalTourVersion = repository.preferences.getInt(`in`.txnsense.app.feature.APP_TOUR_VERSION_KEY, 0)
        // This test exercises a returning user's notification path. Fresh-install onboarding
        // is verified separately by AppTourLifecycleUiTest.
        repository.preferences.edit().putBoolean("onboarding_complete", true)
            .putInt(`in`.txnsense.app.feature.APP_TOUR_VERSION_KEY, BuildConfig.VERSION_CODE).commit()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().startActivitySync(
            android.content.Intent(app, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val manager = app.getSystemService(android.app.NotificationManager::class.java)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val first = runBlocking { repository.save(`in`.txnsense.app.data.ManualInput("700.01", `in`.txnsense.app.core.model.Direction.Debit,
            System.currentTimeMillis(), `in`.txnsense.app.core.model.TransactionType.Other)) }
        val second = runBlocking { repository.save(`in`.txnsense.app.data.ManualInput("700.02", `in`.txnsense.app.core.model.Direction.Debit,
            System.currentTimeMillis(), `in`.txnsense.app.core.model.TransactionType.Other)) }
        try {
            for (id in listOf(first, second)) `in`.txnsense.app.sms.TransactionNotifications.post(app, runBlocking { repository.get(id)!! }, true)
            rule.waitUntil(15000) { manager.activeNotifications.map { it.tag }.containsAll(listOf(first, second)) }
            val edit = manager.activeNotifications.single { it.tag == first }.notification.actions.single { it.title.toString() == "Edit" }.actionIntent
            val view = manager.activeNotifications.single { it.tag == second }.notification.contentIntent
            val classify = manager.activeNotifications.single { it.tag == first }.notification.actions.single { it.title.toString() == "Categorize" }.actionIntent
            classify.send()
            rule.waitUntil(15000) { rule.onAllNodesWithText("Category & purpose").fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(editableLedgerField("Amount (INR)")).assertDoesNotExist()
            rule.onNode(publicEditorChoice("Category", "Other")).performClick()
            rule.onNodeWithText("Food").performClick()
            rule.onNodeWithText("For someone else").performClick()
            rule.waitUntil(15_000) { rule.onAllNodes(publicEditorSaveButton() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(publicEditorSaveButton()).performClick()
            rule.waitUntil(15000) { runBlocking { repository.get(first)?.category == "Food" } }
            org.junit.Assert.assertEquals("ForOther", runBlocking { repository.get(first)!!.ownership })
            org.junit.Assert.assertEquals(70001L, runBlocking { repository.get(first)!!.amountMinor })
            rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("Close transaction editor").fetchSemanticsNodes().isEmpty() }
            classify.send()
            rule.waitUntil(15000) { rule.onAllNodesWithText("Category & purpose").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Family").performClick()
            rule.onNode(publicEditorChoice("Category", "Food")).performClick()
            // Use a fresh-ledger default; retired categories are only offered when in use.
            rule.onNodeWithText("Bills").performClick()
            rule.waitUntil(15_000) { rule.onAllNodes(publicEditorSaveButton() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(publicEditorSaveButton()).performClick()
            rule.waitUntil(15000) { runBlocking { repository.get(first)?.ownership == "Family" } }
            val family = runBlocking { repository.get(first)!! }
            org.junit.Assert.assertEquals("Bills", family.category)
            org.junit.Assert.assertEquals(70001L, family.personalShareMinor)
            org.junit.Assert.assertNull(repaymentSummary(family))
            rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("Close transaction editor").fetchSemanticsNodes().isEmpty() }
            edit.send()
            rule.waitUntil(15000) { rule.onAllNodesWithText("Payment details").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("700.01").assertExists()
            rule.onNode(publicEditorChoice("Category", "Bills")).assertExists()
            rule.onNode(editableLedgerField("Amount (INR)")).performTextReplacement("701.01")
            view.send()
            rule.waitUntil(15000) { rule.onAllNodesWithText("Open another transaction?").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Keep editing").performClick()
            rule.onNodeWithText("701.01").assertExists()
            rule.onNodeWithContentDescription("Close transaction editor").performSemanticsAction(SemanticsActions.OnClick)
            rule.onNodeWithText("Discard changes").performClick()
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single().recreate()
            }
            rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("Open home").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Open home").assertIsDisplayed()
            view.send()
            rule.waitUntil(15000) { rule.onAllNodesWithText("Edit / confirm").fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasText("₹700.02") and hasAnyAncestor(isDialog())).assertExists()
            rule.onNodeWithText("Edit / confirm").assertIsDisplayed()
            runBlocking { repository.delete(second) }
            view.send()
            rule.waitUntil(15000) { rule.onAllNodesWithText("This transaction no longer exists.").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Open home").assertIsDisplayed()
        } finally {
            runBlocking { repository.delete(first); repository.delete(second) }
            repository.preferences.edit().putBoolean("onboarding_complete", onboardingComplete)
                .putInt(`in`.txnsense.app.feature.APP_TOUR_VERSION_KEY, originalTourVersion).commit()
            // PendingIntent delivery can create another activity task. Finish test-owned
            // MainActivity instances before ActivityScenario performs its teardown.
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                androidx.test.runner.lifecycle.Stage.entries.flatMap { monitor.getActivitiesInStage(it).toList() }
                    .filterIsInstance<MainActivity>().distinct().forEach { it.finish() }
            }
        }
    }

}
