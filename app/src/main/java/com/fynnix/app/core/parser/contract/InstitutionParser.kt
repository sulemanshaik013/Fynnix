package com.fynnix.app.core.parser.contract

import com.fynnix.app.core.parser.NormalizedSms
import com.fynnix.app.core.parser.TransactionDraft
import com.fynnix.app.core.parser.TransactionIntent
import com.fynnix.app.core.parser.TrustLevel

/**
 * Strategy pattern: one implementation per institution. Adding HDFC vs ICICI
 * is two classes both honoring this contract; Pipeline never changes.
 *
 * The fallback `GenericFinancialHandler` returns true from canHandle for
 * everything but at the lowest priority — chain-of-responsibility.
 */
interface InstitutionParser {
    val name: String
    val priority: Int                        // higher wins
    val trustLevel: TrustLevel               // assumed trust if sender is unrecognized

    fun canHandle(senderUpper: String): Boolean

    /**
     * Run extraction templates. Return null when this parser cannot
     * produce a usable draft for this message (so the next handler in the
     * chain gets a turn).
     */
    fun parse(sms: NormalizedSms, intent: TransactionIntent): TransactionDraft?
}
