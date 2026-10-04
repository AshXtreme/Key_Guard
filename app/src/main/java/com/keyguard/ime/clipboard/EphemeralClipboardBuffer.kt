package com.keyguard.ime.clipboard

import com.keyguard.ime.clipboard.model.ClipboardItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.ArrayDeque
import java.util.Arrays
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * EphemeralClipboardBuffer
 *
 * Tier-1 in-memory clipboard buffer enforcing:
 *  1. Zero Disk Residue: Stored purely in volatile RAM, never serialized or cached to disk.
 *  2. Capacity Cap: Strict 50-item circular ring buffer.
 *  3. NIST SP 800-88 Memory Sanitization: Evicted items have their backing CharArrays overwritten with '\0'.
 *  4. Password Exclusion: Refuses to record clipboard data when isSecureTarget == true.
 *  5. 24-Hour Expiration: Automated coroutine periodically purges expired items.
 */
class EphemeralClipboardBuffer(
    val maxCapacity: Int = MAX_CAPACITY,
    val ttlMs: Long = DEFAULT_TTL_MS,
    private val purgeIntervalMs: Long = DEFAULT_PURGE_INTERVAL_MS,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : Closeable {

    companion object {
        const val MAX_CAPACITY = 50
        const val DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L // 24 Hours
        const val DEFAULT_PURGE_INTERVAL_MS = 5 * 60 * 1000L // 5 Minutes
    }

    private val lock = ReentrantReadWriteLock()
    private val buffer = ArrayDeque<ClipboardItem>(maxCapacity)
    private var purgeJob: Job? = null

    init {
        startAutoPurge()
    }

    /**
     * Attempts to record a new clipboard entry into the ephemeral ring buffer.
     *
     * Invariants:
     *  - If isSecureTarget is true, the entry is REJECTED and the incoming array is sanitized with '\0'.
     *  - If buffer is full (50 items), the oldest item is evicted and zeroized.
     *
     * @param chars Mutable character payload.
     * @param isSecureTarget True if copied from or currently in a password/PIN field.
     * @return True if recorded, false if rejected due to security exclusion.
     */
    fun add(
        chars: CharArray,
        isSecureTarget: Boolean,
        timestampMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (isSecureTarget) {
            // INVARIANT 3: Hard-exclude passwords & zeroize incoming parameter immediately
            Arrays.fill(chars, '\u0000')
            return false
        }

        if (chars.isEmpty()) {
            return false
        }

        lock.write {
            // Deduplicate against the most recent entry
            if (buffer.isNotEmpty()) {
                val mostRecent = buffer.last
                if (!mostRecent.isWiped && chars.contentEquals(mostRecent.chars)) {
                    // Update timestamp on duplicate copy
                    buffer.removeLast()
                    buffer.addLast(ClipboardItem(rawChars = chars, timestampMs = timestampMs))
                    return true
                }
            }

            // INVARIANT 2: Evict oldest entry when capacity is reached and sanitize memory
            while (buffer.size >= maxCapacity) {
                val evicted = buffer.removeFirst()
                evicted.wipe()
            }

            val newItem = ClipboardItem(rawChars = chars, timestampMs = timestampMs)
            buffer.addLast(newItem)
            return true
        }
    }

    /**
     * Returns an immutable snapshot list of active, un-wiped items (newest first).
     */
    fun getItems(): List<ClipboardItem> {
        lock.read {
            return buffer.filter { !it.isWiped }.reversed()
        }
    }

    /**
     * Retrieves an item by its unique ID.
     */
    fun getItem(id: String): ClipboardItem? {
        lock.read {
            return buffer.firstOrNull { it.id == id && !it.isWiped }
        }
    }

    /**
     * Removes an item by ID and sanitizes its memory.
     */
    fun remove(id: String): Boolean {
        lock.write {
            val iterator = buffer.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.id == id) {
                    iterator.remove()
                    item.wipe()
                    return true
                }
            }
            return false
        }
    }

    /**
     * Current count of active items in the buffer.
     */
    val size: Int
        get() = lock.read { buffer.size }

    /**
     * Scans for items exceeding the 24-hour TTL, removes them from the buffer,
     * and performs NIST SP 800-88 memory zeroization on each.
     *
     * @param currentTimeMs Current timestamp reference (allows deterministic testing).
     * @return Number of items purged.
     */
    fun purgeExpired(currentTimeMs: Long = System.currentTimeMillis()): Int {
        var purgedCount = 0
        lock.write {
            val iterator = buffer.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.isExpired(currentTimeMs, ttlMs)) {
                    iterator.remove()
                    item.wipe()
                    purgedCount++
                }
            }
        }
        return purgedCount
    }

    /**
     * Cryptographically wipes all items in the ring buffer and clears memory.
     */
    fun clearAll() {
        lock.write {
            for (item in buffer) {
                item.wipe()
            }
            buffer.clear()
        }
    }

    private fun startAutoPurge() {
        purgeJob?.cancel()
        purgeJob = scope.launch {
            while (isActive) {
                delay(purgeIntervalMs)
                purgeExpired()
            }
        }
    }

    override fun close() {
        purgeJob?.cancel()
        clearAll()
    }
}
