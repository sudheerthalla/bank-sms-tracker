package com.banksmstracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";
    private static final String SERVER_URL = "https://FinancialTracker.sudheer20991.repl.co/sms/process";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)) {
            Log.d(TAG, "SMS Received");
            
            // Get all SMS messages from the intent
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            
            if (messages != null && messages.length > 0) {
                for (SmsMessage smsMessage : messages) {
                    String sender = smsMessage.getDisplayOriginatingAddress();
                    String body = smsMessage.getMessageBody();
                    long timestamp = smsMessage.getTimestampMillis();
                    
                    // Check if this is a bank SMS
                    if (isBankSms(sender, body)) {
                        Log.d(TAG, "Bank SMS detected: " + sender);
                        
                        // Process the bank SMS message
                        processBankSms(context, sender, body, timestamp);
                    }
                }
            }
        }
    }

    private boolean isBankSms(String sender, String body) {
        // List of known bank SMS sender IDs
        List<String> bankSenders = new ArrayList<>();
        bankSenders.add("HDFCBK");
        bankSenders.add("ICICIBK");
        bankSenders.add("SBIINB");
        bankSenders.add("AXISBK");
        bankSenders.add("KOTAKB");
        // Add more bank sender IDs as needed
        
        // Check if sender is a bank
        for (String bankSender : bankSenders) {
            if (sender != null && sender.contains(bankSender)) {
                return true;
            }
        }
        
        // If sender is not in our list, check for transaction keywords
        if (body != null) {
            return body.contains("credited") || 
                   body.contains("debited") || 
                   body.contains("account") || 
                   body.contains("transaction") || 
                   body.contains("balance");
        }
        
        return false;
    }

    private void processBankSms(final Context context, String sender, String body, long timestamp) {
        try {
            // Format the timestamp as a readable date string
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String formattedDate = sdf.format(new Date(timestamp));
            
            // Create JSON object for this SMS
            JSONObject smsJson = new JSONObject();
            smsJson.put("sender", sender);
            smsJson.put("message", body);
            smsJson.put("timestamp", formattedDate);
            
            // Add to array
            JSONArray messagesArray = new JSONArray();
            messagesArray.put(smsJson);
            
            // Create the request JSON
            JSONObject requestJson = new JSONObject();
            requestJson.put("messages", messagesArray);
            
            // Send to server
            sendToServer(context, requestJson);
            
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON: " + e.getMessage());
            Toast.makeText(context, "Error processing SMS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendToServer(final Context context, JSONObject requestJson) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(JSON, requestJson.toString());
        
        Request request = new Request.Builder()
                .url(SERVER_URL)
                .post(requestBody)
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to upload: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body().string();
                Log.d(TAG, "Server response: " + responseBody);
                
                if (response.isSuccessful()) {
                    Log.d(TAG, "Successfully uploaded bank SMS");
                } else {
                    Log.e(TAG, "Server error: " + responseBody);
                }
            }
        });
    }
}