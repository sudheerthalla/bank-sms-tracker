const { Pool } = require('pg');
const moment = require('moment');

// Define transaction types
const TRANSACTION_TYPE = {
  INCOME: 'income',
  EXPENSE: 'expense'
};

// Create PostgreSQL connection pool
const pool = new Pool({
  connectionString: process.env.DATABASE_URL
});

// Initialize database with required tables
async function initializeDatabase() {
  const client = await pool.connect();
  try {
    // Create transactions table if it doesn't exist
    await client.query(`
      CREATE TABLE IF NOT EXISTS transactions (
        id SERIAL PRIMARY KEY,
        amount DECIMAL(12,2) NOT NULL,
        type VARCHAR(10) NOT NULL,
        description TEXT,
        source TEXT,
        date TIMESTAMP,
        month VARCHAR(2),
        year VARCHAR(4)
      )
    `);
    console.log('PostgreSQL database initialized');
  } catch (err) {
    console.error('Error initializing database:', err);
  } finally {
    client.release();
  }
}

// Initialize database on startup
initializeDatabase();

// DeepSeek LLM integration for SMS analysis
let deepseekIntegration;
try {
  deepseekIntegration = require('./deepseek_integration');
  console.log('DeepSeek LLM integration loaded successfully');
} catch (error) {
  console.error('Failed to load DeepSeek LLM integration:', error);
  deepseekIntegration = {
    analyzeSMSWithDeepSeek: async () => {
      throw new Error('DeepSeek LLM integration not available');
    }
  };
}

// Parse transaction information from SMS using LLM
async function parseTransactionFromSMS(message, sender, timestamp) {
  try {
    // Initialize transaction with basic data
    let transaction = {
      amount: 0,
      type: null,
      description: '',
      source: sender || 'Unknown',
      date: timestamp || moment().format('YYYY-MM-DD HH:mm:ss'),
      month: moment(timestamp || new Date()).format('MM'),
      year: moment(timestamp || new Date()).format('YYYY')
    };
    
    // Try to analyze with DeepSeek LLM
    try {
      const aiResult = await deepseekIntegration.analyzeSMSWithDeepSeek(message);
      
      // Update transaction with AI results
      transaction.type = aiResult.type === 'credit' ? TRANSACTION_TYPE.INCOME : 
                        (aiResult.type === 'debit' ? TRANSACTION_TYPE.EXPENSE : transaction.type);
      transaction.amount = parseFloat(aiResult.amount);
      
      if (aiResult.description) {
        transaction.description = aiResult.description;
      }
      
      if (aiResult.category) {
        transaction.description = `${transaction.description} (${aiResult.category})`.trim();
      }
      
      if (aiResult.accountInfo) {
        transaction.source = `${transaction.source} - Acc: ${aiResult.accountInfo}`.trim();
      }
      
      // If we have a date from the LLM, use it
      if (aiResult.date) {
        transaction.date = aiResult.date + (aiResult.date.includes(':') ? '' : ' 00:00:00');
        const dateMoment = moment(transaction.date);
        if (dateMoment.isValid()) {
          transaction.month = dateMoment.format('MM');
          transaction.year = dateMoment.format('YYYY');
        }
      }
      
      console.log('Transaction parsed successfully using DeepSeek LLM:', transaction);
      return transaction;
    } catch (aiError) {
      console.warn('DeepSeek LLM analysis failed, falling back to rule-based parsing:', aiError.message);
      
      // If DeepSeek LLM analysis fails, fall back to rule-based parsing
      const smsText = message.toLowerCase();
      
      // Fallback detection logic
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
      
      console.log('Transaction parsed successfully using rule-based approach:', transaction);
      return transaction;
    }
  } catch (error) {
    console.error('Failed to parse transaction from SMS:', error);
    throw error;
  }
}

// Process SMS message and save transaction to database
async function processSMS(message, sender, timestamp) {
  try {
    // Parse transaction data from SMS
    const transaction = await parseTransactionFromSMS(message, sender, timestamp);
    
    // Save transaction to database
    const result = await saveTransaction(transaction);
    return { ...transaction, id: result.id };
  } catch (error) {
    throw error;
  }
}

// Save transaction to database
async function saveTransaction(transaction) {
  const client = await pool.connect();
  try {
    const query = `
      INSERT INTO transactions (amount, type, description, source, date, month, year)
      VALUES ($1, $2, $3, $4, $5, $6, $7)
      RETURNING id
    `;
    const values = [
      transaction.amount,
      transaction.type,
      transaction.description,
      transaction.source,
      transaction.date,
      transaction.month,
      transaction.year
    ];
    
    const res = await client.query(query, values);
    return { id: res.rows[0].id };
  } catch (err) {
    console.error('Error saving transaction:', err);
    throw err;
  } finally {
    client.release();
  }
}

// Get all transactions
async function getAllTransactions() {
  const client = await pool.connect();
  try {
    const res = await client.query('SELECT * FROM transactions ORDER BY date DESC');
    return res.rows;
  } catch (err) {
    console.error('Error getting transactions:', err);
    throw err;
  } finally {
    client.release();
  }
}

// Get transactions by month
async function getTransactionsByMonth(month, year) {
  const client = await pool.connect();
  try {
    const query = 'SELECT * FROM transactions WHERE month = $1 AND year = $2 ORDER BY date DESC';
    const res = await client.query(query, [month, year]);
    return res.rows;
  } catch (err) {
    console.error('Error getting transactions by month:', err);
    throw err;
  } finally {
    client.release();
  }
}

// Get total income for a month
async function getTotalIncomeByMonth(month, year) {
  const client = await pool.connect();
  try {
    const query = `
      SELECT COALESCE(SUM(amount), 0) as total
      FROM transactions
      WHERE month = $1 AND year = $2 AND type = $3
    `;
    const res = await client.query(query, [month, year, TRANSACTION_TYPE.INCOME]);
    return res.rows[0];
  } catch (err) {
    console.error('Error getting income by month:', err);
    throw err;
  } finally {
    client.release();
  }
}

// Get total expenses for a month
async function getTotalExpensesByMonth(month, year) {
  const client = await pool.connect();
  try {
    const query = `
      SELECT COALESCE(SUM(amount), 0) as total
      FROM transactions
      WHERE month = $1 AND year = $2 AND type = $3
    `;
    const res = await client.query(query, [month, year, TRANSACTION_TYPE.EXPENSE]);
    return res.rows[0];
  } catch (err) {
    console.error('Error getting expenses by month:', err);
    throw err;
  } finally {
    client.release();
  }
}

// Get all available months that have transactions
async function getAvailableMonths() {
  const client = await pool.connect();
  try {
    const query = `
      SELECT DISTINCT month, year
      FROM transactions
      ORDER BY year DESC, month DESC
    `;
    const res = await client.query(query);
    return res.rows;
  } catch (err) {
    console.error('Error getting available months:', err);
    throw err;
  } finally {
    client.release();
  }
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