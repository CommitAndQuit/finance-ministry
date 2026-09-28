package `in`.txnsense.app.parser.induction

import java.security.MessageDigest
import java.util.Locale

/**
 * The stored form of a [Skeleton].
 *
 * A skeleton is the only shape of a received message the app may keep, so its encoding is designed to
 * be read by a person: `rs spent on card #ACCOUNT:6 at #WORD:11 on #DATE:10`. Anyone inspecting the
 * database — or a reviewer reading this file — can see at a glance that what is stored is a layout and
 * not a message. Blank widths are kept because they are what tells the aligner how wide a field ran,
 * and they say nothing about the value that was there.
 *
 * Vocabulary words can never begin with `#` (see [Skeletonizer]'s word pattern), so the marker prefix
 * is unambiguous and decoding needs no escaping.
 */
object SkeletonCodec {

    private const val BREAK = "#NL"
    private const val MARKER = '#'

    fun encode(skeleton: Skeleton): String = skeleton.tokens.joinToString(" ") { token ->
        when (token) {
            is Token.Break -> BREAK
            is Token.Literal -> token.word
            is Token.Blank -> "$MARKER${token.slot.name.uppercase(Locale.ROOT)}:${token.width}"
        }
    }

    /** Null when [encoded] is not something [encode] produced, so a corrupt row is skipped, not guessed at. */
    fun decode(bankId: String, encoded: String): Skeleton? {
        val tokens = encoded.split(' ').filter(String::isNotEmpty).map { piece ->
            when {
                piece == BREAK -> Token.Break
                piece.firstOrNull() == MARKER -> blank(piece) ?: return null
                else -> Token.Literal(piece)
            }
        }
        return if (tokens.isEmpty()) null else Skeleton(bankId, tokens)
    }

    private fun blank(piece: String): Token.Blank? {
        val name = piece.substringAfter(MARKER).substringBefore(':')
        val width = piece.substringAfter(':', "").toIntOrNull() ?: return null
        if (width < 0 || !piece.contains(':')) return null
        val slot = Slot.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
        return Token.Blank(slot, width)
    }

    /**
     * A stable identity for one layout.
     *
     * Repeated sightings of a layout collapse onto the same key, so the corpus grows with the number of
     * *distinct* layouts rather than the number of messages received. Two messages of one layout still
     * differ whenever their fields were different lengths, which is exactly the variation the aligner
     * needs, so collapsing the identical ones costs no evidence.
     */
    fun key(bankId: String, encoded: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$bankId\u0000$encoded".toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
