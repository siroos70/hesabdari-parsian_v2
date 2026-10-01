package io.github.mojri.hesabyar.rust

// Backup, checksum, and Excel export domain of the RustBridge façade.
// See RustBridgeCore for the split.

/** Backup payload (de)serialization, checksums, and XLSX generation. */
internal interface RustBridgeBackup : RustBridgeCore {
  /** Parses backup JSON into a payload. Null when Rust is unavailable. */
  fun parseBackupJsonSync(json: String): BackupPayload? = rustCallSync(null) { HesabyarCore.parseBackupJson(json) }

  /** Validates a parsed backup payload. Returns an invalid result when Rust is unavailable. */
  fun validateBackupPayloadSync(payload: BackupPayload): ValidationResult =
    rustCallSync(ValidationResult(isValid = false, errors = emptyList())) {
      HesabyarCore.validateBackupPayload(payload)
    }

  /** Serializes a backup payload to JSON. Empty string when Rust is unavailable. */
  fun exportBackupJsonSync(payload: BackupPayload): String = rustCallSync("") { HesabyarCore.exportBackupJson(payload) }

  /** Computes the checksum of a byte array. Empty string when Rust is unavailable. */
  fun computeChecksumSync(data: ByteArray): String = rustCallSync("") { HesabyarCore.computeChecksum(data) }

  /** Verifies a checksum against a byte array. False when Rust is unavailable. */
  fun verifyChecksumSync(
    data: ByteArray,
    expected: String
  ): Boolean = rustCallSync(false) { HesabyarCore.verifyChecksum(data, expected) }

  /** Builds an XLSX workbook. Null when Rust is unavailable. */
  fun generateExcelSync(workbook: WorkbookData): ByteArray? =
    rustCallSync(null) { HesabyarCore.generateExcel(workbook) }
}
