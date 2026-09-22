package `in`.txnsense.app.parser.induction

/**
 * What kind of value occupied a variable position in an SMS layout.
 *
 * A slot is all a skeleton remembers about that position — the kind, never the value. Each slot
 * carries an invented [filler] so a skeleton can be rendered back into a realistically shaped
 * message for validation without reproducing anything the user actually received.
 */
enum class Slot(val filler: String) {
    Money("Rs.42.00"),
    Account("XX1234"),
    Date("01-01-2026"),
    Time("12:34:56"),
    Number("123456"),
    Url("http://example.invalid/r"),
    Word("TESTWORD"),
}

/** One position in a [Skeleton]. */
sealed interface Token {
    /** A word present in [BankVocabulary] — the only original text a skeleton keeps. */
    data class Literal(val word: String) : Token

    /** A redacted position. [width] is the original character span, used to bound emitted gaps. */
    data class Blank(val slot: Slot, val width: Int) : Token

    /** A line break. Bank layouts use these structurally, so a break anchors like a literal does. */
    data object Break : Token
}

/**
 * A privacy-safe outline of one received message: banking vocabulary plus typed blanks.
 *
 * Skeletons are what the inducer learns from, and the only form of a message that may be stored.
 * Everything a message said that was not banking vocabulary — merchant names, payee names, amounts,
 * references, account numbers, links — is already gone by the time a skeleton exists.
 */
data class Skeleton(val bankId: String, val tokens: List<Token>) {

    /** The anchoring tokens: vocabulary words and line breaks, in order. */
    val anchors: List<Token> = tokens.filter { it is Token.Literal || it is Token.Break }

    /** Just the vocabulary words, for clustering and for the semantic labeller. */
    val words: List<String> = tokens.filterIsInstance<Token.Literal>().map(Token.Literal::word)

    /**
     * A synthetic message with this layout and invented values. Validation only ever sees rendered
     * skeletons, never a real body, so a learned pattern is proven against safe text.
     */
    fun render(): String = buildString {
        tokens.forEach { token ->
            when (token) {
                is Token.Break -> append('\n')
                is Token.Literal -> {
                    if (isNotEmpty() && last() != '\n') append(' ')
                    append(token.word)
                }
                is Token.Blank -> {
                    if (isNotEmpty() && last() != '\n') append(' ')
                    append(token.slot.filler)
                }
            }
        }
    }
}
