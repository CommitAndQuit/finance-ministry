package `in`.financeministry.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Home must not invent a person count from a legacy group or an unnamed balance. */
class HomeBalancePresentationUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun withLedger(seed: suspend (TransactionRepository) -> Unit, check: (TransactionRepository) -> Unit) {
        val repo = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "home_balance_${UUID.randomUUID()}")
        try {
            repo.preferences.edit().putBoolean("onboarding_complete", true)
                .putInt("app_tour_seen_version", BuildConfig.VERSION_CODE).commit()
            runBlocking { seed(repo) }
            rule.setContent { LedgerApp(repo, null, consumeRequest = {}) }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Still owed to you").fetchSemanticsNodes().isNotEmpty() }
            check(repo)
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    private suspend fun group(repo: TransactionRepository): String = repo.save(ManualInput("900", Direction.Debit,
        System.currentTimeMillis(), TransactionType.Other, label = "Dinner", ownership = SpendingOwnership.Group,
        groupLabel = "Friends", personalShare = "300"))

    // Counting an unnamed group as one person must fail in the rendered Home explanation.
    @Test fun a_legacy_group_is_presented_as_one_balance_not_one_person() = withLedger({ group(it) }) { repo ->
        rule.waitUntil(15000) { rule.onAllNodesWithText("₹600 from this month · 1 balance").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("₹600 from this month · 1 balance").assertIsDisplayed()
        rule.onNodeWithText("₹600 from this month · 1 person").assertDoesNotExist()
        rule.onNodeWithContentDescription("Open repayments").performClick()
        rule.waitUntil(15000) { rule.onNodeWithText("1 balance · every repayment accounted for").isDisplayed() }
        rule.onNodeWithText("1 balance · every repayment accounted for").assertIsDisplayed()
        assertEquals(60000L, runBlocking { repo.snapshot().outstandingRepayments.toLong() })
        assertEquals(30000L, runBlocking { repo.snapshot().personalSpend.toLong() })
    }

    // Real individual shares must still show their two people rather than a generic group count.
    @Test fun individual_shares_show_two_people_without_changing_the_amount_owed() = withLedger({ repo ->
        val id = group(repo)
        val alice = repo.addLocalMember("Alice"); val bob = repo.addLocalMember("Bob")
        repo.saveGroupSplit(id, 30000, listOf(MemberShareInput(alice.id, 30000), MemberShareInput(bob.id, 30000)), repo.get(id)!!.updatedAt)
    }) { repo ->
        rule.waitUntil(15000) { rule.onAllNodesWithText("₹600 from this month · 2 people").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("₹600 from this month · 2 people").assertIsDisplayed()
        rule.onNodeWithContentDescription("Open repayments").performClick()
        rule.waitUntil(15000) { rule.onNodeWithText("2 people · every repayment accounted for").isDisplayed() }
        rule.onNodeWithText("2 people · every repayment accounted for").assertIsDisplayed()
        rule.onNodeWithText("Alice").assertExists(); rule.onNodeWithText("Bob").assertExists()
        assertEquals(60000L, runBlocking { repo.snapshot().outstandingRepayments.toLong() })
    }

    @Test fun unnamed_someone_else_payment_is_a_balance_not_a_fabricated_person() = withLedger({ repo ->
        repo.save(ManualInput("100", Direction.Debit, System.currentTimeMillis(), TransactionType.Other,
            ownership = SpendingOwnership.ForOther))
    }) { repo ->
        rule.waitUntil(15000) { rule.onAllNodesWithText("₹100 from this month · 1 balance").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("₹100 from this month · 1 balance").assertIsDisplayed()
        assertEquals(10000L, runBlocking { repo.snapshot().outstandingRepayments.toLong() })
    }

    // Default Material chip labels must not enlarge the approved People hierarchy.
    @Test fun people_choices_keep_quiet_captions_and_accessible_targets_without_changing_balances() =
        withLedger({ group(it) }) { repo ->
            rule.onNodeWithContentDescription("Open repayments").performClick()
            rule.waitUntil(15000) { rule.onAllNodes(hasText("Outstanding") and isSelectable()).fetchSemanticsNodes().isNotEmpty() }
            listOf("Outstanding", "Settled").forEach { label ->
                rule.onNodeWithText(label, useUnmergedTree = true).assertQuietChoiceCaption(label)
                val bounds = rule.onNode(hasText(label) and isSelectable()).fetchSemanticsNode().boundsInWindow
                assertTrue("$label must retain its48dp target", bounds.height >= with(rule.density) { 48.dp.toPx() })
            }
            rule.onNode(hasText("Settled") and isSelectable()).performClick().assertIsSelected()
            rule.onNodeWithText("No balances here").assertIsDisplayed()
            assertEquals(60000L, runBlocking { repo.snapshot().outstandingRepayments.toLong() })
            assertEquals(30000L, runBlocking { repo.snapshot().personalSpend.toLong() })
        }

    // Direction filtering must stay compact without shrinking its selectable targets.
    @Test fun activity_direction_choices_keep_quiet_captions_and_filter_the_real_ledger() =
        withLedger({ group(it) }) { repo ->
            rule.onNodeWithContentDescription("Open activity").performClick()
            rule.waitUntil(15000) { rule.onAllNodes(hasText("Money in") and isSelectable()).fetchSemanticsNodes().isNotEmpty() }
            val rootBounds = rule.onRoot().getUnclippedBoundsInRoot()
            listOf("All", "Money out", "Money in").forEach { label ->
                rule.onNodeWithText(label, useUnmergedTree = true).assertQuietChoiceCaption(label, singleLine = true)
                val choice = rule.onNode(hasText(label) and isSelectable()).assertIsDisplayed()
                val bounds = choice.fetchSemanticsNode().boundsInWindow
                assertTrue("$label must retain its48dp target", bounds.height >= with(rule.density) { 48.dp.toPx() })
                val unclipped = choice.getUnclippedBoundsInRoot()
                assertTrue("$label must remain fully inside the activity content, including large native text",
                    unclipped.left >= rootBounds.left + 19.dp && unclipped.right <= rootBounds.right - 19.dp)
            }
            rule.onNode(hasText("Money in") and isSelectable()).performClick().assertIsSelected()
            rule.waitUntil(15000) { rule.onAllNodesWithText("No matching transactions").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("No matching transactions").assertIsDisplayed()
            assertEquals(90000L, runBlocking { repo.snapshot().debit.toLong() })
            assertEquals(30000L, runBlocking { repo.snapshot().personalSpend.toLong() })
        }
}
