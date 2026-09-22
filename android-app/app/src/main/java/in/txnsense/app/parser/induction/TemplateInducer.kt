package `in`.txnsense.app.parser.induction

/** What became of one cluster of skeletons. */
sealed interface Outcome {
    data class Learned(val template: InducedTemplate) : Outcome
    /** A cluster that did not become a template, and why. Nothing is learned silently. */
    data class Skipped(val bankId: String, val sampleCount: Int, val reason: String) : Outcome
}

/**
 * Derives parsing templates from messages the curated parser could not read.
 *
 * The pipeline is: cluster messages that share a layout, align them to recover the invariant spine,
 * decide which variable positions are the fields worth capturing, write a bounded pattern, ask what
 * the layout means, and put the result through [CandidateValidator]. Only the last two steps involve
 * any judgement; the rest is structural, which is why this works with no model on the device.
 *
 * Swap [labeller] for a language-model implementation to improve the judgement step. It is handed
 * only the layout's vocabulary words, never a message.
 */
class TemplateInducer(
    private val labeller: SemanticLabeller = HeuristicLabeller(),
    private val validator: CandidateValidator = CandidateValidator(),
    private val minOverlap: Double = SkeletonClusterer.DEFAULT_MIN_OVERLAP,
) {

    /** The layouts worth offering to the engine. */
    fun induce(skeletons: List<Skeleton>): List<InducedTemplate> =
        outcomes(skeletons).filterIsInstance<Outcome.Learned>().map(Outcome.Learned::template)

    /** Every cluster with what happened to it, so a run can be explained rather than just trusted. */
    fun outcomes(skeletons: List<Skeleton>): List<Outcome> =
        SkeletonClusterer.cluster(skeletons, minOverlap).map(::consider)

    private fun consider(cluster: List<Skeleton>): Outcome {
        val bankId = cluster.first().bankId
        fun skip(reason: String) = Outcome.Skipped(bankId, cluster.size, reason)

        val alignment = SpineAligner.align(cluster) ?: return skip("no layout shared by every message")
        val roles = SlotRoleAssigner.assign(alignment) ?: return skip("no amount that can be read safely")
        val semantics = labeller.label(alignment.words) ?: return skip("not a settled money movement")
        val pattern = PatternEmitter.emit(alignment, roles)
        val candidate = InducedTemplate(
            templateId = InducedTemplate.identify(bankId, pattern),
            bankId = bankId,
            regexPattern = pattern,
            semantics = semantics,
            sampleCount = cluster.size,
            literalChars = alignment.literalChars,
            anchorCount = alignment.spine.size,
        )
        return when (val verdict = validator.verdict(candidate, cluster)) {
            is Verdict.Accepted -> Outcome.Learned(candidate)
            is Verdict.Rejected -> skip(verdict.reason)
        }
    }
}
