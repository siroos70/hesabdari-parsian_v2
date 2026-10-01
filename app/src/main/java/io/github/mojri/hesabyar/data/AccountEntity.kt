package io.github.mojri.hesabyar.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

enum class AccountType(
  val displayName: String
) {
  BANK("بانکی"),
  CASH_WALLET("کیف پول نقدی"),
  SAVINGS_INVESTMENT("پس\u200cانداز/سرمایه\u200cگذاری"),
  OTHER("سایر");

  companion object {
    /**
     * Parse an [AccountType] from a name string, returning [OTHER] for
     * unrecognized values instead of throwing [IllegalArgumentException].
     * Used by Rust mappers where the Rust core may emit future enum variants
     * not yet known to the Kotlin side.
     */
    fun safeValueOf(name: String): AccountType =
      try {
        valueOf(name)
      } catch (_: IllegalArgumentException) {
        OTHER
      }
  }
}

@Entity(tableName = "accounts")
data class AccountEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val type: AccountType,
  val bankName: String? = null,
  val cardNumber: String? = null,
  val accountNumber: String? = null,
  val iban: String? = null,
  // Rial — @ColumnInfo(defaultValue) must match the SQL DEFAULT in MIGRATION_5_6
  // so Room's schema validation passes on upgrade.
  @ColumnInfo(defaultValue = "0") val initialBalance: Long = 0L,
  @ColumnInfo(defaultValue = "${DEFAULT_COLOR}") val color: Long = DEFAULT_COLOR,
  val icon: String? = null,
  @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
  @ColumnInfo(defaultValue = "0") val displayOrder: Int = 0,
  // Timestamps: SQL DEFAULT 0 matches the entity annotation; the Kotlin-level
  // default (System.currentTimeMillis()) is used for new rows at the app layer.
  // MIGRATION_6_7 uses DEFAULT 0 so the schema matches exactly.
  @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
  @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 2L

    /** Default account colour (green) — single source of truth for this value. */
    const val DEFAULT_COLOR: Long = 0xFF4CAF50L

    val DEFAULT_ACCOUNT =
      AccountEntity(
        id = 1L,
        name = "حساب اصلی",
        type = AccountType.BANK,
        initialBalance = 0L,
        displayOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
      )
  }
}
