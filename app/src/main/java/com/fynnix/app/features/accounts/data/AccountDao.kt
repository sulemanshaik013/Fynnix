package com.fynnix.app.features.accounts.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fynnix.app.core.utils.AccountType
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query(
        "SELECT * FROM accounts " +
            "WHERE bankName = :bankName AND lastFourDigits = :last4 AND type = :type LIMIT 1"
    )
    suspend fun findByKey(bankName: String, last4: String, type: AccountType): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getAccountCount(): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(accountEntity: AccountEntity): Long

    @Update
    suspend fun updateAccount(accountEntity: AccountEntity)

    @Query("UPDATE accounts SET balance = :balance, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setBalance(id: Long, balance: Double, updatedAt: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
