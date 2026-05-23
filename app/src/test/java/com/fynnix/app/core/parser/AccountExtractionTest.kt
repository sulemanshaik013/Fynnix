package com.fynnix.app.core.parser

import com.fynnix.app.core.parser.extractor.AccountExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountExtractionTest {

    private val extractor = AccountExtractor()

    @Test
    fun `extracts last4 from masked account`() {
        val body = "rs.1500.00 debited from a/c xx9876 on 01-jan-26."

        assertEquals("9876", extractor.extract(body))
    }

    @Test
    fun `does not mistake the amount for the account when 'ac' occurs inside another word`() {
        val body = "transaction alert! rs.1500.00 debited from a/c xx9876 towards purchase."

        assertEquals("9876", extractor.extract(body))
    }

    @Test
    fun `does not capture unrelated digits next to a keyword embedded in a longer word`() {
        val body = "we made a backup 1234 today from a/c xx5678."

        assertEquals("5678", extractor.extract(body))
    }

    @Test
    fun `returns null when no account marker is present at all`() {
        val body = "your otp for login is 4455, valid for 10 minutes."

        assertNull(extractor.extract(body))
    }

    @Test
    fun `extracts last4 from unmasked account number`() {
        val body = "rs.500 credited to a/c 1234567890."

        assertEquals("7890", extractor.extract(body))
    }

    @Test
    fun `extracts last4 from 'ending with' phrasing`() {
        val body = "your card ending with 4321 was used for a payment of rs.200."

        assertEquals("4321", extractor.extract(body))
    }
}
