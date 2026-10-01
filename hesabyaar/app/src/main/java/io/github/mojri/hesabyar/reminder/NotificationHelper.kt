package io.github.mojri.hesabyar.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.mojri.hesabyar.MainActivity
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.ui.CurrencyFormatter

object NotificationHelper {
  private const val CHANNEL_ID_INSTALLMENT = "installment_reminders"
  private const val CHANNEL_ID_LOAN = "loan_reminders"

  fun createNotificationChannels(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val installmentChannel =
      NotificationChannel(
        CHANNEL_ID_INSTALLMENT,
        "یادآوری اقساط",
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = "یادآوری سررسید اقساط"
      }

    val loanChannel =
      NotificationChannel(
        CHANNEL_ID_LOAN,
        "یادآوری وام‌ها",
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = "یادآوری بازپرداخت وام‌ها"
      }

    manager.createNotificationChannel(installmentChannel)
    manager.createNotificationChannel(loanChannel)
  }

  fun showInstallmentReminder(
    context: Context,
    installmentId: Long,
    title: String,
    amount: Long,
    daysUntilDue: Int
  ) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        installmentId.toInt(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

    val markPaidIntent = MarkPaidReceiver.createIntent(context, installmentId)
    val markPaidPendingIntent =
      PendingIntent.getBroadcast(
        context,
        installmentId.toInt(),
        markPaidIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

    val formattedAmount = CurrencyFormatter.format(amount)
    val titleText: String
    val bodyText: String

    when {
      daysUntilDue < 0 -> {
        titleText = "قسط سررسید گذشته"
        bodyText = "قسط «$title» به مبلغ $formattedAmount ${-daysUntilDue} روز پیش سررسید شده است."
      }
      daysUntilDue == 0 -> {
        titleText = "قسط امروز سررسید است"
        bodyText = "قسط «$title» به مبلغ $formattedAmount امروز سررسید شده است."
      }
      daysUntilDue == 1 -> {
        titleText = "قسط فردا سررسید است"
        bodyText = "قسط «$title» به مبلغ $formattedAmount فردا سررسید می‌شود."
      }
      else -> {
        titleText = "یادآوری قسط"
        bodyText = "قسط «$title» به مبلغ $formattedAmount $daysUntilDue روز دیگر سررسید می‌شود."
      }
    }

    val notification =
      NotificationCompat
        .Builder(context, CHANNEL_ID_INSTALLMENT)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(titleText)
        .setContentText(bodyText)
        .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .addAction(R.drawable.ic_launcher_foreground, "پرداخت شد", markPaidPendingIntent)
        .build()

    manager.notify(installmentId.toInt(), notification)
  }

  fun showLoanReminder(
    context: Context,
    loanId: Long,
    personName: String,
    remainingAmount: Long,
    loanType: LoanType
  ) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("OPEN_TAB", "LOANS")
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        (loanId + 10000).toInt(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

    val formattedAmount = CurrencyFormatter.format(remainingAmount)
    val typeLabel = if (loanType == LoanType.CREDITOR) "بدهی" else "طلب"
    val titleText = "یادآوری $typeLabel"
    val bodyText = "شما $typeLabel به مبلغ $formattedAmount به «$personName» دارید."

    val notification =
      NotificationCompat
        .Builder(context, CHANNEL_ID_LOAN)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(titleText)
        .setContentText(bodyText)
        .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .addAction(R.drawable.ic_launcher_foreground, "مشاهده", pendingIntent)
        .build()

    manager.notify((loanId + 10000).toInt(), notification)
  }
}
