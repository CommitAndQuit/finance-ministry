package `in`.txnsense.app.parser.induction

/**
 * Guards on machine-written patterns.
 *
 * Learned patterns run in the SMS broadcast path, so a pathological one does not merely return a
 * wrong answer — it stalls message delivery. Two independent protections apply:
 *
 * 1. **Bounded by construction.** [PatternEmitter] only ever emits repetitions with an upper bound,
 *    and [violations] verifies that property on the finished string. A pattern with no unbounded
 *    repetition and no quantified group cannot backtrack catastrophically.
 * 2. **Bounded in practice.** [findWithin] enforces a wall-clock budget while matching, so even a
 *    pattern that slipped through the first check cannot run away.
 */
object RegexSafety {

    const val MAX_PATTERN_LENGTH = 1200

    /** Default matching budget for one learned pattern against one message. */
    const val DEFAULT_BUDGET_MILLIS = 50L

    /** Everything wrong with [pattern]; empty means it is safe to compile and run. */
    fun violations(pattern: String): List<String> {
        val issues = mutableListOf<String>()
        if (pattern.isEmpty()) return listOf("pattern is empty")
        if (pattern.length > MAX_PATTERN_LENGTH) issues += "longer than $MAX_PATTERN_LENGTH characters"

        var index = 0
        var inClass = false
        while (index < pattern.length) {
            val character = pattern[index]
            when {
                character == '\\' -> index++
                inClass -> if (character == ']') inClass = false
                character == '[' -> inClass = true
                character == '*' || character == '+' -> issues += "unbounded repetition '$character' at $index"
                character == ')' && pattern.getOrNull(index + 1)?.let { it == '{' || it == '*' || it == '+' } == true ->
                    issues += "repeated group at $index"
                character == '{' -> {
                    val close = pattern.indexOf('}', index)
                    if (close < 0) issues += "unterminated repetition at $index"
                    else {
                        if (pattern.substring(index + 1, close).endsWith(",")) {
                            issues += "open-ended repetition at $index"
                        }
                        index = close
                    }
                }
            }
            index++
        }
        if (inClass) issues += "unterminated character class"
        runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }
            .onFailure { issues += "does not compile: ${it.message?.take(80)}" }
        return issues
    }

    /** Compiles [pattern] when it is safe, or returns null. */
    fun compile(pattern: String): Regex? =
        if (violations(pattern).isEmpty()) Regex(pattern, RegexOption.IGNORE_CASE) else null

    /**
     * Finds [regex] in [text], abandoning the attempt once [budgetMillis] has elapsed.
     *
     * The deadline is enforced by handing the matcher a character sequence that refuses to be read
     * once time is up, which is the only way to interrupt `java.util.regex` mid-match. A timed-out
     * match is reported as "no match", never as an error: a slow learned pattern must not stop the
     * curated ones from being tried.
     */
    fun findWithin(regex: Regex, text: String, budgetMillis: Long = DEFAULT_BUDGET_MILLIS): MatchResult? =
        try {
            regex.find(Deadline(text, System.nanoTime() + budgetMillis * 1_000_000))
        } catch (_: BudgetExhausted) {
            null
        }

    private class BudgetExhausted : RuntimeException(null, null, false, false)

    private class Deadline(private val delegate: CharSequence, private val deadlineNanos: Long) : CharSequence {
        override val length: Int get() = delegate.length

        override fun get(index: Int): Char {
            if (System.nanoTime() > deadlineNanos) throw BudgetExhausted()
            return delegate[index]
        }

        /** Group values are read after the match settles, so slices are handed over unguarded. */
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            delegate.subSequence(startIndex, endIndex)

        override fun toString(): String = delegate.toString()
    }
}
