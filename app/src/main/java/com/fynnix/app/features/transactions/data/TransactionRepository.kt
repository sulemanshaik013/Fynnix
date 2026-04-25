package com.fynnix.app.features.transactions.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.fynnix.app.features.categories.data.CategoryDao
import com.fynnix.app.features.categories.data.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) {

    val categories: Flow<List<CategoryEntity>> = categoryDao.getAllFlow()

    val categoryMetaFlow: Flow<Map<Long, Triple<String, ImageVector, Color>>> =
        categories.map { list -> list.associate { it.id to it.toUiMeta() } }

    fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions().map { it.map(TransactionEntity::toDomain) }

    fun getTransactions(): Flow<List<Transaction>> = getAllTransactions()

    suspend fun insertTransaction(transaction: Transaction) =
        transactionDao.insertReplace(transaction.toEntity())

    suspend fun insertTransactions(transactions: List<Transaction>) {
        transactions.forEach { transactionDao.insertReplace(it.toEntity()) }
    }

    suspend fun update(transaction: Transaction) =
        transactionDao.update(transaction.toEntity())

    suspend fun getById(id: String): Transaction? = transactionDao.getById(id)?.toDomain()

    suspend fun softDelete(id: String) = transactionDao.softDelete(id, System.currentTimeMillis())

    suspend fun markReviewed(id: String) = transactionDao.markReviewed(id)

    suspend fun getCount(): Long = transactionDao.getCount()

    suspend fun clearAll() = transactionDao.clearAll()
}

fun CategoryEntity.toUiMeta(): Triple<String, ImageVector, Color> {
    val iv: ImageVector = when (icon) {
        "restaurant" -> Icons.Default.Restaurant
        "car", "directions_car" -> Icons.Default.DirectionsCar
        "home" -> Icons.Default.Home
        "shopping", "shopping_bag" -> Icons.Default.ShoppingBag
        "receipt" -> Icons.Default.Receipt
        "movie" -> Icons.Default.Movie
        "medical_services" -> Icons.Default.MedicalServices
        "payments" -> Icons.Default.Payments
        "help" -> Icons.Default.Help
        else -> Icons.Default.Category
    }
    val color = try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        Color.Gray
    }
    return Triple(name, iv, color)
}

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    amount = amount,
    currency = currency,
    merchant = merchant,
    categoryId = categoryId,
    accountId = accountId,
    source = source,
    dateTime = dateTime,
    type = type,
    isReviewed = isReviewed,
    isSplit = isSplit,
    splitAmount = splitAmount,
    isReimbursement = isReimbursement,
    note = note,
    rawMessage = rawMessage,
    messageSource = messageSource,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    amount = amount,
    currency = currency,
    type = type,
    merchant = merchant,
    categoryId = categoryId,
    accountId = accountId,
    dateTime = dateTime,
    source = source,
    isReviewed = isReviewed,
    isSplit = isSplit,
    splitAmount = splitAmount,
    isReimbursement = isReimbursement,
    note = note,
    rawMessage = rawMessage,
    messageSource = messageSource,
)
