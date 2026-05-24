package com.fynnix.app.core.utils

enum class AccountType {
    SAVINGS, CREDIT_CARD, WALLET, UPI,
    /** Corporate benefit/prepaid cards — meal, fuel, gift, and multi-purpose employee
     *  benefit cards (Zaggle, Sodexo/Pluxee, Zeta, etc.). Kept distinct from WALLET and
     *  CREDIT_CARD since these are employer-funded, usually non-transferable, and
     *  functionally a separate spending "pot" from the user's own money. */
    BENEFIT_CARD,
}

enum class TransactionSource {
    SMS,
    /** Extracted by the on-device LLM fallback after the deterministic parser pipeline
     *  found no handler or couldn't extract a required field — see LlmTransactionExtractor. */
    SMS_LLM,
    MANUAL, CSV_IMPORT
}

enum class TransactionType {
    DEBIT, CREDIT
}

enum class PaymentMode {
    UPI, NEFT, IMPS, CARD, CASH
}

enum class RuleField {
    MERCHANT, AMOUNT, NOTE
}

enum class RuleCondition {
    CONTAINS, EQUALS, STARTS_WITH
}

enum class PeriodType {
    MONTHLY
}

enum class AccountSource {
    AUTO, MANUAL
}

enum class MandateStatus {
    ACTIVE, CANCELLED
}