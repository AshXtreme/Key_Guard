package com.keyguard.ime.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * KeyStoreManager
 *
 * Hardware-backed cryptographic root of trust for KeyGuard.
 * Enforces:
 *  1. StrongBox HSM generation with automatic fallback to standard TEE.
 *  2. AES-256-GCM authenticated encryption for hardware-derived secrets.
 *  3. Dynamic generation and memory zeroization of SQLCipher database passphrases.
 */
class KeyStoreManager {

    companion object {
        private const val TAG = "KeyStoreManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "KeyGuard_Master_Vault_Key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PASSPHRASE_LENGTH_BYTES = 32 // 256-bit SQLCipher key
        private const val ENCRYPTED_PASSPHRASE_FILE = "vault_passphrase.enc"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Retrieves or generates the hardware-backed master key.
     * Prioritizes dedicated StrongBox HSM hardware, gracefully falling back to TEE.
     */
    fun getOrCreateMasterKey(): SecretKey {
        synchronized(this) {
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
            }

            // INVARIANT 1: StrongBox HSM first, fallback to standard TEE
            return try {
                generateMasterKey(isStrongBox = true)
            } catch (e: Exception) {
                if (e is StrongBoxUnavailableException ||
                    e.cause is StrongBoxUnavailableException ||
                    e.message?.contains("StrongBox", ignoreCase = true) == true
                ) {
                    Log.w(TAG, "StrongBox HSM unavailable on device. Falling back to standard TEE.", e)
                } else {
                    Log.w(TAG, "Hardware KeyStore initialization exception. Falling back to standard TEE.", e)
                }
                generateMasterKey(isStrongBox = false)
            }
        }
    }

    private fun generateMasterKey(isStrongBox: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val specBuilder = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isStrongBox) {
            specBuilder.setIsStrongBoxBacked(true)
        }

        keyGenerator.init(specBuilder.build())
        val key = keyGenerator.generateKey()
        Log.i(TAG, "Generated hardware master key (StrongBox=$isStrongBox)")
        return key
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with hardware-backed key.
     * Returns: [12-byte IV] + [Ciphertext + 16-byte GCM Tag]
     */
    fun encrypt(plainBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(plainBytes)

        val combined = ByteArray(iv.size + encryptedData.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)
        return combined
    }

    /**
     * Decrypts combined [IV + Ciphertext + Tag] using AES-256-GCM.
     */
    fun decrypt(combinedBytes: ByteArray): ByteArray {
        require(combinedBytes.size > GCM_IV_LENGTH_BYTES) { "Invalid encrypted payload length" }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        val ciphertextLength = combinedBytes.size - GCM_IV_LENGTH_BYTES
        val ciphertext = ByteArray(ciphertextLength)

        System.arraycopy(combinedBytes, 0, iv, 0, GCM_IV_LENGTH_BYTES)
        System.arraycopy(combinedBytes, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertextLength)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), spec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * INVARIANT 2: No Plaintext Passphrases.
     * Retrieves or generates a 256-bit cryptographically secure database passphrase
     * encrypted at rest via hardware Keystore.
     *
     * The returned ByteArray MUST be zeroized using [wipeByteArray] immediately after
     * passing to SQLCipher's SupportOpenHelperFactory.
     */
    fun getDatabasePassphrase(context: Context): ByteArray {
        val secretFile = File(context.filesDir, ENCRYPTED_PASSPHRASE_FILE)
        synchronized(this) {
            if (secretFile.exists()) {
                val encryptedBlob = secretFile.readBytes()
                return decrypt(encryptedBlob)
            }

            // Generate fresh 256-bit cryptographically random passphrase
            val rawPassphrase = ByteArray(PASSPHRASE_LENGTH_BYTES)
            SecureRandom().nextBytes(rawPassphrase)

            // Encrypt with hardware Keystore before persisting to internal private app storage
            val encryptedBlob = encrypt(rawPassphrase)
            secretFile.writeBytes(encryptedBlob)

            return rawPassphrase
        }
    }

    /**
     * Securely zeroizes sensitive memory buffers (NIST SP 800-88).
     */
    fun wipeByteArray(bytes: ByteArray) {
        Arrays.fill(bytes, 0.toByte())
    }
}
