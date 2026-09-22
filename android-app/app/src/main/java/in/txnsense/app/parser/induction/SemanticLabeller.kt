package `in`.txnsense.app.parser.induction

import `in`.txnsense.app.core.model.Channel
import `in`.txnsense.app.core.model.Direction
import `in`.txnsense.app.core.model.TransactionStatus
import `in`.txnsense.app.core.model.TransactionType

/** What a discovered layout means, once something has judged it. */
data class TemplateSemantics(
    val direction: Direction,
    val status: TransactionStatus,
    val channel: Channel,
    val transactionType: TransactionType,
)

/**
 * Judges what an aligned layout means.
 *
 * The structural half of induction finds the *shape* of a layout without understanding it; this is
 * the half that says whether the shape describes money leaving or arriving, over which channel, and
 * whether it describes a movement at all. Refusing a layout — returning null — is the most important
 * thing an implementation does: a promotion, a due-date reminder and a one-time password all have
 * amounts in them and none of them belong in a ledger.
 *
 * An implementation is handed [Alignment.words] and nothing else: the invariant vocabulary words of
 * the layout, with every merchant, payee, amount, account and reference already removed. That is
 * what makes this the safe place to put an on-device language model — the model would see
 * "spent on card ending at on" and never a line of the user's mail.
 */
interface SemanticLabeller {
    fun label(words: List<String>): TemplateSemantics?
}

/**
 * Keyword labeller over the layout's vocabulary.
 *
 * It answers the same questions a model would, from the word sets in [BankVocabulary], and is the
 * default so that learning works with no model shipped at all.
 */
class HeuristicLabeller : SemanticLabeller {

    override fun label(words: List<String>): TemplateSemantics? {
        val present = words.toSet()
        // A message that talks about an amount without completing a movement is not a transaction.
        if (present.any { it in BankVocabulary.disqualifiers }) return null
        val direction = direction(movementWords(words)) ?: return null
        val status = status(present)
        // Only a settled movement may be learned as a recordable layout; anything in flight would
        // need a second message to resolve, which single-message induction cannot see.
        if (status == TransactionStatus.Pending) return null
        return TemplateSemantics(direction, status, channel(words), type(present, direction))
    }

    /**
     * The movement words, with card names excluded.
     *
     * "Credit"/"debit" name both a direction and a kind of card, and a card spend says both — "Rs.100
     * spent on your Credit Card" moves money *out*. Reading them as directions there would make
     * every card layout look self-contradictory, so they only count when they are not naming a card.
     */
    private fun movementWords(words: List<String>): Set<String> = words
        .filterIndexed { index, word ->
            val namesACard = word in AMBIGUOUS && words.getOrNull(index + 1)?.let { it in CARD_NOUNS } == true
            !namesACard
        }
        .toSet()

    /** Null when the layout names no movement, or names both and so cannot be read either way. */
    private fun direction(present: Set<String>): Direction? {
        val debit = present.any { it in DEBIT }
        val credit = present.any { it in CREDIT }
        return when {
            debit && !credit -> Direction.Debit
            credit && !debit -> Direction.Credit
            else -> null
        }
    }

    private fun status(present: Set<String>): TransactionStatus = when {
        present.any { it in setOf("declined", "failed", "unsuccessful", "rejected") } -> TransactionStatus.Failed
        present.any { it in setOf("reversed", "reversal") } -> TransactionStatus.Reversed
        present.contains("initiated") -> TransactionStatus.Pending
        else -> TransactionStatus.Successful
    }

    private fun channel(words: List<String>): Channel {
        val present = words.toSet()
        /** The word before a card noun, when the layout names one. */
        val cardQualifier = words.indices.firstNotNullOfOrNull { index ->
            if (words[index] in CARD_NOUNS) words.getOrNull(index - 1) else null
        }
        return when {
            present.contains("upi") || present.contains("vpa") -> Channel.UPI
            present.contains("atm") || present.contains("w/d") -> Channel.ATM
            present.contains("imps") -> Channel.IMPS
            present.contains("neft") -> Channel.NEFT
            present.contains("rtgs") -> Channel.RTGS
            present.contains("wallet") -> Channel.Wallet
            present.contains("netbanking") -> Channel.NetBanking
            cardQualifier == "credit" -> Channel.CreditCard
            cardQualifier == "debit" -> Channel.DebitCard
            // An unqualified card alert stays generic, exactly as the curated card templates leave
            // it; a credit-card-only sender resolves it afterwards in the engine.
            present.any { it in CARD_NOUNS } -> Channel.Card
            else -> Channel.Unknown
        }
    }

    private fun type(present: Set<String>, direction: Direction): TransactionType = when {
        present.any { it in setOf("reversed", "reversal") } -> TransactionType.Reversal
        present.any { it in setOf("refund", "refunded") } -> TransactionType.Refund
        present.contains("salary") -> TransactionType.SalaryIncome
        present.any { it in setOf("withdrawn", "withdrawal") } || present.contains("atm") -> TransactionType.CashWithdrawal
        present.any { it in setOf("deposit", "deposited") } && direction == Direction.Credit -> TransactionType.Deposit
        present.any { it in setOf("fee", "fees", "charges", "interest") } -> TransactionType.FeeCharge
        present.any { it in setOf("repaid", "repayment", "settled") } -> TransactionType.CardRepayment
        else -> TransactionType.Unknown
    }

    private companion object {
        /** Nouns that turn a preceding "credit"/"debit" into a card name rather than a direction. */
        val CARD_NOUNS = setOf("card", "cards", "cardmember")
        val AMBIGUOUS = setOf("credit", "debit")
        val DEBIT = setOf(
            "debited", "debit", "spent", "spend", "paid", "withdrawn", "withdrawal", "charged",
            "charge", "deducted", "purchase", "dr", "repaid", "repayment",
        )
        val CREDIT = setOf("credited", "credit", "received", "refund", "refunded", "deposited", "deposit", "cr")
    }
}
