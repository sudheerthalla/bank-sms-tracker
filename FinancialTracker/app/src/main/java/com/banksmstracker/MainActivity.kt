package com.banksmstracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.banksmstracker.ui.DashboardFragment
import com.banksmstracker.ui.HistoryFragment

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SMS_PERMISSION_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupNavigation()
        checkSmsPermission()
    }

    private fun setupNavigation() {
        val bottomNavView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        
        // Setup navigation between dashboard and history fragments
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DashboardFragment())
            .commit()
        
        bottomNavView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, DashboardFragment())
                        .commit()
                    true
                }
                R.id.navigation_history -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HistoryFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }
    }

    private fun checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
                SMS_PERMISSION_CODE
            )
        } else {
            // Permission already granted, load initial SMS messages
            loadInitialSmsMessages()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            SMS_PERMISSION_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission granted
                    loadInitialSmsMessages()
                } else {
                    // Permission denied
                    Toast.makeText(
                        this,
                        "SMS permissions are required to track bank transactions",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
        }
    }

    private fun loadInitialSmsMessages() {
        // Load and process existing bank SMS messages
        Thread {
            val transactionParser = TransactionParser(this)
            transactionParser.processExistingSmsMessages()
        }.start()
    }
}
