package `in`.txnsense.app.parser.induction

/** Which aligned positions carry the fields the engine reads back, by index into [Alignment.slots]. */
data class SlotRoles(val amount: Int, val account: Int?, val merchant: Int?)

/**
 * Decides which variable positions of a layout are worth capturing.
 *
 * The amount is the one field a recorded transaction cannot do without, so a layout with no
 * unambiguous amount is not learnable and [assign] refuses it. An amount introduced by a balance or
 * limit word is the wrong number — "Avl Bal Rs.900" is not what was spent — so those positions are
 * passed over in favour of a later one.
 */
object SlotRoleAssigner {

    fun assign(alignment: Alignment): SlotRoles? {
        val amount = alignment.slots.indices.firstOrNull { index ->
            alignment.slots[index] == GapShape.Typed(Slot.Money) &&
                alignment.wordsBefore(index, 2).none { it in BankVocabulary.balanceMarkers }
        } ?: return null
        val account = alignment.slots.indices.firstOrNull { alignment.slots[it] == GapShape.Typed(Slot.Account) }
        val merchant = alignment.slots.indices.firstOrNull { index ->
            val shape = alignment.slots[index]
            val namesSomeone = shape == GapShape.Typed(Slot.Word) || shape is GapShape.Free
            namesSomeone && alignment.wordsBefore(index, 1).any { it in BankVocabulary.counterpartyLeadIns }
        }
        return SlotRoles(amount, account, merchant)
    }
}

/**
 * Writes the regular expression for an aligned layout.
 *
 * Every fragment here has an upper bound on how much it can consume, which is what lets
 * [RegexSafety] certify the finished pattern as backtracking-safe. The fragments mirror the shapes
 * the curated templates use, so a learned pattern reads like a hand-written one and fills the same
 * `amount`/`account`/`merchant` groups the engine already knows how to interpret.
 */
object PatternEmitter {

    private const val CURRENCY = """(?:rs\.?|inr|₹)"""
    private const val DIGITS = """[\d,]{1,16}(?:\.\d{1,2})?"""
    private const val DATE = """(?:\d{4}-\d{2}-\d{2}|\d{1,2}[-/](?:\d{1,2}|[a-z]{3})[-/]\d{2,4})(?!\d)"""
    private const val TIME = """\d{1,2}:\d{2}(?::\d{2})?"""

    /** Punctuation and whitespace the tokenizer skipped, allowed back between anchors. */
    private const val SEPARATOR = """[\s.,;:!?*()\[\]&'"#\-]{0,6}"""

    /** A line break, with the indentation banks put around it. */
    private const val BREAK = """[ \t]{0,4}\r?\n[ \t]{0,4}"""

    private val META: Set<Char> = setOf('\\', '^', '$', '.', '|', '?', '*', '+', '(', ')', '[', ']', '{', '}')

    /** Narrowest upper bound an unresolved gap is given, however short the samples were. */
    private const val MIN_FREE_WIDTH = 40

    fun emit(alignment: Alignment, roles: SlotRoles): String {
        val pattern = StringBuilder()
        val tail = alignment.slots.lastIndex
        var separatorPending = false

        /** [absorbing] fragments already tolerate adjacent whitespace, so they need no separator. */
        fun add(fragment: String, absorbing: Boolean = false) {
            if (separatorPending && !absorbing) pattern.append(SEPARATOR)
            pattern.append(fragment)
            separatorPending = !absorbing
        }

        // The leading position is the only optional one: the engine searches rather than matching
        // from position zero, so a variable prefix can simply be left out. When nothing is left out,
        // the pattern is anchored, which stops the layout matching deep inside another message.
        val head = alignment.slots.first()
        val headFragment = fragment(head, 0, roles, required = false)
        if (head == GapShape.Empty || headFragment != null) add("^", absorbing = true)
        headFragment?.let { add(it.first, it.second) }

        alignment.spine.indices.forEach { index ->
            when (val anchor = alignment.spine[index]) {
                is Token.Break -> add(BREAK, absorbing = true)
                is Token.Literal -> add(escape(anchor.word))
                is Token.Blank -> Unit // Blanks are never anchors.
            }
            // The trailing position is droppable for the same reason as the leading one; everything
            // between two anchors has to be expressed or the pattern cannot match its own samples.
            val slot = index + 1
            fragment(alignment.slots[slot], slot, roles, required = slot != tail)?.let { add(it.first, it.second) }
        }
        return pattern.toString()
    }

    /** The fragment for one slot, or null when the position contributes nothing to the pattern. */
    private fun fragment(shape: GapShape, index: Int, roles: SlotRoles, required: Boolean): Pair<String, Boolean>? {
        val role = when (index) {
            roles.amount -> "amount"
            roles.account -> "account"
            roles.merchant -> "merchant"
            else -> null
        }
        if (shape == GapShape.Empty) return null
        if (role == null && !required) return null
        return when (shape) {
            is GapShape.Empty -> null
            is GapShape.Typed -> when (shape.slot) {
                Slot.Money -> "$CURRENCY\\s{0,3}${group(role, DIGITS)}" to false
                Slot.Account -> "[x*•]{0,6}${group(role, """\d{3,4}""")}(?!\\d)" to false
                Slot.Date -> group(role, DATE) to false
                Slot.Time -> group(role, TIME) to false
                Slot.Number -> group(role, """\d{1,18}""") to false
                Slot.Url -> group(role, """\S{1,200}?""") to false
                Slot.Word -> group(role, """\S{1,40}?""") to false
            }
            // Observed widths are evidence, not a rule: three messages say little about how long the
            // next merchant name will be. The bound is widened well past what was seen, because it
            // exists to keep matching cheap, while precision comes from the surrounding literals.
            is GapShape.Free -> {
                val lower = if (role == null) 0 else 1
                val upper = (shape.maxWidth * 2).coerceIn(MIN_FREE_WIDTH, SpineAligner.MAX_GAP_WIDTH)
                group(role, """[^\r\n]{$lower,$upper}?""") to true
            }
        }
    }

    /** Wraps [body] in a named group, or leaves it uncaptured when the position has no role. */
    private fun group(name: String?, body: String): String =
        if (name == null) "(?:$body)" else "(?<$name>$body)"

    private fun escape(word: String): String = buildString {
        word.forEach { character ->
            if (character in META) append('\\')
            append(character)
        }
    }
}
