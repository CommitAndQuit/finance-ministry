package `in`.txnsense.app.data

import `in`.txnsense.app.core.model.Direction
import `in`.txnsense.app.core.model.IncomingSms
import `in`.txnsense.app.core.model.ParseAssessment
import `in`.txnsense.app.core.model.ParseDecision
import `in`.txnsense.app.parser.engine.ParsingTemplate
import `in`.txnsense.app.parser.engine.TemplateEngineParser
import `in`.txnsense.app.parser.induction.InducedTemplate
import `in`.txnsense.app.parser.induction.Outcome
import `in`.txnsense.app.parser.induction.Skeleton
import `in`.txnsense.app.parser.induction.SkeletonCodec
import `in`.txnsense.app.parser.induction.Skeletonizer
import `in`.txnsense.app.parser.induction.TemplateInducer

/** What one induction run examined and what came of it, so a run can be explained rather than trusted. */
data class InductionReport(
    val layoutsExamined: Int,
    val learned: List<String>,
    val skipped: List<Outcome.Skipped>,
)

/** The state of learning, for the settings screen. */
data class LearningSummary(
    val layoutsRemembered: Int,
    val onProbation: Int,
    val inUse: Int,
    val retired: Int,
)

/**
 * The lifecycle of a learned template, from an unread message to a template that may book a review.
 *
 * Nothing here decides a transaction on its own. A layout goes through four stages, and each one can
 * only ever be reached by evidence:
 *
 * 1. **Remembered.** A message no template read is reduced to a skeleton and counted. The message
 *    itself is not stored, and could not be reconstructed from what is.
 * 2. **Induced.** When a layout has been seen enough distinct times, the inducer writes a pattern for
 *    it and [`in`.txnsense.app.parser.induction.CandidateValidator] decides whether it may exist at all.
 *    A surviving pattern starts in [LearnedState.Shadow], where it is matched against live messages and
 *    books nothing.
 * 3. **Promoted.** After [MIN_SHADOW_AGREEMENTS] live messages it read cleanly, it becomes
 *    [LearnedState.Active] and may create transactions — always as `NeedsReview`, never recorded
 *    outright, so a person sees every one of them before it counts towards a total.
 * 4. **Retired.** If it ever reads a message differently from the curated template that owns that
 *    message, it is retired on the spot and never relearned. One contradiction is enough: the curated
 *    answer is the authority, and a learned pattern that argues with it is wrong by definition.
 *
 * The DAO is an interface on purpose — every rule above is exercised in unit tests against a fake.
 */
