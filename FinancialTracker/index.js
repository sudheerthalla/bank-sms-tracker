const express = require('express');
const bodyParser = require('body-parser');
const path = require('path');

// Create Express app
const app = express();
const PORT = process.env.PORT || 5000;

// Set up EJS as the view engine
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

// Middlewares
app.use(bodyParser.urlencoded({ extended: true }));
app.use(bodyParser.json());
app.use(express.static(path.join(__dirname, 'public')));

// Import routes - using PostgreSQL implementations
const dashboardRoutes = require('./routes/pg_dashboard');
const historyRoutes = require('./routes/pg_history');
const transactionsRoutes = require('./routes/pg_transactions');
const smsRoutes = require('./routes/pg_sms');

// Use routes
app.use('/', dashboardRoutes);
app.use('/history', historyRoutes);
app.use('/transactions', transactionsRoutes);
app.use('/sms', smsRoutes);

// Error handling middleware
app.use((err, req, res, next) => {
  console.error('Application error:', err);
  res.status(500).send('Something went wrong. Please try again later.');
});

// Check for necessary API keys
if (!process.env.DEEPSEEK_API_KEY) {
  console.log('DEEPSEEK_API_KEY not found, LLM features will be disabled');
}

// Start the server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server running on port ${PORT}`);
  console.log(`Using PostgreSQL database at ${process.env.PGHOST}`);
});