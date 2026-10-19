package `in`.financeministry.app

import `in`.financeministry.app.data.*
import org.junit.Assert.*
import org.junit.Test

class GroupPresetAccountingTest {
    @Test fun resaving_an_unchanged_preview_keeps_the_original_ratio_but_edits_replace_it() {
        val preset = GroupPreset(GroupPresetEntity("p", "Dinner", "Custom", 100, 1, 1),
            listOf(GroupPresetMemberEntity("m", "p", "alice", 0, 200)))
        val preview = preset.preview(1001)
        assertEquals(listOf(100L, 200L), preset.weightsForDefault(1001, preview.personalMinor, preview.others))
        assertEquals(listOf(30000L, 60000L), GroupPresetAccounting.custom(90000,
            preset.weightsForDefault(1001, preview.personalMinor, preview.others)))
        assertEquals(listOf(400L, 601L), preset.weightsForDefault(1001, 400, listOf(MemberShareInput("alice", 601))))
        assertEquals(listOf(334L, 667L), preset.weightsForDefault(1001, 334, listOf(MemberShareInput("bob", 667))))
    }

    // Break caught: reusing stored paise as fixed amounts instead of applying the default ratio.
    @Test fun custom_default_scales_to_the_new_expense_and_preserves_every_paise() {
        assertEquals(listOf(250L, 501L, 250L), GroupPresetAccounting.custom(1001, listOf(1L, 2L, 1L)))
        assertEquals(listOf(0L, 1001L, 0L), GroupPresetAccounting.custom(1001, listOf(0L, 10L, 0L)))
    }

    // Break caught: rounding each share independently or losing selection-order tie resolution.
    @Test fun custom_remainders_use_largest_fraction_then_you_and_selection_order() {
        assertEquals(listOf(3L, 2L, 2L), GroupPresetAccounting.custom(7, listOf(1L, 1L, 1L)))
        assertEquals(listOf(0L, 1L, 0L), GroupPresetAccounting.custom(1, listOf(1L, 3L, 2L)))
    }

    // Break caught: Long multiplication overflow or unsafe sums near the supported monetary limit.
    @Test fun weights_and_total_near_long_limit_are_exact() {
        assertEquals(listOf(4611686018427387904L, 4611686018427387903L),
            GroupPresetAccounting.custom(Long.MAX_VALUE, listOf(Long.MAX_VALUE, Long.MAX_VALUE)))
    }

    // Break caught: accepting malformed presets that cannot generate valid individual splits.
    @Test fun rejects_empty_zero_negative_or_oversized_defaults() {
        listOf(emptyList(), listOf(1L), listOf(0L, 0L), listOf(1L, -1L), List(22) { 1L }).forEach { weights ->
            assertThrows(IllegalArgumentException::class.java) { GroupPresetAccounting.custom(100, weights) }
        }
        assertThrows(IllegalArgumentException::class.java) { GroupPresetAccounting.custom(0, listOf(1L, 1L)) }
    }
}
