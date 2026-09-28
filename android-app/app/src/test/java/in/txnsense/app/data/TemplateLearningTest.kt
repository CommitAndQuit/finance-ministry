package `in`.txnsense.app.data

import `in`.txnsense.app.core.model.Channel
import `in`.txnsense.app.core.model.Direction
import `in`.txnsense.app.core.model.IncomingSms
import `in`.txnsense.app.core.model.ParseAssessment
import `in`.txnsense.app.core.model.ParseDecision
import `in`.txnsense.app.core.model.TransactionStatus
import `in`.txnsense.app.core.model.TransactionType
import `in`.txnsense.app.parser.engine.TemplateEngineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lifecycle of a learned template, against an in-memory DAO.
 *
 * These tests are the safety argument for the whole feature, so they are written as claims about what
 * the app is *not* allowed to do: not keep a message, not use a pattern it has not watched, not record
 * anything without a person, and not bring back a pattern that was caught being wrong.
 *
 * The layout below is invented, as is the institution that supposedly sends it. No curated template
 * reads it, which is the point — everything the parser gains here it derived from the messages.
 */
class TemplateLearningTest {

    private val sender = "AD-ZZQQPAY-S"

    private val sightings = listOf(
        "ZZQQ Pay: Rs.150.75 spent via QuickSettle from a/c XX4321 towards COFFEEWORKS on 05-03-2026. Ref 887766.",
        "ZZQQ Pay: Rs.20 spent via QuickSettle from a/c XX4321 towards BOOKSELLER on 06-03-2026. Ref 887767.",
        "ZZQQ Pay: Rs.1,250.00 spent via QuickSettle from a/c XX9876 towards METRO TOPUP on 07-03-2026. Ref 887768.",
    )

    private val unseen =
        "ZZQQ Pay: Rs.99.50 spent via QuickSettle from a/c XX1111 towards TEASTALL on 08-03-2026. Ref 887770."

    private val dao = FakeLearningDao()
    private var now = 1_700_000_000_000L
    private val learning = TemplateLearning(dao, clock = { now })

    private fun sms(body: String) = IncomingSms(sender, now, body)

    /** Remembers the fixture and runs one induction pass, as a night on a charging phone would. */
    private fun learnFixture(): InductionReport {
        sightings.forEach { learning.remember(sms(it)) }
        return learning.induce()
    }

    // --- Remembering ---

    @Test fun an_unreadable_message_is_remembered_as_a_layout_and_not_as_a_message() {
        learning.remember(sms(sightings.first()))

        val row = dao.sightings.values.single()
        assertEquals(1, row.sightings)
        listOf("coffeeworks", "150", "4321", "887766").forEach {
            assertTrue("$it leaked into ${row.encoded}", !row.encoded.contains(it))
        }
    }

    @Test fun seeing_one_layout_again_counts_it_rather_than_storing_it_twice() {
        learning.remember(sms(sightings.first()))
        now += 1_000
        // Same layout with the same field widths — a different message, but nothing new to learn from.
        learning.remember(
            sms("ZZQQ Pay: Rs.999.99 spent via QuickSettle from a/c XX9876 towards BOOKSELLERS on 06-03-2026. Ref 887767."),
        )

        val row = dao.sightings.values.single()
        assertEquals(2, row.sightings)
        assertEquals(now, row.lastSeen)
        assertTrue(row.firstSeen < row.lastSeen)
    }

    @Test fun a_message_with_almost_no_banking_words_is_not_worth_remembering() {
        learning.remember(sms("hey are we still on for 7"))

        assertEquals(0, dao.sightings.size)
    }

    // --- Induction ---

    @Test fun a_layout_seen_three_times_becomes_a_template_on_trial() {
        val report = learnFixture()

        assertEquals(1, report.learned.size)
        val row = dao.templates.values.single()
        assertEquals(LearnedState.Shadow.name, row.state)
        assertEquals(Direction.Debit.name, row.direction)
        assertEquals(3, row.sampleCount)
    }

    @Test fun a_template_on_trial_is_not_given_to_the_parser() {
        learnFixture()

        assertEquals(emptyList<Any>(), learning.activeTemplates())
    }

    @Test fun inducing_again_does_not_duplicate_what_is_already_on_file() {
        learnFixture()

        val second = learning.induce()

        assertEquals(emptyList<String>(), second.learned)
        assertEquals(1, dao.templates.size)
    }

    @Test fun the_number_of_live_templates_is_capped() {
        repeat(TemplateLearning.MAX_LIVE_TEMPLATES + 3) { index ->
            dao.addTemplate(template("learned_filler_$index"))
        }
        sightings.forEach { learning.remember(sms(it)) }

        val report = learning.induce()

        assertEquals(emptyList<String>(), report.learned)
    }

    // --- Shadow mode and promotion ---

