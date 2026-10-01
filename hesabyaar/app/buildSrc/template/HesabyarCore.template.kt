
// ============================================================================
// HesabyarCore — backward-compatible accessor object.
//
// UniFFI 0.28+ generates top-level functions. This object re-exports them so
// that existing call sites (HesabyarCore.xxx()) continue to work.
// The generateAndFixBindings Gradle task appends this template to the freshly
// generated bindings. The wrapper signatures are HAND-MAINTAINED: when a
// Rust FFI function's signature changes, update the matching line here too —
// the task only appends this file, it does not patch its signatures.
// ============================================================================
object HesabyarCore {
    fun initialize() = __PKG__.initialize()
    fun gregorianToJalali(timestampMs: Long): Long = __PKG__.gregorianToJalali(timestampMs)
    fun jalaliToGregorian(year: Int, month: Int, day: Int): Long = __PKG__.jalaliToGregorian(year, month, day)
    fun getJalaliDaysInMonth(year: Int, month: Int): Int = __PKG__.getJalaliDaysInMonth(year, month)
    fun isJalaliLeapYear(year: Int): Boolean = __PKG__.isJalaliLeapYear(year)
    fun formatCurrency(rial: Long, unit: CurrencyUnit): String = __PKG__.formatCurrency(rial, unit)
    fun toRial(displayValue: Long, unit: CurrencyUnit): Long = __PKG__.toRial(displayValue, unit)
    fun fromRial(rial: Long, unit: CurrencyUnit): Long = __PKG__.fromRial(rial, unit)
    fun formatNumber(value: Long): String = __PKG__.formatNumber(value)
    fun parseSentenceOffline(rawSentence: String): ParsedResult = __PKG__.parseSentenceOffline(rawSentence)
    fun parseSentenceOfflineAt(rawSentence: String, nowMs: Long): ParsedResult = __PKG__.parseSentenceOfflineAt(rawSentence, nowMs)
    fun inferExpenseCategory(sentence: String): CategoryGuess = __PKG__.inferExpenseCategory(sentence)
    fun parsePersianAmount(sentence: String): Long = __PKG__.parsePersianAmount(sentence)
    fun containsMoney(sentence: String): Boolean = __PKG__.containsMoney(sentence)
    fun preprocessPersianText(text: String): String = __PKG__.preprocessPersianText(text)
    fun normalizeMoneyText(text: String): String = __PKG__.normalizeMoneyText(text)
    fun getOfflineBudgetAdvice(transactions: List<Transaction>, categories: List<Category>, excludedCategoryIds: List<Long> = emptyList()): String = __PKG__.getOfflineBudgetAdvice(transactions, categories, excludedCategoryIds)
    fun getOfflineForecast(transactions: List<Transaction>, loans: List<Loan>, installments: List<Installment>, bankLoans: List<BankLoan> = emptyList(), excludedCategoryIds: List<Long> = emptyList()): String = __PKG__.getOfflineForecast(transactions, loans, installments, bankLoans, excludedCategoryIds)
    fun calculateDebtToIncomeRatio(loans: List<Loan>, installments: List<Installment>, monthlyIncome: Long, bankLoans: List<BankLoan> = emptyList()): Double = __PKG__.calculateDebtToIncomeRatio(loans, installments, bankLoans, monthlyIncome)
    fun predictTimeToGoal(currentSavings: Long, monthlySavings: Long, goalAmount: Long): Int = __PKG__.predictTimeToGoal(currentSavings, monthlySavings, goalAmount)
    fun calculateFinancialHealthScore(transactions: List<Transaction>, loans: List<Loan>, installments: List<Installment>, categories: List<Category>, bankLoans: List<BankLoan> = emptyList(), excludedCategoryIds: List<Long> = emptyList()): Int = __PKG__.calculateFinancialHealthScore(transactions, loans, installments, bankLoans, categories, excludedCategoryIds)
    fun computeAnalytics(transactions: List<Transaction>, loans: List<Loan>, installments: List<Installment>, categories: List<Category>, bankLoans: List<BankLoan>, accounts: List<Account>, accountId: kotlin.Long?, includeArchived: kotlin.Boolean = false, excludedCategoryIds: List<Long> = emptyList()): AnalyticsData? = __PKG__.computeAnalytics(transactions, loans, installments, categories, bankLoans, accounts, accountId, includeArchived, excludedCategoryIds)
    fun computeDashboardData(transactions: List<Transaction>, loans: List<Loan>, installments: List<Installment>, bankLoans: List<BankLoan>, accounts: List<Account>, accountId: kotlin.Long?, includeArchived: kotlin.Boolean, nowMs: kotlin.Long, excludedCategoryIds: List<Long> = emptyList()): DashboardData? = __PKG__.computeDashboardData(transactions, loans, installments, bankLoans, accounts, accountId, includeArchived, nowMs, excludedCategoryIds)
    fun parseBackupJson(json: String): BackupPayload = __PKG__.parseBackupJson(json)
    @Throws(HesabyarException::class) fun validateBackup(payload: BackupPayload) = __PKG__.validateBackup(payload)
    fun exportBackupJson(payload: BackupPayload): String = __PKG__.exportBackupJson(payload)
    fun searchTransactions(transactions: List<Transaction>, query: SearchQuery): SearchResponse = __PKG__.searchTransactions(transactions, query)
// UniFFI 0.32+ requires a direct ByteBuffer for byte arguments.
    private fun toDirectBuffer(data: ByteArray): java.nio.ByteBuffer {
        val buffer = java.nio.ByteBuffer.allocateDirect(data.size)
        buffer.put(data)
        buffer.flip()
        return buffer
    }

    fun computeChecksum(data: ByteArray): String = __PKG__.computeChecksum(toDirectBuffer(data))
    fun verifyChecksum(data: ByteArray, expected: String): Boolean = __PKG__.verifyChecksum(toDirectBuffer(data), expected)
    @Throws(HesabyarException::class) fun validateTransaction(transaction: Transaction) = __PKG__.validateTransaction(transaction)
    @Throws(HesabyarException::class) fun validateLoan(loan: Loan) = __PKG__.validateLoan(loan)
    @Throws(HesabyarException::class) fun validateInstallment(installment: Installment) = __PKG__.validateInstallment(installment)
    @Throws(HesabyarException::class) fun validateParsedResult(result: ParsedResult) = __PKG__.validateParsedResult(result)
    fun validateBackupPayload(payload: BackupPayload): ValidationResult = __PKG__.validateBackupPayload(payload)
    @Throws(HesabyarException::class) fun generateExcel(workbook: WorkbookData): ByteArray = __PKG__.generateExcel(workbook)
    @Throws(HesabyarException::class) fun parseAiTransactionJson(json: String): AiParsedTransaction = __PKG__.parseAiTransactionJson(json)
    fun validateAiAdvice(text: String): AdviceValidation = __PKG__.validateAiAdvice(text)
}
