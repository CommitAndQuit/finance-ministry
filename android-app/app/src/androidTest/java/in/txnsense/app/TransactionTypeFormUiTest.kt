package `in`.txnsense.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import `in`.txnsense.app.data.*
import `in`.txnsense.app.feature.TransactionForm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TransactionTypeFormUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun repository() = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "type_ui_${UUID.randomUUID()}")
    private fun row(direction: String = "Debit", channel: String = "UPI", type: String = "Unknown") =
        System.currentTimeMillis().let { now -> TransactionEntity("fixture", sourceType = "SMS", sourceTimestamp = now,
            effectiveTimestamp = now, amountMinor = 1234, direction = direction, status = "Successful", channel = channel,
            transactionType = type, category = "Food", reviewState = "NeedsReview", createdAt = now, updatedAt = now) }
    private fun open(repo: TransactionRepository, fixture: TransactionEntity) {
        runBlocking { repo.withLedger { it.transactions().insert(fixture) } }
        rule.setContent { MaterialTheme { TransactionForm(repo, fixture, onDone = {}) } }
    }
    private fun awaitSources() {
        rule.waitUntil(15000) { rule.onAllNodes(publicEditorSaveButton() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun save(repo: TransactionRepository) {
        awaitSources()
        rule.onNode(publicEditorSaveButton()).performClick()
        rule.waitUntil(15000) { runBlocking { repo.get("fixture")!!.reviewState == "Confirmed" } }
    }

    @Test fun ordinary_review_can_save_without_choosing_a_technical_type() {
        val repo = repository()
        try {
            open(repo, row())
            rule.onNodeWithText("Transaction type — please choose: Not identified").assertDoesNotExist()
            save(repo)
            assertEquals("Other", runBlocking { repo.get("fixture")!!.transactionType })
            assertEquals("Food", runBlocking { repo.get("fixture")!!.category })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    @Test fun unidentified_card_credit_is_blocked_until_bill_or_refund_or_other_is_chosen() {
        val repo = repository()
        try {
            open(repo, row("Credit", "Card"))
            awaitSources()
            rule.onNode(publicEditorSaveButton()).performClick()
            rule.onNodeWithText("Choose card bill payment, refund, or other money in.").assertExists()
            assertEquals("NeedsReview", runBlocking { repo.get("fixture")!!.reviewState })
            rule.onNodeWithText("Refund").performScrollTo().performClick()
            save(repo)
            assertEquals("Refund", runBlocking { repo.get("fixture")!!.transactionType })
            assertEquals("Food", runBlocking { repo.get("fixture")!!.category })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    @Test fun existing_refund_is_preserved_without_opening_advanced_details() {
        val repo = repository()
        try {
            open(repo, row("Credit", "Card", "Refund"))
            save(repo)
            assertEquals("Refund", runBlocking { repo.get("fixture")!!.transactionType })
            assertEquals("Food", runBlocking { repo.get("fixture")!!.category })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    @Test fun self_transfer_action_excludes_payment_without_changing_its_category() {
        val repo = repository()
        try {
            open(repo, row())
            rule.onNodeWithText("Self transfer").performScrollTo().performClick()
            save(repo)
            val saved = runBlocking { repo.get("fixture")!! }
            assertEquals("SelfTransfer", saved.transactionType)
            assertEquals("SelfTransfer", saved.ownership)
            assertEquals("Food", saved.category)
            assertEquals("0", runBlocking { repo.snapshot().debit.toString() })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    @Test fun ordinary_card_credit_requires_explicit_other_choice_and_counts_as_money_in() {
        val repo = repository()
        try {
            open(repo, row("Credit", "Card"))
            rule.onNodeWithText("Other money in").performScrollTo().performClick()
            save(repo)
            assertEquals("Other", runBlocking { repo.get("fixture")!!.transactionType })
            assertEquals("1234", runBlocking { repo.snapshot().credit.toString() })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    @Test fun technical_types_are_available_only_after_opening_advanced_details() {
        val repo = repository()
        try {
            open(repo, row("Credit", "NEFT", "SalaryIncome"))
            rule.onNodeWithText("Salary Income").assertDoesNotExist()
            rule.onNodeWithText("More details").performScrollTo().performClick()
            rule.onNodeWithText("Advanced details").performScrollTo().performClick()
            rule.onNodeWithText("Salary Income").performScrollTo().assertIsDisplayed()
            save(repo)
            assertEquals("SalaryIncome", runBlocking { repo.get("fixture")!!.transactionType })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    @Test fun save_waits_for_source_lookup_before_resolving_an_unknown_upi_card_credit() {
        val repo = repository()
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = row("Credit", "UPI").copy(paymentSourceId = "card")
        runBlocking { repo.withLedger { db ->
            db.transactions().insert(fixture)
            db.transactions().addSource(PaymentSourceEntity("card", "UPI card", "Credit card", "UPI", createdAt = System.currentTimeMillis()))
        } }
        val lockJob = CoroutineScope(Dispatchers.IO).launch { repo.withLedger { locked.countDown(); check(release.await(60, TimeUnit.SECONDS)) } }
        try {
            check(locked.await(15, TimeUnit.SECONDS))
            rule.setContent { MaterialTheme { TransactionForm(repo, fixture, onDone = {}) } }
            rule.onNode(publicEditorSaveButton()).assertIsNotEnabled()
            // Exercise a queued callback as well as the visual disabled state.
            rule.onNode(publicEditorSaveButton()).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            release.countDown()
            runBlocking { lockJob.join() }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Other money in").fetchSemanticsNodes().isNotEmpty() }
            awaitSources()
            assertEquals("NeedsReview", runBlocking { repo.get("fixture")!!.reviewState })
            rule.onNode(publicEditorSaveButton()).performClick()
            rule.onNodeWithText("Choose card bill payment, refund, or other money in.").assertExists()
            assertEquals("NeedsReview", runBlocking { repo.get("fixture")!!.reviewState })
            rule.onNodeWithText("Refund").performScrollTo().performClick()
            save(repo)
            assertEquals("Refund", runBlocking { repo.get("fixture")!!.transactionType })
        } finally {
            release.countDown()
            runBlocking { lockJob.join(); repo.eraseAll(); repo.close() }
        }
    }
}
