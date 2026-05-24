package com.fynnix.app.features.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantIntentParserTest {

    @Test
    fun `parses category spend with an explicit period`() {
        val intent = AssistantIntentParser.parse(
            """{"intent": "CATEGORY_SPEND", "category": "Food", "period": "THIS_WEEK"}"""
        )

        assertTrue(intent is AssistantIntent.CategorySpend)
        val spend = intent as AssistantIntent.CategorySpend
        assertEquals("Food", spend.categoryName)
        assertEquals(AssistantPeriod.THIS_WEEK, spend.period)
    }

    @Test
    fun `category spend with a blank category falls back to unknown`() {
        val intent = AssistantIntentParser.parse(
            """{"intent": "CATEGORY_SPEND", "category": "", "period": "THIS_MONTH"}"""
        )

        assertEquals(AssistantIntent.Unknown, intent)
    }

    @Test
    fun `defaults to this month when period is omitted`() {
        val intent = AssistantIntentParser.parse("""{"intent": "PERIOD_TOTAL"}""")

        assertTrue(intent is AssistantIntent.PeriodTotal)
        assertEquals(AssistantPeriod.THIS_MONTH, (intent as AssistantIntent.PeriodTotal).period)
    }

    @Test
    fun `parses the parameterless intents`() {
        assertEquals(AssistantIntent.SubscriptionsList, AssistantIntentParser.parse("""{"intent": "SUBSCRIPTIONS"}"""))
        assertEquals(AssistantIntent.BudgetStatus, AssistantIntentParser.parse("""{"intent": "BUDGET_STATUS"}"""))
        assertEquals(AssistantIntent.NetWorth, AssistantIntentParser.parse("""{"intent": "NET_WORTH"}"""))
    }

    @Test
    fun `unrecognized intent name falls back to unknown`() {
        assertEquals(AssistantIntent.Unknown, AssistantIntentParser.parse("""{"intent": "SOMETHING_ELSE"}"""))
    }

    @Test
    fun `unparseable text falls back to unknown`() {
        assertEquals(AssistantIntent.Unknown, AssistantIntentParser.parse("I don't know what you mean."))
    }
}
