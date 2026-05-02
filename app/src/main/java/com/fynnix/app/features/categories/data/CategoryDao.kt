package com.fynnix.app.features.categories.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun getAllFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    /**
     * Seeds the sentinel "Uncategorized" row with its literal id (0) via raw SQL.
     * Room's typed @Insert path can't be used for this: with
     * `@PrimaryKey(autoGenerate = true)` on a non-null Long, Room treats an explicit
     * id of exactly 0 as "unset" and lets SQLite auto-assign a different id instead of
     * inserting 0 literally — silently seeding "Uncategorized" under some other id and
     * leaving every categoryId=0 reference throughout the app (the documented fallback
     * for "no category matched") pointing at a row that doesn't exist.
     */
    @Query(
        "INSERT OR IGNORE INTO categories (id, name, icon, colorHex, isDefault, sortOrder) " +
            "VALUES (0, 'Uncategorized', 'help', '#9E9E9E', 1, 99)"
    )
    suspend fun seedUncategorizedCategory()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun updateCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT id FROM categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findIdByName(name: String): Long?
}