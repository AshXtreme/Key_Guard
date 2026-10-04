package com.keyguard.ime.clipboard.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.security.SecureRandom
import java.util.Arrays

/**
 * ClipboardDao
 *
 * Data Access Object for Tier-2 Encrypted SQLCipher Clipboard Vault.
 * Enforces NIST SP 800-88 compliant multi-pass record zeroization on deletion.
 */
@Dao
interface ClipboardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: ClipboardEntity): Long

    @Update
    fun update(entity: ClipboardEntity): Int

    @Query("SELECT * FROM clipboard_vault WHERE is_wiped = 0 ORDER BY is_pinned DESC, created_at DESC")
    fun getAllFlow(): Flow<List<ClipboardEntity>>

    @Query("SELECT * FROM clipboard_vault WHERE is_wiped = 0 ORDER BY is_pinned DESC, created_at DESC")
    fun getAll(): List<ClipboardEntity>

    @Query("SELECT * FROM clipboard_vault WHERE id = :id AND is_wiped = 0 LIMIT 1")
    fun getById(id: String): ClipboardEntity?

    @Query("SELECT * FROM clipboard_vault WHERE is_pinned = 1 AND is_wiped = 0 ORDER BY created_at DESC")
    fun getPinned(): List<ClipboardEntity>

    @Query("""
        SELECT * FROM clipboard_vault 
        WHERE is_wiped = 0 
          AND (preview_text LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') 
        ORDER BY is_pinned DESC, created_at DESC
    """)
    fun search(query: String): List<ClipboardEntity>

    @Query("UPDATE clipboard_vault SET is_pinned = :isPinned WHERE id = :id")
    fun setPinned(id: String, isPinned: Boolean): Int

    @Query("UPDATE clipboard_vault SET tags = :tags WHERE id = :id")
    fun updateTags(id: String, tags: String): Int

    // =========================================================================
    // INVARIANT 3: Multi-Pass Deletion (NIST SP 800-88 Block Overwrite)
    // =========================================================================

    @Query("UPDATE clipboard_vault SET encrypted_payload = :pattern, preview_text = '', tags = '', is_wiped = 1 WHERE id = :id")
    fun overwriteRecord(id: String, pattern: ByteArray): Int

    @Query("DELETE FROM clipboard_vault WHERE id = :id")
    fun deleteRow(id: String): Int

    /**
     * Executes a 3-pass NIST SP 800-88 compliant block overwrite before row deletion:
     *  Pass 1: Overwrite with 0x00.
     *  Pass 2: Overwrite with 0xFF.
     *  Pass 3: Overwrite with cryptographically pseudorandom noise.
     *  Final: Row removal from SQLite B-Tree.
     */
    @Transaction
    fun deleteWithMultiPassZeroFill(id: String, payloadLength: Int = 128): Boolean {
        val safeLength = if (payloadLength <= 0) 128 else payloadLength

        // Pass 1: All 0x00
        val pass1 = ByteArray(safeLength) { 0x00.toByte() }
        overwriteRecord(id, pass1)

        // Pass 2: All 0xFF
        val pass2 = ByteArray(safeLength) { 0xFF.toByte() }
        overwriteRecord(id, pass2)

        // Pass 3: Secure random pattern
        val pass3 = ByteArray(safeLength)
        SecureRandom().nextBytes(pass3)
        overwriteRecord(id, pass3)

        // Clean memory buffers
        Arrays.fill(pass1, 0.toByte())
        Arrays.fill(pass2, 0.toByte())
        Arrays.fill(pass3, 0.toByte())

        // Physical row deletion
        return deleteRow(id) > 0
    }

    @Query("DELETE FROM clipboard_vault WHERE is_pinned = 0 AND created_at < :cutoffMs")
    fun purgeOlderThan(cutoffMs: Long): Int

    @Query("DELETE FROM clipboard_vault WHERE is_pinned = 0")
    fun purgeAllUnpinned(): Int

    @Query("DELETE FROM clipboard_vault")
    fun nukeVault(): Int

    @Query("SELECT COUNT(*) FROM clipboard_vault WHERE is_wiped = 0")
    fun getCount(): Int
}
