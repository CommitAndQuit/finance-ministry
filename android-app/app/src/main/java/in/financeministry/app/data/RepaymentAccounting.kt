package `in`.financeministry.app.data

import java.math.BigInteger
import java.util.Locale

data class PersonExpenseBalance(val expense: TransactionEntity, val memberId: String?,
    val shareMinor: Long, val outstandingMinor: Long)

data class PersonRepaymentBalance(val id: String, val name: String, val expenses: List<PersonExpenseBalance>) {
    val outstanding: BigInteger get() = expenses.fold(BigInteger.ZERO) { n, item -> n + BigInteger.valueOf(item.outstandingMinor) }
    val received: BigInteger get() = expenses.fold(BigInteger.ZERO) { n, item -> n + BigInteger.valueOf(item.shareMinor - item.outstandingMinor) }
}

data class RepaymentCreditMatch(val row: TransactionEntity, val availableMinor: Long,
    val score: Int, val reasons: List<String>)

data class RepaymentSummary(
    val outstanding: BigInteger, val newlyOwed: BigInteger,
    val received: BigInteger, val monthOutstanding: BigInteger,
    val expenses: List<TransactionEntity>, val repayments: List<RepaymentEntity>,
    val shares: List<GroupShareEntity> = emptyList(), val members: List<LocalMemberEntity> = emptyList(),
)

object RepaymentAccounting {
    /** Keep a person's entire history together, including settled expenses and unnamed legacy balances. */
    fun people(summary: RepaymentSummary): List<PersonRepaymentBalance> {
        val entries = summary.expenses.flatMap { expense ->
            val shares = summary.shares.filter { it.expenseId == expense.id }
            if (shares.isEmpty()) {
                val name = expense.groupLabel.orEmpty().trim().ifBlank { "Unassigned" }
                listOf(Triple("legacy:${expense.ownership}:${name.lowercase(Locale.ROOT)}", name,
                    PersonExpenseBalance(expense, null, principal(expense), owed(expense))))
            } else shares.map { share ->
                Triple("person:${share.memberId}", summary.members.firstOrNull { it.id == share.memberId }?.name ?: "Local person",
                    PersonExpenseBalance(expense, share.memberId, share.amountMinor, GroupSplitAccounting.owed(share, summary.repayments)))
            }
        }
        return entries.groupBy { it.first }.map { (id, rows) ->
            PersonRepaymentBalance(id, rows.first().second, rows.map { it.third }.sortedByDescending { it.expense.effectiveTimestamp })
        }.sortedWith(compareBy<PersonRepaymentBalance> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
    }

    /** Suggestions are evidence, never allocations. Only recordRepayment can change a balance. */
    fun rankCredits(expense: TransactionEntity, payer: String, unpaidMinor: Long,
        credits: List<Pair<TransactionEntity, Long>>): List<RepaymentCreditMatch> {
        val nameTokens = payer.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 }.toSet()
        return credits.filter { (row, available) -> available > 0 && available <= (row.amountMinor ?: 0) &&
            eligible(row) && row.direction == "Credit" && row.transactionType !in setOf("Refund", "Reversal") }
            .map { (row, available) ->
                val reasons = mutableListOf<String>()
                var score = 0
                val labelTokens = row.counterpartyLabel.orEmpty().lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")) .toSet()
                if (nameTokens.isNotEmpty() && labelTokens.containsAll(nameTokens)) {
                    reasons += "Name matches ${payer.trim()}"; score += 60
                }
                if (unpaidMinor > 0 && available == unpaidMinor) { reasons += "Exact unpaid amount"; score += 30 }
                else if (unpaidMinor > 0 && available < unpaidMinor) { reasons += "Amount fits within unpaid balance"; score += 10 }
                val elapsed = BigInteger.valueOf(row.effectiveTimestamp) - BigInteger.valueOf(expense.effectiveTimestamp)
                if (elapsed.signum() >= 0 && elapsed <= BigInteger.valueOf(30L * 86_400_000)) {
                    reasons += "Received within 30 days of this expense"; score += 15
                }
                RepaymentCreditMatch(row, available, score, reasons)
            }.sortedWith(compareByDescending<RepaymentCreditMatch> { it.score }
                .thenByDescending { it.row.effectiveTimestamp }.thenBy { it.row.id })
    }

    fun eligible(row: TransactionEntity, reversed: Set<String> = emptySet()): Boolean =
        row.id !in reversed && row.duplicateOfId == null && row.currency == "INR" &&
            row.amountMinor != null && row.amountMinor > 0 && row.status == "Successful" &&
            row.reviewState in setOf("AutoRecorded", "Confirmed") && row.transactionType !in setOf("SelfTransfer", "CardRepayment") && row.ownership != "SelfTransfer"

    fun personal(row: TransactionEntity): Long = if (!row.repaymentExpected || row.ownership !in setOf("ForOther", "Group"))
        row.amountMinor ?: 0 else row.personalShareMinor ?: row.amountMinor ?: 0

    fun principal(row: TransactionEntity): Long = if (eligible(row) && row.direction == "Debit" &&
        row.repaymentExpected && row.ownership in setOf("ForOther", "Group"))
        ((row.amountMinor ?: 0) - personal(row)).coerceAtLeast(0) else 0

    fun owed(row: TransactionEntity): Long = (principal(row) - row.repaidMinor).coerceAtLeast(0)

    fun summary(rows: List<TransactionEntity>, repayments: List<RepaymentEntity>, start: Long, end: Long,
        reversed: Set<String> = emptySet()): RepaymentSummary {
        val expenses = rows.filter { eligible(it, reversed) && it.direction == "Debit" && it.repaymentExpected && it.ownership in setOf("ForOther", "Group") }
        val ids = expenses.map { it.id }.toSet()
        val month = expenses.filter { it.effectiveTimestamp in start until end }
        fun List<Long>.total() = fold(BigInteger.ZERO) { n, value -> n + BigInteger.valueOf(value) }
        return RepaymentSummary(expenses.map(::owed).total(), month.map(::principal).total(),
            repayments.filter { it.expenseId in ids && it.receivedAt?.let { time -> time in start until end } == true }
                .map { it.amountMinor }.total(), month.map(::owed).total(), expenses, repayments.filter { it.expenseId in ids })
    }

    fun validateAllocation(expense: TransactionEntity, incoming: TransactionEntity?, amount: Long, allocatedCredit: Long) {
        require(amount > 0) { "Enter a positive repayment amount." }
        require(eligible(expense) && principal(expense) > 0) { "Choose a confirmed expense with repayment expected." }
        require(amount <= owed(expense)) { "Repayment exceeds the amount still owed." }
        if (incoming != null) {
            require(eligible(incoming) && incoming.direction == "Credit" && incoming.transactionType !in setOf("Refund", "Reversal")) {
                "Choose a confirmed incoming payment, not a refund or transfer."
            }
            require(allocatedCredit >= 0 && allocatedCredit <= incoming.amountMinor!!) { "Incoming allocation is invalid." }
            require(amount <= incoming.amountMinor!! - allocatedCredit) { "The incoming payment does not have enough unallocated money." }
        }
    }
}
