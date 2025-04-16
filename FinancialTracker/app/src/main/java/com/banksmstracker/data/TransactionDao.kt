package com.banksmstracker.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.*

/**
 * Data Access Object (DAO) for Transaction entities.
 */
@Dao
interface TransactionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long
    
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): LiveData<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE type = 'INCOME' ORDER BY date DESC")
    fun getAllIncome(): LiveData<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE type = 'EXPENSE' ORDER BY date DESC")
    fun getAllExpenses(): LiveData<List<Transaction>>
    
    /**
     * Gets all transactions for a specific month and year.
     */
    @Query("""
        SELECT * FROM transactions 
        WHERE strftime('%m', date / 1000, 'unixepoch') = :month 
        AND strftime('%Y', date / 1000, 'unixepoch') = :year
        ORDER BY date DESC
    """)
    fun getTransactionsForMonth(month: String, year: String): LiveData<List<Transaction>>
    
    /**
     * Gets the sum of income for a specific month and year.
     */
    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE type = 'INCOME' 
        AND strftime('%m', date / 1000, 'unixepoch') = :month 
        AND strftime('%Y', date / 1000, 'unixepoch') = :year
    """)
    fun getTotalIncomeForMonth(month: String, year: String): LiveData<Double>
    
    /**
     * Gets the sum of expenses for a specific month and year.
     */
    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE type = 'EXPENSE' 
        AND strftime('%m', date / 1000, 'unixepoch') = :month 
        AND strftime('%Y', date / 1000, 'unixepoch') = :year
    """)
    fun getTotalExpenseForMonth(month: String, year: String): LiveData<Double>
    
    /**
     * Gets a list of all months for which we have transactions, with count of transactions.
     */
    @Query("""
        SELECT 
            strftime('%Y', date / 1000, 'unixepoch') || '-' || 
            strftime('%m', date / 1000, 'unixepoch') as monthYear, 
            COUNT(*) as count
        FROM transactions
        GROUP BY monthYear
        ORDER BY monthYear DESC
    """)
    fun getAvailableMonths(): LiveData<List<MonthYear>>
}

/**
 * Helper data class for month selection.
 */
data class MonthYear(
    val monthYear: String,
    val count: Int
)
