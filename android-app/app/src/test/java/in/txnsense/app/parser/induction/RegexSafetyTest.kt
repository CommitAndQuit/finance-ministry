package `in`.txnsense.app.parser.induction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexSafetyTest {

    @Test fun unbounded_repetition_is_refused() {
        assertTrue(RegexSafety.violations("""ab+c""").isNotEmpty())
        assertTrue(RegexSafety.violations("""ab*c""").isNotEmpty())
        assertTrue(RegexSafety.violations("""ab{2,}c""").isNotEmpty())
    }

    @Test fun bounded_repetition_is_allowed() {
        assertEquals(emptyList<String>(), RegexSafety.violations("""a[\d,]{1,16}(?:\.\d{1,2})?b"""))
    }

    @Test fun repetition_symbols_inside_a_character_class_are_literal_characters() {
        assertEquals(emptyList<String>(), RegexSafety.violations("""[x*•]{0,6}\d{3,4}"""))
    }

    @Test fun a_repeated_group_is_refused_but_an_optional_one_is_not() {
        assertTrue(RegexSafety.violations("""(?:ab){1,5}""").isNotEmpty())
        assertEquals(emptyList<String>(), RegexSafety.violations("""(?:ab)?c"""))
    }

    @Test fun an_overlong_or_malformed_pattern_is_refused() {
        assertTrue(RegexSafety.violations("a".repeat(RegexSafety.MAX_PATTERN_LENGTH + 1)).isNotEmpty())
        assertTrue(RegexSafety.violations("""(ab""").isNotEmpty())
        assertTrue(RegexSafety.violations("""[ab""").isNotEmpty())
        assertTrue(RegexSafety.violations("").isNotEmpty())
    }

    @Test fun compiling_yields_a_pattern_only_when_it_is_safe() {
        assertNotNull(RegexSafety.compile("""a\d{1,3}b"""))
        assertNull(RegexSafety.compile("""a\d+b"""))
    }

    @Test fun a_runaway_match_is_abandoned_instead_of_stalling_the_caller() {
        // Nothing the emitter writes looks like this; the budget exists for patterns that reach the
        // engine another way, and for the ones a future labeller might learn to write.
        val pathological = Regex("""(a+)+b""")
        val text = "a".repeat(40)

        val started = System.nanoTime()
        val match = RegexSafety.findWithin(pathological, text, budgetMillis = 30)
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertNull(match)
        assertTrue("gave up after ${elapsedMillis}ms", elapsedMillis < 2_000)
    }

    @Test fun a_match_inside_the_budget_is_returned_normally() {
        val match = RegexSafety.findWithin(Regex("""(?<amount>\d{1,4})"""), "spent 4200 today")

        assertEquals("4200", match?.groups?.get("amount")?.value)
    }
}
