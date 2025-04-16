package com.banksmstracker.data

import android.content.Context
import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository to manage Transaction data operations.
 */
class TransactionRepository(context: Context) {
    
    private val transactionDao: TransactionDao
    
    init {
        val database = TransactionDatabase.getDatabase(context)
        transactionDao = database.transactionDao()
    }
    
    // Insert transaction using coroutine
    fun insert(transaction: Transaction) {
        CoroutineScope(Dispatchers.IO).launch {
            transactionDao.insert(transaction)
        }
    }
    
    // Get all transactions
    fun getAllTransactions(): LiveData<List<Transaction>> {
        return transactionDao.getAllTransactions()
    }
    
    // Get all income transactions
    fun getAllIncome(): LiveData<List<Transaction>> {
        return transactionDao.getAllIncome()
    }
    
    // Get all expense transactions
    fun getAllExpenses(): LiveData<List<Transaction>> {
        return transactionDao.getAllExpenses()
    }
    
    // Get transactions for a specific month
    fun getTransactionsForMonth(month: Int, year: Int): LiveData<List<Transaction>> {
        val monthStr = String.format("%02d", month + 1) // +1 because Calendar.MONTH is 0-based
        val yearStr = year.toString()
        return transactionDao.getTransactionsForMonth(monthStr, yearStr)
    }
    
    // Get total income for a specific month
    fun getTotalIncomeForMonth(month: Int, year: Int): LiveData<Double> {
        val monthStr = String.format("%02d", month + 1)
        val yearStr = year.toString()
        return transactionDao.getTotalIncomeForMonth(monthStr, yearStr)
    }
    
    // Get total expenses for a specific month
    fun getTotalExpenseForMonth(month: Int, year: Int): LiveData<Double> {
        val monthStr = String.format("%02d", month + 1)
        val yearStr = year.toString()
        return transactionDao.getTotalExpenseForMonth(monthStr, yearStr)
    }
    
    // Get available months with transaction data
    fun getAvailableMonths(): LiveData<List<MonthYear>> {
        return transactionDao.getAvailableMonths()
    }
    
    /**
     * Formats a month-year string for display.
     */
    fun formatMonthYear(monthYear: String): String {
        val parts = monthYear.split("-")
        if (parts.size != 2) return monthYear
        
        val year = parts[0]
        val month = parts[1].toInt()
        
        val monthNames = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        
        return "${monthNames[month-1]} $year"
    }
    
    /**
     * Gets current month and year as formatted strings.
     */
    fun getCurrentMonth(): Pair<String, String> {
        val calendar = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MM", Locale.getDefault())
        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
        
        return Pair(monthFormat.format(calendar.time), yearFormat.format(calendar.time))
    }
}
