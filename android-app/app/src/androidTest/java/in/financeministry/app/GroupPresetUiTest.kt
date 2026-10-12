package `in`.financeministry.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import `in`.financeministry.app.data.*
import `in`.financeministry.app.feature.GroupSplitEditor
import `in`.financeministry.app.feature.TransactionForm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class GroupPresetUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun applying_a_saved_group_prefills_its_name_on_the_expense() {
        val r=TransactionRepository(ApplicationProvider.getApplicationContext<Application>(),"preset_name_ui_${UUID.randomUUID()}")
        var done=false
        try {
            runBlocking {
                val alice=r.addLocalMember("Alice");val bob=r.addLocalMember("Bob")
                r.saveGroupPreset("Friends dinner",true,100,listOf(MemberShareInput(alice.id,100),MemberShareInput(bob.id,100)))
            }
            rule.setContent {FinanceMinistryTheme {Surface(Modifier.fillMaxSize()) {TransactionForm(r,null,{done=true})}}}
            rule.onNode(editableLedgerField("Amount (INR)")).performTextInput("900")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.onNodeWithText("Group").performScrollTo().performClick()
            rule.onNodeWithText("Split with people").performScrollTo().performClick()
            rule.waitUntil(15000) {rule.onAllNodesWithText("Choose saved group").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("Choose saved group").performScrollTo().performClick()
            rule.onNodeWithText("Use Friends dinner").performClick()
            rule.onNodeWithText("Apply split").performClick()
            rule.onNode(hasText("Add payment") and hasClickAction()).performClick()
            rule.waitUntil(15000) {done}
            val row=runBlocking {r.snapshot().rows.single()}
            assertEquals("Friends dinner",row.groupLabel)
            assertEquals(30000L,row.personalShareMinor)
            assertEquals(listOf(30000L,30000L),runBlocking {r.groupSharesFor(row.id).map {it.amountMinor}})
        } finally {runBlocking {r.eraseAll();r.close()}}
    }

    @Test fun selecting_a_saved_group_previews_exact_shares_and_can_be_overridden_before_apply() {
        val r = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "preset_ui_${UUID.randomUUID()}")
        var applied: GroupPresetPreview? = null
        try {
            runBlocking {
                val alice = r.addLocalMember("Alice"); val bob = r.addLocalMember("Bob")
                r.saveGroupPreset("Dinner", true, 100, listOf(MemberShareInput(alice.id, 100), MemberShareInput(bob.id, 100)))
            }
            rule.setContent { FinanceMinistryTheme { Surface(Modifier.fillMaxSize()) {
                GroupSplitEditor(r, 1001, 0, emptyList(), { personal, shares -> applied = GroupPresetPreview(personal, shares) }, {})
            } } }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Choose saved group").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Choose saved group").performScrollTo().performClick()
            rule.onNodeWithText("Use Dinner").performClick()
            // The reconciliation preview intentionally repeats your share. Count
            // participant values separately, then verify the labelled summary.
            rule.onAllNodes(hasText("₹3.34") and !hasText("Your spending")).assertCountEquals(2)
            rule.onNodeWithText("₹3.33").assertExists()
            rule.onNode(hasText("Your spending") and hasText("₹3.34")).assertExists()
            rule.onNode(hasText("Owed back to you") and hasText("₹6.67")).assertExists()
            assertNull(applied)
            rule.onNodeWithText("Custom amounts").performScrollTo().performClick()
            rule.onNodeWithContentDescription("Share for You (INR)").performScrollTo().performTextReplacement("2.01")
            rule.onNodeWithContentDescription("Share for Alice (INR)").performScrollTo().performTextReplacement("4")
            rule.onNodeWithContentDescription("Share for Bob (INR)").performScrollTo().performTextReplacement("4")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.onNodeWithText("Apply split").performClick()
            rule.waitUntil(15000) { applied != null }
            assertEquals(201L, applied!!.personalMinor)
            assertEquals(listOf(400L, 400L), applied!!.others.map { it.amountMinor })
            assertTrue(runBlocking { r.groupPresets().single().equal })
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }
}
