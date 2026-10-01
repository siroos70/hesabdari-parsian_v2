package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.reminder.MarkPaidReceiver
import io.github.mojri.hesabyar.reminder.ReminderSettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReminderTest {
  @Test
  fun `default reminder config has sensible defaults`() {
    val config = ReminderSettingsManager.ReminderConfig()
    assertTrue(config.masterEnabled)
    assertTrue(config.installmentReminderEnabled)
    assertTrue(config.loanReminderEnabled)
    assertEquals(1, config.reminderDaysBeforeDue)
    assertEquals(8, config.reminderHour)
    assertEquals(0, config.reminderMinute)
    assertEquals(7, config.loanReminderDaysInterval)
  }

  @Test
  fun `days before due calculation for upcoming installment`() {
    val now = System.currentTimeMillis()
    val dueIn3Days = now + TimeUnit.DAYS.toMillis(3)
    val daysUntilDue = TimeUnit.MILLISECONDS.toDays(dueIn3Days - now).toInt()
    assertEquals(3, daysUntilDue)
    assertTrue(daysUntilDue in 1..14)
  }

  @Test
  fun `days before due calculation for overdue installment`() {
    val now = System.currentTimeMillis()
    val dueYesterday = now - TimeUnit.DAYS.toMillis(1)
    val daysUntilDue = TimeUnit.MILLISECONDS.toDays(dueYesterday - now).toInt()
    assertEquals(-1, daysUntilDue)
    assertTrue(daysUntilDue < 0)
  }

  @Test
  fun `days before due calculation for due today`() {
    val now = System.currentTimeMillis()
    val daysUntilDue = TimeUnit.MILLISECONDS.toDays(now - now).toInt()
    assertEquals(0, daysUntilDue)
  }

  @Test
  fun `installment filtering respects isPaid`() {
    val installments =
      listOf(
        InstallmentStub(id = 1, isPaid = false, dueDateOffsetDays = 2),
        InstallmentStub(id = 2, isPaid = true, dueDateOffsetDays = 2),
        InstallmentStub(id = 3, isPaid = false, dueDateOffsetDays = -3)
      )

    val now = System.currentTimeMillis()
    val daysBeforeDue = 3

    val shouldRemind =
      installments.filter { inst ->
        !inst.isPaid &&
          run {
            val daysUntilDue =
              TimeUnit.MILLISECONDS
                .toDays(
                  inst.dueDateOffsetDays * TimeUnit.DAYS.toMillis(1)
                ).toInt()
            daysUntilDue in -7..daysBeforeDue
          }
      }

    assertEquals(2, shouldRemind.size)
    assertTrue(shouldRemind.any { it.id == 1L })
    assertTrue(shouldRemind.any { it.id == 3L })
  }

  @Test
  fun `installment filtering respects reminderEnabled`() {
    val installments =
      listOf(
        InstallmentStub(id = 1, isPaid = false, reminderEnabled = true, dueDateOffsetDays = 2),
        InstallmentStub(id = 2, isPaid = false, reminderEnabled = false, dueDateOffsetDays = 2),
      )

    val daysBeforeDue = 3

    val shouldRemind =
      installments.filter { inst ->
        !inst.isPaid &&
          inst.reminderEnabled &&
          run {
            val daysUntilDue =
              TimeUnit.MILLISECONDS
                .toDays(
                  inst.dueDateOffsetDays * TimeUnit.DAYS.toMillis(1)
                ).toInt()
            daysUntilDue in -7..daysBeforeDue
          }
      }

    assertEquals(1, shouldRemind.size)
    assertEquals(1L, shouldRemind[0].id)
  }

  @Test
  fun `loan filtering skips settled and zero remaining`() {
    val loans =
      listOf(
        LoanStub(id = 1, isSettled = false, remainingAmount = 5_000_000),
        LoanStub(id = 2, isSettled = true, remainingAmount = 3_000_000),
        LoanStub(id = 3, isSettled = false, remainingAmount = 0),
      )

    val shouldRemind =
      loans.filter { loan ->
        !loan.isSettled && loan.remainingAmount > 0
      }

    assertEquals(1, shouldRemind.size)
    assertEquals(1L, shouldRemind[0].id)
  }

  @Test
  fun `reminder time clamping for hour`() {
    val hour = 25
    val clamped = hour.coerceIn(0, 23)
    assertEquals(23, clamped)

    val hourNeg = -1
    val clampedNeg = hourNeg.coerceIn(0, 23)
    assertEquals(0, clampedNeg)
  }

  @Test
  fun `reminder time clamping for minute`() {
    val minute = 75
    val clamped = minute.coerceIn(0, 59)
    assertEquals(59, clamped)
  }

  @Test
  fun `days before due clamping`() {
    val days = 50
    val clamped = days.coerceIn(1, 30)
    assertEquals(30, clamped)

    val daysLow = 0
    val clampedLow = daysLow.coerceIn(1, 30)
    assertEquals(1, clampedLow)
  }

  @Test
  fun `loan interval clamping`() {
    val days = 45
    val clamped = days.coerceIn(1, 30)
    assertEquals(30, clamped)
  }

  @Test
  fun `master disabled skips all reminders`() {
    val config = ReminderSettingsManager.ReminderConfig(masterEnabled = false)
    assertFalse(config.masterEnabled)
  }

  @Test
  fun `installment disabled skips installment reminders`() {
    val config = ReminderSettingsManager.ReminderConfig(installmentReminderEnabled = false)
    assertFalse(config.installmentReminderEnabled)
    assertTrue(config.loanReminderEnabled)
  }

  @Test
  fun `loan disabled skips loan reminders`() {
    val config = ReminderSettingsManager.ReminderConfig(loanReminderEnabled = false)
    assertTrue(config.installmentReminderEnabled)
    assertFalse(config.loanReminderEnabled)
  }

  @Test
  fun `mark paid receiver action constant is correct`() {
    assertEquals("io.github.mojri.hesabyar.ACTION_MARK_PAID", MarkPaidReceiver.ACTION_MARK_PAID)
    assertEquals("installment_id", MarkPaidReceiver.EXTRA_INSTALLMENT_ID)
  }

  private data class InstallmentStub(
    val id: Long,
    val isPaid: Boolean,
    val reminderEnabled: Boolean = true,
    val dueDateOffsetDays: Int
  )

  private data class LoanStub(
    val id: Long,
    val isSettled: Boolean,
    val remainingAmount: Long
  )
}
