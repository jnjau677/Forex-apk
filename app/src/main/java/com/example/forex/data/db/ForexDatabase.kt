package com.example.forex.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    // v5: dropped user_profiles — authentication/users are owned by Firebase Auth,
    // and the table previously held local password hashes (REVIEW.md F-04).
    entities = [SignalEntity::class, AlertEntity::class, WatchlistEntity::class, CandleEntity::class, UserSettingsEntity::class],
    version = 5,
    exportSchema = false
)
abstract class ForexDatabase : RoomDatabase() {
    abstract fun forexDao(): ForexDao

    companion object {
        @Volatile
        private var INSTANCE: ForexDatabase? = null

        /**
         * v4 -> v5: drop only the local credential table. Watchlist, saved
         * signals, alerts, candles and settings survive the upgrade
         * (REVIEW.md F-09 — destructive fallback remains only as a last resort
         * for un-migrated version jumps).
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("DROP TABLE IF EXISTS user_profiles")
            }
        }

        fun getDatabase(context: Context): ForexDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ForexDatabase::class.java,
                    "forex_analyzer_db"
                ).addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
