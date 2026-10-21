package `in`.txnsense.app

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import `in`.txnsense.app.data.*
import `in`.txnsense.app.feature.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class SupportAndDescriptionUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun support_opens_reviewable_email_drafts_without_ledger_data_or_attachments() {
        var launched: Intent? = null
        rule.setContent { FinanceMinistryTheme { Surface { Column { SupportPanel { launched = it } } } } }
        rule.onNodeWithText("Report a bug").performClick()
        val bug = requireNotNull(launched)
        assertEquals(Intent.ACTION_SENDTO, bug.action)
        assertEquals("mailto", bug.data!!.scheme)
        assertTrue(bug.data.toString().startsWith("mailto:financeministrysupport@gmail.com?"))
        assertTrue(bug.getStringExtra(Intent.EXTRA_SUBJECT)!!.contains("Bug report"))
        assertTrue(bug.getStringExtra(Intent.EXTRA_TEXT)!!.contains(BuildConfig.VERSION_NAME))
        assertFalse(bug.hasExtra(Intent.EXTRA_STREAM)); assertNull(bug.clipData)
        assertEquals(setOf(Intent.EXTRA_EMAIL, Intent.EXTRA_SUBJECT, Intent.EXTRA_TEXT), bug.extras!!.keySet())
        rule.onNodeWithText("Suggest a feature").performClick()
        assertTrue(launched!!.getStringExtra(Intent.EXTRA_SUBJECT)!!.contains("Feature request"))
        rule.onNodeWithText("Email draft opened. You can review it before sending.").assertExists()
    }

    @Test fun missing_email_app_leaves_a_copyable_address_and_does_not_crash() {
        rule.setContent { FinanceMinistryTheme { Surface { Column { SupportPanel { throw ActivityNotFoundException() } } } } }
        rule.onNodeWithText("Report a bug").performClick()
        rule.onNodeWithText("No email app is available. Copy the address below and use your email service.").assertExists()
        rule.onNodeWithText(SUPPORT_EMAIL).assertExists()
        rule.onNodeWithText("Copy email address").performClick()
        rule.onNodeWithText("Support email copied.").assertExists()
    }

    @Test fun optional_description_is_discoverable_and_can_be_saved_edited_and_cleared() {
        val r = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "description_${UUID.randomUUID()}")
        val existing = mutableStateOf<TransactionEntity?>(null)
        var saves = 0
        try {
            rule.setContent { FinanceMinistryTheme { Surface(Modifier.fillMaxSize()) {
                TransactionForm(r, existing.value, { saves++ })
            } } }
            rule.onNode(editableLedgerField("Amount (INR)")).performTextInput("90")
            rule.onNode(editableLedgerField("Description (optional)")).performScrollTo().performTextInput("Dinner after the movie")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.onNode(hasText("Add payment") and hasClickAction()).performClick()
            rule.waitUntil(15000) { saves == 1 }
            val row = runBlocking { r.snapshot().rows.single() }
            assertEquals("Dinner after the movie", row.userNotes)
            rule.runOnIdle { existing.value = row }
            rule.onNode(editableLedgerField("Description (optional)")).performScrollTo().assertTextEquals("Dinner after the movie")
            rule.onNode(editableLedgerField("Description (optional)")).performTextReplacement("Shared snacks")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.onNode(hasText("Save changes") and hasClickAction()).performClick()
            rule.waitUntil(15000) { saves == 2 }
            val edited = runBlocking { r.get(row.id)!! }
            assertEquals("Shared snacks", edited.userNotes)
            rule.runOnIdle { existing.value = edited }
            rule.onNode(editableLedgerField("Description (optional)")).performScrollTo().performTextClearance()
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.onNode(hasText("Save changes") and hasClickAction()).performClick()
            rule.waitUntil(15000) { saves == 3 }
            assertNull(runBlocking { r.get(row.id)!!.userNotes })
            assertEquals(9000L, runBlocking { r.snapshot().debit.toLong() })
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }
}
