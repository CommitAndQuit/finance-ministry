package `in`.txnsense.app.parser.induction

import `in`.txnsense.app.core.model.IncomingSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invented values only. No private SMS, names, references or balances in the repo. */
class SpineAlignerTest {

    private val skeletonizer = Skeletonizer()

    private fun align(vararg bodies: String) =
        SpineAligner.align(bodies.map { skeletonizer.skeletonize(IncomingSms("AD-HDFCBK-S", 0L, it)) })

    @Test fun the_spine_is_what_every_message_kept_saying() {
        val alignment = align(
            "Rs.42.00 spent on card XX1234 at ZZQQ on 05-03-2026",
            "Rs.90.50 spent on card XX9876 at WIDGETWORKS on 06-03-2026",
            "Rs.7 spent on card XX1234 at TEASTALL on 07-03-2026",
        )

        assertEquals(listOf("spent", "on", "card", "at", "on"), alignment?.words)
    }

    @Test fun a_position_that_held_one_kind_of_blank_everywhere_is_a_capturable_field() {
        val alignment = align(
            "Rs.42.00 spent on card XX1234 at ZZQQ",
            "Rs.90.50 spent on card XX9876 at WIDGETWORKS",
            "Rs.7 spent on card XX4321 at TEASTALL",
        )!!

        // Before "spent" every message had an amount, and after "card" an account hint.
        assertEquals(GapShape.Typed(Slot.Money), alignment.slots.first())
        assertEquals(GapShape.Typed(Slot.Account), alignment.slots[alignment.words.indexOf("card") + 1])
    }

    @Test fun a_position_the_messages_disagreed_about_becomes_a_bounded_run() {
        val alignment = align(
            "Rs.42.00 spent at ZZQQ on 05-03-2026",
            "Rs.90.50 spent at WIDGETWORKS PRIVATE on 06-03-2026",
            "Rs.7 spent at TEASTALL on 07-03-2026",
        )!!

        val merchant = alignment.slots[alignment.words.indexOf("at") + 1]
        assertTrue(merchant.toString(), merchant is GapShape.Free)
        assertTrue((merchant as GapShape.Free).maxWidth <= SpineAligner.MAX_GAP_WIDTH)
    }

    @Test fun a_slot_count_of_one_more_than_the_anchors_leaves_room_at_both_ends() {
        val alignment = align(
            "Rs.42.00 spent at ZZQQ",
            "Rs.90.50 spent at WIDGETWORKS",
            "Rs.7 spent at TEASTALL",
        )!!

        assertEquals(alignment.spine.size + 1, alignment.slots.size)
    }

    @Test fun the_words_around_a_slot_are_readable_in_both_directions() {
        val alignment = align(
            "Rs.42.00 spent on card XX1234 at ZZQQ",
            "Rs.90.50 spent on card XX9876 at WIDGETWORKS",
        )!!

        val account = alignment.words.indexOf("card") + 1
        assertEquals(listOf("card", "on", "spent"), alignment.wordsBefore(account))
        assertEquals(listOf("at"), alignment.wordsAfter(account))
    }

    @Test fun a_line_break_is_an_anchor_like_any_other_word() {
        val alignment = align(
            "Rs.42.00 spent on card XX1234\nAvl Lmt Rs.900",
            "Rs.90.50 spent on card XX9876\nAvl Lmt Rs.500",
        )!!

        assertTrue(alignment.spine.contains(Token.Break))
        assertEquals(listOf("spent", "on", "card", "avl", "lmt"), alignment.words)
    }

    @Test fun a_gap_that_could_swallow_a_line_break_abandons_the_cluster() {
        // One message wraps where the other does not, so the varying part spans a newline in one
        // sample only. A pattern for that would match across lines it has never seen.
        assertNull(
            align(
                "Rs.42.00 spent at ZZQQ WIDGETWORKS on 05-03-2026",
                "Rs.90.50 spent at TEASTALL\nPRIVATE on 06-03-2026",
            )
        )
    }

    @Test fun messages_with_nothing_in_common_have_no_layout() {
        assertNull(align("ZZQQ WIDGETWORKS PRIVATE", "TEASTALL COFFEEWORKS"))
    }

    @Test fun an_empty_cluster_has_no_layout() {
        assertNull(SpineAligner.align(emptyList()))
    }

    @Test fun one_message_aligns_with_itself() {
        // Not enough evidence to learn from, but alignment itself is defined: everything is invariant.
        val alignment = align("Rs.42.00 spent on card XX1234 at ZZQQ")

        assertNotNull(alignment)
        assertEquals(1, alignment?.sampleCount)
        assertEquals(listOf("spent", "on", "card", "at"), alignment?.words)
    }
}
