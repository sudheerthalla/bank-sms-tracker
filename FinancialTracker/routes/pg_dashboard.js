const express = require('express');
const router = express.Router();
const db = require('../models/pg_database');
const moment = require('moment');

// Helper function to get current month and year
function getCurrentMonthYear() {
  return {
    month: moment().format('MM'),
    year: moment().format('YYYY')
  };
}

// Dashboard route - shows summary of current month
router.get('/', async (req, res) => {
  try {
    const { month, year } = req.query.month && req.query.year 
      ? { month: req.query.month, year: req.query.year } 
      : getCurrentMonthYear();
    
    // Get available months for the dropdown
    const months = await db.getAvailableMonths();
    
    // Format the month names for display
    const formattedMonths = months.map(m => ({
      value: `${m.year}-${m.month}`,
      display: moment(`${m.year}-${m.month}`, 'YYYY-MM').format('MMMM YYYY')
    }));
    
    // Add current month if it doesn't exist in the list
    const currentMonth = `${year}-${month}`;
    if (!formattedMonths.some(m => m.value === currentMonth)) {
      formattedMonths.unshift({
        value: currentMonth,
        display: moment(currentMonth, 'YYYY-MM').format('MMMM YYYY')
      });
    }
    
    // Get income and expenses for the selected month
    const incomeResult = await db.getTotalIncomeByMonth(month, year);
    const expenseResult = await db.getTotalExpensesByMonth(month, year);
    
    const totalIncome = incomeResult && incomeResult.total ? parseFloat(incomeResult.total) : 0;
    const totalExpense = expenseResult && expenseResult.total ? parseFloat(expenseResult.total) : 0;
    const netBalance = totalIncome - totalExpense;
    
    // Get recent transactions for this month
    const transactions = await db.getTransactionsByMonth(month, year);
    
    // Render the dashboard with all the data
    res.render('dashboard', {
      months: formattedMonths,
      selectedMonth: currentMonth,
      totalIncome,
      totalExpense,
      netBalance,
      transactions: transactions.slice(0, 5), // Get only the first 5 transactions
      moment
    });
  } catch (err) {
    console.error('Error loading dashboard:', err);
    res.status(500).send('Error loading dashboard data');
  }
});

module.exports = router;