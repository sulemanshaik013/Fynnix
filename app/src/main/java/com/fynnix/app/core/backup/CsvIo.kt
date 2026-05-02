package com.fynnix.app.core.backup

import android.content.Context
import android.net.Uri
import com.fynnix.app.core.utils.TransactionSource
import com.fynnix.app.core.utils.TransactionType
import com.fynnix.app.features.categories.data.CategoryDao
import com.fynnix.app.features.transactions.data.TransactionDao
import com.fynnix.app.features.transactions.data.TransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a CSV import, broken down so the caller can give the user an honest count. */
data class CsvImportResult(
    val imported: Int = 0,
    val skippedExisting: Int = 0,
    val invalid: Int = 0,
)

/**
 * Lightweight CSV bridge for transaction data.
 *
 * Schema (header row, in this exact order):
 *   id,dateTime,type,amount,merchant,categoryId,messageSource,smsReference,rawMessage
 */
@Singleton
class CsvIo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TransactionDao,
    private val categoryDao: CategoryDao,
) {
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    suspend fun exportTo(uri: Uri): Int {
        val tx = dao.getAllTransactions().first()
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { w ->
                w.append(HEADER).append('\n')
                tx.forEach { e ->
                    w.append(toRow(e)).append('\n')
                }
                w.flush()
            }
        }
        return tx.size
    }

    /**
     * Imports rows from [uri]. A row whose id already exists in the database is skipped
     * rather than overwritten — CSV ids are attacker/user-controlled plain text, and
     * silently replacing an existing transaction's data on an id collision (e.g. from
     * re-importing an edited export, or a differently-sourced CSV using the same id
     * scheme) would corrupt live transaction history with no way to detect or undo it.
     */
    suspend fun importFrom(uri: Uri): CsvImportResult {
        val rows = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
                r.readLines()
            }
        } ?: return CsvImportResult()
        if (rows.isEmpty()) return CsvImportResult()

        val data = if (rows.first().startsWith("id,")) rows.drop(1) else rows
        val validCategoryIds = categoryDao.getAll().map { it.id }.toSet()

        var invalid = 0
        var skippedExisting = 0
        val toInsert = mutableListOf<TransactionEntity>()

        for (row in data) {
            val cols = parseRow(row)
            if (cols.size < 9) { invalid++; continue }

            val entity = runCatching {
                val amount = cols[3].toDouble()
                require(amount.isFinite() && amount > 0.0) { "amount out of range" }
                val categoryId = cols[5].toLongOrNull()?.takeIf { it in validCategoryIds } ?: 0L
                TransactionEntity(
                    id = cols[0],
                    dateTime = parseDate(cols[1]),
                    type = TransactionType.valueOf(cols[2]),
                    amount = amount,
                    merchant = cols[4],
                    categoryId = categoryId,
                    messageSource = cols[6],
                    smsReference = cols[7],
                    rawMessage = cols[8],
                    source = TransactionSource.CSV_IMPORT,
                )
            }.getOrNull()

            if (entity == null) { invalid++; continue }
            if (dao.getById(entity.id) != null) { skippedExisting++; continue }
            toInsert += entity
        }

        if (toInsert.isNotEmpty()) dao.insertAllIgnore(toInsert)
        return CsvImportResult(imported = toInsert.size, skippedExisting = skippedExisting, invalid = invalid)
    }

    private fun toRow(e: TransactionEntity): String = listOf(
        e.id,
        df.format(Date(e.dateTime)),
        e.type.name,
        e.amount.toString(),
        e.merchant,
        e.categoryId.toString(),
        e.messageSource,
        e.smsReference,
        e.rawMessage,
    ).joinToString(",") { csvEscape(it) }

    /**
     * Escapes a field for CSV output. Values that start with `=`, `+`, `-`, `@`, tab, or
     * CR are prefixed with a `'` before quoting — spreadsheet apps (Excel, Google Sheets)
     * evaluate such leading characters as a formula, and merchant/raw-message fields
     * originate from SMS text an attacker can influence.
     */
    private fun csvEscape(s: String): String {
        val neutralized = if (s.isNotEmpty() && s[0] in FORMULA_TRIGGER_CHARS) "'$s" else s
        if (neutralized.contains(',') || neutralized.contains('"') || neutralized.contains('\n')) {
            return "\"" + neutralized.replace("\"", "\"\"") + "\""
        }
        return neutralized
    }

    private fun parseRow(row: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < row.length) {
            val c = row[i]
            when {
                inQuotes && c == '"' && i + 1 < row.length && row[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    out.add(sb.toString()); sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    private fun parseDate(s: String): Long = try {
        df.parse(s)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        s.toLongOrNull() ?: System.currentTimeMillis()
    }

    private companion object {
        const val HEADER = "id,dateTime,type,amount,merchant,categoryId,messageSource,smsReference,rawMessage"
        val FORMULA_TRIGGER_CHARS = charArrayOf('=', '+', '-', '@', '\t', '\r')
    }
}
