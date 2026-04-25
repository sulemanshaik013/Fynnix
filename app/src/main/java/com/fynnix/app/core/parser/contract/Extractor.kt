package com.fynnix.app.core.parser.contract

/**
 * Single-method extractor. T is the field type (Double for amount, String
 * for merchant/account/reference). Each extractor is a pure function of
 * the cleaned/raw body — no side effects, easy to unit test.
 */
fun interface Extractor<T> {
    fun extract(text: String): T?
}
