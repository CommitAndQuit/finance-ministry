package `in`.financeministry.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import `in`.financeministry.app.feature.TransactionForm
import `in`.financeministry.app.ui.FinanceLedgerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Changing visible direction must not retain validation for hidden debit-purpose fields. */
class TransactionDirectionDraftUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun withForm(seed: (suspend (TransactionRepository) -> TransactionEntity?)? = null,
        check: (TransactionRepository, TransactionEntity?) -> Unit) {
        val repo = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "direction_ui_${UUID.randomUUID()}")
        try {
            val row = runBlocking { seed?.invoke(repo) }
            rule.setContent { FinanceLedgerTheme { Surface(Modifier.fillMaxSize()) {
                TransactionForm(repo, row, onDone = {})
            } } }
            rule.waitUntil(15000) { rule.onAllNodes(publicEditorSaveButton() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            try { check(repo, row) }
            catch (failure: Throwable) {
                println(rule.onAllNodes(isRoot()).printToString())
                throw failure
            }
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    private fun direction(value: String) {
        rule.onNode(hasText(value) and isSelectable()).performScrollTo().performClick()
    }

    private fun save() {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNode(publicEditorSaveButton()).assertIsDisplayed().performClick()
    }

    private suspend fun group(repo: TransactionRepository, withRepayment: Boolean): TransactionEntity {
        val alice = repo.addLocalMember("Alice")
        val id = repo.save(ManualInput("900", Direction.Debit, System.currentTimeMillis(), TransactionType.Other,
            label = "Dinner", category = "Food", ownership = SpendingOwnership.Group, groupLabel = "Friends", personalShare = "300"))
        repo.saveGroupSplit(id, 30000, listOf(MemberShareInput(alice.id, 60000)), repo.get(id)!!.updatedAt)
        if (withRepayment) repo.recordRepayment(id, "100", System.currentTimeMillis(), "Alice", memberId = alice.id)
        return repo.get(id)!!
    }

    // The old callback left an empty Group draft hidden, blocking Add with Name the group.
    @Test fun group_draft_can_be_changed_to_money_in_without_completing_hidden_group_fields() = withForm { repo, _ ->
        rule.onNode(editableLedgerField("Amount (INR)")).performTextInput("900")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        direction("Group")
        direction("Money in")
        rule.onNode(editableLedgerField("Group name")).assertDoesNotExist()
        save()
        rule.waitUntil(15000) { runBlocking { repo.snapshot().rows.size == 1 } }
        val row = runBlocking { repo.snapshot().rows.single() }
        assertEquals("Credit", row.direction)
        assertEquals("Personal", row.ownership)
        assertNull(row.groupLabel)
        assertEquals(90000L, row.amountMinor)
        assertTrue(runBlocking { repo.groupSharesFor(row.id).isEmpty() })
        assertEquals(90000L, runBlocking { repo.snapshot().credit.toLong() })
        assertEquals(0L, runBlocking { repo.snapshot().personalSpend.toLong() })
    }

    // Clearing to null would retain stored individual shares; this catches that partial fix.
    @Test fun unlinked_group_can_be_corrected_to_credit_without_retaining_individual_shares() =
        withForm(seed = { group(it, false) }) { repo, original ->
            direction("Money in")
            save()
            rule.waitUntil(15000) { runBlocking { repo.get(original!!.id)?.direction == "Credit" } }
            val saved = runBlocking { repo.get(original!!.id)!! }
            assertEquals("Personal", saved.ownership)
            assertEquals(90000L, saved.amountMinor)
            assertEquals(original!!.effectiveTimestamp, saved.effectiveTimestamp)
            assertEquals("Food", saved.category)
            assertTrue(runBlocking { repo.groupSharesFor(saved.id).isEmpty() })
            assertEquals("Alice", runBlocking { repo.localMembers().single().name })
            assertEquals(90000L, runBlocking { repo.snapshot().credit.toLong() })
            assertEquals(0L, runBlocking { repo.snapshot().debit.toLong() })
        }

    // Hidden SelfTransfer state must not silently exclude a newly selected ordinary credit.
    @Test fun self_transfer_draft_switched_to_money_in_counts_as_an_ordinary_credit() = withForm { repo, _ ->
        rule.onNode(editableLedgerField("Amount (INR)")).performTextInput("125.50")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        direction("Self transfer")
        direction("Money in")
        save()
        rule.waitUntil(15000) { runBlocking { repo.snapshot().rows.size == 1 } }
        val row = runBlocking { repo.snapshot().rows.single() }
        assertEquals("Credit", row.direction)
        assertEquals("Personal", row.ownership)
        assertEquals("Other", row.transactionType)
        assertEquals(12550L, runBlocking { repo.snapshot().credit.toLong() })
    }

    // Opening or reselecting Credit on an existing self-transfer must not invent income.
    @Test fun an_existing_credit_self_transfer_is_not_reclassified_when_its_direction_is_unchanged() =
        withForm(seed = { repo ->
            val id = repo.save(ManualInput("125.50", Direction.Credit, System.currentTimeMillis(), TransactionType.SelfTransfer,
                ownership = SpendingOwnership.SelfTransfer, label = "Own account transfer"))
            repo.get(id)
        }) { repo, original ->
            direction("Money in")
            save()
            rule.waitUntil(15000) { runBlocking { repo.get(original!!.id)?.updatedAt != original.updatedAt } }
            val saved = runBlocking { repo.get(original!!.id)!! }
            assertEquals("SelfTransfer", saved.ownership)
            assertEquals("SelfTransfer", saved.transactionType)
            assertEquals(0L, runBlocking { repo.snapshot().credit.toLong() })
            assertEquals(0L, runBlocking { repo.snapshot().personalSpend.toLong() })
        }

    // UI normalization must not bypass repository protections on settled expenses.
    @Test fun correcting_a_group_with_repayments_still_requires_explicit_unlinking() =
        withForm(seed = { group(it, true) }) { repo, original ->
            val beforeShares = runBlocking { repo.groupSharesFor(original!!.id) }
            val beforeRepayments = runBlocking { repo.repaymentsFor(original!!.id) }
            direction("Money in")
            save()
            rule.waitUntil(15000) { rule.onAllNodes(
                hasText("Review and unlink existing repayments before changing financial details or the spending split.") or
                    hasText("Unlink existing repayments before changing or assigning people. Incoming payments are kept; then relink them to each person.")
            ).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(original, runBlocking { repo.get(original!!.id) })
            assertEquals(beforeShares, runBlocking { repo.groupSharesFor(original!!.id) })
            assertEquals(beforeRepayments, runBlocking { repo.repaymentsFor(original!!.id) })
            assertEquals(30000L, runBlocking { repo.snapshot().personalSpend.toLong() })
            assertEquals(50000L, runBlocking { RepaymentAccounting.owed(repo.get(original!!.id)!!) })
        }
}
