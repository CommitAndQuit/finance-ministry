package `in`.financeministry.app.parser

import org.junit.Assert.*
import org.junit.Test

class TransactionReferenceTest {
    @Test fun colon_upi_layout_keeps_leading_zeroes_and_bank_suffix() {
        assertEquals("000000000001", TransactionReference.extract("UPI:000000000001."))
        assertEquals("000000000001", TransactionReference.extract("upi: 000000000001-ICICI Bank."))
        assertEquals("000000000001", TransactionReference.extract("UPI:000000000001 Ref No. 000000000001"))
        assertEquals("000000000001", TransactionReference.extract("UPI Ref no 000000000001).Indian Bank"))
        assertEquals("000000000001", TransactionReference.extract("Ref No 000000000001.Small txns? Use UPI Lite"))
    }

    @Test fun conflicting_or_malformed_reference_tokens_are_not_partial_matches() {
        listOf(
            "UPI:000000000001 Ref 000000000002",
            "Ref 000000000001ABC",
            "Ref 000000000001.25",
            "Ref 000000000001/000000000002",
            "Ref 000000000001_2",
            "Ref 000000000001 Ref 123",
            "UPI:0000000000001",
            "UPI:000000000001@test",
            "UPI 000000000001",
            "UPI:1234",
            "UPI:000000000001000000000001",
            "Contact test@upi:000000000001"
        ).forEach { assertNull(it, TransactionReference.extract(it)) }
    }

    @Test fun security_footer_is_not_reference_evidence() {
        assertNull(TransactionReference.extract("INR 42 debited. Not You? Quote Ref 000000000001"))
        assertEquals("000000000001", TransactionReference.extract(
            "INR 42 debited Ref 000000000001. Not You? Quote Ref 000000000002"))
    }

    @Test fun only_explicit_unambiguous_references_are_extracted() {
        assertEquals("000000000001", TransactionReference.extract("UPI Ref No 000000000001"))
        assertEquals("000000000001", TransactionReference.extract("UTR: 000000000001"))
        assertNull(TransactionReference.extract("INR 42 account 000000000001"))
        assertNull(TransactionReference.extract("Ref 000000000001 original Ref 000000000002"))
        assertNull(TransactionReference.extract("Ref 123"))
        assertNull(TransactionReference.extract("Ref 1234567890123456789012345"))
    }
}
