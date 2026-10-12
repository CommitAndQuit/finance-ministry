package `in`.financeministry.app.feature

import `in`.financeministry.app.data.TransactionEntity
import `in`.financeministry.app.data.PersonRepaymentBalance

/** A legacy group is a balance, not an invented individual person. */
fun owedBalanceCountLabel(balances: List<PersonRepaymentBalance>): String {
    val outstanding = balances.filter { it.outstanding.signum() > 0 }
    val onlyPeople = outstanding.all { balance -> balance.expenses.all {
        it.memberId != null || it.expense.ownership == "ForOther" && balance.name != "Unassigned"
    } }
    val count = outstanding.size
    val noun = if (onlyPeople) if (count == 1) "person" else "people" else if (count == 1) "balance" else "balances"
    return "$count $noun"
}

fun transactionFlowLabel(row: TransactionEntity): String =
    if (row.transactionType == "CardRepayment") {
        if (row.direction == "Credit") "Card confirmation" else "Card bill payment"
    } else friendly(row.direction)

/** Display copy only. Persisted enum values remain unchanged for existing ledgers. */
fun friendly(value: String): String = when (value) {
    "Debit" -> "Money out"
    "Credit" -> "Money in"
    "AutoRecorded" -> "Saved automatically"
    "NeedsReview" -> "Needs review"
    "Confirmed" -> "Confirmed by you"
    "CashManual" -> "Cash"
    "ForOther" -> "For someone else"
    "SelfTransfer" -> "Self transfer"
    "MerchantPayment" -> "Purchase"
    "CardRepayment" -> "Card bill payment"
    "Unknown" -> "Not identified"
    "SMS" -> "From SMS"
    "Manual" -> "Added manually"
    else -> value.replace(Regex("([a-z])([A-Z])"), "$1 $2")
}
