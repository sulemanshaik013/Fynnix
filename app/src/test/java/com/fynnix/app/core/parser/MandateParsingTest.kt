package com.fynnix.app.core.parser

import com.fynnix.app.core.parser.extractor.AmountExtractor
import com.fynnix.app.core.parser.extractor.DateExtractor
import com.fynnix.app.core.parser.extractor.MandateExtractor
import com.fynnix.app.core.parser.extractor.MerchantExtractor
import com.fynnix.app.core.parser.intent.IntentClassifier
import com.fynnix.app.core.parser.normalize.TextNormalizer
import com.fynnix.app.core.utils.MandateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MandateParsingTest {

    private val normalizer = TextNormalizer()
    private val intentClassifier = IntentClassifier()
    private val mandateExtractor = MandateExtractor(AmountExtractor(), MerchantExtractor(), DateExtractor())

    @Test
    fun `registration SMS is classified as MANDATE_REGISTERED not AUTOPAY`() {
        val body = "Dear Customer, your e-mandate towards NETFLIX INDIA for Rs 649.00 has been " +
            "registered successfully. UMN: HDFC0001234567890123. Next debit on 05-Sep-2026."
        val sms = normalizer.normalize(SmsInput("1", "HDFCBK", body, System.currentTimeMillis()))

        val intent = intentClassifier.classify(sms.cleanedBody)

        assertEquals(TransactionIntent.MANDATE_REGISTERED, intent)
        assertTrue("Registration must not be treated as a stored transaction", !intent.isFinancial)
    }

    @Test
    fun `registration SMS extracts merchant, amount, UMN and next-due date, not the registration date`() {
        val body = "Dear Customer, your e-mandate towards NETFLIX INDIA for Rs 649.00 has been " +
            "registered successfully on 19-Aug-2026. UMN: HDFC0001234567890123. Next debit on 05-Sep-2026."
        val sms = normalizer.normalize(SmsInput("1", "HDFCBK", body, System.currentTimeMillis()))

        val mandate = mandateExtractor.extract(body, sms.cleanedBody, TransactionIntent.MANDATE_REGISTERED)

        assertNotNull(mandate)
        assertEquals("Netflix", mandate!!.merchant)
        assertEquals(649.00, mandate.amount!!, 0.001)
        assertEquals("HDFC0001234567890123", mandate.umn)
        assertEquals(MandateStatus.ACTIVE, mandate.status)
        assertNotNull("next-due date must resolve to the 'Next debit on' date, not the registration date", mandate.nextDueDate)
    }

    @Test
    fun `cancellation SMS is classified as MANDATE_CANCELLED`() {
        val body = "Your e-mandate towards NETFLIX INDIA has been cancelled as per your request."
        val sms = normalizer.normalize(SmsInput("2", "HDFCBK", body, System.currentTimeMillis()))

        val intent = intentClassifier.classify(sms.cleanedBody)

        assertEquals(TransactionIntent.MANDATE_CANCELLED, intent)
    }

    @Test
    fun `an actual autopay debit is still classified as AUTOPAY, not a mandate event`() {
        val body = "Rs.199.00 debited from your account via Autopay towards NETFLIX INDIA on 05-Sep-26. " +
            "Avl Bal: Rs.10,542.00"
        val sms = normalizer.normalize(SmsInput("3", "HDFCBK", body, System.currentTimeMillis()))

        val intent = intentClassifier.classify(sms.cleanedBody)

        assertEquals(TransactionIntent.AUTOPAY, intent)
        assertTrue(intent.isFinancial)
    }

    @Test
    fun `a payment request is not classified as a mandate event`() {
        val body = "Rahul has requested Rs 500.00 from you via UPI. Ignore if already paid."
        val sms = normalizer.normalize(SmsInput("4", "PAYTM", body, System.currentTimeMillis()))

        val intent = intentClassifier.classify(sms.cleanedBody)

        assertEquals(TransactionIntent.PAYMENT_REQUEST, intent)
    }

    @Test
    fun `a mandate event with no identifiable merchant is dropped`() {
        val body = "Your standing instruction has been registered successfully."
        val sms = normalizer.normalize(SmsInput("5", "HDFCBK", body, System.currentTimeMillis()))

        val mandate = mandateExtractor.extract(body, sms.cleanedBody, TransactionIntent.MANDATE_REGISTERED)

        assertNull(mandate)
    }
}
