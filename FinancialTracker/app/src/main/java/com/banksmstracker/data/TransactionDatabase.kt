package com.banksmstracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The Room database for this app.
 */
@Database(entities = [Transaction::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class TransactionDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: TransactionDatabase? = null

        fun getDatabase(context: Context): TransactionDatabase {
            // If the INSTANCE is not null, return it, otherwise create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TransactionDatabase::class.java,
                    "transaction_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                // Return instance
                instance
            }
        }
    }
}
