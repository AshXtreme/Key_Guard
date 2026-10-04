package com.keyguard.ime.clipboard.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.keyguard.ime.security.KeyStoreManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * KeyGuardDatabase
 *
 * Encrypted Room database instance securing Tier-2 long-term clipboard entries.
 * Configured with Zetetic SQLCipher (AES-256-GCM) via SupportOpenHelperFactory.
 *
 * Invariant: The database passphrase is dynamically derived from hardware Keystore
 * and immediately zero-wiped from volatile RAM.
 */
@Database(
    entities = [ClipboardEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KeyGuardDatabase : RoomDatabase() {

    abstract fun clipboardDao(): ClipboardDao

    companion object {
        private const val TAG = "KeyGuardDatabase"
        const val DATABASE_NAME = "keyguard_vault.db"

        @Volatile
        private var INSTANCE: KeyGuardDatabase? = null

        init {
            // Load native SQLCipher encryption engine
            try {
                System.loadLibrary("sqlcipher")
                Log.i(TAG, "SQLCipher native library loaded successfully.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Fatal: Failed to load SQLCipher native library.", e)
            }
        }

        /**
         * Returns thread-safe singleton instance of the encrypted Room database.
         * Derives key from hardware StrongBox/TEE Keystore and zeroes key in RAM immediately.
         */
        fun getInstance(
            context: Context,
            keyStoreManager: KeyStoreManager = KeyStoreManager()
        ): KeyGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext, keyStoreManager).also {
                    INSTANCE = it
                }
            }
        }

        private fun buildDatabase(
            appContext: Context,
            keyStoreManager: KeyStoreManager
        ): KeyGuardDatabase {
            // Retrieve hardware-derived 256-bit passphrase
            val passphraseBytes = keyStoreManager.getDatabasePassphrase(appContext)

            // Configure SQLCipher SupportOpenHelperFactory
            val factory = SupportOpenHelperFactory(passphraseBytes)

            // INVARIANT 2: Wipe plaintext passphrase from memory immediately after factory creation
            keyStoreManager.wipeByteArray(passphraseBytes)
            Log.i(TAG, "Database passphrase initialized and sanitized from volatile RAM.")

            return Room.databaseBuilder(
                appContext,
                KeyGuardDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * Clears singleton instance (e.g. for testing or vault lock).
         */
        fun closeAndReset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
