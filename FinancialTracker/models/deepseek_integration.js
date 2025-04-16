/**
 * DeepSeek LLM integration for Bank SMS Tracker
 * This module provides functions to analyze SMS messages using DeepSeek LLM
 */

const https = require('https');

// API configuration
const DEEPSEEK_API_URL = 'https://api.deepseek.ai/v1/chat/completions';
const DEEPSEEK_API_KEY = process.env.DEEPSEEK_API_KEY;

/**
 * Analyze SMS text using DeepSeek LLM to determine transaction type and extract details
 * @param {string} smsText - The SMS message to analyze
 * @returns {Object} Transaction details including type, amount, and description
 */
async function analyzeSMSWithDeepSeek(smsText) {
    // Check if API key is available
    if (!DEEPSEEK_API_KEY) {
        console.log('DEEPSEEK_API_KEY not found, using rule-based analysis instead');
        return fallbackToRuleBasedAnalysis(smsText);
    }

    try {
        // Prepare the prompt for DeepSeek
        const prompt = `
You are a financial transaction analyzer. Analyze the following bank SMS message and extract key information:

SMS: "${smsText}"

Extract the following details in JSON format:
- transaction_type: "credit" if money was added to account, "debit" if money was spent or withdrawn
- amount: the transaction amount (number only, no currency symbols)
- category: categorize the transaction (e.g., "salary", "shopping", "food", "transfer", "bill_payment", "atm_withdrawal", etc.)
- description: short description of the transaction
- date: the transaction date if available (in YYYY-MM-DD format)
- account_info: any account number or masked account number if available
- balance: remaining balance if available (number only)

Return ONLY valid JSON with these fields, no explanation.`;

        // Configure the API request
        const requestData = {
            model: "deepseek-chat",
            messages: [
                { role: "system", content: "You are a financial SMS analyzer that extracts transaction information accurately." },
                { role: "user", content: prompt }
            ],
            temperature: 0.3,
            max_tokens: 1000
        };

        // Make the API request
        const response = await makeRequest(DEEPSEEK_API_URL, requestData, DEEPSEEK_API_KEY);
        
        if (response && response.choices && response.choices[0] && response.choices[0].message) {
            const content = response.choices[0].message.content;
            
            // Parse the JSON response from DeepSeek
            try {
                const parsedData = JSON.parse(content);
                
                // Format and structure the response
                return {
                    type: parsedData.transaction_type || 'unknown',
                    amount: parseFloat(parsedData.amount) || 0,
                    category: parsedData.category || 'uncategorized',
                    description: parsedData.description || 'Unknown transaction',
                    date: parsedData.date || new Date().toISOString().split('T')[0],
                    accountInfo: parsedData.account_info || '',
                    balance: parseFloat(parsedData.balance) || null,
                    llm_analyzed: true
                };
            } catch (jsonError) {
                console.error('Error parsing DeepSeek response JSON:', jsonError);
                console.log('Invalid JSON response:', content);
                return fallbackToRuleBasedAnalysis(smsText);
            }
        } else {
            console.error('Unexpected DeepSeek API response structure');
            return fallbackToRuleBasedAnalysis(smsText);
        }
    } catch (error) {
        console.error('Error using DeepSeek API:', error);
        return fallbackToRuleBasedAnalysis(smsText);
    }
}

/**
 * Makes an HTTP request to the DeepSeek API
 * @param {string} url - The API endpoint URL
 * @param {Object} data - The request payload
 * @param {string} apiKey - DeepSeek API key
 * @returns {Promise<Object>} The API response
 */
function makeRequest(url, data, apiKey) {
    return new Promise((resolve, reject) => {
        const options = {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${apiKey}`
            }
        };

        const req = https.request(url, options, (res) => {
            let responseData = '';

            res.on('data', (chunk) => {
                responseData += chunk;
            });

            res.on('end', () => {
                try {
                    const parsedData = JSON.parse(responseData);
                    resolve(parsedData);
                } catch (error) {
                    reject(new Error(`Error parsing API response: ${error.message}`));
                }
            });
        });

        req.on('error', (error) => {
            reject(new Error(`API request error: ${error.message}`));
        });

        req.write(JSON.stringify(data));
        req.end();
    });
}

/**
 * Fallback function that uses rule-based analysis when DeepSeek is unavailable
 * @param {string} smsText - The SMS message to analyze
 * @returns {Object} Transaction details extracted using basic rules
 */
function fallbackToRuleBasedAnalysis(smsText) {
    console.log('Using rule-based SMS analysis');
    
    const text = smsText.toLowerCase();
    
    // Determine transaction type
    const isCredit = text.includes('credited') || 
                    text.includes('received') || 
                    text.includes('deposited') || 
                    text.includes('credit');
                    
    const isDebit = text.includes('debited') || 
                   text.includes('withdrawn') || 
                   text.includes('spent') || 
                   text.includes('debit') || 
                   text.includes('paid');
    
    const type = isCredit ? 'credit' : (isDebit ? 'debit' : 'unknown');
    
    // Extract amount
    const amountMatch = text.match(/rs\.?\s?([0-9,]+\.[0-9]+|[0-9,]+)/i) || 
                       text.match(/inr\s?([0-9,]+\.[0-9]+|[0-9,]+)/i);
    
    let amount = 0;
    if (amountMatch && amountMatch[1]) {
        amount = parseFloat(amountMatch[1].replace(/,/g, ''));
    }
    
    // Determine category (basic)
    let category = 'uncategorized';
    if (text.includes('salary') || text.includes('income')) {
        category = 'salary';
    } else if (text.includes('atm') || text.includes('withdrawn')) {
        category = 'atm_withdrawal';
    } else if (text.includes('upi')) {
        category = 'upi_payment';
    } else if (text.includes('bill')) {
        category = 'bill_payment';
    } else if (text.includes('shop') || text.includes('store') || text.includes('mart')) {
        category = 'shopping';
    } else if (text.includes('transfer')) {
        category = 'transfer';
    }
    
    // Extract account info
    let accountInfo = '';
    const accountMatch = text.match(/a\/c\s?(?:no)?\.?\s?(x+[0-9]+|[0-9]+)/i) || 
                        text.match(/account\s?(?:no)?\.?\s?(x+[0-9]+|[0-9]+)/i);
    
    if (accountMatch && accountMatch[1]) {
        accountInfo = accountMatch[1];
    }
    
    // Extract balance
    let balance = null;
    const balanceMatch = text.match(/(?:balance|bal)(?:ance)?:?\s?(?:rs\.?|inr)?\s?([0-9,]+\.[0-9]+|[0-9,]+)/i);
    
    if (balanceMatch && balanceMatch[1]) {
        balance = parseFloat(balanceMatch[1].replace(/,/g, ''));
    }
    
    // Create basic description
    let description = `${type === 'credit' ? 'Money received' : 'Payment made'}`;
    if (category !== 'uncategorized') {
        description = `${category.replace('_', ' ')}`;
        if (category === 'salary') {
            description = 'Salary received';
        } else if (category === 'atm_withdrawal') {
            description = 'ATM withdrawal';
        } else if (category === 'upi_payment') {
            description = 'UPI payment';
        }
    }
    
    return {
        type,
        amount,
        category,
        description,
        date: new Date().toISOString().split('T')[0], // Today's date as default
        accountInfo,
        balance,
        llm_analyzed: false
    };
}

module.exports = {
    analyzeSMSWithDeepSeek
};