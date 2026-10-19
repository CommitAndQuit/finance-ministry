package `in`.financeministry.app.data

import java.math.BigInteger
import java.math.BigDecimal

data class MemberShareInput(val memberId: String, val amountMinor: Long)

/** All split arithmetic is integer paise, never floating point or rounded totals. */
object GroupSplitAccounting {
    /** Two decimal places allow half-parts; scaled integers preserve the exact ratio. */
    fun partWeights(parts: List<String>): List<Long> {
        require(parts.size in 2..21) { "Add at least one person besides you." }
        val weights = parts.map {
            require(it.matches(Regex("[0-9]{1,9}(\\.[0-9]{1,2})?"))) {
                "Enter parts from 0 to 999999999.99, with up to two decimal places."
            }
            BigDecimal(it).movePointRight(2).longValueExact()
        }
        require(weights.any { it > 0 }) { "At least one person needs more than zero parts." }
        return weights
    }

    fun byParts(total: Long, parts: List<String>): List<Long> =
        GroupPresetAccounting.custom(total, partWeights(parts))

    fun equal(total: Long, people: Int): List<Long> {
        require(total > 0 && people in 2..21) { "Choose between two and twenty-one people, including you." }
        return List(people) { total / people + if (it < total % people) 1L else 0L }
    }
    fun validate(total: Long, personal: Long, others: List<MemberShareInput>) {
        require(total > 0 && personal in 0..total && others.size in 1..20) { "Choose people and check your share." }
        require(others.all { it.memberId.isNotBlank() && it.amountMinor in 0..total } &&
            others.map { it.memberId }.distinct().size == others.size) { "Each person must appear once with a non-negative share." }
        val sum = others.fold(BigInteger.valueOf(personal)) { n, s -> n + BigInteger.valueOf(s.amountMinor) }
        require(sum == BigInteger.valueOf(total)) { "Shares must add up exactly to the expense amount." }
    }
    fun owed(share: GroupShareEntity, repayments: List<RepaymentEntity>): Long {
        val received = repayments.filter { it.expenseId == share.expenseId && it.memberId == share.memberId }
            .fold(0L) { n, r -> Math.addExact(n, r.amountMinor) }
        require(received in 0..share.amountMinor) { "Repayments exceed this person's share." }
        return share.amountMinor - received
    }
    fun validateRepayment(shares: List<GroupShareEntity>, repayments: List<RepaymentEntity>, memberId: String?, amount: Long) {
        if (shares.isEmpty()) { require(memberId == null) { "This expense has no individual split." }; return }
        val share = requireNotNull(shares.singleOrNull { it.memberId == memberId }) { "Choose the person whose balance this payment settles." }
        require(amount > 0 && amount <= owed(share, repayments)) { "Repayment exceeds this person's unpaid share." }
    }
}
