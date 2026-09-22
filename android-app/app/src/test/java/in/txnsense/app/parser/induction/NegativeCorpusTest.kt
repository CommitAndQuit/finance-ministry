package `in`.txnsense.app.parser.induction

import `in`.txnsense.app.core.model.IncomingSms
import `in`.txnsense.app.core.model.ParseDecision
import `in`.txnsense.app.parser.engine.TemplateEngineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The negative corpus is only useful if it really is negative.
 *
 * Every entry has to be something the curated parser already refuses, because the whole point is to
 * catch a learned pattern that would accept what the hand-written ones reject. An entry the curated
 * parser records would quietly disarm that check.
 */
class NegativeCorpusTest {

    private val parser = TemplateEngineParser()

    @Test fun the_curated_parser_refuses_every_entry() {
        NegativeCorpus.bodies.forEach {
            assertEquals(it, ParseDecision.Reject, parser.parse(IncomingSms("AD-HDFCBK-S", 0L, it)).decision)
        }
    }

    @Test fun the_corpus_covers_the_ways_a_message_can_mention_money_without_moving_it() {
        val corpus = NegativeCorpus.bodies.joinToString(" ").lowercase()

        listOf("otp", "eligible", "due", "mandate", "avl bal", "pin change", "initiated", "premium")
            .forEach { assertTrue("no entry mentioning '$it'", corpus.contains(it)) }
    }
}
