package `in`.financeministry.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the approved screen flow with an isolated ledger, never the user's database. */
class PublicLedgerUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun repository() = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "public_ui_${UUID.randomUUID()}")
    private fun seed(repo: TransactionRepository) = runBlocking {
        repo.preferences.edit().putString("preferred_name", "Sam")
            .putInt("app_tour_seen_version", BuildConfig.VERSION_CODE).putBoolean("onboarding_complete", true).commit()
        fun time(day:Int,hour:Int,minute:Int)=LocalDate.now().withDayOfMonth(day).atTime(hour,minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val day=time(11,12,42)
        fun input(amount: String, label: String, category: String="Other", purpose: SpendingOwnership=SpendingOwnership.Personal, share: String="", timestamp:Long=day) =
            ManualInput(amount, Direction.Debit, timestamp, TransactionType.Other, label=label, category=category, ownership=purpose, personalShare=share,
                groupLabel=if(purpose==SpendingOwnership.Group) "Friends dinner" else if(purpose==SpendingOwnership.ForOther) "Rahul" else "")
        val alice=repo.addLocalMember("Alice");val bob=repo.addLocalMember("Bob")
        val dinner=repo.save(input("900", "Friends dinner", "Food", SpendingOwnership.Group, "300"))
        repo.saveGroupSplit(dinner,30000,listOf(MemberShareInput(alice.id,30000),MemberShareInput(bob.id,30000)),repo.get(dinner)!!.updatedAt)
        repo.save(input("540", "Metro recharge", "Travel",timestamp=time(11,9,18)))
        repo.save(input("1850", "Weekly groceries", "Food", SpendingOwnership.Family,timestamp=time(10,18,24)))
        repo.save(input("2490", "Home essentials", "Shopping",timestamp=time(9,14,2)))
        val rahul=repo.save(input("3200", "Paid for Rahul", "Shopping", SpendingOwnership.ForOther,timestamp=time(8,16,10)))
        repo.save(input("12000", "October rent", "Flat expenses",timestamp=time(2,9,0)))
        repo.recordRepayment(dinner,"100",time(11,10,12),"Alice",memberId=alice.id)
        repo.recordRepayment(rahul,"1000",time(10,10,31),"Rahul")
        repo.save(ManualInput("45000",Direction.Credit,time(1,8,30),TransactionType.Other,label="Salary",category="Income"))
        repo.save(ManualInput("450",Direction.Credit,time(7,11,35),TransactionType.Refund,label="Shopping refund",category="Shopping"))
        repo.save(ManualInput("200",Direction.Credit,time(11,11,4),TransactionType.Other,label="Alice"))
    }
    private fun show(repo: TransactionRepository) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent { FinanceMinistryTheme { LedgerApp(repo,null,consumeRequest={}) } }
        rule.waitUntil(15000) { rule.onAllNodesWithText("₹17,180.00").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        val committed = CountDownLatch(1)
        rule.runOnUiThread { rule.activity.window.decorView.apply {
            viewTreeObserver.registerFrameCommitCallback { committed.countDown() }; invalidate()
        } }
        assertTrue("Ledger window did not commit a rendered frame", committed.await(5, TimeUnit.SECONDS))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val variant=InstrumentationRegistry.getArguments().getString("visualVariant")
            ?.takeIf {it in setOf("light","dark","compact-light","compact-dark","large-text-light","large-text-dark")} ?: "light"
        val file=File(instrumentation.targetContext.getExternalFilesDir("public-ui"),name.replace("-light.png","-$variant.png"))
        file.outputStream().use { check(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) }
    }
    @Test fun approved_home_reconciles_and_all_four_destinations_are_reachable() {
        val repo=repository()
        try {
            seed(repo); show(repo)
            rule.onNodeWithContentDescription("Open home").assertIsDisplayed()
            rule.onNodeWithContentDescription("Open activity").assertIsDisplayed()
            rule.onNodeWithContentDescription("Open people").assertIsDisplayed()
            rule.onNodeWithContentDescription("Open review tab").assertIsDisplayed()
            rule.onNodeWithText("₹20,980").assertIsDisplayed()
            rule.onNodeWithText("₹46,750").assertIsDisplayed()
            rule.onNodeWithText("₹2,700").assertIsDisplayed()
            capture("home-light.png")
            rule.onNodeWithContentDescription("Open activity").performClick()
            rule.onNodeWithText("Search your transactions").assertIsDisplayed()
            capture("activity-light.png")
            rule.onNodeWithContentDescription("Open people").performClick()
            // Navigation finishes before the encrypted repayment summary loads.
            // Await the actual visible hero; a permanently hidden hero still fails.
            rule.waitUntil(15000) { rule.onNodeWithText("Still owed to you · all dates").isDisplayed() }
            rule.onNodeWithText("Still owed to you · all dates").assertIsDisplayed()
            capture("people-light.png")
            rule.onNodeWithContentDescription("Open review tab").performClick()
            // Review awaits an encrypted queue read after navigation. Preserve the
            // merged-tree assertion, but require actual readiness before capturing it.
            rule.waitUntil(15000) { rule.onNodeWithText("All caught up").isDisplayed() }
            rule.onNodeWithText("All caught up").assertIsDisplayed()
            capture("review-light.png")
            assertEquals(1718000L,runBlocking { repo.snapshot().personalSpend.toLong() })
        } finally { runBlocking { repo.eraseAll();repo.close() } }
    }
    @Test fun quick_category_sheet_preserves_spending_and_money_flow() {
        val repo=repository()
        try {
            seed(repo);show(repo)
            val before=runBlocking { repo.snapshot() }
            rule.onNodeWithContentDescription("Open activity").performClick()
            rule.onNodeWithText("Search your transactions").performTextInput("Weekly groceries")
            rule.waitUntil(15000) {rule.onAllNodesWithText("1 transaction").fetchSemanticsNodes().isNotEmpty()}
            rule.onNode(hasSetTextAction() and hasText("Weekly groceries")).performImeAction()
            rule.onNode(hasSetTextAction() and hasText("Weekly groceries")).assertIsNotFocused()
            capture("search-results-light.png")
            rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("Categorize Weekly groceries").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Categorize Weekly groceries").performClick()
            rule.onNodeWithText("Category & purpose").assertExists()
            rule.onNodeWithText("Save changes").assertIsDisplayed()
            capture("quick-category-light.png")
            rule.onNode(hasText("Food") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
            rule.onNodeWithText("Travel").performClick()
            rule.onNodeWithText("Save changes").performClick()
            rule.waitUntil(15000) {runBlocking {repo.snapshot().rows.any {it.counterpartyLabel=="Weekly groceries" && it.category=="Travel"}}}
            val after=runBlocking { repo.snapshot() }
            assertEquals(before.personalSpend,after.personalSpend)
            assertEquals(before.debit,after.debit)
            assertEquals(before.credit,after.credit)
            assertEquals("Travel",after.rows.single {it.counterpartyLabel=="Weekly groceries"}.category)
        } finally { runBlocking { repo.eraseAll();repo.close() } }
    }
    @Test fun closing_a_category_draft_does_not_report_a_successful_save() {
        val repo=repository()
        try {
            seed(repo);show(repo)
            rule.onNodeWithContentDescription("Open activity").performClick()
            rule.onNodeWithText("Search your transactions").performTextInput("Weekly groceries")
            rule.onNode(hasSetTextAction() and hasText("Weekly groceries")).performImeAction()
            rule.waitUntil(15000) {rule.onAllNodesWithContentDescription("Categorize Weekly groceries").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithContentDescription("Categorize Weekly groceries").performClick()
            rule.onNodeWithContentDescription("Close transaction editor").performClick()
            rule.onNodeWithText("Category and purpose saved.").assertDoesNotExist()
            assertEquals(1718000L,runBlocking {repo.snapshot().personalSpend.toLong()})
        } finally {runBlocking {repo.eraseAll();repo.close()}}
    }
    @Test fun activity_totals_distinguish_personal_spending_from_gross_money_out() {
        val repo=repository()
        try {
            seed(repo);show(repo)
            rule.onNodeWithContentDescription("Open activity").performClick()
            rule.waitUntil(15000) {rule.onAllNodesWithText("Totals").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("Totals").performClick()
            rule.waitUntil(15000) {rule.onAllNodesWithText("Matching totals").fetchSemanticsNodes().isNotEmpty()}
            rule.onNode(hasText("Money out") and hasText("₹20,980")).assertIsDisplayed()
            rule.onNode(hasText("Money in") and hasText("₹46,750")).assertIsDisplayed()
            rule.onNode(hasText("Your spending") and hasText("₹17,180")).assertIsDisplayed()
            capture("matching-totals-light.png")
            rule.onNodeWithContentDescription("Close matching totals").performClick()
            rule.onNodeWithText("Search your transactions").performTextInput("Friends dinner")
            rule.waitUntil(15000) {rule.onAllNodesWithText("1 transaction").fetchSemanticsNodes().isNotEmpty()}
            // Large text and the IME can move this action below the viewport.
            // Reach it through the same scroll action available to the user.
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Totals"))
            rule.onNodeWithText("Totals").assertIsDisplayed()
            rule.onNodeWithText("Totals").performClick()
            rule.waitUntil(15000) {rule.onAllNodesWithText("Matching totals").fetchSemanticsNodes().isNotEmpty()}
            capture("filtered-totals-light.png")
            rule.onNode(hasText("Money out") and hasText("₹900")).assertIsDisplayed()
            rule.onNode(hasText("Money in") and hasText("₹0")).assertIsDisplayed()
            rule.onNode(hasText("Your spending") and hasText("₹300")).assertIsDisplayed()
            rule.onNodeWithContentDescription("Close matching totals").performClick()
            rule.onNode(hasSetTextAction() and hasText("Friends dinner")).assertIsNotFocused()
            rule.onNodeWithText("1 transaction").assertIsDisplayed()
            assertEquals(1718000L,runBlocking {repo.snapshot().personalSpend.toLong()})
        } finally {runBlocking {repo.eraseAll();repo.close()}}
    }
    @Test fun stale_category_sheet_cannot_overwrite_a_newer_classification() {
        val repo=repository()
        try {
            seed(repo);show(repo)
            rule.onNodeWithContentDescription("Open activity").performClick()
            rule.onNodeWithText("Search your transactions").performTextInput("Weekly groceries")
            rule.onNode(hasSetTextAction() and hasText("Weekly groceries")).performImeAction()
            rule.waitUntil(15000) {rule.onAllNodesWithContentDescription("Categorize Weekly groceries").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithContentDescription("Categorize Weekly groceries").performClick()
            val id=runBlocking {repo.snapshot().rows.single {it.counterpartyLabel=="Weekly groceries"}.id}
            runBlocking {repo.classify(id,"Travel",SpendingOwnership.Family)}
            rule.onNodeWithText("Save changes").performClick()
            rule.waitUntil(15000) {
                runBlocking {repo.get(id)?.category=="Food"} ||
                    rule.onAllNodesWithText("This record changed. Reopen it before saving.").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("Travel",runBlocking {repo.get(id)!!.category})
            assertEquals("Family",runBlocking {repo.get(id)!!.ownership})
            assertEquals(1718000L,runBlocking {repo.snapshot().personalSpend.toLong()})
            rule.onNodeWithText("This record changed. Reopen it before saving.").assertIsDisplayed()
        } finally {runBlocking {repo.eraseAll();repo.close()}}
    }
}
