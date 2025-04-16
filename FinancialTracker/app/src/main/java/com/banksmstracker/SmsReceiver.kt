package com.banksmstracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * BroadcastReceiver for handling incoming SMS messages.
 * This class processes new SMS messages in real-time and passes them to the parser
 * if they appear to be bank transaction messages.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            messages.forEach { smsMessage ->
                val sender = smsMessage.displayOriginatingAddress
                val messageBody = smsMessage.messageBody
                
                Log.d(TAG, "SMS received from: $sender")
                
                // Process only if it's likely a bank message
                if (isBankSms(sender, messageBody)) {
                    Log.d(TAG, "Bank SMS detected, processing...")
                    val transactionParser = TransactionParser(context)
                    transactionParser.parseSms(sender, messageBody, smsMessage.timestampMillis)
                }
            }
        }
    }

    /**
     * Determines if an SMS is likely from a bank based on sender and content.
     */
    private fun isBankSms(sender: String, message: String): Boolean {
        // Common bank SMS senders (this list should be expanded based on local banks)
        val bankSenders = listOf(
            "HDFCBK", "SBIINB", "ICICIB", "AXISBK", "BOIIND", "PNBSMS", "SCBANK", 
            "KOTAKB", "INDBNK", "CANBNK", "CENTBK", "UNIONB", "YESBNK"
        )
        
        // Keywords commonly found in bank transaction messages
        val transactionKeywords = listOf(
            "credited", "debited", "txn", "transaction", "a/c", "account", 
            "balance", "withdrawal", "deposit", "transfer", "info", "amt"
        )
        
        // Check if the sender is in our list of bank senders
        val isBankSender = bankSenders.any { 
            sender.contains(it, ignoreCase = true) 
        }
        
        // Check if the message contains transaction-related keywords
        val containsTransactionKeywords = transactionKeywords.any { 
            message.contains(it, ignoreCase = true) 
        }
        
        return isBankSender || (containsTransactionKeywords && 
                (message.contains("rs", ignoreCase = true) || 
                 message.contains("inr", ignoreCase = true) || 
                 message.contains("₹")))
    }
}
