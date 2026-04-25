package com.fynnix.app.core.utils

enum class AccountType {
    SAVINGS, CREDIT_CARD, WALLET,UPI
}

enum class TransactionSource {
    SMS, MANUAL, CSV_IMPORT
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