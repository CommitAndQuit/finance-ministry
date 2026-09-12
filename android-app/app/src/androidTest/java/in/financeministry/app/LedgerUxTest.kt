package `in`.financeministry.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class LedgerUxTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun settings_are_separate_and_return_to_home() {
        rule.onNodeWithText("Erase all local data").assertDoesNotExist()
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("SMS and past messages").performClick()
        rule.onNodeWithText("Recording notifications").assertExists()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Data and privacy").performClick()
        rule.onNodeWithText("Erase all local data").performScrollTo().assertExists()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Home").performClick()
        rule.onNodeWithText("+ Add transaction").assertIsDisplayed()
    }

    @Test fun repeating_active_filter_does_not_remove_saved_history() {
        val repository = (rule.activity.application as FinanceMinistryApp).container.repository
        val id = runBlocking { repository.save(`in`.financeministry.app.data.ManualInput("71.23", `in`.financeministry.app.core.model.Direction.Debit, System.currentTimeMillis(), `in`.financeministry.app.core.model.TransactionType.Other)) }
        try {
            rule.onNodeWithText("Filters").performClick()
            rule.onNodeWithText("Added manually").performClick()
            rule.onNodeWithText("Apply").performClick()
            rule.waitUntil(15000) {
                rule.onAllNodesWithText("Your spending").fetchSemanticsNodes().isNotEmpty() &&
                    rule.onAllNodesWithText("Loading transactions…").fetchSemanticsNodes().isEmpty()
            }
            rule.onNodeWithText("Added manually").performClick()
            rule.onNodeWithText("Apply").performClick()
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("₹71.23"))
            rule.onNodeWithText("₹71.23").assertIsDisplayed()
        } finally { runBlocking { repository.delete(id) } }
    }

    @Test fun cancel_changed_form_keeps_draft_until_discard_confirmed() {
        rule.onNodeWithText("+ Add transaction").performClick()
        rule.onNodeWithText("Amount (INR)").performTextInput("42.00")
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Keep editing").performClick()
        rule.onNodeWithText("42.00").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Discard changes").performClick()
        rule.onNodeWithText("+ Add transaction").assertIsDisplayed()
    }

    @Test fun review_reminder_navigation_does_not_discard_a_dirty_form() {
        rule.onNodeWithText("+ Add transaction").performClick()
        rule.onNodeWithText("Amount (INR)").performTextInput("43.21")
        rule.activityRule.scenario.onActivity { it.requestReview() }
        rule.onNodeWithText("Open review queue?").assertIsDisplayed()
        rule.onNodeWithText("Keep editing").performClick()
        rule.onNodeWithText("43.21").assertExists()
    }

    @Test fun money_in_choice_saves_credit_and_draft_survives_recreation() {
        val repository = (rule.activity.application as FinanceMinistryApp).container.repository
        val before = runBlocking { repository.snapshot().rows.map { it.id }.toSet() }
        try {
            rule.onNodeWithText("+ Add transaction").performClick()
            rule.onNodeWithText("Amount (INR)").performTextInput("62.19")
            rule.onNodeWithText("Money in").performClick()
            rule.activityRule.scenario.recreate()
            rule.onNodeWithText("62.19").assertExists()
            rule.onNodeWithText("Money in").assertIsSelected()
            rule.onNodeWithText("Save transaction").performSemanticsAction(SemanticsActions.OnClick)
            rule.waitUntil(15000) { runBlocking { repository.snapshot().rows.any { it.id !in before && it.amountMinor == 6219L } } }
            val saved = runBlocking { repository.snapshot().rows.single { it.id !in before && it.amountMinor == 6219L } }
            assertEquals("Credit", saved.direction)
            assertEquals("Successful", saved.status)
        } finally { runBlocking { repository.snapshot().rows.filter { it.id !in before }.forEach { repository.delete(it.id) } } }
    }

    @Test fun incomplete_sms_can_be_reviewed_without_reentering_known_direction() {
        val app = rule.activity.application as FinanceMinistryApp
        val repository = app.container.repository
        val consent = repository.preferences.getBoolean("sms_disclosure", false)
        val before = runBlocking { repository.snapshot().rows.map { it.id }.toSet() }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName, android.Manifest.permission.RECEIVE_SMS)
        try {
            repository.preferences.edit().putBoolean("sms_disclosure", true).commit()
            assertTrue(runBlocking { repository.ingest(`in`.financeministry.app.core.model.IncomingSms("SYNTHETIC", System.currentTimeMillis(), "Your account was debited via UPI.")) })
            rule.waitForIdle()
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Open review queue"))
            rule.onNodeWithContentDescription("Open review queue").performClick()
            val target = hasText("Amount unknown") and hasText("Money out")
            try {
                rule.onNode(hasScrollToNodeAction()).performScrollToNode(target)
            } catch (failure: Throwable) {
                throw AssertionError("Review row did not appear. UI state: ${rule.onRoot().printToString()}", failure)
            }
            rule.onNode(target).performClick()
            rule.onNodeWithText("Check this transaction").assertExists()
            rule.onNodeWithText("Edit / confirm").performClick()
            rule.onNodeWithText("Money out").assertIsSelected()
            rule.onNodeWithText("Amount (INR)").performTextInput("81.25")
            rule.onNodeWithText("Transaction type — please choose: Not identified").performScrollTo().performClick()
            rule.onNodeWithText("Other").performClick()
            rule.onNodeWithText("Save transaction").performSemanticsAction(SemanticsActions.OnClick)
            rule.waitForIdle()
            try {
                rule.waitUntil(15000) { runBlocking { repository.snapshot().rows.any { it.id !in before && it.amountMinor == 8125L && it.reviewState == "Confirmed" } } }
            } catch (failure: Throwable) {
                val rows = runBlocking { repository.snapshot().rows.filter { it.id !in before } }
                throw AssertionError("Review save did not finish. Rows: $rows UI state: ${rule.onRoot().printToString()}", failure)
            }
        } finally {
            repository.preferences.edit().putBoolean("sms_disclosure", consent).commit()
            runBlocking { repository.snapshot().rows.filter { it.id !in before }.forEach { repository.delete(it.id) } }
        }
    }

    @Test fun category_only_form_edit_preserves_exact_timestamp_and_reversal_link() {
        val app = rule.activity.application as FinanceMinistryApp
        val repository = app.container.repository
        val consent = repository.preferences.getBoolean("sms_disclosure", false)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(app.packageName, android.Manifest.permission.RECEIVE_SMS)
        val minute = System.currentTimeMillis().let { it - it % 60_000 }
        var originalId: String? = null
        var reversalId: String? = null
        try {
            repository.preferences.edit().putBoolean("sms_disclosure", true).commit()
            assertTrue(runBlocking { repository.ingest(`in`.financeministry.app.core.model.IncomingSms(
                "SYNTHETIC", minute + 12_345, "INR 42 debited via UPI account XX0000 Ref 000000000001")) })
            originalId = runBlocking { repository.snapshot().rows.first { it.sourceTimestamp == minute + 12_345 }.id }
            assertTrue(runBlocking { repository.ingest(`in`.financeministry.app.core.model.IncomingSms(
                "SYNTHETIC", minute + 22_345, "INR 42 debit transaction reversed via UPI account XX0000 Ref 000000000001")) })
            reversalId = runBlocking { repository.snapshot().rows.first { it.sourceTimestamp == minute + 22_345 }.id }
            assertEquals(originalId, runBlocking { repository.get(reversalId!!)!!.linkedOriginalId })

            rule.waitForIdle()
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("All"))
            rule.onNodeWithText("All").performClick()
            val linkedCards = hasText("₹42.00") and hasText("Money out")
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(linkedCards)
            rule.onAllNodes(linkedCards)[1].performClick()
            rule.onNodeWithText("Edit / confirm").performClick()
            rule.onNodeWithText("Category: Other").performScrollTo().performClick()
            rule.onNodeWithText("Food").performClick()
            rule.onNodeWithText("Transaction type — please choose: Not identified").performScrollTo().performClick()
            rule.onNodeWithText("Other").performClick()
            rule.onNodeWithText("Save transaction").performSemanticsAction(SemanticsActions.OnClick)
            try {
                rule.waitUntil(15_000) { rule.onAllNodesWithText("+ Add transaction").fetchSemanticsNodes().isNotEmpty() }
            } catch (failure: Throwable) {
                throw AssertionError("Category edit did not leave the form. Stored=${runBlocking { repository.get(originalId!!) }} UI=${rule.onRoot().printToString()}", failure)
            }

            val original = runBlocking { repository.get(originalId!!)!! }
            assertEquals(minute + 12_345, original.effectiveTimestamp)
            assertEquals(originalId, runBlocking { repository.get(reversalId!!)!!.linkedOriginalId })
            assertEquals("0", runBlocking { repository.snapshot().debit.toString() })
        } finally {
            repository.preferences.edit().putBoolean("sms_disclosure", consent).commit()
            runBlocking { listOfNotNull(reversalId, originalId).forEach { repository.delete(it) } }
        }
    }

    @Test fun focused_source_picker_does_not_confirm_a_review_transaction() {
        val app = rule.activity.application as FinanceMinistryApp
        val repository = app.container.repository
        val consent = repository.preferences.getBoolean("sms_disclosure", false)
        val nickname = "Test UPI ${System.currentTimeMillis().toString().takeLast(6)}"
        var transactionId: String? = null
        var sourceId: String? = null
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(app.packageName, android.Manifest.permission.RECEIVE_SMS)
        try {
            repository.preferences.edit().putBoolean("sms_disclosure", true).commit()
            val receivedAt = System.currentTimeMillis()
            assertTrue(runBlocking { repository.ingest(`in`.financeministry.app.core.model.IncomingSms(
                "SYNTHETIC", receivedAt, "INR 87.65 refund credited via UPI account XX6789")) })
            transactionId = runBlocking { repository.snapshot().rows.first { it.sourceTimestamp == receivedAt }.id }
            assertEquals("NeedsReview", runBlocking { repository.get(transactionId!!)!!.reviewState })
            sourceId = runBlocking { repository.addPaymentSource(nickname, "Bank account", `in`.financeministry.app.core.model.Channel.UPI, "Test bank", "6789").id }

            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("₹87.65"))
            rule.onNodeWithText("₹87.65").performClick()
            rule.onNodeWithText("Choose payment source").performClick()
            rule.onNodeWithText("This only assigns a source. It won’t confirm or change the transaction.").assertExists()
            rule.onNodeWithText(nickname).performClick()
            rule.waitUntil(10_000) { runBlocking { repository.get(transactionId!!)!!.paymentSourceId } == sourceId }
            assertEquals("NeedsReview", runBlocking { repository.get(transactionId!!)!!.reviewState })
            rule.onNodeWithText("Check this transaction").assertExists()
            rule.onNodeWithText("Edit / confirm").assertExists()
        } finally {
            repository.preferences.edit().putBoolean("sms_disclosure", consent).commit()
            transactionId?.let { runBlocking { repository.delete(it) } }
            sourceId?.let { runBlocking { repository.deletePaymentSource(it) } }
        }
    }
}
