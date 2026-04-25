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

/** Ordered list passed to Room.addMigrations(). */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
