package `in`.financeministry.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Per-method spend/income aggregation: refunds net against spend and never count as income. */
class SpendAggregationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun repository() = TransactionRepository(context, "spend_agg_${UUID.randomUUID()}")
    private fun spend(amount: String, channel: Channel, type: TransactionType = TransactionType.MerchantPayment) =
        ManualInput(amount, Direction.Debit, System.currentTimeMillis(), type, channel = channel)
    private fun credit(amount: String, channel: Channel, type: TransactionType) =
        ManualInput(amount, Direction.Credit, System.currentTimeMillis(), type, channel = channel)

    @Test fun spend_nets_refunds_and_income_excludes_them() = runBlocking {
        val repo = repository()
        try {
            repo.save(spend("1000.00", Channel.CreditCard))
            repo.save(spend("400.00", Channel.CreditCard))
            repo.save(credit("150.00", Channel.CreditCard, TransactionType.Refund))   // nets against CC spend
            repo.save(spend("250.00", Channel.UPI))
            repo.save(credit("5000.00", Channel.BankTransfer, TransactionType.SalaryIncome)) // real income
            repo.save(spend("300.00", Channel.UPI, TransactionType.SelfTransfer))       // excluded
            repo.save(spend("700.00", Channel.CreditCard, TransactionType.CardRepayment)) // excluded

            val spend = repo.spendByMethod().associateBy { it.channel }
            assertEquals(140000L, spend.getValue("CreditCard").grossSpendMinor)
            assertEquals(15000L, spend.getValue("CreditCard").refundMinor)
            assertEquals(125000L, spend.getValue("CreditCard").netSpendMinor)   // 1400 - 150
            assertEquals(2, spend.getValue("CreditCard").spendCount)            // repayment not counted
            assertEquals(25000L, spend.getValue("UPI").netSpendMinor)           // self-transfer excluded
            assertEquals(1, spend.getValue("UPI").spendCount)
            assertNull("income methods are not spends", spend["BankTransfer"])

            val income = repo.incomeByMethod().associateBy { it.channel }
            assertEquals(500000L, income.getValue("BankTransfer").incomeMinor)
            assertNull("refund is not income", income["CreditCard"])
            assertNull("spend-only method has no income", income["UPI"])
        } finally { repo.eraseAll(); repo.close() }
    }
}
