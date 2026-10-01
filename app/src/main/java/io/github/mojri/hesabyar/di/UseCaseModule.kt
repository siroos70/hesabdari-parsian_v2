package io.github.mojri.hesabyar.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mojri.hesabyar.data.ExcelExporter
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.domain.usecase.ExportExcelUseCase
import io.github.mojri.hesabyar.domain.usecase.GetAnalyticsUseCase
import io.github.mojri.hesabyar.domain.usecase.GetBudgetAdviceUseCase
import io.github.mojri.hesabyar.domain.usecase.GetDashboardDataUseCase
import io.github.mojri.hesabyar.domain.usecase.GetForecastUseCase
import io.github.mojri.hesabyar.domain.usecase.GetSettingsUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageBankLoanUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageCategoryUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageInstallmentUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageLoanUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageTransactionUseCase
import io.github.mojri.hesabyar.domain.usecase.ParseTransactionUseCase
import io.github.mojri.hesabyar.domain.usecase.SubmitManualTransactionUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
  @Provides
  fun provideGetDashboardDataUseCase(repository: HesabyarRepositoryInterface): GetDashboardDataUseCase =
    GetDashboardDataUseCase(repository)

  @Provides
  fun provideManageTransactionUseCase(repository: HesabyarRepositoryInterface): ManageTransactionUseCase =
    ManageTransactionUseCase(repository)

  @Provides
  fun provideManageLoanUseCase(repository: HesabyarRepositoryInterface): ManageLoanUseCase =
    ManageLoanUseCase(repository)

  @Provides
  fun provideManageInstallmentUseCase(repository: HesabyarRepositoryInterface): ManageInstallmentUseCase =
    ManageInstallmentUseCase(repository)

  @Provides
  fun provideSubmitManualTransactionUseCase(
    manageTransaction: ManageTransactionUseCase,
    manageLoan: ManageLoanUseCase,
    manageInstallment: ManageInstallmentUseCase
  ): SubmitManualTransactionUseCase = SubmitManualTransactionUseCase(manageTransaction, manageLoan, manageInstallment)

  @Provides
  fun provideManageBankLoanUseCase(repository: HesabyarRepositoryInterface): ManageBankLoanUseCase =
    ManageBankLoanUseCase(repository)

  @Provides
  fun provideManageCategoryUseCase(repository: HesabyarRepositoryInterface): ManageCategoryUseCase =
    ManageCategoryUseCase(repository)

  @Provides
  fun provideParseTransactionUseCase(repository: HesabyarRepositoryInterface): ParseTransactionUseCase =
    ParseTransactionUseCase(repository)

  @Provides
  fun provideGetBudgetAdviceUseCase(): GetBudgetAdviceUseCase = GetBudgetAdviceUseCase()

  @Provides
  fun provideGetForecastUseCase(): GetForecastUseCase = GetForecastUseCase()

  @Provides
  fun provideGetAnalyticsUseCase(): GetAnalyticsUseCase = GetAnalyticsUseCase()

  @Provides
  fun provideManageBackupUseCase(
    @ApplicationContext context: Context,
    repository: HesabyarRepositoryInterface
  ): ManageBackupUseCase = ManageBackupUseCase(repository, application = context)

  @Provides
  fun provideExportExcelUseCase(
    repository: HesabyarRepositoryInterface,
    excelExporter: ExcelExporter
  ): ExportExcelUseCase = ExportExcelUseCase(repository, excelExporter)

  @Provides
  @Singleton
  fun provideGetSettingsUseCase(
    @ApplicationContext context: Context
  ): GetSettingsUseCase = GetSettingsUseCase(context.getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE))

  @Provides
  @Singleton
  fun provideExcelExporter(
    @ApplicationContext context: Context
  ): ExcelExporter = ExcelExporter(context)
}
