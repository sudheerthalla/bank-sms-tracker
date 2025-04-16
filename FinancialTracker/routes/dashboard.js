const express = require('express');
const router = express.Router();
const db = require('../models/database');
const moment = require('moment');

// Helper function to get current month and year
function getCurrentMonthYear() {
  return {
    month: moment().format('MM'),
    year: moment().format('YYYY')
  };
}

// Dashboard route - shows summary of current month
router.get('/', (req, res) => {
  const { month, year } = req.query.month && req.query.year 
    ? { month: req.query.month, year: req.query.year } 
    : getCurrentMonthYear();
  
  // Get available months for the dropdown
  db.getAvailableMonths((err, months) => {
    if (err) {
      console.error('Error getting available months:', err);
      return res.status(500).send('Error loading dashboard data');
    }
    
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
    
    // Get income for the selected month
    db.getTotalIncomeByMonth(month, year, (err, incomeResult) => {
      if (err) {
        console.error('Error getting income data:', err);
        return res.status(500).send('Error loading income data');
      }
      
      const totalIncome = incomeResult && incomeResult.total ? incomeResult.total : 0;
      
      // Get expenses for the selected month
      db.getTotalExpensesByMonth(month, year, (err, expenseResult) => {
        if (err) {
          console.error('Error getting expense data:', err);
          return res.status(500).send('Error loading expense data');
        }
        
        const totalExpense = expenseResult && expenseResult.total ? expenseResult.total : 0;
        const netBalance = totalIncome - totalExpense;
        
        // Get recent transactions for this month
        db.getTransactionsByMonth(month, year, (err, transactions) => {
          if (err) {
            console.error('Error getting transactions:', err);
            return res.status(500).send('Error loading transaction data');
          }
          
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
        });
      });
    });
  });
});

module.exports = router;