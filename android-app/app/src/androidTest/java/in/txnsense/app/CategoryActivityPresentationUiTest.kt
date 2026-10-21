package `in`.txnsense.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import `in`.txnsense.app.core.model.*
import `in`.txnsense.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Category navigation must not insert a second dashboard above the Activity list. */
class CategoryActivityPresentationUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun category_drilldown_keeps_a_clean_list_and_explains_gross_and_own_share_in_totals() {
        val repo = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "category_activity_${UUID.randomUUID()}")
        try {
            repo.preferences.edit().putBoolean("onboarding_complete", true)
                .putInt("app_tour_seen_version", BuildConfig.VERSION_CODE).commit()
            val id = runBlocking { repo.save(ManualInput("900", Direction.Debit, System.currentTimeMillis(),
                TransactionType.Other, label = "Sample dinner", category = "Food", ownership = SpendingOwnership.Group,
                groupLabel = "Friends", personalShare = "300")) }
            rule.setContent { LedgerApp(repo, null, consumeRequest = {}) }
            rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("How totals work").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("How totals work").performClick()
            rule.onNodeWithText("See spending by category").performScrollTo().performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("Open Food spending").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Open Food spending").performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("Categorize Sample dinner").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Your share · Food").assertDoesNotExist()
            // The active filter chip remains discoverable; only the duplicate
            // dashboard was removed, not the user's category/direction filter.
            rule.onNode(hasText("Money out · Food") and hasClickAction()).assertIsDisplayed()
            rule.onNodeWithText("Totals").performClick()
            rule.onNode(hasText("Money out") and hasText("₹900") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            rule.onNode(hasText("Your spending") and hasText("₹300") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            rule.onNodeWithContentDescription("Close matching totals").performClick()
            rule.onNodeWithContentDescription("Search transactions").performTextInput("No matching dinner")
            rule.waitUntil(15000) { rule.onAllNodesWithText("0 transactions").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Totals").performClick()
            rule.onNode(hasText("Money out") and hasText("₹0") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            rule.onNode(hasText("Your spending") and hasText("₹0") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            rule.onNodeWithContentDescription("Close matching totals").performClick()
            rule.onNodeWithContentDescription("Clear search").performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("Categorize Sample dinner").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(90000L, runBlocking { repo.get(id)!!.amountMinor })
            assertEquals(30000L, runBlocking { repo.snapshot().personalSpend.toLong() })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }
}
