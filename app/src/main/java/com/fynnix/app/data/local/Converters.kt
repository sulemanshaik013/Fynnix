package com.fynnix.app.data.local

import androidx.room.TypeConverter
import com.fynnix.app.data.local.entity.PaymentMode
import com.fynnix.app.data.local.entity.PeriodType
import com.fynnix.app.data.local.entity.RuleCondition
import com.fynnix.app.data.local.entity.RuleField
import com.fynnix.app.data.local.entity.TransactionSource
import com.fynnix.app.data.local.entity.TransactionType

class Converters {
    @TypeConverter
    fun fromTransactionSource(value: TransactionSource) = value.name

    @TypeConverter
    fun toTransactionSource(value: String) = TransactionSource.valueOf(value)

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
}