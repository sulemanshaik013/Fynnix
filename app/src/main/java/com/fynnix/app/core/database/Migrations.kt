package com.fynnix.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * All Room schema migrations in version order.
 *
 * Rules:
 *  - Never modify a migration once it has shipped to production.
 *  - Every schema change to AppDatabase MUST have a migration here.
 *  - Bump AppDatabase.version by 1 and add MIGRATION_(old)_(new).
 *
 * Current history:
 *  1 → 2  : add transactions.deletedAt (soft delete)
 *  2 → 3  : drop categories.budget (canonical budget lives in BudgetEntity)
 *  3 → 4  : add merchant_category_overrides.updatedAt
 *  4 → 5  : dedupe transactions by dedupKey, make dedupKey unique, add FK constraints
 *           on transactions.categoryId/accountId, add index on transactions.accountId
 *  5 → 6  : add subscription_mandates (confirmed recurring debits parsed from
 *           e-mandate/standing-instruction SMS, keyed one row per merchant)
 *  6 → 7  : add bill_reminders (due-date bill/statement reminders parsed from SMS),
 *           add subscription_mandates.notifiedForDate (bill-notification dedup)
 */

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE transactions ADD COLUMN deletedAt INTEGER"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite does not support DROP COLUMN directly before API 35.
        // Recreate the table without the budget column.
        db.execSQL(
            """
            CREATE TABLE categories_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                icon TEXT NOT NULL,
                colorHex TEXT NOT NULL,
                isDefault INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO categories_new (id, name, icon, colorHex, isDefault, sortOrder) " +
                "SELECT id, name, icon, colorHex, isDefault, sortOrder FROM categories"
        )
        db.execSQL("DROP TABLE categories")
        db.execSQL("ALTER TABLE categories_new RENAME TO categories")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE merchant_category_overrides ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // The "Uncategorized" sentinel category (id 0) was never reliably seeded prior
        // to this migration: Room's typed insert path treats an explicit id of exactly
        // 0 on an autoGenerate primary key as "unset" and lets SQLite assign a different
        // id instead, so every categoryId=0 reference throughout the app has actually
        // been pointing at a row that doesn't exist. Ensure it exists via raw SQL (which
        // respects the literal value) before the FK constraint added below can enforce
        // that categoryId must reference a real row.
        db.execSQL(
            "INSERT OR IGNORE INTO categories (id, name, icon, colorHex, isDefault, sortOrder) " +
                "VALUES (0, 'Uncategorized', 'help', '#9E9E9E', 1, 99)"
        )
        // Re-point any transaction whose categoryId/accountId doesn't reference an
        // existing row (a latent consequence of the bug above, or of a category/account
        // deleted before FK cascade behavior existed) so the FK-constrained table below
        // can be populated without failing on pre-existing inconsistent data.
        db.execSQL("UPDATE transactions SET categoryId = 0 WHERE categoryId NOT IN (SELECT id FROM categories)")
        db.execSQL("UPDATE transactions SET accountId = NULL WHERE accountId IS NOT NULL AND accountId NOT IN (SELECT id FROM accounts)")

        // Remove pre-existing duplicate rows before the unique index can be created —
        // a non-unique dedupKey index let a TOCTOU race between concurrent sync paths
        // insert two rows for the same real transaction. Keep the earliest-inserted
        // (lowest rowid) row per dedupKey; NULL dedupKeys (manual entries) are exempt
        // since SQLite already treats every NULL as distinct.
        db.execSQL(
            """
            DELETE FROM transactions
            WHERE dedupKey IS NOT NULL
            AND rowid NOT IN (
                SELECT MIN(rowid) FROM transactions
                WHERE dedupKey IS NOT NULL
                GROUP BY dedupKey
            )
            """.trimIndent()
        )

        // SQLite can't ALTER TABLE to add a FOREIGN KEY or change an index's uniqueness —
        // recreate the table with the full target schema.
        db.execSQL(
            """
            CREATE TABLE transactions_new (
                id TEXT NOT NULL PRIMARY KEY,
                amount REAL NOT NULL,
                currency TEXT NOT NULL,
                type TEXT NOT NULL,
                merchant TEXT NOT NULL,
                categoryId INTEGER NOT NULL DEFAULT 0,
                accountId INTEGER,
                dateTime INTEGER NOT NULL,
                source TEXT NOT NULL,
                smsReference TEXT NOT NULL,
                rawMessage TEXT NOT NULL,
                messageSource TEXT NOT NULL,
                parsingConfidence REAL NOT NULL,
                smsHash TEXT,
                dedupKey TEXT,
                isReviewed INTEGER NOT NULL,
                isDuplicate INTEGER NOT NULL,
                isExcluded INTEGER NOT NULL,
                isSplit INTEGER NOT NULL,
                splitAmount REAL,
                isReimbursement INTEGER NOT NULL,
                isUserEdited INTEGER NOT NULL,
                note TEXT,
                createdAt INTEGER NOT NULL,
                syncedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET DEFAULT,
                FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO transactions_new (
                id, amount, currency, type, merchant, categoryId, accountId, dateTime,
                source, smsReference, rawMessage, messageSource, parsingConfidence,
                smsHash, dedupKey, isReviewed, isDuplicate, isExcluded, isSplit,
                splitAmount, isReimbursement, isUserEdited, note, createdAt, syncedAt, deletedAt
            )
            SELECT
                id, amount, currency, type, merchant, categoryId, accountId, dateTime,
                source, smsReference, rawMessage, messageSource, parsingConfidence,
                smsHash, dedupKey, isReviewed, isDuplicate, isExcluded, isSplit,
                splitAmount, isReimbursement, isUserEdited, note, createdAt, syncedAt, deletedAt
            FROM transactions
            """.trimIndent()
        )
        db.execSQL("DROP TABLE transactions")
        db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")

        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_dateTime ON transactions(dateTime)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_categoryId ON transactions(categoryId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_accountId ON transactions(accountId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_smsHash ON transactions(smsHash)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_dedupKey ON transactions(dedupKey)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS subscription_mandates (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                merchant TEXT NOT NULL,
                amount REAL,
                umn TEXT NOT NULL,
                nextDueDate INTEGER,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_subscription_mandates_merchant " +
                "ON subscription_mandates(merchant)"
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscription_mandates ADD COLUMN notifiedForDate INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bill_reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                label TEXT NOT NULL,
                amount REAL,
                dueDate INTEGER,
                notifiedForDate INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_bill_reminders_label ON bill_reminders(label)"
        )
    }
}

/** Ordered list passed to Room.addMigrations(). */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
)
