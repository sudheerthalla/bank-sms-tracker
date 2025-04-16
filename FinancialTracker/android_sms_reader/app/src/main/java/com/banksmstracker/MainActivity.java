package com.banksmstracker;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

public class MainActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 100;
    private static final String SERVER_URL = "https://FinancialTracker.sudheer20991.repl.co/sms/process";
    
    private TextView statusTextView;
    private Button syncButton;
    private Button viewDashboardButton;
    
    private List<SmsMessage> bankSmsMessages = new ArrayList<>();
    private static final long ONE_MONTH_MILLIS = TimeUnit.DAYS.toMillis(30);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        statusTextView = findViewById(R.id.statusTextView);
        syncButton = findViewById(R.id.syncButton);
        viewDashboardButton = findViewById(R.id.viewDashboardButton);
        
        syncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionAndReadSms();
            }
        });
        
        viewDashboardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open web dashboard
                openWebDashboard();
            }
        });
    }
    
    private void checkPermissionAndReadSms() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) 
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_SMS}, 
                    SMS_PERMISSION_CODE);
        } else {
            readSmsAndFilter();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                readSmsAndFilter();
            } else {
                statusTextView.setText("SMS permission denied. Cannot read bank messages.");
            }
        }
    }
    
    private void readSmsAndFilter() {
        statusTextView.setText("Reading SMS messages...");
        bankSmsMessages.clear();
        
        // Define bank sender IDs
        List<String> bankSenders = new ArrayList<>();
        bankSenders.add("HDFCBK");
        bankSenders.add("ICICIBK");
        bankSenders.add("SBIINB");
        bankSenders.add("AXISBK");
        bankSenders.add("KOTAKB");
        // Add more bank sender IDs as needed
        
        // Get current time minus one month (to limit our search)
        long oneMonthAgo = System.currentTimeMillis() - ONE_MONTH_MILLIS;
        
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                new String[]{
                        Telephony.Sms._ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE
                },
                Telephony.Sms.DATE + " > ?",
                new String[]{String.valueOf(oneMonthAgo)},
                Telephony.Sms.DATE + " DESC"
        );
        
        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                String sender = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                
                // Check if sender is a bank or if message is a bank transaction
                boolean isFromBank = false;
                for (String bankSender : bankSenders) {
                    if (sender != null && sender.contains(bankSender)) {
                        isFromBank = true;
                        break;
                    }
                }
                
                // If sender is not a bank, check if message contains bank transaction keywords
                if (!isFromBank) {
                    if (body != null && (body.contains("credited") || body.contains("debited") || 
                            body.contains("account") || body.contains("transaction") || 
                            body.contains("balance"))) {
                        isFromBank = true;
                    }
                }
                
                if (isFromBank) {
                    SmsMessage smsMessage = new SmsMessage(sender, body, timestamp);
                    bankSmsMessages.add(smsMessage);
                }
            }
            cursor.close();
            
            statusTextView.setText("Found " + bankSmsMessages.size() + " bank SMS messages");
            if (!bankSmsMessages.isEmpty()) {
                uploadSmsToServer();
            }
        } else {
            if (cursor != null) {
                cursor.close();
            }
            statusTextView.setText("No SMS messages found");
        }
    }
    
    private void uploadSmsToServer() {
        statusTextView.setText("Uploading messages to server...");
        
        try {
            JSONArray jsonArray = new JSONArray();
            
            for (SmsMessage sms : bankSmsMessages) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("sender", sms.getSender());
                jsonObject.put("message", sms.getBody());
                jsonObject.put("timestamp", sms.getDateFormatted());
                jsonArray.put(jsonObject);
            }
            
            JSONObject requestJson = new JSONObject();
            requestJson.put("messages", jsonArray);
            
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
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusTextView.setText("Failed to upload: " + e.getMessage());
                        }
                    });
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response.isSuccessful()) {
                                statusTextView.setText("Successfully uploaded " + bankSmsMessages.size() + " messages");
                            } else {
                                statusTextView.setText("Server error: " + responseBody);
                            }
                        }
                    });
                }
            });
            
        } catch (JSONException e) {
            statusTextView.setText("Error creating JSON: " + e.getMessage());
        }
    }
    
    private void openWebDashboard() {
        // Open web dashboard in browser
        Toast.makeText(this, "Opening dashboard...", Toast.LENGTH_SHORT).show();
    }
    
    private class SmsMessage {
        private String sender;
        private String body;
        private long timestamp;
        
        public SmsMessage(String sender, String body, long timestamp) {
            this.sender = sender;
            this.body = body;
            this.timestamp = timestamp;
        }
        
        public String getSender() {
            return sender;
        }
        
        public String getBody() {
            return body;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public String getDateFormatted() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }
}