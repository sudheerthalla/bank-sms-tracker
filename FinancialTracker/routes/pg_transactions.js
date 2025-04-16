const express = require('express');
const router = express.Router();
const db = require('../models/pg_database');
const moment = require('moment');

// API route to get all transactions
router.get('/api/all', async (req, res) => {
  try {
    const transactions = await db.getAllTransactions();
    res.json(transactions);
  } catch (err) {
    console.error('Error getting transactions:', err);
    res.status(500).json({ error: 'Failed to get transactions' });
  }
});

// API route to get transactions by month
router.get('/api/month/:year/:month', async (req, res) => {
  try {
    const { year, month } = req.params;
    const transactions = await db.getTransactionsByMonth(month, year);
    res.json(transactions);
  } catch (err) {
    console.error('Error getting transactions for month:', err);
    res.status(500).json({ error: 'Failed to get transactions' });
  }
});

// API route to get summary for month
router.get('/api/summary/:year/:month', async (req, res) => {
  try {
    const { year, month } = req.params;
    
    // Get income for the month
    const incomeResult = await db.getTotalIncomeByMonth(month, year);
    const totalIncome = incomeResult && incomeResult.total ? parseFloat(incomeResult.total) : 0;
    
    // Get expenses for the month
    const expenseResult = await db.getTotalExpensesByMonth(month, year);
    const totalExpense = expenseResult && expenseResult.total ? parseFloat(expenseResult.total) : 0;
    
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
  } catch (err) {
    console.error('Error getting monthly summary:', err);
    res.status(500).json({ error: 'Failed to get summary data' });
  }
});

// API route to add a manual transaction
router.post('/add', async (req, res) => {
  try {
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
    await db.saveTransaction(transaction);
    
    // Redirect back to referring page or dashboard
    res.redirect(req.headers.referer || '/');
  } catch (err) {
    console.error('Error saving transaction:', err);
    res.status(500).json({ error: 'Failed to save transaction' });
  }
});

module.exports = router;