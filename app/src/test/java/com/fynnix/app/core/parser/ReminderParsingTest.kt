package com.fynnix.app.core.parser

import com.fynnix.app.core.parser.extractor.AmountExtractor
import com.fynnix.app.core.parser.extractor.DateExtractor
import com.fynnix.app.core.parser.extractor.MerchantExtractor
import com.fynnix.app.core.parser.extractor.ReminderExtractor
import com.fynnix.app.core.parser.intent.IntentClassifier
import com.fynnix.app.core.parser.normalize.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderParsingTest {

    private val normalizer = TextNormalizer()
    private val intentClassifier = IntentClassifier()
    private val reminderExtractor = ReminderExtractor(AmountExtractor(), MerchantExtractor(), DateExtractor())

    @Test
    fun `credit card statement SMS is classified as REMINDER`() {
        val body = "Your HDFC Bank Credit Card statement is ready. Total amount due Rs 12,450.00, " +
            "due date 05-Sep-2026."
        val sms = normalizer.normalize(SmsInput("1", "HDFCBK", body, System.currentTimeMillis()))

        assertEquals(TransactionIntent.REMINDER, intentClassifier.classify(sms.cleanedBody))
    }

    @Test
    fun `reminder extracts amount and due date, falling back to sender when no merchant found`() {
        val body = "Your HDFC Bank Credit Card statement is ready. Total amount due Rs 12,450.00, " +
            "due date 05-Sep-2026."
        val sms = normalizer.normalize(SmsInput("1", "HDFCBK", body, System.currentTimeMillis()))

        val reminder = reminderExtractor.extract(body, sms.cleanedBody, "HDFCBK")

        assertNotNull(reminder)
        assertEquals("Bill (HDFCBK)", reminder!!.label)
        assertEquals(12450.00, reminder.amount!!, 0.001)
        assertNotNull(reminder.dueDate)
    }

    @Test
    fun `reminder with an identifiable merchant uses it as the label`() {
        val body = "EMI Info: Bajaj Finserv. Rs 3500 is due on 05-Sep-2026."
        val sms = normalizer.normalize(SmsInput("2", "BAJAJFIN", body, System.currentTimeMillis()))

        val reminder = reminderExtractor.extract(body, sms.cleanedBody, "BAJAJFIN")

        assertNotNull(reminder)
        assertEquals("Bajaj Finserv", reminder!!.label)
    }

    @Test
    fun `a reminder with no extractable due date is dropped`() {
        val body = "Your card statement is ready. Please check the app for details."
        val sms = normalizer.normalize(SmsInput("3", "HDFCBK", body, System.currentTimeMillis()))

        assertNull(reminderExtractor.extract(body, sms.cleanedBody, "HDFCBK"))
    }
}
