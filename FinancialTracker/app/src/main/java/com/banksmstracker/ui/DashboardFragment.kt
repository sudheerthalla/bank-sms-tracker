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
import com.banksmstracker.R
import com.banksmstracker.data.MonthYear
import com.banksmstracker.viewmodel.TransactionViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private lateinit var viewModel: TransactionViewModel
    private lateinit var pieChart: PieChart
    private lateinit var monthSpinner: Spinner
    private lateinit var tvTotalIncome: TextView
    private lateinit var tvTotalExpense: TextView
    private lateinit var tvNetAmount: TextView
    
    private var availableMonths = mutableListOf<MonthYear>()
    private var selectedMonthPosition = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)
        
        pieChart = view.findViewById(R.id.pieChart)
        monthSpinner = view.findViewById(R.id.monthSpinner)
        tvTotalIncome = view.findViewById(R.id.tvTotalIncome)
        tvTotalExpense = view.findViewById(R.id.tvTotalExpense)
        tvNetAmount = view.findViewById(R.id.tvNetAmount)
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(requireActivity()).get(TransactionViewModel::class.java)
        
        setupMonthSelector()
        setupChart()
        observeData()
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
    
    private fun setupChart() {
        pieChart.description.isEnabled = false
        pieChart.setUsePercentValues(true)
        pieChart.setExtraOffsets(5f, 10f, 5f, 5f)
        pieChart.dragDecelerationFrictionCoef = 0.95f
        pieChart.isDrawHoleEnabled = true
        pieChart.setHoleColor(android.R.color.white)
        pieChart.setTransparentCircleColor(android.R.color.white)
        pieChart.setTransparentCircleAlpha(110)
        pieChart.holeRadius = 58f
        pieChart.transparentCircleRadius = 61f
        pieChart.setDrawCenterText(true)
        pieChart.rotationAngle = 0f
        pieChart.isRotationEnabled = true
        pieChart.highlightPerTapEnabled = true
        pieChart.legend.isEnabled = true
        pieChart.setCenterTextSize(16f)
    }
    
    private fun observeData() {
        viewModel.totalIncome.observe(viewLifecycleOwner) { income ->
            val incomeValue = income ?: 0.0
            tvTotalIncome.text = String.format("₹%.2f", incomeValue)
            updateChart()
        }
        
        viewModel.totalExpense.observe(viewLifecycleOwner) { expense ->
            val expenseValue = expense ?: 0.0
            tvTotalExpense.text = String.format("₹%.2f", expenseValue)
            updateChart()
        }
        
        // Calculate and display net amount
        viewModel.totalIncome.observe(viewLifecycleOwner) { income ->
            viewModel.totalExpense.observe(viewLifecycleOwner) { expense ->
                val incomeValue = income ?: 0.0
                val expenseValue = expense ?: 0.0
                val net = incomeValue - expenseValue
                
                tvNetAmount.text = String.format("₹%.2f", net)
                tvNetAmount.setTextColor(
                    resources.getColor(
                        if (net >= 0) android.R.color.holo_green_dark 
                        else android.R.color.holo_red_dark
                    )
                )
            }
        }
    }
    
    private fun updateChart() {
        val income = viewModel.totalIncome.value ?: 0.0
        val expense = viewModel.totalExpense.value ?: 0.0
        
        // Only show chart if we have data
        if (income <= 0 && expense <= 0) {
            pieChart.visibility = View.GONE
            return
        }
        
        pieChart.visibility = View.VISIBLE
        
        val entries = ArrayList<PieEntry>()
        
        if (income > 0) {
            entries.add(PieEntry(income.toFloat(), "Income"))
        }
        
        if (expense > 0) {
            entries.add(PieEntry(expense.toFloat(), "Expense"))
        }
        
        val dataSet = PieDataSet(entries, "Financial Summary")
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        
        // Use green for income, red for expense
        val colors = ArrayList<Int>()
        colors.add(resources.getColor(android.R.color.holo_green_light))
        colors.add(resources.getColor(android.R.color.holo_red_light))
        dataSet.colors = colors
        
        val data = PieData(dataSet)
        data.setValueTextSize(15f)
        data.setValueTextColor(resources.getColor(android.R.color.white))
        
        pieChart.data = data
        pieChart.centerText = "Monthly\nSummary"
        
        // Refresh the chart
        pieChart.invalidate()
    }
}
