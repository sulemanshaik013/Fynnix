package com.fynnix.app.core.di

import com.fynnix.app.core.parser.contract.ConfidenceScorer
import com.fynnix.app.core.parser.contract.InstitutionParser
import com.fynnix.app.core.parser.contract.SmsFilter
import com.fynnix.app.core.parser.contract.TransactionValidator
import com.fynnix.app.core.parser.filter.OtpFilter
import com.fynnix.app.core.parser.filter.PromotionalFilter
import com.fynnix.app.core.parser.filter.SanityFilter
import com.fynnix.app.core.parser.institution.AxisBankHandler
import com.fynnix.app.core.parser.institution.BankOfBarodaHandler
import com.fynnix.app.core.parser.institution.BankOfIndiaHandler
import com.fynnix.app.core.parser.institution.CanaraBankHandler
import com.fynnix.app.core.parser.institution.FederalBankHandler
import com.fynnix.app.core.parser.institution.GenericFinancialHandler
import com.fynnix.app.core.parser.institution.HDFCBankHandler
import com.fynnix.app.core.parser.institution.ICICIBankHandler
import com.fynnix.app.core.parser.institution.IDFCFirstBankHandler
import com.fynnix.app.core.parser.institution.IndusIndBankHandler
import com.fynnix.app.core.parser.institution.KotakBankHandler
import com.fynnix.app.core.parser.institution.PNBBankHandler
import com.fynnix.app.core.parser.institution.PaytmHandler
import com.fynnix.app.core.parser.institution.RBLBankHandler
import com.fynnix.app.core.parser.institution.SBIBankHandler
import com.fynnix.app.core.parser.institution.UnionBankHandler
import com.fynnix.app.core.parser.institution.YesBankHandler
import com.fynnix.app.core.parser.scoring.ConfidenceEngine
import com.fynnix.app.core.parser.validator.DefaultTransactionValidator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {

    /* --- filters (priority field controls order) --- */
    @Binds @IntoSet abstract fun bindSanityFilter(impl: SanityFilter): SmsFilter
    @Binds @IntoSet abstract fun bindOtpFilter(impl: OtpFilter): SmsFilter
    @Binds @IntoSet abstract fun bindPromoFilter(impl: PromotionalFilter): SmsFilter

    /* --- institution parsers (priority field controls order in registry) --- */
    @Binds @IntoSet abstract fun bindHdfc(impl: HDFCBankHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindIcici(impl: ICICIBankHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindSbi(impl: SBIBankHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindAxis(impl: AxisBankHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindKotak(impl: KotakBankHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindIdfc(impl: IDFCFirstBankHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindPnb(impl: PNBBankHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindBob(impl: BankOfBarodaHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindCanara(impl: CanaraBankHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindYes(impl: YesBankHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindIndus(impl: IndusIndBankHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindFederal(impl: FederalBankHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindRbl(impl: RBLBankHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindBoi(impl: BankOfIndiaHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindUnion(impl: UnionBankHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindPaytm(impl: PaytmHandler): InstitutionParser
    @Binds @IntoSet abstract fun bindGeneric(impl: GenericFinancialHandler): InstitutionParser

    /* --- single-bind contracts --- */
    @Binds abstract fun bindScorer(impl: ConfidenceEngine): ConfidenceScorer
    @Binds abstract fun bindValidator(impl: DefaultTransactionValidator): TransactionValidator
}
