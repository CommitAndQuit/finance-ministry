package `in`.financeministry.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.data.TransactionEntity
import `in`.financeministry.app.ui.LedgerTransactionRow
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Presentation and action routing only: no database, permissions or personal ledger. */
class LedgerRowPresentationUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val variant = InstrumentationRegistry.getArguments().getString("visualVariant")
            ?.takeIf { it in setOf("light", "dark", "compact-light", "compact-dark", "large-text-dark") } ?: "light"
        val file = File(instrumentation.targetContext.getExternalFilesDir("public-ui"), "$name-$variant.png")
        file.outputStream().use {
            check(instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun fixture(id:String,label:String,direction:String="Debit",status:String="Successful",
        review:String="Confirmed",purpose:String="Personal") = TransactionEntity(
        id=id,sourceType="Manual",sourceTimestamp=1788600000000L,effectiveTimestamp=1788600000000L,
        amountMinor=185000,direction=direction,status=status,channel="CashManual",transactionType="Other",
        counterpartyLabel=label,category="Food",ownership=purpose,reviewState=review,
        createdAt=1788600000000L,updatedAt=1788600000000L)

    // A card-account acknowledgement is not new income. A '+' credit presentation
    // or a missing confirmation label must fail even when accounting excludes it.
    @Test fun card_repayment_confirmation_is_visible_as_a_neutral_confirmation_not_income() {
        val row = fixture("card-receipt", "Card receipt", direction = "Credit", review = "AutoRecorded")
            .copy(transactionType = "CardRepayment", channel = "Card")
        rule.setContent { FinanceMinistryTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(20.dp)) { LedgerTransactionRow(row, "Everyday card", {}, {}) }
        } } }
        rule.onNodeWithText("₹1,850", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("+₹1,850", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Card confirmation", substring = true, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Categorize Card receipt").assert(
            SemanticsMatcher("announces a card confirmation, not money in") { node ->
                val description = node.config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
                description.contains("Card confirmation") && !description.contains("Money in")
            })
        capture("card-confirmation-row")
    }

    @Test fun family_purpose_is_distinct_from_category_and_row_still_opens_quick_editor() {
        val action=mutableStateOf("")
        val row=fixture("family","Weekly groceries",purpose="Family")
        rule.setContent { FinanceMinistryTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                LedgerTransactionRow(row,"HDFC card",onOpen={action.value="Details requested"},
                    onClassify={action.value="Category editor requested"})
                if(action.value.isNotBlank()) Text(action.value)
            }
        } } }
        capture("family-row")
        rule.onNodeWithText("Food",useUnmergedTree=true).assertIsDisplayed()
        rule.onNodeWithText("Family",useUnmergedTree=true).assertIsDisplayed()
        rule.onNodeWithText("₹1,850",useUnmergedTree=true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Categorize Weekly groceries").performClick()
        rule.onNodeWithText("Category editor requested").assertIsDisplayed()
        rule.onNodeWithText("Details requested").assertDoesNotExist()
    }

    @Test fun group_badge_does_not_replace_category_gross_amount_or_personal_share() {
        val row=fixture("group","Friends dinner",purpose="Group").copy(amountMinor=90000,personalShareMinor=30000)
        rule.setContent { FinanceMinistryTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                LedgerTransactionRow(row,null,{},{})
            }
        } } }
        capture("group-row")
        rule.onNodeWithText("Food",useUnmergedTree=true).assertIsDisplayed()
        rule.onNodeWithText("Group",useUnmergedTree=true).assertIsDisplayed()
        rule.onNodeWithText("₹900",useUnmergedTree=true).assertIsDisplayed()
        rule.onNodeWithText("Your share ₹300",useUnmergedTree=true).assertIsDisplayed()
    }

    @Test fun rows_announce_flow_status_and_review_state_without_relying_on_color() {
        val cases=listOf(
            fixture("debit","Groceries") to listOf("Money out","Successful","Confirmed by you"),
            fixture("credit","Receipt",direction="Credit",review="AutoRecorded") to listOf("Money in","Successful","Saved automatically"),
            fixture("transfer","Between accounts",direction="Transfer",purpose="SelfTransfer") to listOf("Transfer","Self transfer","Successful"),
            fixture("unknown","Payment alert",direction="Unknown",status="Pending",review="NeedsReview") to listOf("Not identified","Pending","Needs review"),
        )
        rule.setContent { FinanceMinistryTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                cases.forEach { (row,_) -> LedgerTransactionRow(row,null,{},{}) }
            }
        } } }
        cases.forEach { (row,parts) ->
            val action=if(row.reviewState=="NeedsReview")"Review" else "Categorize"
            rule.onNodeWithContentDescription("$action ${row.counterpartyLabel}").assert(
                SemanticsMatcher("announces ${parts.joinToString()}") { node ->
                    val state=node.config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
                    parts.all {state.contains(it)}
                })
        }
    }

    @Test fun unsuccessful_alerts_show_status_in_the_row() {
        val failed=fixture("failed","Card payment",status="Failed",review="NeedsReview")
        val pending=fixture("pending","UPI payment",status="Pending",review="NeedsReview")
        rule.setContent { FinanceMinistryTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                LedgerTransactionRow(failed,null,{},{})
                LedgerTransactionRow(pending,null,{},{})
            }
        } } }
        rule.onNodeWithText("Failed",substring=true,useUnmergedTree=true).assertIsDisplayed()
        rule.onNodeWithText("Pending",substring=true,useUnmergedTree=true).assertIsDisplayed()
    }

    @Test fun an_excluded_alert_remains_labelled_and_opens_details_not_quick_edit() {
        val action=mutableStateOf("")
        val row=fixture("excluded","Excluded alert",review="Dismissed")
        rule.setContent { FinanceMinistryTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(20.dp)) {
                LedgerTransactionRow(row,null,onOpen={action.value="Details requested"},
                    onClassify={action.value="Category editor requested"})
                if(action.value.isNotBlank()) Text(action.value)
            }
        } } }
        rule.onNodeWithText("Not a transaction",substring=true,useUnmergedTree=true).assertIsDisplayed()
        rule.onNodeWithContentDescription("View excluded alert Excluded alert").performClick()
        rule.onNodeWithText("Details requested").assertIsDisplayed()
        rule.onNodeWithText("Category editor requested").assertDoesNotExist()
    }
}
