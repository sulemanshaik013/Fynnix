package com.fynnix.app.core.llm

import com.fynnix.app.core.utils.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LlmTransactionJsonParserTest {

    @Test
    fun `parses a valid debit response`() {
        val response = """
            Sure, here is the JSON you asked for:
            {"isTransaction": true, "type": "DEBIT", "amount": 500.50, "merchant": "merchant@okbank", "accountLast4": "1234"}
        """.trimIndent()

        val draft = LlmTransactionJsonParser.parse(response)

        assertNotNull(draft)
        assertEquals(TransactionType.DEBIT, draft!!.type)
        assertEquals(500.50, draft.amount, 0.001)
        assertEquals("merchant@okbank", draft.merchant)
        assertEquals("1234", draft.accountLast4)
    }

    @Test
    fun `parses a valid credit response with no account digits`() {
        val json = """{"isTransaction": true, "type": "CREDIT", "amount": 2000, "merchant": "John Doe", "accountLast4": null}"""

        val draft = LlmTransactionJsonParser.parse(json)

        assertNotNull(draft)
        assertEquals(TransactionType.CREDIT, draft!!.type)
        assertNull(draft.accountLast4)
    }

    @Test
    fun `returns null when isTransaction is false`() {
        assertNull(LlmTransactionJsonParser.parse("""{"isTransaction": false}"""))
    }

    @Test
    fun `returns null for a missing or invalid type`() {
        assertNull(LlmTransactionJsonParser.parse("""{"isTransaction": true, "amount": 100, "merchant": "X"}"""))
        assertNull(LlmTransactionJsonParser.parse("""{"isTransaction": true, "type": "TRANSFER", "amount": 100, "merchant": "X"}"""))
    }

    @Test
    fun `rejects a non-positive amount`() {
        assertNull(LlmTransactionJsonParser.parse("""{"isTransaction": true, "type": "DEBIT", "amount": 0, "merchant": "X"}"""))
        assertNull(LlmTransactionJsonParser.parse("""{"isTransaction": true, "type": "DEBIT", "amount": -50, "merchant": "X"}"""))
    }

    @Test
    fun `rejects an amount above the sane cap`() {
        assertNull(LlmTransactionJsonParser.parse("""{"isTransaction": true, "type": "DEBIT", "amount": 999999999, "merchant": "X"}"""))
    }

    @Test
    fun `rejects a blank merchant`() {
        assertNull(LlmTransactionJsonParser.parse("""{"isTransaction": true, "type": "DEBIT", "amount": 100, "merchant": "   "}"""))
    }

    @Test
    fun `rejects an accountLast4 that is not exactly 4 digits`() {
        val draft = LlmTransactionJsonParser.parse(
            """{"isTransaction": true, "type": "DEBIT", "amount": 100, "merchant": "X", "accountLast4": "12a4"}"""
        )
        assertNotNull(draft)
        assertNull(draft!!.accountLast4)
    }

    @Test
    fun `returns null for unparseable text with no JSON object`() {
        assertNull(LlmTransactionJsonParser.parse("I'm not sure how to answer that."))
    }

    @Test
    fun `returns null for malformed JSON`() {
        assertNull(LlmTransactionJsonParser.parse("""{"isTransaction": true, "type": }"""))
    }
}