    @Test fun a_template_earns_its_promotion_one_live_message_at_a_time() {
        learnFixture()
        val templateId = dao.templates.keys.single()

        repeat(TemplateLearning.MIN_SHADOW_AGREEMENTS - 1) {
            assertEquals(templateId, learning.shadowEvaluate(sms(unseen)))
            assertEquals(LearnedState.Shadow.name, dao.templates.getValue(templateId).state)
        }
        learning.shadowEvaluate(sms(unseen))

        assertEquals(LearnedState.Active.name, dao.templates.getValue(templateId).state)
        assertEquals(now, dao.templates.getValue(templateId).promotedAt)
        assertEquals(1, learning.activeTemplates().size)
    }

    @Test fun a_message_no_learned_template_reads_earns_nothing() {
        learnFixture()

        assertNull(learning.shadowEvaluate(sms("Your OTP is 448190. Do not share it.")))
        assertEquals(0, dao.templates.values.single().agreements)
    }

    @Test fun a_promoted_template_only_ever_asks_for_review() {
        learnFixture()
        repeat(TemplateLearning.MIN_SHADOW_AGREEMENTS) { learning.shadowEvaluate(sms(unseen)) }

        val parsed = TemplateEngineParser(learning.activeTemplates()).parse(sms(unseen))

        // The whole point: a layout nobody wrote down can suggest a transaction but never record one.
        assertEquals(ParseDecision.NeedsReview, parsed.decision)
        assertEquals(9950L, parsed.amountMinor)
        assertEquals(Direction.Debit, parsed.direction)
    }

    @Test fun a_curated_template_keeps_answering_the_messages_it_already_reads() {
        learnFixture()
        repeat(TemplateLearning.MIN_SHADOW_AGREEMENTS) { learning.shadowEvaluate(sms(unseen)) }

        val parsed = TemplateEngineParser(learning.activeTemplates()).parse(sms("INR 250.50 debited from your account via UPI"))

        assertEquals("template_match", parsed.ruleId)
    }

    // --- Retirement ---

    @Test fun a_template_that_disagrees_with_the_curated_answer_is_retired_on_the_spot() {
        learnFixture()
        val templateId = dao.templates.keys.single()

        val retired = learning.auditAgainstCurated(sms(unseen), curated(amountMinor = 12345L))

        assertEquals(templateId, retired)
        assertEquals(LearnedState.Retired.name, dao.templates.getValue(templateId).state)
        assertEquals(1, dao.templates.getValue(templateId).contradictions)
        assertEquals(emptyList<Any>(), learning.activeTemplates())
    }

    @Test fun a_template_that_agrees_with_the_curated_answer_is_left_alone() {
        learnFixture()

        val retired = learning.auditAgainstCurated(sms(unseen), curated(amountMinor = 9950L))

        assertNull(retired)
        assertEquals(LearnedState.Shadow.name, dao.templates.values.single().state)
    }

    @Test fun a_promoted_template_can_still_be_retired() {
        learnFixture()
        repeat(TemplateLearning.MIN_SHADOW_AGREEMENTS) { learning.shadowEvaluate(sms(unseen)) }

        learning.auditAgainstCurated(sms(unseen), curated(direction = Direction.Credit))

        assertEquals(LearnedState.Retired.name, dao.templates.values.single().state)
    }

    @Test fun retirement_is_permanent_even_if_the_layout_keeps_arriving() {
        learnFixture()
        learning.auditAgainstCurated(sms(unseen), curated(amountMinor = 12345L))

        // The same layout, seen again for months, and reconsidered on every nightly run.
        repeat(20) { learning.remember(sms(unseen)) }
        val report = learning.induce()

        assertEquals(emptyList<String>(), report.learned)
        assertEquals(LearnedState.Retired.name, dao.templates.values.single().state)
        assertEquals(emptyList<Any>(), learning.activeTemplates())
    }

    // --- Housekeeping ---

    @Test fun a_layout_nothing_has_sent_in_months_is_forgotten() {
        learning.remember(sms(sightings.first()))
        now += TemplateLearning.CORPUS_TTL_MILLIS + 1
        learning.remember(sms("ZZQQ Pay: Rs.5 received via QuickSettle into a/c XX4321 from A on 09-03-2026."))

        learning.prune()

        assertEquals(1, dao.sightings.size)
    }

    @Test fun the_corpus_cannot_grow_without_bound() {
        repeat(TemplateLearning.MAX_CORPUS_ROWS + 25) { index ->
            now += 1_000
            learning.remember(sms("ZZQQ Pay: Rs.$index.50 spent via QuickSettle from a/c XX4321 towards M$index on 05-03-2026."))
        }

        learning.prune()

        assertTrue(dao.sightings.size.toString(), dao.sightings.size <= TemplateLearning.MAX_CORPUS_ROWS)
    }

    @Test fun the_settings_screen_can_say_where_everything_stands() {
        learnFixture()

        // Three sightings whose fields ran to different widths, which is three layouts and one rule.
        assertEquals(LearningSummary(layoutsRemembered = 3, onProbation = 1, inUse = 0, retired = 0), learning.summary())
    }

