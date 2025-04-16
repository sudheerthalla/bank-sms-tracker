package com.banksmstracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.banksmstracker.data.MonthYear
import com.banksmstracker.data.Transaction
import com.banksmstracker.data.TransactionRepository
import java.util.*

/**
 * ViewModel to manage UI-related data in a lifecycle-conscious way.
 */
class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: TransactionRepository = TransactionRepository(application)
    
    // Current month and year
    private val currentMonth = MutableLiveData<Int>()
    private val currentYear = MutableLiveData<Int>()
    
    // List of all transactions for the current month
    val monthlyTransactions: LiveData<List<Transaction>> = 
        Transformations.switchMap(currentMonth) { month ->
            Transformations.switchMap(currentYear) { year ->
                repository.getTransactionsForMonth(month, year)
            }
        }
    
    // Total income for the current month
    val totalIncome: LiveData<Double> = 
        Transformations.switchMap(currentMonth) { month ->
            Transformations.switchMap(currentYear) { year ->
                repository.getTotalIncomeForMonth(month, year)
            }
        }
    
    // Total expenses for the current month
    val totalExpense: LiveData<Double> = 
        Transformations.switchMap(currentMonth) { month ->
            Transformations.switchMap(currentYear) { year ->
                repository.getTotalExpenseForMonth(month, year)
            }
        }
    
    // List of all available months with transaction data
    val availableMonths: LiveData<List<MonthYear>> = repository.getAvailableMonths()
    
    init {
        // Initialize with current month and year
        val calendar = Calendar.getInstance()
        currentMonth.value = calendar.get(Calendar.MONTH)
        currentYear.value = calendar.get(Calendar.YEAR)
    }
    
    /**
     * Set the current month for which to display data.
     */
    fun setCurrentMonth(month: Int, year: Int) {
        currentMonth.value = month
        currentYear.value = year
    }
    
    /**
     * Format month-year string for display.
     */
    fun formatMonthYear(monthYear: String): String {
        return repository.formatMonthYear(monthYear)
    }
}
