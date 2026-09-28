package `in`.txnsense.app.parser.induction

import `in`.txnsense.app.core.model.IncomingSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored form of a layout.
 *
 * Two properties matter here and nothing else does. A skeleton must survive a round trip through
 * storage unchanged, because a layout that decodes differently from how it was encoded would be
 * aligned against layouts it never matched. And the encoding must be legible as a layout, because that
 * is the claim the privacy notice makes about what the corpus holds.
 */
class SkeletonCodecTest {

    private val skeletonizer = Skeletonizer()

    private fun skeleton(body: String) = skeletonizer.skeletonize(IncomingSms("AD-ZZQQPAY-S", 0L, body))

    private val body =
        "ZZQQ Pay: Rs.150.75 spent via QuickSettle from a/c XX4321 towards COFFEEWORKS on 05-03-2026. Ref 887766."

    @Test fun a_layout_survives_storage_unchanged() {
        val original = skeleton(body)

        val restored = SkeletonCodec.decode(original.bankId, SkeletonCodec.encode(original))

        assertEquals(original.bankId, restored?.bankId)
        assertEquals(original.tokens, restored?.tokens)
    }

    @Test fun the_stored_form_holds_banking_words_and_blanks_and_nothing_else() {
        val encoded = SkeletonCodec.encode(skeleton(body))

        // The vocabulary words are there, because they are what identifies the layout.
        assertTrue(encoded, encoded.contains("spent"))
        assertTrue(encoded, encoded.contains("#MONEY:"))
        assertTrue(encoded, encoded.contains("#ACCOUNT:"))
        assertTrue(encoded, encoded.contains("#DATE:"))
        // Nothing the message was actually about survives.
        listOf("coffeeworks", "quicksettle", "4321", "150", "887766").forEach {
            assertTrue("$it leaked into $encoded", !encoded.contains(it))
        }
    }

    @Test fun a_line_break_is_kept_because_layouts_differ_by_where_they_wrap() {
        val encoded = SkeletonCodec.encode(skeleton("Rs.90 spent from a/c XX4321\nRef 887766"))

        assertTrue(encoded, encoded.contains("#NL"))
        assertEquals(1, SkeletonCodec.decode("UNKNOWN", encoded)?.tokens?.count { it is Token.Break })
    }

    @Test fun two_messages_of_one_layout_share_a_key_when_their_fields_ran_the_same_width() {
        val first = skeleton(body)
        val second = skeleton(
            "ZZQQ Pay: Rs.999.99 spent via QuickSettle from a/c XX9876 towards BOOKSELLERS on 06-03-2026. Ref 887767.",
        )

        // Entirely different values, identical layout and field widths: one row in the corpus.
        assertEquals(
            SkeletonCodec.key(first.bankId, SkeletonCodec.encode(first)),
            SkeletonCodec.key(second.bankId, SkeletonCodec.encode(second)),
        )
    }

    @Test fun the_same_layout_with_differently_sized_fields_is_kept_separately() {
        val first = skeleton(body)
        val second = skeleton(
            "ZZQQ Pay: Rs.20 spent via QuickSettle from a/c XX4321 towards TEA on 06-03-2026. Ref 887767.",
        )

        // Deliberate: how wide a field ran is the only variation the aligner has to work out where the
        // fields are, so collapsing these two would throw away the evidence induction runs on.
        assertTrue(
            SkeletonCodec.key(first.bankId, SkeletonCodec.encode(first)) !=
                SkeletonCodec.key(second.bankId, SkeletonCodec.encode(second)),
        )
    }

    @Test fun a_different_layout_gets_a_different_key() {
        val spent = skeleton(body)
        val received = skeleton(
            "ZZQQ Pay: Rs.150.75 received via QuickSettle into a/c XX4321 from PAYROLLCO on 05-03-2026. Ref 887766.",
        )

        assertTrue(
            SkeletonCodec.key(spent.bankId, SkeletonCodec.encode(spent)) !=
                SkeletonCodec.key(received.bankId, SkeletonCodec.encode(received)),
        )
    }

    @Test fun the_same_layout_from_two_banks_is_two_layouts() {
        val encoded = SkeletonCodec.encode(skeleton(body))

        assertTrue(SkeletonCodec.key("BANK_A", encoded) != SkeletonCodec.key("BANK_B", encoded))
    }

    @Test fun a_key_is_stable_across_runs() {
        val encoded = SkeletonCodec.encode(skeleton(body))

        // Stored rows are found by this key, so it may never depend on anything but its inputs.
        assertEquals(SkeletonCodec.key("UNKNOWN", encoded), SkeletonCodec.key("UNKNOWN", encoded))
        assertEquals(32, SkeletonCodec.key("UNKNOWN", encoded).length)
    }

    @Test fun a_corrupt_row_is_refused_rather_than_guessed_at() {
        listOf(
            "spent #MONEY at #WORD:4",       // no width
            "spent #MONEY:wide at #WORD:4",  // width is not a number
            "spent #NOTASLOT:6 at #WORD:4",  // slot no longer exists
            "",
        ).forEach { assertNull(it, SkeletonCodec.decode("UNKNOWN", it)) }
    }

    @Test fun a_hand_written_layout_decodes_to_the_tokens_it_reads_as() {
        val restored = SkeletonCodec.decode("UNKNOWN", "rs #MONEY:8 spent on card #ACCOUNT:6 on #DATE:10")

        assertEquals(
            listOf(
                Token.Literal("rs"),
                Token.Blank(Slot.Money, 8),
                Token.Literal("spent"),
                Token.Literal("on"),
                Token.Literal("card"),
                Token.Blank(Slot.Account, 6),
                Token.Literal("on"),
                Token.Blank(Slot.Date, 10),
            ),
            restored?.tokens,
        )
    }
}
