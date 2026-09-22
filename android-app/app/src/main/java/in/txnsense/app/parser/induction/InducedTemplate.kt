package `in`.txnsense.app.parser.induction

import `in`.txnsense.app.core.model.ParseDecision
import `in`.txnsense.app.parser.engine.ParsingTemplate
import java.security.MessageDigest
import java.util.Locale

/**
 * A layout the app worked out for itself, ready to be offered to the engine.
 *
 * A learned template is not trusted the way a curated one is. It is tried only after every curated
 * template, and by default it asks for review rather than recording, so its first effect on a ledger
 * is a suggestion a person can reject.
 */
data class InducedTemplate(
    val templateId: String,
    val bankId: String,
    val regexPattern: String,
    val semantics: TemplateSemantics,
    /** Distinct messages the layout was recovered from. */
    val sampleCount: Int,
    /** Invariant characters in the layout — how specific the pattern is. */
    val literalChars: Int,
    /** Invariant words and line breaks in the layout. */
    val anchorCount: Int,
) {
    /**
     * The engine's view of this layout.
     *
     * [decision] is what the template does when it matches: [ParseDecision.NeedsReview] while the
     * layout is still on probation, [ParseDecision.Record] once it has earned its place.
     */
    fun toParsingTemplate(decision: ParseDecision = ParseDecision.NeedsReview): ParsingTemplate =
        ParsingTemplate(
            templateId = templateId,
            regexPattern = regexPattern,
            direction = semantics.direction,
            status = semantics.status,
            channel = semantics.channel,
            transactionType = semantics.transactionType,
            decision = decision,
            ruleId = templateId,
        )

    companion object {
        /**
         * A name derived from the pattern itself, so the same layout learned twice — on a re-scan, or
         * after an erase — is the same template rather than a duplicate.
         */
        fun identify(bankId: String, regexPattern: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(regexPattern.toByteArray())
            val suffix = digest.take(4).joinToString("") { "%02x".format(it) }
            return "learned_${bankId.lowercase(Locale.ROOT)}_$suffix"
        }
    }
}
