package `in`.txnsense.app.parser.induction

import `in`.txnsense.app.core.model.IncomingSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invented values only. No private SMS, names, references or balances in the repo. */
class SkeletonizerTest {

    private val skeletonizer = Skeletonizer()

    private fun skeleton(body: String, sender: String = "AD-HDFCBK-S") =
        skeletonizer.skeletonize(IncomingSms(sender, 0L, body))

    @Test fun skeletonizing_discards_every_value_the_message_carried() {
        val outline = skeleton(
            "Rs.1,250.50 spent on your HDFC Bank Card XX4321 at ZZQQ COFFEEWORKS on 05-03-2026. Ref 998877."
        )

        val rendered = outline.render()
        listOf("1,250", "4321", "998877", "zzqq", "coffeeworks", "05-03").forEach {
            assertFalse("'$it' survived skeletonizing", rendered.lowercase().contains(it))
        }
        assertTrue(outline.words.containsAll(listOf("spent", "on", "your", "hdfc", "bank", "card", "at", "ref")))
    }

    @Test fun words_outside_the_vocabulary_become_blanks_rather_than_text() {
        val outline = skeleton("ZZQQ WIDGETWORKS PRIVATE")

        assertEquals(emptyList<String>(), outline.words)
        assertEquals(3, outline.tokens.count { it is Token.Blank && it.slot == Slot.Word })
    }

    @Test fun a_masked_group_is_recognized_as_an_account_hint() {
        val outline = skeleton("A/c XX1234 debited Rs.42.00")

        assertTrue(outline.tokens.contains(Token.Blank(Slot.Account, 6)))
    }

    @Test fun a_bare_four_digit_group_after_a_card_word_is_an_account_hint_too() {
        val outline = skeleton("Card 1234 debited Rs.42.00")

        assertTrue(outline.tokens.contains(Token.Blank(Slot.Account, 4)))
    }

    @Test fun a_longer_digit_run_is_a_number_not_an_account() {
        val outline = skeleton("Ref 887766 debited Rs.42.00")

        assertTrue(outline.tokens.contains(Token.Blank(Slot.Number, 6)))
        assertFalse(outline.tokens.any { it is Token.Blank && it.slot == Slot.Account })
    }

    @Test fun line_breaks_are_kept_because_layouts_are_built_on_them() {
        val outline = skeleton("Spent Rs.42\nCard no. XX1234\nAvl Lmt Rs.900")

        assertEquals(2, outline.tokens.count { it is Token.Break })
        assertEquals(2, outline.render().count { it == '\n' })
    }

    @Test fun the_fraud_reporting_footer_is_not_part_of_any_layout() {
        val outline = skeleton("Rs.42.00 debited from a/c XX1234. Not you? Call 1800000000 to block.")

        assertFalse(outline.words.contains("call"))
        assertFalse(outline.words.contains("block"))
    }

    @Test fun amounts_time_and_dates_are_classified_rather_than_counted_as_numbers() {
        val outline = skeleton("Rs.42.00 spent on 05-03-2026 12:34:56 at ZZQQ")

        assertTrue(outline.tokens.contains(Token.Blank(Slot.Money, 8)))
        assertTrue(outline.tokens.any { it is Token.Blank && it.slot == Slot.Date })
        assertTrue(outline.tokens.any { it is Token.Blank && it.slot == Slot.Time })
        assertFalse(outline.tokens.any { it is Token.Blank && it.slot == Slot.Number })
    }

    @Test fun a_known_sender_names_the_bank_and_an_unknown_one_does_not() {
        assertEquals("HDFC", skeleton("Rs.42.00 debited", sender = "AD-HDFCBK-S").bankId)
        assertEquals(Skeletonizer.UNKNOWN_BANK, skeleton("Rs.42.00 debited", sender = "AD-ZZQQ-S").bankId)
    }
}
