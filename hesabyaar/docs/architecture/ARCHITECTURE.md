# Hesabyar Architecture

## Vision

Hesabyar is a Persian-first intelligent accounting assistant.

The application enables users to manage personal finances using:

- Manual Entry
- Text Commands
- Voice Commands
- AI Assistance

Primary goals:

- Fast financial recording
- Natural Persian interaction
- Offline-first experience
- Reliable local storage
- Scalable architecture
- Modern Material 3 user experience

---

# Core Principles

## Offline First

All critical functionality must work without internet access.

The local database is the primary source of truth.

---

## User Ownership

User data belongs to the user.

Cloud services are optional enhancements.

---

## AI Assisted, Not AI Controlled

AI helps users create and categorize records.

AI never modifies financial data without confirmation.

---

## Source of Truth

Room Database is the single source of truth for **stored data**.

Rust Core (`rust/hesabyar-core`) is the single source of truth for **business logic**, calculations, and validation. All reports and calculations must derive from stored records and compute through the Rust core.

## Business Logic Policy

Rust Core (`rust/hesabyar-core`) is the canonical implementation for new business logic. Kotlin fallbacks exist only for the pre-approved exception list. See `ADR-001-rust-sole-implementation.md` (`## Decision` and `### Permanent Kotlin Fallbacks (Exception List)`) for the full policy, exception list, and rationale.

---

# Domain Model

## Account

Represents where money exists.

Examples:

- Cash
- Bank Account
- Credit Card
- Wallet

Fields:

```text
id
name
type
createdAt
```

---

## Category

Represents transaction classification.

Examples:

- Food
- Transportation
- Salary
- Shopping

Fields:

```text
id
name
icon
type
createdAt
```

---

## Transaction

Represents:

- Expense
- Income

Fields:

```text
id
accountId
categoryId
amount
note
date
createdAt
```

---

## Loan

Represents:

- Debt
- Credit

Fields:

```text
id
personName
amount
status
createdAt
```

---

## Installment

Represents scheduled or recurring payments.

Fields:

```text
id
title
amount
dueDate
paid
createdAt
```

---

# Architecture Style

Feature-Based Architecture

Current repository uses:

- **Rust** — canonical implementation for business logic, calculations, and validation (`rust/hesabyar-core`)
- Kotlin — UI (Jetpack Compose, Material 3), persistence orchestration (Room), and FFI bridge via UniFFI
- Navigation Compose
- Firebase AI (optional, for online natural language parsing)
- Hilt (Dependency Injection)

Kotlin package layout:

- `ui/` — Screens, ViewModels, Theme
- `api/` — AI providers (GeminiParser, BudgetAdvisor, AiProvider interface)
- `data/` — Room entities, DAOs, Repository, ExcelExporter, BackupModels
- `domain/` — UseCases (thin wrappers), DTO mappers
- `rust/` — UniFFI bridge (RustBridge.kt, RustMappers.kt), generated bindings
- `auth/` — AuthManager, BiometricHelper, Pin/LockScreen
- `core/` — AppLogger
- `di/` — Hilt modules (AiModule, DatabaseModule, RepositoryModule, UseCaseModule)
- `reminder/` — WorkManager workers, notification helpers

The project should remain modular and scalable.

---

# Project Structure

```text
app/src/main/java/io/github/mojri/hesabyar/

├── api/               # AI providers: AdviceValidationPolicy, AiProvider,
│                      # AiProviderConfig, BudgetAdviceGenerator,
│                      # BudgetAdvisor, GeminiParser
├── auth/              # AuthManager, BiometricHelper, PinScreen,
│                      # LockScreen, PinStorage, BackupCipher
├── core/              # AppLogger
├── data/              # Room database: AppDatabase, Entities, Daos,
│                      # TypeConverters, Repository (+Interface),
│                      # ExcelExporter, BackupModels, DatabaseKeyManager
├── di/                # Hilt modules: AiModule, DatabaseModule,
│                      # RepositoryModule, UseCaseModule
├── domain/
│   ├── exception/     # Domain exceptions
│   ├── usecase/       # Use cases (transaction, budget, backup, export)
│   └── utils/         # TransactionAmountResolver
├── reminder/          # WorkManager workers, notification helpers,
│                      # BootReceiver, MarkPaidReceiver, ReminderScheduler
├── rust/              # RustBridge.kt, RustMappers.kt, generated UniFFI
│                      # bindings (hesabyar_core.kt)
└── ui/
    ├── components/    # Shared reusable Compose elements
    ├── designsystem/  # Design tokens (spacing, shape, elevation, color)
    ├── screens/       # Compose screens (dashboard/, account/)
    ├── theme/         # Material 3 theme (Color, Theme, Type)
    ├── utils/         # Formatters, category icons
    └── ViewModels, JalaliCalendarHelper, CurrencyFormatter, UiState,
        and backup coordinators sit at the package root
```

---

# Data Flow

```text
Repository ←→ Room Database (AppDatabase)
  ↓ (loads records, emits Flow<List<T>>)
ViewModel (AndroidViewModel)
  ↓ (passes data snapshots to)
UseCase (business logic orchestration)
  ↓
RustBridge → Rust Core (all calculations, validation, advisory)
  ↑ (returns results to; Rust Core does NOT read Room directly)
ViewModel
  ↓
StateFlow / collectAsState()
  ↓
UI (Compose Screens)
```

