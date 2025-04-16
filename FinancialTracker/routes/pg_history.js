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

// Transaction history route
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
    
    // Get transactions for the selected month
    const transactions = await db.getTransactionsByMonth(month, year);
    
    // Render the history page with transaction data
    res.render('history', {
      months: formattedMonths,
      selectedMonth: currentMonth,
      transactions,
      moment,
      isEmpty: transactions.length === 0
    });
  } catch (err) {
    console.error('Error loading transaction history:', err);
    res.status(500).send('Error loading transaction history');
  }
});

module.exports = router;