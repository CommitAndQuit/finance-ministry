package `in`.txnsense.app.parser.induction

import `in`.txnsense.app.core.model.IncomingSms
import `in`.txnsense.app.core.model.ParseDecision
import `in`.txnsense.app.parser.ParserRules
import `in`.txnsense.app.parser.engine.TemplateEngineParser

/** Whether a candidate layout may be offered to the engine. */
sealed interface Verdict {
    data object Accepted : Verdict
    data class Rejected(val reason: String) : Verdict
}

/**
 * The gate every induced layout has to pass.
 *
 * Induction is allowed to be wrong; it is not allowed to be wrong quietly. A candidate is replayed
 * against rendered versions of the very messages it came from, against a shipped list of messages it
 * must not match, and against the curated engine, and it is only accepted if all three agree. The
 * checks are ordered cheapest-first so a bad candidate is discarded before the expensive replays.
 *
 * Every string this class matches against is synthetic: rendered skeletons and [NegativeCorpus].
 * Validation never reads a message the user received.
 */
class CandidateValidator(
    private val negatives: List<String> = NegativeCorpus.bodies,
    /** Distinct messages a layout must be seen in before it is worth learning. */
    private val minSamples: Int = 3,
    /** Invariant characters a layout needs to be specific enough to trust. */
    private val minLiteralChars: Int = 12,
    /** Invariant words and breaks a layout needs for the same reason. */
    private val minAnchors: Int = 3,
    private val curated: TemplateEngineParser = TemplateEngineParser(),
) {

    fun verdict(candidate: InducedTemplate, cluster: List<Skeleton>): Verdict {
        if (candidate.sampleCount < minSamples) return Verdict.Rejected("seen in only ${candidate.sampleCount} messages")
        if (candidate.anchorCount < minAnchors) return Verdict.Rejected("only ${candidate.anchorCount} invariant words")
        if (candidate.literalChars < minLiteralChars) return Verdict.Rejected("layout too generic")

        val violations = RegexSafety.violations(candidate.regexPattern)
        if (violations.isNotEmpty()) return Verdict.Rejected("unsafe pattern: ${violations.first()}")
        val regex = RegexSafety.compile(candidate.regexPattern) ?: return Verdict.Rejected("pattern does not compile")

        // Nothing that already works may be relearned: a curated answer is always the better one.
        val rendered = cluster.map(Skeleton::render)
        rendered.firstOrNull { curated.parse(sms(candidate.bankId, it)).decision != ParseDecision.Reject }
            ?.let { return Verdict.Rejected("a curated template already reads this layout") }

        // The pattern has to read back what it was grown from, amount included. A layout that cannot
        // recover its own samples would only produce records nobody can check.
        val engine = TemplateEngineParser(listOf(candidate.toParsingTemplate(ParseDecision.Record)))
        rendered.forEachIndexed { index, body ->
            val parsed = engine.parse(sms(candidate.bankId, body))
            if (parsed.ruleId != candidate.templateId) return Verdict.Rejected("does not match its own sample $index")
            if (parsed.amountMinor != RENDERED_AMOUNT_MINOR) {
                return Verdict.Rejected("reads the wrong amount from sample $index")
            }
            if (parsed.direction != candidate.semantics.direction) {
                return Verdict.Rejected("reads sample $index in the wrong direction")
            }
        }

        // The decisive check: a pattern that also fits a password, a promotion or a bill would invent
        // transactions, and no amount of matching its own samples makes up for that.
        negatives.firstOrNull { RegexSafety.findWithin(regex, ParserRules.transactionText(it)) != null }
            ?.let { return Verdict.Rejected("also matches a message that is not a transaction") }

        return Verdict.Accepted
    }

    /** Validation input. The bank id stands in for the sender, which is all the engine reads it for. */
    private fun sms(bankId: String, body: String) = IncomingSms(bankId, 0L, body)

    companion object {
        /** What [Slot.Money]'s filler is worth, in minor units. */
        const val RENDERED_AMOUNT_MINOR = 4200L
    }
}
