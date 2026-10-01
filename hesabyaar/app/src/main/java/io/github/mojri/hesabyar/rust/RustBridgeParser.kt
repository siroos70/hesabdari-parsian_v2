package io.github.mojri.hesabyar.rust

// Persian natural-language parser domain of the RustBridge façade.
// See RustBridgeCore for the split.

/** Offline Persian sentence parsing and money-text helpers. */
internal interface RustBridgeParser : RustBridgeCore {
  /** Parses a Persian sentence into a transaction. Null when Rust is unavailable. */
  fun parseSentenceOfflineSync(rawSentence: String): ParsedResult? =
    parseSentenceOfflineSync(rawSentence, System.currentTimeMillis())

  /** Parses a Persian sentence against an explicit "now" timestamp. */
  fun parseSentenceOfflineSync(
    rawSentence: String,
    nowMs: Long,
  ): ParsedResult? = rustCallSync(null) { HesabyarCore.parseSentenceOfflineAt(rawSentence, nowMs) }

  /** Guesses the expense category of a sentence. Falls back to "Other". */
  fun inferExpenseCategorySync(sentence: String): CategoryGuess =
    rustCallSync(CategoryGuess(category = "Other", subcategory = "")) {
      HesabyarCore.inferExpenseCategory(sentence)
    }

  /** True when the sentence contains a money expression. */
  fun containsMoneySync(sentence: String): Boolean = rustCallSync(false) { HesabyarCore.containsMoney(sentence) }

  /** Normalizes Persian money text before amount extraction. */
  fun normalizeMoneyTextSync(text: String): String = rustCallSync(text) { HesabyarCore.normalizeMoneyText(text) }

  /** Extracts the Toman amount written in a Persian sentence. Zero when absent. */
  fun parsePersianAmountSync(sentence: String): Long = rustCallSync(0L) { HesabyarCore.parsePersianAmount(sentence) }

  /** Applies the parser text pre-processing pipeline. */
  fun preprocessPersianTextSync(text: String): String = rustCallSync(text) { HesabyarCore.preprocessPersianText(text) }

  /** Parses the model JSON reply into a transaction draft. Null on failure. */
  fun parseAiTransactionJsonSync(json: String): AiParsedTransaction? =
    rustCallSync(null) { HesabyarCore.parseAiTransactionJson(json) }
}
