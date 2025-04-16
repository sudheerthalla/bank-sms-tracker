const express = require('express');
const router = express.Router();
const db = require('../models/database');

// Route to handle SMS submission via form
router.post('/submit', (req, res) => {
  const { message, sender, timestamp } = req.body;
  
  // Validate input
  if (!message) {
    return res.status(400).send('Message is required');
  }
  
  // Process the SMS message
  db.processSMS(message, sender, timestamp, (err, transaction) => {
    if (err) {
      console.error('Error processing SMS:', err);
      return res.status(400).json({ 
        success: false, 
        error: err.message 
      });
    }
    
    // Successfully processed SMS
    res.json({ 
      success: true, 
      transaction,
      message: 'Transaction saved successfully'
    });
  });
});

// Form to manually submit SMS for testing
router.get('/test', (req, res) => {
  res.render('sms-test');
});

module.exports = router;