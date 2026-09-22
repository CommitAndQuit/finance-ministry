package `in`.txnsense.app.parser.induction

/** What the samples had in one variable position of an aligned layout. */
sealed interface GapShape {
    /** Every sample had nothing here. */
    data object Empty : GapShape

    /** Every sample had exactly one blank, of the same kind. This is a capturable field. */
    data class Typed(val slot: Slot) : GapShape

    /** The samples disagreed. Expressible only as a bounded run of characters. */
    data class Free(val minWidth: Int, val maxWidth: Int) : GapShape
}

/**
 * A layout recovered from several messages: the parts they all share, and what varied between them.
 *
 * [spine] holds the anchors (vocabulary words and line breaks) common to every sample, in order.
 * [slots] holds one [GapShape] per position around them — `spine.size + 1` of them, the first before
 * the first anchor and the last after the last one.
 */
data class Alignment(
    val bankId: String,
    val spine: List<Token>,
    val slots: List<GapShape>,
    val sampleCount: Int,
) {
    /** The vocabulary words of the spine, in order — everything the semantic labeller gets to see. */
    val words: List<String> = spine.filterIsInstance<Token.Literal>().map(Token.Literal::word)

    /** Total literal characters. A spine too short to be specific is not worth learning. */
    val literalChars: Int = words.sumOf(String::length)

    /** Vocabulary words immediately before slot [index], nearest first. */
    fun wordsBefore(index: Int, count: Int = 3): List<String> =
        spine.take(index).filterIsInstance<Token.Literal>().takeLast(count).reversed().map(Token.Literal::word)

    /** Vocabulary words immediately after slot [index], nearest first. */
    fun wordsAfter(index: Int, count: Int = 3): List<String> =
        spine.drop(index).filterIsInstance<Token.Literal>().take(count).map(Token.Literal::word)
}

/**
 * Recovers the shared layout of a cluster of skeletons.
 *
 * The spine is the longest common subsequence of the samples' anchors, folded pairwise across the
 * cluster. Everything the samples did *not* share falls into the gaps between spine anchors, and a
 * gap that held the same kind of blank in every sample is a field the emitted pattern can capture.
 *
 * No language model is involved: a bank layout is by definition the invariant text around variable
 * values, so aligning several instances of it recovers the layout directly.
 */
object SpineAligner {

    /** Widest run of characters an unresolved gap may stand for. */
    const val MAX_GAP_WIDTH = 80

    fun align(cluster: List<Skeleton>): Alignment? {
        if (cluster.isEmpty()) return null
        val spine = cluster.map(Skeleton::anchors).reduce(::longestCommonSubsequence)
        if (spine.isEmpty()) return null
        val sliced = cluster.map { slice(it.tokens, spine) ?: return null }
        val slots = (0..spine.size).map { position -> resolve(sliced.map { it[position] }) ?: return null }
        return Alignment(cluster.first().bankId, spine, slots, cluster.size)
    }

    /** Splits [tokens] around the [spine] anchors, yielding `spine.size + 1` slices. */
    private fun slice(tokens: List<Token>, spine: List<Token>): List<List<Token>>? {
        val slices = mutableListOf<List<Token>>()
        var cursor = 0
        for (anchor in spine) {
            val offset = tokens.subList(cursor, tokens.size).indexOf(anchor)
            if (offset < 0) return null
            slices += tokens.subList(cursor, cursor + offset).toList()
            cursor += offset + 1
        }
        slices += tokens.subList(cursor, tokens.size).toList()
        return slices
    }

    /**
     * Collapses what every sample had in one position into a single shape. Returns null when the
     * position spanned a line break in some sample — a gap that may swallow newlines is too blunt to
     * learn from, so the whole cluster is abandoned rather than matched loosely.
     */
    private fun resolve(slices: List<List<Token>>): GapShape? {
        if (slices.all(List<Token>::isEmpty)) return GapShape.Empty
        val first = slices.first().singleOrNull()
        if (first is Token.Blank && slices.all { it.singleOrNull().let { only -> only is Token.Blank && only.slot == first.slot } }) {
            return GapShape.Typed(first.slot)
        }
        if (slices.any { slice -> slice.any { it is Token.Break } }) return null
        val widths = slices.map(::width)
        return GapShape.Free(widths.min(), widths.max().coerceIn(1, MAX_GAP_WIDTH))
    }

    /** Characters a slice stood for, counting one separator between its tokens. */
    private fun width(slice: List<Token>): Int = slice.sumOf {
        when (it) {
            is Token.Literal -> it.word.length + 1
            is Token.Blank -> it.width + 1
            is Token.Break -> 1
        }
    }

    private fun longestCommonSubsequence(left: List<Token>, right: List<Token>): List<Token> {
        val lengths = Array(left.size + 1) { IntArray(right.size + 1) }
        for (i in left.indices.reversed()) {
            for (j in right.indices.reversed()) {
                lengths[i][j] = if (left[i] == right[j]) lengths[i + 1][j + 1] + 1
                else maxOf(lengths[i + 1][j], lengths[i][j + 1])
            }
        }
        val common = mutableListOf<Token>()
        var i = 0
        var j = 0
        while (i < left.size && j < right.size) {
            when {
                left[i] == right[j] -> { common += left[i]; i++; j++ }
                lengths[i + 1][j] >= lengths[i][j + 1] -> i++
                else -> j++
            }
        }
        return common
    }
}