AI Flow:
```text
User Text Input
 ↓
AiAssistantViewModel.parseSmartSentence()
 ↓
GeminiParser.parseSentence()
 ├── Online: AiProvider.generateContent() → API → JSON parse
 └── Offline: RustBridge.parseSentenceOfflineSync() → Rust NLP core (Kotlin parser retained as permanent fallback per ADR-001 exception list)
 ↓
ParsedResult (type, amount, category, description, ...)
 ↓
User Confirmation Dialog
 ↓
Repository.insertTransaction/insertLoan/insertInstallment
 ↓
Room Database → Flow emission → UI update
```

---

# Storage Architecture

## Primary Storage

Room Database

Responsibilities:

- Accounts
- Categories
- Transactions
- Loans
- Installments
- Settings

---

## Future Storage Features

- Encrypted Backup
- Restore
- Cloud Sync
- Multi Device Support

---

# AI Architecture

AI functionality lives inside:

```text
api/
```

Structure:

```text
AdviceValidationPolicy.kt  # AI advice discard policy (fallback decision)
AiProvider.kt              # Multi-provider AI client
AiProviderConfig.kt        # Config management + EncryptedSharedPrefs
BudgetAdviceGenerator.kt   # Budget advice generation (extracted from GeminiParser)
BudgetAdvisor.kt           # AI + offline budget advice
GeminiParser.kt            # Sentence parsing (online + offline)
```

> **Note:** paths above refer to `app/src/main/java/io/github/mojri/hesabyar/...` packages. Money detection and Persian amount extraction run in the Rust core (`rust/hesabyar-core/src/parser/`), not in Kotlin.

---

## Voice Flow

```text
Voice Input
 ↓
Speech To Text
 ↓
Text
 ↓
AI Parser
 ↓
Structured Result
 ↓
Confirmation Dialog
 ↓
Repository
 ↓
Database Save
```

---

## Text Flow

```text
User Text
 ↓
AI Parser
 ↓
Structured Result
 ↓
Confirmation Dialog
 ↓
Repository
 ↓
Database Save
```

---

# Reporting System

Reports are generated from source records.

Rules:

- Never store aggregated values
- Never duplicate balances
- Never persist calculated totals
- Always calculate from transactions

Supported Reports:

- Daily Summary
- Weekly Summary
- Monthly Summary
- Category Analysis
- Cash Flow
- Loan Overview
- Installment Tracking

---

# Design System Architecture

## Design Language

Hesabyar uses Material Design 3 (Material You).

The application should feel similar to modern Google applications.

Design Inspirations:

- Google Wallet
- Google Calendar
- Google Tasks
- Google Keep

Design Goals:

- Clean
- Minimal
- Friendly
- Financially Focused

---

# Theme Architecture

Theme defines:

- Color Scheme
- Typography
- Shapes
- Spacing
- Elevation

No hardcoded styling values are allowed inside screens.

---

# Design Tokens

## AppSpacing

```text
XS = 4dp
SM = 8dp
MD = 16dp
LG = 24dp
XL = 32dp
```

---

## AppShapes

```text
Small
Medium
Large
```

---

## AppTypography

```text
Display
Headline
Title
Body
Label
```

---

## AppElevation

```text
Level0
Level1
Level2
Level3
Level4
Level5
```

---

# Design System Structure

```text
ui/components/              # Shared Compose components
ui/utils/                   # Shared utility functions
ui/theme/                   # Material 3 theme (colors, typography, shapes)
```

---

# Reusable Components

Location:

```text
ui/components/
```

> **Note:** paths above refer to `app/src/main/java/io/github/mojri/hesabyar/...` packages.

Components:

- AppCard
- AppTopBar
- AppDialog
- AppScaffold
- AppBottomSheet
- AppTextField
- CurrencyInput
- CurrencyText
- PersianDateText
- BalanceCard
- SummaryCard
- EmptyState

---

# Screen Architecture

Example Dashboard:

```text
DashboardScreen

├── BalanceCard
├── IncomeExpenseSummary
├── UpcomingInstallments
└── RecentTransactions
```

---

# Navigation

```text
Dashboard

Transactions
 ├── Add Transaction
 └── Edit Transaction

Loans

Installments

Reports

Settings
```

---

# Accessibility

Requirements:

- RTL First Design
- Screen Reader Support
- Semantic Labels
- High Contrast Support
- Minimum Touch Targets

Accessibility is mandatory.

---

# Dark Mode

Dark Mode is required.

Requirements:

- Material 3 Color System
- Dynamic Colors
- Proper Contrast Ratios

Rules:

- No custom dark theme
- Theme-driven colors only

---

# Security

Requirements:

- Secure Local Storage
- Encrypted Secrets
- Secure Backup Files

Future:

- Database Encryption

---

# Performance

Requirements:

- Fast Startup
- Low Memory Usage
- Smooth Scrolling
- Efficient Queries
- Lazy Loading

---

# Reliability

Requirements:

- No Data Loss
- Safe Migrations
- Automated Testing
- Error Recovery

---

# Maintainability

Requirements:

- Modular Structure
- Reusable Components
- Testable Business Logic
- Clear Separation of Concerns

---

# Roadmap

## Phase 1 - MVP

- Accounts
- Categories
- Transactions
- Loans
- Installments
- Dashboard
- Basic Reports

---

## Phase 2 - Productivity

- Backup
- Restore
- Excel Export
- Notifications
- Search
- Filters

---

## Phase 3 - Intelligence

- Voice Entry
- AI Parsing
- AI Categorization
- Smart Reports
- Spending Insights

---

## Phase 4 - Ecosystem

- Cloud Sync
- Multi Device
- Shared Accounts
- Family Finance Management

---

# Success Criteria

A successful Hesabyar release should provide:

- Fast financial recording
- Natural Persian interaction
- Reliable local storage
- Useful financial insights
- Modern Material 3 experience
- Accessibility compliance
- Future-ready architecture
