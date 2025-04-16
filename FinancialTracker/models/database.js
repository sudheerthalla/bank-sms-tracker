const sqlite3 = require('sqlite3').verbose();
const moment = require('moment');
const path = require('path');

// Define transaction types
const TRANSACTION_TYPE = {
  INCOME: 'income',
  EXPENSE: 'expense'
};

// Initialize database connection
const db = new sqlite3.Database(path.join(__dirname, '../data.sqlite'), (err) => {
  if (err) {
    console.error('Database connection error:', err.message);
  } else {
    console.log('Connected to the SQLite database');
    // Create tables if they don't exist
    initializeDatabase();
  }
});

// Create necessary tables
function initializeDatabase() {
  db.serialize(() => {
    // Create transactions table
    db.run(`CREATE TABLE IF NOT EXISTS transactions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      amount REAL NOT NULL,
      type TEXT NOT NULL,
      description TEXT,
      source TEXT,
      date TEXT,
      month TEXT,
      year TEXT
    )`);
  });
}

// Parse transaction information from SMS
function parseTransactionFromSMS(message, sender, timestamp) {
  const smsText = message.toLowerCase();
  let transaction = {
    amount: 0,
    type: null,
    description: '',
    source: sender || 'Unknown',
    date: timestamp || moment().format('YYYY-MM-DD HH:mm:ss'),
    month: moment(timestamp).format('MM') || moment().format('MM'),
    year: moment(timestamp).format('YYYY') || moment().format('YYYY')
  };
  
  // Detect transaction type
  if (smsText.includes('credited') || smsText.includes('received') || smsText.includes('credit')) {
    transaction.type = TRANSACTION_TYPE.INCOME;
  } else if (smsText.includes('debited') || smsText.includes('spent') || smsText.includes('debit')) {
    transaction.type = TRANSACTION_TYPE.EXPENSE;
  } else {
    throw new Error('Unable to determine transaction type. Message must contain "credited" or "debited".');
  }
  
  // Extract amount - search for patterns like Rs. 1,000.00 or INR 1000
  const amountRegex = /(?:rs\.?|inr)\s*([0-9,]+(?:\.[0-9]+)?)/i;
  const amountMatch = smsText.match(amountRegex);
  
  if (amountMatch && amountMatch[1]) {
    // Remove commas and convert to number
    transaction.amount = parseFloat(amountMatch[1].replace(/,/g, ''));
  } else {
    throw new Error('Unable to extract amount from the message.');
  }
  
  // Extract description - look for keywords like "info", "ref", "towards", "for", etc.
  const descriptionRegexes = [
    /info:\s*([^.]+)/i,
    /ref:\s*([^.]+)/i,
    /towards\s*([^.]+)/i,
    /for\s*([^.]+)/i,
    /by\s*([^.]+)/i,
    /at\s*([^.]+)/i,
    /from\s*([^.]+)/i,
    /to\s*([^.]+)/i
  ];
  
  for (const regex of descriptionRegexes) {
    const match = smsText.match(regex);
    if (match && match[1]) {
      transaction.description = match[1].trim();
      break;
    }
  }
  
  // If no description found, use a default based on type
  if (!transaction.description) {
    transaction.description = transaction.type === TRANSACTION_TYPE.INCOME ? 
      'Income transaction' : 'Expense transaction';
  }
  
  return transaction;
}

// Process SMS message and save transaction to database
function processSMS(message, sender, timestamp, callback) {
  try {
    // Parse transaction data from SMS
    const transaction = parseTransactionFromSMS(message, sender, timestamp);
    
    // Save transaction to database
    saveTransaction(transaction, (err, result) => {
      if (err) {
        return callback(err);
      }
      
      callback(null, { ...transaction, id: result.id });
    });
  } catch (error) {
    callback(error);
  }
}

// Save transaction to database
function saveTransaction(transaction, callback) {
  db.run(
    `INSERT INTO transactions (amount, type, description, source, date, month, year) 
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
    [
      transaction.amount,
      transaction.type,
      transaction.description,
      transaction.source,
      transaction.date,
      transaction.month,
      transaction.year
    ],
    function(err) {
      if (err) {
        return callback(err);
      }
      
      callback(null, { id: this.lastID });
    }
  );
}

// Get all transactions
function getAllTransactions(callback) {
  db.all(
    'SELECT * FROM transactions ORDER BY date DESC',
    [],
    callback
  );
}

// Get transactions by month
function getTransactionsByMonth(month, year, callback) {
  db.all(
    'SELECT * FROM transactions WHERE month = ? AND year = ? ORDER BY date DESC',
    [month, year],
    callback
  );
}

// Get total income for a month
function getTotalIncomeByMonth(month, year, callback) {
  db.get(
    `SELECT SUM(amount) as total FROM transactions 
     WHERE month = ? AND year = ? AND type = ?`,
    [month, year, TRANSACTION_TYPE.INCOME],
    callback
  );
}

// Get total expenses for a month
function getTotalExpensesByMonth(month, year, callback) {
  db.get(
    `SELECT SUM(amount) as total FROM transactions 
     WHERE month = ? AND year = ? AND type = ?`,
    [month, year, TRANSACTION_TYPE.EXPENSE],
    callback
  );
}

// Get all available months that have transactions
function getAvailableMonths(callback) {
  db.all(
    `SELECT DISTINCT month, year FROM transactions 
     ORDER BY year DESC, month DESC`,
    [],
    callback
  );
}

module.exports = {
  TRANSACTION_TYPE,
  processSMS,
  parseTransactionFromSMS,
  saveTransaction,
  getAllTransactions,
  getTransactionsByMonth,
  getTotalIncomeByMonth,
  getTotalExpensesByMonth,
  getAvailableMonths
};