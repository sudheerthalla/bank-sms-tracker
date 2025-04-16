const OpenAI = require('openai');

// Define transaction types to avoid circular dependency
const TRANSACTION_TYPE = {
  INCOME: 'income',
  EXPENSE: 'expense'
};

// Initialize OpenAI client only if API key is present
let openai = null;
try {
  if (process.env.OPENAI_API_KEY) {
    openai = new OpenAI({
      apiKey: process.env.OPENAI_API_KEY
    });
    console.log("OpenAI client initialized successfully");
  } else {
    console.log("OPENAI_API_KEY not found, LLM features will be disabled");
  }
} catch (error) {
  console.error("Failed to initialize OpenAI client:", error);
}

/**
 * Analyze SMS text using OpenAI to determine transaction type and extract details
 * @param {string} smsText - The SMS message to analyze
 * @returns {Object} Transaction details including type, amount, and description
 */
async function analyzeSMSWithOpenAI(smsText) {
  try {
    // Return early if no API key is available
    if (!process.env.OPENAI_API_KEY) {
      throw new Error('OpenAI API key is not set. Using fallback method instead.');
    }
    
    // Define the prompt to analyze SMS
    const prompt = `
      Analyze this bank SMS message and extract the following information:
      1. Transaction type (income or expense)
      2. Amount
      3. Description (what the transaction was for)
      4. Source (the entity involved in the transaction)
      
      SMS: "${smsText}"
      
      Respond with a JSON object in this format:
      {
        "type": "income" or "expense",
        "amount": number (without currency symbol or commas),
        "description": "brief description",
        "source": "entity name"
      }
      
      Logic for determining transaction type:
      - Income: money received, credited, added, deposited, transferred to account
      - Expense: money spent, debited, paid, withdrawn, transferred from account
    `;
    
    // Call OpenAI API
    const response = await openai.chat.completions.create({
      model: "gpt-4o", // the newest OpenAI model is "gpt-4o" which was released May 13, 2024. do not change this unless explicitly requested by the user
      messages: [
        {
          role: "system",
          content: "You are a financial analysis assistant that extracts transaction details from bank SMS messages."
        },
        {
          role: "user",
          content: prompt
        }
      ],
      response_format: { type: "json_object" }
    });
    
    // Parse the response
    const result = JSON.parse(response.choices[0].message.content);
    
    // Validate the result
    if (!result.type || !result.amount) {
      throw new Error('Failed to extract required transaction details from the SMS.');
    }
    
    // Normalize the transaction type
    result.type = result.type.toLowerCase() === 'income' ? 
      TRANSACTION_TYPE.INCOME : TRANSACTION_TYPE.EXPENSE;
    
    return result;
  } catch (error) {
    console.error('OpenAI analysis error:', error);
    throw error;
  }
}

module.exports = {
  analyzeSMSWithOpenAI
};