    @Test fun a_rule_the_user_does_not_want_is_removed_outright() {
        learnFixture()
        repeat(TemplateLearning.MIN_SHADOW_AGREEMENTS) { learning.shadowEvaluate(sms(unseen)) }

        learning.forget(dao.templates.keys.single())

        assertEquals(0, dao.templates.size)
        assertEquals(emptyList<Any>(), learning.activeTemplates())
    }

    @Test fun switching_learning_off_leaves_nothing_behind() {
        learnFixture()

        learning.clear()

        assertEquals(0, dao.sightings.size)
        assertEquals(0, dao.templates.size)
        assertEquals(LearningSummary(0, 0, 0, 0), learning.summary())
    }

    @Test fun a_stored_pattern_that_is_no_longer_safe_is_dropped_rather_than_run() {
        // A row as an older, less careful emitter might have written it: unbounded repetition.
        dao.addTemplate(template("learned_unsafe", pattern = """rs\.?\s*(?<amount>[\d,]+)\s+spent\s+(.*)+""")
            .copy(state = LearnedState.Active.name))

        assertEquals(emptyList<Any>(), learning.activeTemplates())
        assertNotNull(dao.template("learned_unsafe"))
    }

    private fun curated(amountMinor: Long = 9950L, direction: Direction = Direction.Debit) = ParseAssessment(
        decision = ParseDecision.Record,
        amountMinor = amountMinor,
        currency = "INR",
        direction = direction,
        status = TransactionStatus.Successful,
        channel = Channel.UPI,
        transactionType = TransactionType.MerchantPayment,
        maskedAccountHint = null,
        counterpartyLabel = null,
        confidence = 90,
        ruleId = "template_match",
    )

    private fun template(templateId: String, pattern: String = """rs\.?\s*(?<amount>\d{1,9})\s+paid""") =
        LearnedTemplateEntity(
            templateId = templateId,
            bankId = "UNKNOWN",
            regexPattern = pattern,
            direction = Direction.Debit.name,
            status = TransactionStatus.Successful.name,
            channel = Channel.Unknown.name,
            transactionType = TransactionType.Unknown.name,
            sampleCount = 3,
            literalChars = 20,
            anchorCount = 3,
            state = LearnedState.Shadow.name,
            createdAt = now,
        )
}

/**
 * An in-memory [LearningDao] with the same semantics as the Room one, including `IGNORE` inserts
 * returning -1 and the guards on promote and retire. The DAO is an interface precisely so this exists.
 */
private class FakeLearningDao : LearningDao {

    val sightings = linkedMapOf<String, SkeletonSightingEntity>()
    val templates = linkedMapOf<String, LearnedTemplateEntity>()

    override fun addSighting(row: SkeletonSightingEntity): Long {
        if (sightings.containsKey(row.layoutKey)) return -1L
        sightings[row.layoutKey] = row
        return sightings.size.toLong()
    }

    override fun touchSighting(layoutKey: String, at: Long) {
        sightings[layoutKey]?.let { sightings[layoutKey] = it.copy(sightings = it.sightings + 1, lastSeen = at) }
    }

    override fun sightings(limit: Int): List<SkeletonSightingEntity> =
        sightings.values.sortedByDescending { it.lastSeen }.take(limit)

    override fun sightingCount(): Int = sightings.size

    override fun expireSightings(before: Long): Int {
        val doomed = sightings.filterValues { it.lastSeen < before }.keys.toList()
        doomed.forEach { sightings.remove(it) }
        return doomed.size
    }

    override fun trimSightings(keep: Int): Int {
        val kept = sightings(keep).map { it.layoutKey }.toSet()
        val doomed = sightings.keys.filterNot { it in kept }
        doomed.forEach { sightings.remove(it) }
        return doomed.size
    }

    override fun clearSightings() = sightings.clear()

    override fun addTemplate(row: LearnedTemplateEntity): Long {
        if (templates.containsKey(row.templateId)) return -1L
        templates[row.templateId] = row
        return templates.size.toLong()
    }

    override fun templates(state: String): List<LearnedTemplateEntity> =
        templates.values.filter { it.state == state }.sortedBy { it.createdAt }

    override fun allTemplates(): List<LearnedTemplateEntity> = templates.values.sortedByDescending { it.createdAt }

    override fun template(templateId: String): LearnedTemplateEntity? = templates[templateId]

    override fun recordAgreement(templateId: String, at: Long) {
        templates[templateId]?.let { templates[templateId] = it.copy(agreements = it.agreements + 1, lastMatchedAt = at) }
    }

    override fun promote(templateId: String, at: Long) {
        val row = templates[templateId] ?: return
        if (row.state != LearnedState.Shadow.name) return
        templates[templateId] = row.copy(state = LearnedState.Active.name, promotedAt = at)
    }

    override fun retire(templateId: String, at: Long) {
        val row = templates[templateId] ?: return
        if (row.state == LearnedState.Retired.name) return
        templates[templateId] = row.copy(
            state = LearnedState.Retired.name,
            contradictions = row.contradictions + 1,
            retiredAt = at,
        )
    }

    override fun forget(templateId: String) {
        templates.remove(templateId)
    }

    override fun clearTemplates() = templates.clear()
}
