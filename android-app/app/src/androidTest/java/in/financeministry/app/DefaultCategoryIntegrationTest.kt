package `in`.financeministry.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DefaultCategoryIntegrationTest {
    private fun ledger() = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "defaults_${UUID.randomUUID()}")

    @Test fun legacy_filter_stays_valid_after_its_last_row_is_recategorized_or_deleted() = runBlocking {
        val r = ledger()
        try {
            val now = System.currentTimeMillis()
            val input = ManualInput("25", Direction.Debit, now, TransactionType.Other, category = "Travel")
            val id = r.save(input)
            assertEquals(id, r.snapshot(filter = "Category:Travel").rows.single().id)
            r.save(input.copy(category = "Food"), id)
            assertFalse("An unused legacy suggestion should disappear", "Travel" in r.categories())
            assertTrue(r.snapshot(filter = "Category:Travel").rows.isEmpty())
            val second = r.save(input)
            r.delete(second)
            assertTrue(r.snapshot(filter = "Category:Travel").rows.isEmpty())
            assertEquals(id, r.snapshot(filter = "Category:Food").rows.single().id)
            assertEquals(2500L, r.snapshot().debit.toLong())
        } finally { r.eraseAll(); r.close() }
    }

    @Test fun a_legacy_filter_on_a_fresh_ledger_returns_an_empty_result() = runBlocking {
        val r = ledger()
        try { assertTrue(r.snapshot(filter = "Category:Travel").rows.isEmpty()) }
        finally { r.eraseAll(); r.close() }
    }

    @Test fun fresh_catalog_is_small_and_new_defaults_can_be_saved_and_filtered() = runBlocking {
        val r = ledger()
        try {
            assertEquals(listOf("Other", "Grocery", "Food", "Entertainment", "Bills", "Transport", "Investment"), r.categories())
            val now = System.currentTimeMillis()
            val grocery = r.save(ManualInput("10", Direction.Debit, now, TransactionType.Other, category = "Grocery"))
            val transport = r.save(ManualInput("20", Direction.Debit, now, TransactionType.Other, category = "Transport"))
            assertEquals(grocery, r.snapshot(filter = "Category:Grocery").rows.single().id)
            assertEquals(transport, r.snapshot(filter = "Category:Transport").rows.single().id)
            assertEquals(3000L, r.snapshot().debit.toLong())
        } finally { r.eraseAll(); r.close() }
    }

    @Test fun used_legacy_categories_remain_editable_filterable_and_restorable_without_unused_clutter() = runBlocking {
        val source = ledger(); val target = ledger()
        val password = "test-only-password-long".toCharArray()
        try {
            val now = System.currentTimeMillis()
            val travel = source.save(ManualInput("50", Direction.Debit, now, TransactionType.Other, category = "Travel"))
            val income = source.save(ManualInput("70", Direction.Credit, now, TransactionType.Other, category = "Income"))
            source.saveCategoryRule("Demo shop", null, "Shopping")
            source.saveBudget("Flat expenses", "1000", null)
            source.saveRecurringReminder("Demo medicine", "10", 1, "Health")
            source.save(ManualInput("51", Direction.Debit, now, TransactionType.Other, category = "Travel"), travel)
            val options = source.categories()
            listOf("Travel", "Income", "Shopping", "Flat expenses", "Health").forEach { assertTrue(it in options) }
            assertFalse("Unused old defaults must not crowd the picker", "Education" in options || "Cash" in options)
            val backup = source.createEncryptedBackup(password)
            target.restoreBackup(target.previewBackup(backup, password), protectCurrent = false)
            assertEquals("Travel", target.get(travel)!!.category)
            assertEquals("Income", target.get(income)!!.category)
            assertEquals(travel, target.snapshot(filter = "Category:Travel").rows.single().id)
            assertEquals(5100L, target.snapshot().debit.toLong())
            assertEquals(7000L, target.snapshot().credit.toLong())
            assertEquals(options, target.categories())
        } finally {
            password.fill('\u0000')
            source.eraseAll(); source.close(); target.eraseAll(); target.close()
        }
    }

    @Test fun older_custom_grocery_and_transport_keep_their_spelling_and_restore_without_duplicate_choices() = runBlocking {
        val source = ledger(); val target = ledger()
        val password = "test-only-password-long".toCharArray()
        try {
            // Simulate names created before Grocery and Transport became defaults.
            source.withLedger { it.transactions().metadata(LedgerMetadataEntity(CUSTOM_CATEGORIES_KEY,
                JSONArray(listOf("GROCERY", "transport")).toString())) }
            val id = source.save(ManualInput("12", Direction.Debit, System.currentTimeMillis(), TransactionType.Other, category = "GROCERY"))
            val options = source.categories()
            assertEquals(listOf("GROCERY"), options.filter { it.equals("Grocery", ignoreCase = true) })
            assertEquals(listOf("transport"), options.filter { it.equals("Transport", ignoreCase = true) })
            val backup = source.createEncryptedBackup(password)
            target.restoreBackup(target.previewBackup(backup, password), protectCurrent = false)
            assertEquals("GROCERY", target.get(id)!!.category)
            assertEquals(id, target.snapshot(filter = "Category:GROCERY").rows.single().id)
            assertEquals(options, target.categories())
            try { target.addCategory("grocery"); fail("A duplicate promoted category was accepted") }
            catch (_: IllegalArgumentException) { }
        } finally {
            password.fill('\u0000')
            source.eraseAll(); source.close(); target.eraseAll(); target.close()
        }
    }
}
