package `in`.txnsense.app.parser.induction

import `in`.txnsense.app.core.model.Channel
import `in`.txnsense.app.core.model.Direction
import `in`.txnsense.app.core.model.IncomingSms
import `in`.txnsense.app.core.model.ParseDecision
import `in`.txnsense.app.core.model.TransactionStatus
import `in`.txnsense.app.parser.engine.TemplateEngineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end induction, on an invented layout no curated template reads.
 *
 * Every message here is made up, as is the institution that supposedly sent it. The point of the
 * fixture is that the parser has never been told about this layout: whatever coverage it gains, it
 * worked out from the messages themselves.
 */
class TemplateInductionTest {

    private val skeletonizer = Skeletonizer()
    private val inducer = TemplateInducer()

    private val sender = "AD-ZZQQPAY-S"

    /** Three sightings of one invented layout, differing in amount, payee, account and date. */
    private val sightings = listOf(
        "ZZQQ Pay: Rs.150.75 spent via QuickSettle from a/c XX4321 towards COFFEEWORKS on 05-03-2026. Ref 887766.",
        "ZZQQ Pay: Rs.20 spent via QuickSettle from a/c XX4321 towards BOOKSELLER on 06-03-2026. Ref 887767.",
        "ZZQQ Pay: Rs.1,250.00 spent via QuickSettle from a/c XX9876 towards METRO TOPUP on 07-03-2026. Ref 887768.",
    )

    private fun sms(body: String) = IncomingSms(sender, 0L, body)

    private fun skeletons(bodies: List<String>) = bodies.map { skeletonizer.skeletonize(sms(it)) }

    private fun learn(bodies: List<String> = sightings): List<InducedTemplate> = inducer.induce(skeletons(bodies))

    @Test fun the_curated_parser_does_not_already_read_this_layout() {
        sightings.forEach { assertEquals(it, ParseDecision.Reject, TemplateEngineParser().parse(sms(it)).decision) }
    }

    @Test fun three_sightings_of_one_layout_become_a_single_template() {
        val learned = learn()

        assertEquals(1, learned.size)
        assertEquals(3, learned.single().sampleCount)
        assertTrue(learned.single().templateId.startsWith("learned_unknown_"))
    }

    @Test fun the_learned_layout_is_read_as_a_settled_debit() {
        val semantics = learn().single().semantics

        assertEquals(Direction.Debit, semantics.direction)
        assertEquals(TransactionStatus.Successful, semantics.status)
        // Nothing in the layout names a channel, and induction does not guess at one.
        assertEquals(Channel.Unknown, semantics.channel)
    }

    @Test fun the_learned_template_reads_a_message_it_was_never_shown() {
        val engine = TemplateEngineParser(listOf(learn().single().toParsingTemplate(ParseDecision.Record)))

        val parsed = engine.parse(sms("ZZQQ Pay: Rs.99.50 spent via QuickSettle from a/c XX1111 towards TEASTALL on 08-03-2026. Ref 887770."))

        assertEquals(ParseDecision.Record, parsed.decision)
        assertEquals(9950L, parsed.amountMinor)
        assertEquals(Direction.Debit, parsed.direction)
        assertEquals("••••1111", parsed.maskedAccountHint)
        assertEquals("TEASTALL", parsed.counterpartyLabel)
    }

    @Test fun a_learned_template_asks_for_review_until_it_is_promoted() {
        val engine = TemplateEngineParser(listOf(learn().single().toParsingTemplate()))

        val parsed = engine.parse(sms(sightings.first()))

        assertEquals(ParseDecision.NeedsReview, parsed.decision)
    }

    @Test fun curated_templates_keep_deciding_the_messages_they_already_read() {
        val engine = TemplateEngineParser(listOf(learn().single().toParsingTemplate(ParseDecision.Record)))

        // A layout the curated repository owns is answered by it, whatever has been learned since.
        val parsed = engine.parse(sms("INR 250.50 debited from your account via UPI"))

        assertEquals("template_match", parsed.ruleId)
        assertEquals(25050L, parsed.amountMinor)
    }

    @Test fun one_sighting_is_not_a_layout() {
        val outcomes = inducer.outcomes(skeletons(sightings.take(2)))

        val skipped = outcomes.filterIsInstance<Outcome.Skipped>().single()
        assertTrue(skipped.reason, skipped.reason.contains("only 2 messages"))
        assertEquals(emptyList<InducedTemplate>(), learn(sightings.take(2)))
    }

    @Test fun messages_that_only_mention_money_are_refused() {
        val reminders = listOf(
            "ZZQQ Pay: Rs.150.75 is due on 05-03-2026 for a/c XX4321 towards COFFEEWORKS. Ref 887766.",
            "ZZQQ Pay: Rs.20 is due on 06-03-2026 for a/c XX4321 towards BOOKSELLER. Ref 887767.",
            "ZZQQ Pay: Rs.90 is due on 07-03-2026 for a/c XX9876 towards METRO TOPUP. Ref 887768.",
        )

        val skipped = inducer.outcomes(skeletons(reminders)).filterIsInstance<Outcome.Skipped>().single()

        assertEquals("not a settled money movement", skipped.reason)
    }

    @Test fun a_learned_pattern_is_bounded_and_therefore_cheap_to_run() {
        val pattern = learn().single().regexPattern

        assertEquals(emptyList<String>(), RegexSafety.violations(pattern))
        assertNotNull(RegexSafety.compile(pattern))
    }

    @Test fun two_unrelated_layouts_are_learned_separately() {
        val credits = listOf(
            "ZZQQ Pay: Rs.500 received via QuickSettle into a/c XX4321 from PAYROLLCO on 05-03-2026. Ref 887780.",
            "ZZQQ Pay: Rs.750 received via QuickSettle into a/c XX4321 from GRANDMOTHER on 06-03-2026. Ref 887781.",
            "ZZQQ Pay: Rs.1,000 received via QuickSettle into a/c XX9876 from PAYROLLCO on 07-03-2026. Ref 887782.",
        )

        val learned = inducer.induce(skeletons(sightings + credits))

        assertEquals(2, learned.size)
        assertEquals(setOf(Direction.Debit, Direction.Credit), learned.map { it.semantics.direction }.toSet())
    }
}
