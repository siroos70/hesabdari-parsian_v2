package io.github.mojri.hesabyar.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mojri.hesabyar.domain.usecase.ExportExcelUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ExportViewModel
  @Inject
  constructor(
    @ApplicationContext private val application: Context,
    private val exportExcelUseCase: ExportExcelUseCase
  ) : ViewModel() {
    private val appContext = application

    val exportState = mutableStateOf<ExportState>(ExportState.Idle)

    @Suppress("TooGenericExceptionCaught") // Rust FFI can rethrow RuntimeException; justified safety net
    fun exportExcel() {
      viewModelScope.launch {
        exportState.value = ExportState.Exporting
        try {
          val result = exportExcelUseCase.export()

          val savedPath = saveToDownloads(result.filename, result.bytes)

          val summary =
            buildString {
              append("فایل اکسل در پوشه Downloads ذخیره شد:\n")
              append(savedPath)
              append("\n\n")
              append("${result.accountCount} حساب")
              append("، ${result.transactionCount} تراکنش")
              if (result.incomeCount > 0) append(" (${result.incomeCount} دریافتی)")
              if (result.expenseCount > 0) append(" (${result.expenseCount} پرداختی)")
              append("، ${result.loanCount} وام، ${result.installmentCount} قسط")
            }

          exportState.value = ExportState.Success(summary)
        } catch (e: java.io.IOException) {
          exportState.value =
            ExportState.Error(
              "خطا در ایجاد یا ذخیره فایل اکسل: ${e.localizedMessage ?: "خطای ناشناخته"}"
            )
        } catch (e: SecurityException) {
          exportState.value =
            ExportState.Error(
              "دسترسی به پوشه Downloads امکان\u200Cپذیر نیست: ${e.localizedMessage ?: "خطای دسترسی"}"
            )
        } catch (e: CancellationException) {
          throw e
        } catch (e: IllegalStateException) {
          exportState.value =
            ExportState.Error(
              "خطا در تولید فایل اکسل: ${e.localizedMessage ?: "خطای ناشناخته"}"
            )
        } catch (e: IllegalArgumentException) {
          android.util.Log.e("ExportViewModel", "Invalid argument during Excel export", e)
          exportState.value =
            ExportState.Error(
              "خطای پارامتر در خروجی اکسل: ${e.localizedMessage ?: "پارامتر نامعتبر"}"
            )
        } catch (e: Exception) {
          // Safety net: Rust FFI (rustCallSync) can rethrow RuntimeException
          // including NPE from the Rust bridge. Without this catch the app crashes.
          android.util.Log.e("ExportViewModel", "Unexpected error during Excel export", e)
          exportState.value =
            ExportState.Error(
              "خطای غیرمنتظره در خروجی اکسل: ${e.localizedMessage ?: "خطای ناشناخته"}"
            )
        }
      }
    }

    private suspend fun saveToDownloads(
      fileName: String,
      bytes: ByteArray
    ): String =
      withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val contentValues =
            ContentValues().apply {
              put(MediaStore.Downloads.DISPLAY_NAME, fileName)
              put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
              put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

          val resolver = appContext.contentResolver
          val uri =
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
              ?: throw java.io.IOException("ایجاد فایل در Downloads ناموفق بود")

          resolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
          } ?: throw java.io.IOException("نوشتن فایل ناموفق بود")

          "Downloads/$fileName"
        } else {
          @Suppress("DEPRECATION")
          val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
          if (!downloadsDir.exists()) downloadsDir.mkdirs()

          val destFile = java.io.File(downloadsDir, fileName)
          destFile.writeBytes(bytes)

          destFile.absolutePath
        }
      }

    fun clearState() {
      exportState.value = ExportState.Idle
    }
  }
