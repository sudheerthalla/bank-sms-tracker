package com.banksmstracker

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.banksmstracker.data.Transaction
import com.banksmstracker.data.TransactionRepository
import com.banksmstracker.data.TransactionType
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Handles the parsing of bank SMS messages to extract transaction information.
 */
class TransactionParser(private val context: Context) {
    
    private val repository = TransactionRepository(context)
    
    companion object {
        private const val TAG = "TransactionParser"
        
        // Common date patterns in SMS
        private val DATE_PATTERNS = arrayOf(
            "dd-MM-yyyy", "dd/MM/yyyy", "dd MM yyyy",
            "dd-MMM-yyyy", "dd/MMM/yyyy", "dd MMM yyyy",
            "yyyy-MM-dd", "yyyy/MM/dd", "yyyy MM dd"
        )
        
        // Common amount patterns in SMS
        private val AMOUNT_PATTERN = Pattern.compile("(?i)(?:rs|inr|₹)\\s*\\.?\\s*(\\d+(?:[.,]\\d+)?)")
    }
    
    /**
     * Processes a single SMS message to extract transaction data.
     */
    fun parseSms(sender: String, message: String, timestamp: Long) {
        try {
            // Skip if not a transaction message
            if (!isTransactionSms(message)) {
                return
            }
            
            // Determine transaction type
            val type = when {
                message.contains("credited", ignoreCase = true) || 
                message.contains("deposit", ignoreCase = true) ||
                message.contains("received", ignoreCase = true) -> TransactionType.INCOME
                
                message.contains("debited", ignoreCase = true) || 
                message.contains("withdraw", ignoreCase = true) ||
                message.contains("spent", ignoreCase = true) ||
                message.contains("payment", ignoreCase = true) -> TransactionType.EXPENSE
                
                else -> return // Skip if type can't be determined
            }
            
            // Extract amount
            val amount = extractAmount(message) ?: return
            
            // Extract date from message or use SMS timestamp
            val date = extractDateFromMessage(message) ?: Date(timestamp)
            
            // Create and save transaction
            val transaction = Transaction(
                id = 0, // Room will auto-generate ID
                type = type,
                amount = amount,
                date = date,
                source = sender,
                description = extractDescription(message) ?: "Transaction",
                rawMessage = message
            )
            
            // Save to database
            repository.insert(transaction)
            Log.d(TAG, "Transaction saved: $type - $amount")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SMS: ${e.message}")
        }
    }
    
    /**
     * Process all existing bank SMS messages on device.
     */
    fun processExistingSmsMessages() {
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(
                Telephony.Sms.Inbox.ADDRESS,
                Telephony.Sms.Inbox.BODY,
                Telephony.Sms.Inbox.DATE
            ),
            null,
            null,
            Telephony.Sms.Inbox.DEFAULT_SORT_ORDER
        )
        
        cursor?.use {
            val addressIndex = it.getColumnIndex(Telephony.Sms.Inbox.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.Inbox.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.Inbox.DATE)
            
            // Process each message
            val processedCount = 0
            while (it.moveToNext()) {
                val sender = it.getString(addressIndex)
                val body = it.getString(bodyIndex)
                val timestamp = it.getLong(dateIndex)
                
                if (isBankSms(sender, body)) {
                    parseSms(sender, body, timestamp)
                }
            }
            
            Log.d(TAG, "Finished processing existing SMS messages")
        }
    }
    
    /**
     * Determines if an SMS appears to be from a bank about a transaction.
     */
    private fun isBankSms(sender: String, message: String): Boolean {
        val bankSenders = listOf(
            "HDFCBK", "SBIINB", "ICICIB", "AXISBK", "BOIIND", "PNBSMS", "SCBANK", 
            "KOTAKB", "INDBNK", "CANBNK", "CENTBK", "UNIONB", "YESBNK"
        )
        
        val transactionKeywords = listOf(
            "credited", "debited", "txn", "transaction", "a/c", "account", 
            "balance", "withdrawal", "deposit", "transfer"
        )
        
        val isBankSender = bankSenders.any { sender.contains(it, ignoreCase = true) }
        val containsTransactionKeywords = transactionKeywords.any { 
            message.contains(it, ignoreCase = true) 
        }
        
        return isBankSender || (containsTransactionKeywords && 
                (message.contains("rs", ignoreCase = true) || 
                 message.contains("inr", ignoreCase = true) || 
                 message.contains("₹")))
    }
    
    /**
     * Determines if message contains transaction information.
     */
    private fun isTransactionSms(message: String): Boolean {
        return (message.contains("credited", ignoreCase = true) || 
                message.contains("debited", ignoreCase = true)) &&
               (message.contains("rs", ignoreCase = true) || 
                message.contains("inr", ignoreCase = true) || 
                message.contains("₹"))
    }
    
    /**
     * Extracts the transaction amount from the message.
     */
    private fun extractAmount(message: String): Double? {
        val matcher = AMOUNT_PATTERN.matcher(message)
        if (matcher.find()) {
            val amountStr = matcher.group(1)
                ?.replace(",", "")  // Remove commas in number
            return amountStr?.toDoubleOrNull()
        }
        return null
    }
    
    /**
     * Tries to extract a date from the message text.
     */
    private fun extractDateFromMessage(message: String): Date? {
        // Try different date formats
        for (pattern in DATE_PATTERNS) {
            val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
            val regex = "\\d{1,2}[-/\\s]\\d{1,2}[-/\\s]\\d{2,4}|\\d{1,2}[-/\\s][A-Za-z]{3}[-/\\s]\\d{2,4}"
            val matcher = Pattern.compile(regex).matcher(message)
            
            if (matcher.find()) {
                try {
                    return dateFormat.parse(matcher.group())
                } catch (e: Exception) {
                    // Continue with next pattern
                }
            }
        }
        return null
    }
    
    /**
     * Attempts to extract a meaningful description from the message.
     */
    private fun extractDescription(message: String): String? {
        // Look for common description patterns
        val patterns = arrayOf(
            Pattern.compile("(?i)info:\\s*([^.]*)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)at\\s+([\\w\\s]+)\\s+on", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)to\\s+([\\w\\s]+)\\s+on", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)from\\s+([\\w\\s]+)\\s+on", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                return matcher.group(1)?.trim()
            }
        }
        
        return null
    }
}
