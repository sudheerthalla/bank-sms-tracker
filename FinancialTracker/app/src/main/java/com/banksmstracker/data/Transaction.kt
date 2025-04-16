package com.banksmstracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.*

/**
 * Enum representing transaction types.
 */
enum class TransactionType {
    INCOME, EXPENSE
}

/**
 * Entity representing a bank transaction extracted from SMS.
 */
@Entity(tableName = "transactions")
@TypeConverters(Converters::class)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val type: TransactionType,
    val amount: Double,
    val date: Date,
    val source: String,  // The SMS sender (bank identifier)
    val description: String,
    val rawMessage: String  // Original SMS message
)

/**
 * Type converters for Room database.
 */
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String {
        return value.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return enumValueOf(value)
    }
}
