package io.github.mojri.hesabyar.domain.exception

/**
 * Thrown by [io.github.mojri.hesabyar.data.HesabyarRepository.deleteAccount]
 * when the account being deleted is the last remaining active (non-archived)
 * account.
 *
 * Extends [IllegalStateException] so it is still caught by legacy catch
 * blocks that expect [IllegalStateException], but callers that need to
 * surface a localized user message should catch this type specifically and
 * resolve [io.github.mojri.hesabyar.R.string.account_delete_last_active_account_error]
 * instead of relying on [message] (which carries an English developer string).
 */
class CannotDeleteLastActiveAccountException(
  val accountId: Long
) : IllegalStateException(
    "Account $accountId is the last remaining active account and cannot be deleted"
  )
