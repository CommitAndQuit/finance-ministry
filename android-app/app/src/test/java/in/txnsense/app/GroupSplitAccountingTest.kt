package `in`.txnsense.app

import `in`.txnsense.app.data.*
import org.junit.Assert.*
import org.junit.Test

class GroupSplitAccountingTest {
    @Test fun parts_allocate_unequal_and_fractional_shares_without_losing_paise() {
        assertEquals(listOf(30000L, 60000L), GroupSplitAccounting.byParts(90000, listOf("1", "2")))
        assertEquals(listOf(334L, 667L, 0L), GroupSplitAccounting.byParts(1001, listOf("1", "2", "0")))
        assertEquals(listOf(250L, 750L), GroupSplitAccounting.byParts(1000, listOf("0.5", "1.5")))
        assertEquals(listOf(1L, 0L, 0L), GroupSplitAccounting.byParts(1, listOf("1", "1", "1")))
    }

    @Test fun parts_reject_empty_negative_excess_precision_and_all_zero_inputs() {
        listOf(listOf("1"), listOf("0", "0"), listOf("", "1"), listOf("-1", "2"),
            listOf("1.001", "1"), listOf("1e2", "1"), listOf("1000000000", "1"), List(22) { "1" }).forEach {
            assertThrows(IllegalArgumentException::class.java) { GroupSplitAccounting.byParts(100, it) }
        }
    }

    @Test fun parts_preserve_large_totals_and_exact_default_ratios() {
        val parts = listOf("999999999.99", "999999999.98", "0.01")
        assertEquals(Long.MAX_VALUE, GroupSplitAccounting.byParts(Long.MAX_VALUE, parts).fold(0L, Math::addExact))
        // Save entered weights, not the 334:667 rounded paise result of a ₹10.01 split.
        assertEquals(listOf(30000L, 60000L), GroupPresetAccounting.custom(90000, GroupSplitAccounting.partWeights(listOf("1", "2"))))
    }

    @Test fun equal_splits_preserve_every_paise_in_selection_order() {
        assertEquals(listOf(30000L, 30000L, 30000L), GroupSplitAccounting.equal(90000, 3))
        assertEquals(listOf(334L, 334L, 333L), GroupSplitAccounting.equal(1001, 3))
        assertEquals(Long.MAX_VALUE, GroupSplitAccounting.equal(Long.MAX_VALUE, 20).fold(0L, Math::addExact))
        assertThrows(IllegalArgumentException::class.java) { GroupSplitAccounting.equal(100, 0) }
    }
    @Test fun custom_splits_require_exact_totals_unique_members_and_nonnegative_shares() {
        GroupSplitAccounting.validate(90000, 20000, listOf(MemberShareInput("alice", 40000), MemberShareInput("bob", 30000)))
        assertThrows(IllegalArgumentException::class.java) { GroupSplitAccounting.validate(90000, 20000, listOf(MemberShareInput("alice", 69999))) }
        assertThrows(IllegalArgumentException::class.java) { GroupSplitAccounting.validate(90000, 20000, listOf(MemberShareInput("alice", 40000), MemberShareInput("alice", 30000))) }
        assertThrows(IllegalArgumentException::class.java) { GroupSplitAccounting.validate(90000, 90001, listOf(MemberShareInput("bob", -1))) }
        assertThrows(IllegalArgumentException::class.java) { GroupSplitAccounting.validate(Long.MAX_VALUE, Long.MAX_VALUE, listOf(MemberShareInput("bob", Long.MAX_VALUE))) }
        GroupSplitAccounting.validate(2100, 100, List(20) { MemberShareInput("person-$it", 100) })
        assertThrows(IllegalArgumentException::class.java) { GroupSplitAccounting.validate(2200, 100, List(21) { MemberShareInput("person-$it", 100) }) }
    }
    @Test fun settling_alice_does_not_reduce_bobs_balance() {
        val shares = listOf(GroupShareEntity("a", "dinner", "alice", 30000), GroupShareEntity("b", "dinner", "bob", 30000))
        val receipts = listOf(RepaymentEntity("r", "dinner", "cash", 10000, 1, "Alice", "Cash", 1, "alice"))
        assertEquals(20000L, GroupSplitAccounting.owed(shares[0], receipts))
        assertEquals(30000L, GroupSplitAccounting.owed(shares[1], receipts))
        assertThrows(IllegalArgumentException::class.java) { GroupSplitAccounting.validateRepayment(shares, receipts, "alice", 20001) }
        assertThrows(IllegalArgumentException::class.java) { GroupSplitAccounting.validateRepayment(shares, receipts, null, 100) }
        assertThrows(IllegalArgumentException::class.java) { GroupSplitAccounting.validateRepayment(shares, receipts, "unknown", 100) }
    }
}
