package com.fynnix.app.core.utils

object AppConstants {

    // ---- Parser / confidence ----------------------------------------
    /** Minimum confidence score (0–100) for a draft to be accepted as a transaction. */
    const val CONFIDENCE_THRESHOLD = 55

    // 1 crore was too tight — property/business NEFT/RTGS transfers legitimately exceed
    // it, and the whole transaction was dropped, not just the amount. 100 crore still
    // guards against garbled OCR/parsing noise while covering real high-value transfers.
    /** Sanity ceiling (INR) for an extracted amount; larger values are rejected as noise. */
    const val AMOUNT_MAX_INR = 1.0e9

    // ---- Deduplication ----------------------------------------------
    const val DAY_MILLIS = 24L * 60 * 60 * 1_000L

    // ---- Transfer pair detection ------------------------------------
    /** Maximum amount delta (INR) for two transactions to be treated as a transfer pair. */
    const val TRANSFER_AMOUNT_DELTA_INR = 0.50

    /** Maximum time window (days) between the two legs of an internal transfer. */
    const val TRANSFER_WINDOW_DAYS = 5

    /** Tighter window (days) used when keyword evidence is absent. */
    const val TRANSFER_TIGHT_WINDOW_DAYS = 2

    // ---- Subscription detection -------------------------------------
    /** Minimum interval (days) between transactions to qualify as a monthly subscription. */
    const val SUBSCRIPTION_MIN_INTERVAL_DAYS = 25

    /** Maximum interval (days) between transactions to qualify as a monthly subscription. */
    const val SUBSCRIPTION_MAX_INTERVAL_DAYS = 35

    /** Allowed fractional variance in amount across subscription occurrences. */
    const val SUBSCRIPTION_AMOUNT_VARIANCE = 0.15

    /** Minimum occurrences to classify a merchant as a subscription. */
    const val SUBSCRIPTION_MIN_OCCURRENCES = 3

    // ---- Bill / subscription reminders -------------------------------
    /** How many days ahead of a confirmed due date to fire a local notification. */
    const val BILL_NOTIFY_WINDOW_DAYS = 2L

    // ---- Insights ---------------------------------------------------
    /** Hour at which "late-night spending" window starts (23 = 11 PM). */
    const val LATE_NIGHT_START_HOUR = 23

    /** Hour at which "late-night spending" window ends (5 = 5 AM). */
    const val LATE_NIGHT_END_HOUR = 5

    /** Minimum late-night transaction count before the insight is surfaced. */
    const val LATE_NIGHT_MIN_TRANSACTIONS = 3

    // ---- Security ---------------------------------------------------
    /** PBKDF2 iteration count for PIN hashing. */
    const val PIN_PBKDF2_ITERATIONS = 200_000

    /** Length of the PIN salt in bytes. */
    const val PIN_SALT_BYTES = 16

    /** Length of the PBKDF2 output hash in bits. */
    const val PIN_HASH_BITS = 256

    /** Minimum accepted PIN length. */
    const val PIN_MIN_LENGTH = 4

    /** Maximum accepted PIN length. */
    const val PIN_MAX_LENGTH = 6

    /** Minimum length for a backup-file password — deliberately longer than the app-lock
     *  PIN since this password is the only thing protecting a backup file that could end
     *  up anywhere (cloud storage, shared drive, email attachment). */
    const val BACKUP_PASSWORD_MIN_LENGTH = 8

    // ---- Date extraction --------------------------------------------
    /**
     * Grace period (ms) for the "no-year" date anchor.
     * If the anchored date is within this window in the future we accept it
     * as "today"; beyond it, we roll the year back by one.
     * 7 days covers SMS delivery delays and Dec 31 → Jan 1 edge cases.
     */
    const val DATE_FUTURE_GRACE_MS = 7L * DAY_MILLIS

    // ---- SMS receiver -----------------------------------------------
    /** Maximum incremental sync jobs allowed per minute (rate-limit bucket). */
    const val SMS_RATE_LIMIT_PER_MINUTE = 10

    // ---- Sync -------------------------------------------------------
    /** Default look-back window (days) for the initial bulk sync. */
    const val DEFAULT_SYNC_DAYS = 90

    /** Periodic sync interval (hours). */
    const val PERIODIC_SYNC_HOURS = 6L
}
