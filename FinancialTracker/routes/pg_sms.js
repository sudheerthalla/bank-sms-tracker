const express = require('express');
const router = express.Router();
const pgDatabase = require('../models/pg_database');

/**
 * POST /sms/process
 * Receives SMS messages from the Android app and processes them
 */
router.post('/process', async (req, res) => {
    try {
        // Log the received data
        console.log('Received SMS data from Android app:', req.body);
        
        // Extract messages array from request body
        const { messages } = req.body;
        
        if (!messages || !Array.isArray(messages)) {
            return res.status(400).json({
                success: false,
                message: 'Invalid request format. Expected messages array.'
            });
        }
        
        // Process each SMS message
        const processedMessages = [];
        
        for (const sms of messages) {
            const { sender, message, timestamp } = sms;
            
            if (!sender || !message) {
                continue; // Skip invalid messages
            }
            
            // Process the SMS message
            try {
                const result = await pgDatabase.processSMS(message, sender, timestamp);
                processedMessages.push(result);
            } catch (err) {
                console.error('Error processing SMS:', err);
                // Continue processing other messages even if one fails
            }
        }
        
        // Return success response
        res.json({
            success: true,
            processed: processedMessages.length,
            total: messages.length,
            messages: processedMessages
        });
        
    } catch (error) {
        console.error('Error in SMS processing endpoint:', error);
        res.status(500).json({
            success: false,
            message: 'Server error processing SMS messages',
            error: error.message
        });
    }
});

/**
 * GET /sms/test
 * Renders the SMS testing page
 */
router.get('/test', (req, res) => {
    res.render('sms-test', {
        title: 'SMS Test',
        active: 'sms-test'
    });
});

/**
 * POST /sms/test
 * Process a test SMS message submitted through the web interface
 */
router.post('/test', async (req, res) => {
    try {
        const { message, sender } = req.body;
        const timestamp = new Date().toISOString();
        
        if (!message || !sender) {
            return res.status(400).json({
                success: false,
                message: 'Message and sender are required'
            });
        }
        
        // Process the SMS
        const result = await pgDatabase.processSMS(message, sender, timestamp);
        
        res.json({
            success: true,
            processed: true,
            message: result
        });
        
    } catch (error) {
        console.error('Error in SMS test endpoint:', error);
        res.status(500).json({
            success: false,
            message: 'Server error processing test SMS',
            error: error.message
        });
    }
});

module.exports = router;