class TemplateLearning(
    private val dao: LearningDao,
    private val skeletonizer: Skeletonizer = Skeletonizer(),
    private val inducer: TemplateInducer = TemplateInducer(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Rebuilt lazily; every state change drops them so the engine never runs a stale set. */
    @Volatile private var activeCache: List<ParsingTemplate>? = null
    @Volatile private var shadowParser: TemplateEngineParser? = null
    @Volatile private var auditParser: TemplateEngineParser? = null

    /**
     * Records the layout of a message no template could read.
     *
     * This is the only path by which anything derived from a message body reaches storage, and what it
     * stores is a skeleton: vocabulary words plus typed blanks.
     */
    fun remember(sms: IncomingSms) {
        val skeleton = skeletonizer.skeletonize(sms)
        // Too little banking vocabulary to ever become a template — the validator would refuse it for
        // having too few anchors — so remembering it would only crowd out layouts that could.
        if (skeleton.words.size < MIN_VOCABULARY_WORDS) return
        val encoded = SkeletonCodec.encode(skeleton)
        val key = SkeletonCodec.key(skeleton.bankId, encoded)
        val now = clock()
        if (dao.addSighting(SkeletonSightingEntity(key, skeleton.bankId, encoded, 1, now, now)) == -1L) {
            dao.touchSighting(key, now)
        }
    }

    /**
     * Runs the probationary layouts against a message the curated templates declined, and promotes one
     * that has now read enough live messages cleanly. Returns the template that fired, if any.
     *
     * Nothing is booked from this. The point of shadow mode is that a pattern proves itself on real
     * traffic while its mistakes cost nothing.
     */
    fun shadowEvaluate(sms: IncomingSms): String? {
        val shadow = shadowParser ?: TemplateEngineParser(templates(LearnedState.Shadow)).also { shadowParser = it }
        val parsed = shadow.parseLearned(sms)
        if (parsed.decision == ParseDecision.Reject) return null
        // Only a clean read is evidence. A pattern that matches but cannot produce an amount, or cannot
        // say which way the money went, would graduate into booking rows nobody can check.
        if (parsed.amountMinor == null || parsed.direction == Direction.Unknown) return null
        val templateId = parsed.ruleId
        dao.recordAgreement(templateId, clock())
        val row = dao.template(templateId) ?: return templateId
        if (row.state == LearnedState.Shadow.name && row.agreements >= MIN_SHADOW_AGREEMENTS) {
            dao.promote(templateId, clock())
            invalidate()
        }
        return templateId
    }

    /**
     * Checks the learned layouts against a message a curated template just read, and retires any that
     * would have read it differently. Returns the template that was retired, if any.
     *
     * This is the check the shipped negative corpus cannot do, because it catches the layouts nobody
     * thought to write down: real traffic where there is a right answer to compare against.
     */
    fun auditAgainstCurated(sms: IncomingSms, curated: ParseAssessment): String? {
        val audit = auditParser
            ?: TemplateEngineParser(templates(LearnedState.Shadow) + templates(LearnedState.Active))
                .also { auditParser = it }
        val learned = audit.parseLearned(sms)
        if (learned.decision == ParseDecision.Reject) return null
        if (learned.amountMinor == curated.amountMinor && learned.direction == curated.direction) return null
        dao.retire(learned.ruleId, clock())
        invalidate()
        return learned.ruleId
    }

    /** The layouts the engine may use to create transactions. Always for review, never recorded outright. */
    fun activeTemplates(): List<ParsingTemplate> =
        activeCache ?: templates(LearnedState.Active).also { activeCache = it }

    /**
     * Derives new layouts from everything remembered so far.
     *
     * Deliberately the whole corpus every time rather than only what is new: a layout becomes learnable
     * the moment its third distinct sighting arrives, and which run that happens on does not matter.
     */
    fun induce(): InductionReport {
        val rows = dao.sightings(MAX_CORPUS_ROWS)
        val skeletons: List<Skeleton> = rows.mapNotNull { SkeletonCodec.decode(it.bankId, it.encoded) }
        val known = dao.allTemplates()
        val liveNames = known.map(LearnedTemplateEntity::templateId).toSet()
        var room = (MAX_LIVE_TEMPLATES - known.count { it.state != LearnedState.Retired.name }).coerceAtLeast(0)
        val outcomes = inducer.outcomes(skeletons)
        val added = mutableListOf<String>()
        val now = clock()
        for (outcome in outcomes.filterIsInstance<Outcome.Learned>()) {
            if (room == 0) break
            // A template already on file is left exactly as it is. That is what makes retirement
            // permanent: the same layout induced again finds its own retired row and stops.
            if (outcome.template.templateId in liveNames) continue
            if (dao.addTemplate(row(outcome.template, now)) != -1L) {
                added += outcome.template.templateId
                room--
            }
        }
        if (added.isNotEmpty()) invalidate()
        return InductionReport(skeletons.size, added, outcomes.filterIsInstance<Outcome.Skipped>())
    }

    /** Drops layouts nothing has matched in a long time, and caps the corpus however long capture runs. */
    fun prune() {
        dao.expireSightings(clock() - CORPUS_TTL_MILLIS)
        dao.trimSightings(MAX_CORPUS_ROWS)
    }

    fun summary(): LearningSummary {
        val templates = dao.allTemplates()
        return LearningSummary(
            layoutsRemembered = dao.sightingCount(),
            onProbation = templates.count { it.state == LearnedState.Shadow.name },
            inUse = templates.count { it.state == LearnedState.Active.name },
            retired = templates.count { it.state == LearnedState.Retired.name },
        )
    }

    fun learnedTemplates(): List<LearnedTemplateEntity> = dao.allTemplates()

    /** Removes one learned layout outright, for a user who does not want it. */
    fun forget(templateId: String) {
        dao.forget(templateId)
        invalidate()
    }

    /** Drops everything learning holds, leaving the curated parser exactly as it was. */
    fun clear() {
        dao.clearSightings()
        dao.clearTemplates()
        invalidate()
    }

    private fun templates(state: LearnedState): List<ParsingTemplate> = dao.templates(state.name)
        .take(MAX_LIVE_TEMPLATES)
        .mapNotNull(::parsingTemplate)

    /**
     * A stored row as the engine sees it. Null when the stored pattern no longer compiles or no longer
     * passes the safety rules — a row written by an older version of the emitter is dropped rather than
     * trusted, because nothing revalidates it on the way out.
     */
    private fun parsingTemplate(row: LearnedTemplateEntity): ParsingTemplate? {
        if (`in`.txnsense.app.parser.induction.RegexSafety.violations(row.regexPattern).isNotEmpty()) return null
        return runCatching {
            ParsingTemplate(
                templateId = row.templateId,
                regexPattern = row.regexPattern,
                direction = enumValueOf<Direction>(row.direction),
                status = enumValueOf<`in`.txnsense.app.core.model.TransactionStatus>(row.status),
                channel = enumValueOf<`in`.txnsense.app.core.model.Channel>(row.channel),
                transactionType = enumValueOf<`in`.txnsense.app.core.model.TransactionType>(row.transactionType),
                // A learned layout never records outright, at any age. Its ceiling is a suggestion.
                decision = ParseDecision.NeedsReview,
                ruleId = row.templateId,
            )
        }.getOrNull()
    }

    private fun row(template: InducedTemplate, now: Long) = LearnedTemplateEntity(
        templateId = template.templateId,
        bankId = template.bankId,
        regexPattern = template.regexPattern,
        direction = template.semantics.direction.name,
        status = template.semantics.status.name,
        channel = template.semantics.channel.name,
        transactionType = template.semantics.transactionType.name,
        sampleCount = template.sampleCount,
        literalChars = template.literalChars,
        anchorCount = template.anchorCount,
        state = LearnedState.Shadow.name,
        createdAt = now,
    )

    private fun invalidate() {
        activeCache = null
        shadowParser = null
        auditParser = null
    }

    companion object {
        /** Live messages a probationary layout must read cleanly before it may book anything. */
        const val MIN_SHADOW_AGREEMENTS = 5

        /** Banking words a layout needs before it is worth remembering at all. */
        const val MIN_VOCABULARY_WORDS = 3

        /** Distinct layouts the corpus keeps. Sightings of one layout share a row, so this is generous. */
        const val MAX_CORPUS_ROWS = 400

        /**
         * Learned layouts that may be live at once. Each one is a regex the receiver runs against
         * messages no curated template claimed, so the set has to stay small enough to be free.
         */
        const val MAX_LIVE_TEMPLATES = 24

        /** How long an unseen layout is kept. A bank that changed its format should stop being learnt. */
        const val CORPUS_TTL_MILLIS = 90L * 24 * 60 * 60 * 1000
    }
}
