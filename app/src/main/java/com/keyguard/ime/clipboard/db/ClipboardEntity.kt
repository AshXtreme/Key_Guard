package com.keyguard.ime.clipboard.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ClipboardEntity
 *
 * Tier-2 Long-Term Clipboard Vault Entity stored inside SQLCipher AES-256-GCM database.
 * Supports encrypted payloads, full-text preview search, pinning, and tag taxonomy.
 */
@Entity(
    tableName = "clipboard_vault",
    indices = [
        Index(value = ["is_pinned"]),
        Index(value = ["created_at"]),
        Index(value = ["content_type"])
    ]
)
data class ClipboardEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "encrypted_payload", typeAffinity = ColumnInfo.BLOB)
    val encryptedPayload: ByteArray,

    @ColumnInfo(name = "preview_text")
    val previewText: String,

    @ColumnInfo(name = "content_type")
    val contentType: String, // PLAIN_TEXT, CODE, URL, EMAIL

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,

    @ColumnInfo(name = "tags")
    val tags: String = "",

    @ColumnInfo(name = "is_wiped")
    val isWiped: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ClipboardEntity
        return id == other.id &&
               encryptedPayload.contentEquals(other.encryptedPayload) &&
               previewText == other.previewText &&
               contentType == other.contentType &&
               createdAt == other.createdAt &&
               isPinned == other.isPinned &&
               tags == other.tags &&
               isWiped == other.isWiped
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + encryptedPayload.contentHashCode()
        result = 31 * result + previewText.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + isPinned.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + isWiped.hashCode()
        return result
    }
}
