package com.fynnix.app.core.database

import androidx.room.TypeConverter
import com.fynnix.app.core.utils.AccountType
import com.fynnix.app.core.utils.MandateStatus
import com.fynnix.app.core.utils.PaymentMode
import com.fynnix.app.core.utils.PeriodType
import com.fynnix.app.core.utils.RuleCondition
import com.fynnix.app.core.utils.RuleField
import com.fynnix.app.core.utils.TransactionSource
import com.fynnix.app.core.utils.TransactionType

class Converters {
    @TypeConverter
    fun fromTransactionSource(value: TransactionSource) = value.name

    @TypeConverter
    fun toTransactionSource(value: String) = TransactionSource.valueOf(value)

    @TypeConverter
    fun fromAccountType(value: AccountType) = value.name

    @TypeConverter
    fun toAccountType(value: String) = AccountType.valueOf(value)

    @TypeConverter
    fun fromTransactionType(value: TransactionType) = value.name

    @TypeConverter
    fun toTransactionType(value: String) = TransactionType.valueOf(value)

    @TypeConverter
    fun fromPaymentMode(value: PaymentMode?) = value?.name

    @TypeConverter
    fun toPaymentMode(value: String?) = value?.let { PaymentMode.valueOf(it) }

    @TypeConverter
    fun fromRuleField(value: RuleField) = value.name

    @TypeConverter
    fun toRuleField(value: String) = RuleField.valueOf(value)

    @TypeConverter
    fun fromRuleCondition(value: RuleCondition) = value.name

    @TypeConverter
    fun toRuleCondition(value: String) = RuleCondition.valueOf(value)

    @TypeConverter
    fun fromPeriodType(value: PeriodType) = value.name

    @TypeConverter
    fun toPeriodType(value: String) = PeriodType.valueOf(value)

    @TypeConverter
    fun fromMandateStatus(value: MandateStatus) = value.name

    @TypeConverter
    fun toMandateStatus(value: String) = MandateStatus.valueOf(value)
}