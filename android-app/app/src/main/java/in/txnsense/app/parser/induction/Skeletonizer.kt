package `in`.txnsense.app.parser.induction

import `in`.txnsense.app.core.BankRegistry
import `in`.txnsense.app.core.model.IncomingSms
import `in`.txnsense.app.parser.ParserRules
import java.util.Locale

/**
 * Turns a received message into a [Skeleton].
 *
 * This is the only component that ever sees a raw body, and it is deliberately one-way: values are
 * classified into [Slot]s and discarded, and words outside [BankVocabulary] are replaced by
 * [Slot.Word] blanks. What comes out cannot be turned back into what went in, which is what makes
 * it safe to keep a corpus of past messages the parser failed on.
 */
class Skeletonizer(private val vocabulary: Set<String> = BankVocabulary.words) {

    fun skeletonize(sms: IncomingSms): Skeleton {
        // The fraud-reporting footer carries its own codes and amounts and is not part of any
        // layout, so the inducer must not learn from it either.
        val text = ParserRules.transactionText(sms.body).lowercase(Locale.ROOT)
        val bankId = BankRegistry.detect(sms.sender) ?: BankRegistry.detect(sms.body) ?: UNKNOWN_BANK
        val tokens = mutableListOf<Token>()
        text.split('\n').map { it.trim('\r', ' ', '\t') }.filter(String::isNotEmpty)
            .forEachIndexed { index, line ->
                if (index > 0) tokens += Token.Break
                tokens += tokenize(line)
            }
        return Skeleton(bankId, promoteAccountHints(tokens))
    }

    private fun tokenize(line: String): List<Token> = pattern.findAll(line).map { match ->
        fun group(name: String) = match.groups[name]?.value
        val width = match.value.length
        when {
            group("url") != null -> Token.Blank(Slot.Url, width)
            group("money") != null -> Token.Blank(Slot.Money, width)
            group("date") != null -> Token.Blank(Slot.Date, width)
            group("time") != null -> Token.Blank(Slot.Time, width)
            group("account") != null -> Token.Blank(Slot.Account, width)
            group("number") != null -> Token.Blank(Slot.Number, width)
            else -> word(match.value)
        }
    }.toList()

    private fun word(raw: String): Token {
        val trimmed = raw.trimEnd('.', '-', '/', '\'', '&')
        val word = trimmed.ifEmpty { raw }
        return if (word in vocabulary) Token.Literal(word) else Token.Blank(Slot.Word, raw.length)
    }

    /**
     * A bare four-digit group right after "card"/"a/c"/"ending" is a masked account hint, not an
     * arbitrary number. Recognizing it here is what lets an induced pattern capture `account`.
     */
    private fun promoteAccountHints(tokens: List<Token>): List<Token> = tokens.mapIndexed { index, token ->
        val previous = tokens.take(index).lastOrNull { it is Token.Literal } as? Token.Literal
        val promotable = token is Token.Blank && token.slot == Slot.Number && token.width == 4 &&
            previous?.word in BankVocabulary.accountLeadIns
        if (promotable) Token.Blank(Slot.Account, (token as Token.Blank).width) else token
    }

    companion object {
        /** Bank id used when neither the sender nor the body names a known institution. */
        const val UNKNOWN_BANK = "UNKNOWN"

        /**
         * One ordered alternation over a line. Regex alternation prefers the earliest alternative at
         * a given position, so the order here *is* the classification priority: a currency-prefixed
         * amount is money before its digits can be read as a plain number, and a masked group is an
         * account before it can be read as a word.
         */
        private val pattern = Regex(
            """(?<url>https?://\S{1,200}|www\.\S{1,200})""" +
                """|(?<money>(?:rs\.?|inr|₹)\s{0,2}\d[\d,]{0,16}(?:\.\d{1,2})?)""" +
                """|(?<date>\d{4}-\d{2}-\d{2}|\d{1,2}[-/](?:\d{1,2}|[a-z]{3})[-/]\d{2,4})(?!\d)""" +
                """|(?<time>\d{1,2}:\d{2}(?::\d{2})?)""" +
                """|(?<account>[x*•]{1,6}\d{3,4})(?!\d)""" +
                """|(?<number>\d[\d,]{0,18}(?:\.\d{1,2})?)""" +
                """|(?<word>[a-z][a-z0-9'&./-]{0,30})""",
            RegexOption.IGNORE_CASE,
        )
    }
}
