package com.banksmstracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.banksmstracker.R
import com.banksmstracker.data.MonthYear
import com.banksmstracker.data.Transaction
import com.banksmstracker.data.TransactionType
import com.banksmstracker.viewmodel.TransactionViewModel
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private lateinit var viewModel: TransactionViewModel
    private lateinit var rvTransactions: RecyclerView
    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var monthSpinner: Spinner
    private lateinit var tvEmptyState: TextView
    
    private var availableMonths = mutableListOf<MonthYear>()
    private var selectedMonthPosition = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        
        rvTransactions = view.findViewById(R.id.rvTransactions)
        monthSpinner = view.findViewById(R.id.monthSpinner)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity()).get(TransactionViewModel::class.java)
        
        setupRecyclerView()
        setupMonthSelector()
        observeTransactions()
    }
    
    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter()
        rvTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionAdapter
        }
    }
    
    private fun setupMonthSelector() {
        viewModel.availableMonths.observe(viewLifecycleOwner) { months ->
            if (months != null && months.isNotEmpty()) {
                availableMonths = months.toMutableList()
                
                // Format month-year for display
                val displayMonths = months.map { 
                    viewModel.formatMonthYear(it.monthYear)
                }
                
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    displayMonths
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                monthSpinner.adapter = adapter
                
                // Set current month as selected if available
                val currentDate = Calendar.getInstance()
                val currentMonthYear = String.format(
                    "%04d-%02d", 
                    currentDate.get(Calendar.YEAR),
                    currentDate.get(Calendar.MONTH) + 1
                )
                
                val currentMonthIndex = months.indexOfFirst { it.monthYear == currentMonthYear }
                if (currentMonthIndex >= 0) {
                    monthSpinner.setSelection(currentMonthIndex)
                    selectedMonthPosition = currentMonthIndex
                }
            } else {
                // If no data, just show current month
                val calendar = Calendar.getInstance()
                val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    arrayOf(monthFormat.format(calendar.time))
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                monthSpinner.adapter = adapter
            }
        }
        
        monthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (availableMonths.isNotEmpty() && position < availableMonths.size) {
                    selectedMonthPosition = position
                    val selectedMonthYear = availableMonths[position].monthYear
                    val parts = selectedMonthYear.split("-")
                    if (parts.size == 2) {
                        val year = parts[0].toInt()
                        val month = parts[1].toInt() - 1 // Adjusting for 0-based month
                        viewModel.setCurrentMonth(month, year)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }
    
    private fun observeTransactions() {
        viewModel.monthlyTransactions.observe(viewLifecycleOwner) { transactions ->
            if (transactions.isNullOrEmpty()) {
                tvEmptyState.visibility = View.VISIBLE
                rvTransactions.visibility = View.GONE
            } else {
                tvEmptyState.visibility = View.GONE
                rvTransactions.visibility = View.VISIBLE
                transactionAdapter.submitList(transactions)
            }
        }
    }
    
    /**
     * Adapter for transaction history list.
     */
    private inner class TransactionAdapter : 
            RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {
        
        private var transactions: List<Transaction> = emptyList()
        
        fun submitList(newList: List<Transaction>) {
            transactions = newList
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_transaction, parent, false)
            return TransactionViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
            holder.bind(transactions[position])
        }
        
        override fun getItemCount() = transactions.size
        
        inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
            private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
            private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
            private val tvSource: TextView = itemView.findViewById(R.id.tvSource)
            
            fun bind(transaction: Transaction) {
                // Format amount with plus/minus sign
                val amountText = when (transaction.type) {
                    TransactionType.INCOME -> "+₹${transaction.amount}"
                    TransactionType.EXPENSE -> "-₹${transaction.amount}"
                }
                
                tvAmount.text = amountText
                tvAmount.setTextColor(
                    resources.getColor(
                        if (transaction.type == TransactionType.INCOME)
                            android.R.color.holo_green_dark
                        else
                            android.R.color.holo_red_dark
                    )
                )
                
                // Format date
                val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                tvDate.text = dateFormat.format(transaction.date)
                
                tvDescription.text = transaction.description
                tvSource.text = transaction.source
            }
        }
    }
}
