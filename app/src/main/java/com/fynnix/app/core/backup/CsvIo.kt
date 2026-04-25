package com.fynnix.app.core.backup

import android.content.Context
import android.net.Uri
import com.fynnix.app.core.utils.TransactionSource
import com.fynnix.app.core.utils.TransactionType
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

    suspend fun importFrom(uri: Uri): Int {
        val rows = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
                r.readLines()
            }
        } ?: return 0
        if (rows.isEmpty()) return 0

        val data = if (rows.first().startsWith("id,")) rows.drop(1) else rows
        var inserted = 0
        for (row in data) {
            val cols = parseRow(row)
            if (cols.size < 9) continue
            val entity = runCatching {
                TransactionEntity(
                    id = cols[0],
                    dateTime = parseDate(cols[1]),
                    type = TransactionType.valueOf(cols[2]),
                    amount = cols[3].toDouble(),
                    merchant = cols[4],
                    categoryId = cols[5].toLongOrNull() ?: 0L,
                    messageSource = cols[6],
                    smsReference = cols[7],
                    rawMessage = cols[8],
                    source = TransactionSource.CSV_IMPORT,
                )
            }.getOrNull() ?: continue
            dao.insertReplace(entity)
            inserted++
        }
        return inserted
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

    private fun csvEscape(s: String): String {
        if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            return "\"" + s.replace("\"", "\"\"") + "\""
        }
        return s
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
    }
}
