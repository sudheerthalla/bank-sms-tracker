const express = require('express');
const router = express.Router();
const db = require('../models/database');
const moment = require('moment');

// API route to get all transactions
router.get('/api/all', (req, res) => {
  db.getAllTransactions((err, transactions) => {
    if (err) {
      console.error('Error getting transactions:', err);
      return res.status(500).json({ error: 'Failed to get transactions' });
    }
    
    res.json(transactions);
  });
});

// API route to get transactions by month
router.get('/api/month/:year/:month', (req, res) => {
  const { year, month } = req.params;
  
  db.getTransactionsByMonth(month, year, (err, transactions) => {
    if (err) {
      console.error('Error getting transactions for month:', err);
      return res.status(500).json({ error: 'Failed to get transactions' });
    }
    
    res.json(transactions);
  });
});

// API route to get summary for month
router.get('/api/summary/:year/:month', (req, res) => {
  const { year, month } = req.params;
  
  // Get income for the month
  db.getTotalIncomeByMonth(month, year, (err, incomeResult) => {
    if (err) {
      console.error('Error getting income data:', err);
      return res.status(500).json({ error: 'Failed to get income data' });
    }
    
    const totalIncome = incomeResult && incomeResult.total ? incomeResult.total : 0;
    
    // Get expenses for the month
    db.getTotalExpensesByMonth(month, year, (err, expenseResult) => {
      if (err) {
        console.error('Error getting expense data:', err);
        return res.status(500).json({ error: 'Failed to get expense data' });
      }
      
      const totalExpense = expenseResult && expenseResult.total ? expenseResult.total : 0;
      
      // Calculate net balance
      const netBalance = totalIncome - totalExpense;
      
      // Return the summary
      res.json({
        month,
        year,
        totalIncome,
        totalExpense,
        netBalance,
        monthDisplay: moment(`${year}-${month}`, 'YYYY-MM').format('MMMM YYYY')
      });
    });
  });
});

// API route to add a manual transaction
router.post('/add', (req, res) => {
  const { amount, type, description, source, date } = req.body;
  
  // Validate input
  if (!amount || !type || isNaN(parseFloat(amount))) {
    return res.status(400).json({ error: 'Invalid transaction data' });
  }
  
  // Parse amount to float
  const parsedAmount = parseFloat(amount);
  
  // Parse date or use current date
  const transactionDate = date ? moment(date) : moment();
  const formattedDate = transactionDate.format('YYYY-MM-DD HH:mm:ss');
  const month = transactionDate.format('MM');
  const year = transactionDate.format('YYYY');
  
  // Create transaction object
  const transaction = {
    amount: parsedAmount,
    type: type === 'income' ? db.TRANSACTION_TYPE.INCOME : db.TRANSACTION_TYPE.EXPENSE,
    description: description || 'Manual transaction',
    source: source || 'Manual entry',
    date: formattedDate,
    month,
    year
  };
  
  // Save transaction to database
  db.saveTransaction(transaction, (err, result) => {
    if (err) {
      console.error('Error saving transaction:', err);
      return res.status(500).json({ error: 'Failed to save transaction' });
    }
    
    // Redirect back to referring page or dashboard
    res.redirect(req.headers.referer || '/');
  });
});

module.exports = router;