package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction
import java.security.MessageDigest

/**
 * Pure, dependency-free data-signature helpers shared by [AiAssistantViewModel]
 * (production cache invalidation) and tests.
 *
 * The signature is a SHA-256 content hash over every field of every entity in
 * the four lists, so any semantic change — transaction type, categoryId, date,
 * amount, loan settlement, installment paid-state, etc. — even when counts and
 * totals are unchanged, invalidates the cache.
 *
 * Kept free of [android.content.Context] and config state so the values are
 * deterministic and unit-testable. [AiAssistantViewModel] appends its own
 * config signature to [computeDataSignature] when building the cache key, so
 * the ViewModel's public signature behavior is unchanged.
 */
internal object AdviceSignature {
  fun computeDataSignature(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    bankLoans: List<BankLoan> = emptyList()
  ): String {
    val sb = StringBuilder()
    sb.append("T:")
    for (t in transactions) sb.append("\n").append(t.contentString())
    sb.append("\nL:")
    for (l in loans) sb.append("\n").append(l.contentString())
    sb.append("\nI:")
    for (i in installments) sb.append("\n").append(i.contentString())
    sb.append("\nC:")
    for (c in categories) sb.append("\n").append(c.contentString())
    sb.append("\nB:")
    for (b in bankLoans) sb.append("\n").append(b.contentString())
    return sb.toString().sha256()
  }

  fun computeAdviceSignature(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
    bankLoans: List<BankLoan> = emptyList()
  ): String = computeDataSignature(transactions, loans, installments, categories, bankLoans)

  private fun Any.contentString(): String = this.toString()

  private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(toByteArray(Charsets.UTF_8))
    val hex = StringBuilder(bytes.size * 2)
    for (b in bytes) hex.append("%02x".format(b))
    return hex.toString()
  }
}
