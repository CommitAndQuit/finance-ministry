package `in`.financeministry.app

import `in`.financeministry.app.core.model.supportedTransactionCategories
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionCategoriesTest {
    @Test fun existing_income_and_investment_records_remain_supported() {
        assertTrue("Existing Income labels must remain valid", "Income" in supportedTransactionCategories)
        assertTrue("Investment must remain valid", "Investment" in supportedTransactionCategories)
    }
}
