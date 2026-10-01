# Architecture Blueprint: Next-Generation Account Management Module

> **Author:** Chief Software Architect  
> **Date:** 2026-07-31  
> **Status:** Design Document — No Implementation  
> **Scope:** Full redesign of Account Management (Add / Edit / Delete / Archive / List)  
> **Audience:** Implementation engineers, code reviewers, future maintainers

---

## Section 1: Architectural Goals

### 1.1 Mission

Redesign the Account Management module into a **scalable, testable, maintainable, and reusable** feature that:

- Preserves 100% of existing functionality (CRUD, archive, color picker, type selection)
- Eliminates all identified technical debt (monolithic file, missing feedback, state loss)
- Introduces proper state management, error handling, and loading states
- Creates reusable components usable across Dashboard, Analytics, Transactions, and future features
- Supports future extensibility (multi-currency, shared accounts, crypto wallets) without major redesign

### 1.2 Design Principles

| Principle | Description |
|---|---|
| **Single Responsibility** | Every class, composable, and function has exactly one reason to change |
| **Unidirectional Data Flow** | Events flow up (User → ViewModel), state flows down (ViewModel → UI) |
| **Composition over Inheritance** | Prefer composable functions and parameterized components |
| **Contract-First** | Define interfaces before implementations; depend on abstractions |
| **Fail Gracefully** | Every operation has error handling; the UI always reflects reality |
| **Token-Driven Design** | All visual properties derive from design tokens; zero hardcoded values |
| **Accessibility by Default** | Every component has contentDescription, semantics, and touch targets ≥48dp |
| **Offline-First** | All operations work without network; Rust fallback is transparent |

### 1.3 Non-Functional Requirements

| Requirement | Target |
|---|---|
| Recomposition count per save | ≤ 3 recompositions (dialog close + list update) |
| Form state survival | Config change + process death (via SavedStateHandle) |
| Test coverage | ≥ 90% line coverage for ViewModel + UseCase |
| Compose UI test coverage | All user flows covered |
| Maximum file length | ≤ 250 lines per file |
| Maximum composable parameters | ≤ 8 per composable |
| Maximum function length | ≤ 40 lines |

### 1.4 Constraints

- **Single-module architecture** — no feature modules (project constraint)
- **Room + Rust dual persistence** — CRUD on Kotlin side, computation on Rust side
- **Persian-first UX** — all strings in Persian, full RTL support
- **Hilt dependency injection** — consistent with project DI pattern
- **Existing design system** — must use ShapeTokens, SpacingTokens, Dimens, FinancialColors
- **No breaking Room migrations** — schema changes must preserve existing data
- **Backward-compatible backup format** — account serialization must not break existing backups

### 1.5 Success Criteria

1. `AccountManagementScreen.kt` is ≤ 100 lines (shell only)
2. Every composable is independently testable
3. All form fields survive configuration change
4. All CRUD operations show loading, success, and error states
5. Archive has confirmation dialog; delete has undo via Snackbar
6. Zero hardcoded dp/color/typography values in account screens
7. All new components have `@Preview` functions
8. Full Compose UI test coverage for add, edit, delete, archive flows

### 1.6 Long-Term Maintainability Goals

- Adding a new account field (e.g., `swiftCode`) requires changes in ≤ 3 files
- Adding a new account type requires changes in ≤ 4 files (enum + icon + type display)
- Adding a new form validation rule requires changes in 1 file (validator)
- Extracting the account feature into a feature module should be possible with zero domain/logic changes

---

## Section 2: Module Boundaries

### 2.1 Layer Responsibilities

```
┌─────────────────────────────────────────────────────┐
│                   PRESENTATION                       │
│  Screens · Components · Dialogs · Forms · Animations │
│  Responsibility: Render state, emit user events      │
├─────────────────────────────────────────────────────┤
│                   STATE LAYER                        │
│  UiState · FormState · DialogState · Event handlers  │
│  Responsibility: State ownership, event routing       │
├─────────────────────────────────────────────────────┤
│                    VIEWMODEL                         │
│  AccountViewModel · StateFlow · Side effects         │
│  Responsibility: Orchestration (calls to Rust core)    │
├─────────────────────────────────────────────────────┤
│                  DOMAIN LAYER                        │
│  UseCases (thin wrappers) · Validators · Domain Models │
│  Responsibility: Data-shape validation, DTO mapping   │
├─────────────────────────────────────────────────────┤
│                  DATA LAYER                          │
│  Repository · DAO · Room Entities · Mappers           │
│  Responsibility: Persistence, data transformation     │
├─────────────────────────────────────────────────────┤
│         FFI BOUNDARY / ADAPTER LAYER                  │
│  RustBridge · RustMappers · UniFFI bindings            │
│  Responsibility: Call marshaling, type mapping          │
│                  (thin adapter, no business logic)      │
├─────────────────────────────────────────────────────┤
│          RUST CORE (rust/hesabyar-core)                │
│  Business logic, calculations, rules, and validation   │
│  (sole implementation location — never in Kotlin)      │
└─────────────────────────────────────────────────────┘
```

### 2.2 Responsibility Matrix

| Responsibility | Layer | Owner |
|---|---|---|
| Render UI | Presentation | Screen composable |
| Manage UI state | State | UiState sealed class |
| Handle user events | Presentation → ViewModel | Event sealed class |
| Validate form data (data-shape) | Presentation → ViewModel | `AccountViewModel` (inline data-shape checks; no dedicated validator class exists today) |
| Enforce business rules / validation | FFI | Rust core (`validation.rs`) — results surfaced by outer-layer Kotlin adapters (for example `BackupJsonValidator`, which calls `RustBridge.validateBackupPayloadSync`) |
| Execute CRUD operations | Domain | UseCase classes (depend on Domain-defined ports; Data implements persistence ports, FFI provides Rust computation) |
| Coordinate side effects | ViewModel | ViewModel |
| Persist data | Data | Repository → DAO → Room |
| Compute balances | FFI | Rust core (ffi/) — sole implementation location |
| All new calculations / business logic | FFI | Rust core — NEVER implement new business logic in Kotlin |
| Show feedback (Snackbar) | Presentation | Screen via SideEffect |
| Manage dialog lifecycle | State | DialogState in UiState |

### 2.3 Dependency Rules

- **Presentation** depends on: State, Domain (UseCase interfaces only)
- **State** depends on: Domain (models, validators)
- **Domain** depends on: nothing above (pure logic); defines ports (interfaces) for persistence and Rust computation that outer layers implement
- **Data** depends on: Domain (implements Domain-defined persistence ports, defines entity DTOs)
- **FFI** depends on: Domain (computation ports) and Data (type mappers)

**Rule:** Domain layer has ZERO Android/framework dependencies. It is pure Kotlin.

> **Business Logic Policy:** Rust Core (`rust/hesabyar-core`) is the sole location for business logic, calculations, rules, and validation.
>
> **Permitted in Kotlin:** Persistence operations, DTO/entity mapping, and structural type mapping needed to call Rust or to persist/display Rust's results. Kotlin-side validators may surface Rust's validation results to the UI.
>
> **Prohibited in Kotlin:** Calculations, normalization, validation, and any rule-driven data transformation — these MUST be implemented in Rust, subject to the ADR-001 exception list (Jalali calendar, currency formatting, offline NLP parser, backup JSON parse/validate, AI advice validation).
>
> The full policy, exception list, and rationale are in `architecture/ADR-001-rust-sole-implementation.md` (`## Decision` and `### Permanent Kotlin Fallbacks (Exception List)`). See also `../plans/2026-08-19-rust-fallback-consolidation-plan.md`.

---

## Section 3: Package Structure

### 3.1 Current Structure (to be refactored)

```
ui/
  AccountViewModel.kt              ← monolithic ViewModel
  screens/account/
    AccountManagementScreen.kt     ← 731-line monolith
  components/
    AccountBalanceCard.kt          ← dashboard only
    AccountSelector.kt             ← dashboard only
    AccountTypeIcon.kt             ← shared
data/
  AccountEntity.kt                 ← Room entity
  Daos.kt                          ← all DAOs in one file
  HesabyarRepository.kt            ← all repos in one file
  HesabyarRepositoryInterface.kt   ← all interfaces in one file
```

### 3.2 Target Structure

```
ui/
  AccountViewModel.kt                    ← focused ViewModel (≤80 lines)
  AccountUiState.kt                      ← state models
  AccountEvent.kt                        ← event sealed class

  screens/account/
    AccountManagementScreen.kt           ← screen shell (≤100 lines)

  components/account/
    AccountListCard.kt                   ← list item card
    AccountFormContent.kt                ← form body (shared Add/Edit)
    AccountColorPicker.kt                ← color grid
    AccountTypeDropdown.kt               ← type selector
    AccountBankFields.kt                 ← conditional bank details
    AccountPreviewRow.kt                 ← live preview in form
    AccountOverflowMenu.kt               ← overflow actions
    AccountStatusBadge.kt                ← active/archived badge

  components/shared/
    EmptyState.kt                        ← ALREADY EXISTS — reuse
    SectionHeader.kt                     ← ALREADY EXISTS — reuse
    ConfirmDialog.kt                     ← ALREADY EXISTS — reuse
    HesabyarDialog.kt                    ← ALREADY EXISTS — reuse
    HesabyarButton.kt                    ← ALREADY EXISTS — reuse
    HesabyarCard.kt                      ← ALREADY EXISTS — reuse
    HesabyarInputField.kt                ← ALREADY EXISTS — reuse
    IconCircle.kt                        ← ALREADY EXISTS — reuse
    AccountTypeIcon.kt                   ← ALREADY EXISTS — reuse
    ColorPickerGrid.kt                   ← NEW — generic color grid

domain/
  usecase/account/
    AddAccountUseCase.kt                 ← validate + insert
    UpdateAccountUseCase.kt              ← validate + update
    DeleteAccountUseCase.kt              ← check transactions + delete
    ArchiveAccountUseCase.kt             ← set archived
    UnarchiveAccountUseCase.kt           ← NEW: restore account
    GetAccountsUseCase.kt                ← reactive account list
    ValidateAccountFormUseCase.kt        ← form validation

  validation/
    AccountValidator.kt                  ← validation rules
    ValidationResult.kt                  ← sealed result

  model/
    AccountDomain.kt                     ← domain model (if needed)
    AccountFormModel.kt                  ← form data model

data/
  account/
    AccountDao.kt                        ← extracted from Daos.kt
    AccountRepository.kt                 ← extracted (interface)
    AccountRepositoryImpl.kt             ← extracted (implementation)
    AccountEntity.kt                     ← Room entity (unchanged)
```

### 3.3 Package Responsibilities

| Package | Responsibility |
|---|---|
| `ui/AccountViewModel.kt` | Coordinate state changes, delegate to UseCases |
| `ui/AccountUiState.kt` | Define all state shapes (UiState, FormState, DialogState) |
| `ui/AccountEvent.kt` | Define all user-triggered events |
| `ui/screens/account/` | Screen-level composables (thin shells) |
| `ui/components/account/` | Account-specific reusable UI components |
| `ui/components/shared/` | App-wide reusable components (already exist) |
| `domain/usecase/account/` | Business operations (CRUD + validation) |
| `domain/validation/` | Validation logic (pure Kotlin, no Android deps) |
| `domain/model/` | Domain models (if different from data entities) |
| `data/account/` | Account persistence (DAO, Repository) |

---

## Section 4: Screen Architecture

### 4.1 AccountManagementScreen (Main List)

| Aspect | Design |
|---|---|
| **Purpose** | Display all accounts, provide navigation to add/edit, show overflow actions |
| **Responsibilities** | Render list, handle FAB click, route to dialogs |
| **Owned State** | None — all state comes from ViewModel |
| **Dependencies** | AccountViewModel |
| **Reusable Components** | AccountListCard, EmptyState, SectionHeader, AccountOverflowMenu |
| **Navigation** | Receives `onBack` callback; no internal routing |
| **Events** | OnAdd, OnAccountClick, OnOverflow, OnBack |
| **Outputs** | None — pure display + event emitter |

**Structure:**
```
AccountManagementScreen
  ├── Scaffold
  │   ├── TopAppBar (title + back)
  │   ├── content: AccountManagementContent
  │   │   ├── [empty] EmptyState (icon, title, description, action CTA)
  │   │   └── [has data] LazyColumn
  │   │       └── items(accounts) → AccountListCard
  │   └── FAB (add)
  └── DialogHost (from ViewModel state)
```

### 4.2 AccountFormDialog (Add / Edit)

| Aspect | Design |
|---|---|
| **Purpose** | Collect account data for creation or editing |
| **Responsibilities** | Render form, validate input, emit save event |
| **Owned State** | FormState (via ViewModel or SavedStateHandle) |
| **Dependencies** | AccountViewModel, AccountValidator |
| **Reusable Components** | HesabyarDialog, HesabyarInputField, HesabyarButton, AccountTypeDropdown, AccountBankFields, AccountColorPicker, AccountPreviewRow |
| **Navigation** | Opened as dialog overlay, not a route |
| **Events** | OnFormChange, OnSave, OnCancel |
| **Outputs** | AccountFormModel (validated) |

**Structure:**
```
AccountFormDialog
  └── HesabyarDialog (title, close, actions)
      └── Column (scrollable)
          ├── HesabyarInputField (name) + error state
          ├── AccountTypeDropdown
          ├── AccountBankFields (conditional on type)
          │   ├── HesabyarInputField (bankName)
          │   ├── HesabyarInputField (cardNumber)
          │   ├── HesabyarInputField (accountNumber)
          │   └── HesabyarInputField (iban)
          ├── HesabyarInputField (initialBalance)
          ├── AccountColorPicker
          ├── AccountPreviewRow
          └── actions: [Cancel, Save]
```

### 4.3 AccountDeleteDialog

| Aspect | Design |
|---|---|
| **Purpose** | Confirm account deletion with safety checks |
| **Responsibilities** | Check transaction count, show appropriate dialog |
| **Owned State** | DeleteCheckResult (loading, canDelete, hasTransactions) |
| **Dependencies** | AccountViewModel |
| **Reusable Components** | ConfirmDialog (existing) |
| **Events** | OnConfirmDelete, OnDismiss, OnArchiveInstead |
| **Outputs** | Deletion confirmed or archived |

**Flow:**
```
User taps Delete → PendingDelete state
  → ViewModel checks canDelete
    → [has transactions] → TransactionWarning dialog
      → "متوجه شدم" → dismiss
      → "آرشیو کن" → archive + dismiss
    → [no transactions] → ConfirmDialog
      → "حذف" → delete + Snackbar undo
      → "انصراف" → dismiss
```

### 4.4 AccountArchiveDialog

| Aspect | Design |
|---|---|
| **Purpose** | Confirm account archival |
| **Responsibilities** | Show confirmation, execute archive |
| **Owned State** | None |
| **Dependencies** | AccountViewModel |
| **Reusable Components** | ConfirmDialog (existing) |
| **Events** | OnConfirmArchive, OnDismiss |
| **Outputs** | Archive confirmed + Snackbar undo |

---

## Section 5: Component Architecture

### 5.1 AccountListCard

| Aspect | Design |
|---|---|
| **Purpose** | Display a single account in the management list |
| **Inputs** | `account: AccountEntity`, `onClick: () -> Unit`, `onOverflow: () -> Unit` |
| **Outputs** | Click events |
| **Ownership** | `ui/components/account/` |
| **Reusability** | Management list, search results, account picker detail |
| **Used by** | AccountManagementScreen |

### 5.2 AccountColorPicker

| Aspect | Design |
|---|---|
| **Purpose** | Select a color from a curated palette |
| **Inputs** | `selectedColor: Long`, `palette: List<Long>`, `columns: Int`, `onColorSelected: (Long) -> Unit` |
| **Outputs** | Color selection |
| **Ownership** | `ui/components/account/` |
| **Reusability** | Account color, category color, tag color |
| **Used by** | AccountFormDialog, (future) CategoryFormDialog |

### 5.3 AccountTypeDropdown

| Aspect | Design |
|---|---|
| **Purpose** | Select account type from predefined options |
| **Inputs** | `selectedType: AccountType`, `onTypeSelected: (AccountType) -> Unit` |
| **Outputs** | Type selection |
| **Ownership** | `ui/components/account/` |
| **Reusability** | Account form only (type-specific) |
| **Used by** | AccountFormDialog |

### 5.4 AccountBankFields

| Aspect | Design |
|---|---|
| **Purpose** | Show bank-specific fields (card, account number, IBAN) conditionally |
| **Inputs** | `bankName: String`, `cardNumber: String`, `accountNumber: String`, `iban: String`, `onBankNameChange`, `onCardNumberChange`, `onAccountNumberChange`, `onIbanChange`, `errors: Map<String, String?>` |
| **Outputs** | Field change events |
| **Ownership** | `ui/components/account/` |
| **Reusability** | Account form only |
| **Used by** | AccountFormDialog |

### 5.5 AccountPreviewRow

| Aspect | Design |
|---|---|
| **Purpose** | Show live preview of the account being created/edited |
| **Inputs** | `name: String`, `type: AccountType`, `color: Long` |
| **Outputs** | None (display only) |
| **Ownership** | `ui/components/account/` |
| **Reusability** | Account form only |
| **Used by** | AccountFormDialog |

### 5.6 AccountOverflowMenu

| Aspect | Design |
|---|---|
| **Purpose** | Show edit/archive/delete actions for an account |
| **Inputs** | `onEdit: () -> Unit`, `onArchive: () -> Unit`, `onDelete: () -> Unit`, `onDismiss: () -> Unit` |
| **Outputs** | Action selection |
| **Ownership** | `ui/components/account/` |
| **Reusability** | Any entity overflow menu (parameterize items) |
| **Used by** | AccountManagementScreen |

**Design decision:** Should this be a generic `OverflowMenu(items: List<MenuItem>)` or account-specific? **Recommendation:** Start account-specific, extract to generic if a second usage appears.

### 5.7 AccountStatusBadge

| Aspect | Design |
|---|---|
| **Purpose** | Show active/archived status |
| **Inputs** | `isArchived: Boolean` |
| **Outputs** | None (display only) |
| **Ownership** | `ui/components/account/` |
| **Reusability** | Account list, transaction details, backup info |
| **Used by** | AccountListCard |

### 5.8 ColorPickerGrid (Generic)

| Aspect | Design |
|---|---|
| **Purpose** | Display a grid of color swatches for selection |
| **Inputs** | `selectedColor: Long`, `colors: List<Long>`, `columns: Int`, `onColorSelected: (Long) -> Unit` |
| **Outputs** | Color selection |
| **Ownership** | `ui/components/shared/` |
| **Reusability** | Account color, category color, tag color, any color selection |
| **Used by** | AccountColorPicker, (future) CategoryColorPicker |

---

## Section 6: State Management

### 6.1 UiState (Screen-Level)

```kotlin
// Conceptual — not code
AccountManagementUiState:
  accounts: List<AccountEntity>
  isLoading: Boolean
  error: String?              // transient — cleared after display
  snackbarMessage: String?    // transient — cleared after display
```

**Owner:** AccountViewModel  
**Lifetime:** ViewModel scope (survives config change)  
**Update:** Via StateFlow, collected by Screen

### 6.2 DialogState

```kotlin
AccountDialogState:
  None
  Add
  Edit(account: AccountEntity)
  DeleteConfirmation(account: AccountEntity)
  TransactionWarning(account: AccountEntity)
  ArchiveConfirmation(account: AccountEntity)
```

**Owner:** AccountViewModel  
**Lifetime:** ViewModel scope  
**Update:** Via ViewModel event handlers

### 6.3 FormState

```kotlin
AccountFormState:
  name: String
  type: AccountType
  bankName: String
  cardNumber: String
  accountNumber: String
  iban: String
  initialBalance: String     // kept as String for TextField binding
  color: Long
  errors: FormErrors         // map of field name → error message
  isSaving: Boolean
```

**Owner:** AccountViewModel (preferred) or SavedStateHandle  
**Lifetime:** ViewModel scope or SavedStateHandle (survives process death)  
**Update:** Via OnFormChange events

**Why ViewModel over rememberSaveable:**
- `rememberSaveable` doesn't support complex objects without `Parcelize`
- ViewModel state survives both config change AND process death
- Centralizes validation logic (validator reads FormState)
- Makes form state testable

### 6.4 DeleteCheckState

```kotlin
DeleteCheckState:
  Loading
  CanDelete(account: AccountEntity)
  HasTransactions(account: AccountEntity)
```

**Owner:** AccountViewModel  
**Lifetime:** Transient (exists only during delete flow)  
**Update:** Async check via `canDeleteAccount` UseCase

### 6.5 State Ownership Summary

| State | Owner | Survives Config Change | Survives Process Death | Testable |
|---|---|---|---|---|
| `accounts` list | ViewModel (StateFlow) | ✅ | ✅ | ✅ |
| `dialogState` | ViewModel | ✅ | ✅ | ✅ |
| `formState` | ViewModel (StateFlow) | ✅ | ✅ (via SavedStateHandle) | ✅ |
| `deleteCheckState` | ViewModel | ✅ | ✅ | ✅ |
| `snackbarMessage` | ViewModel | ✅ | ❌ (transient) | ✅ |
| `isLoading` | ViewModel | ✅ | ✅ | ✅ |

### 6.6 Derived State

| Derived | Source | Purpose |
|---|---|---|
| `isFormValid` | `formState.name.isNotBlank()` | Enable/disable save button |
| `filteredAccounts` | `accounts` | Future: search/filter |
| `formErrors` | `formState` + `AccountValidator` | Real-time validation feedback |

### 6.7 rememberSaveable Usage

Only for truly transient UI state that doesn't belong in ViewModel:

| State | Use rememberSaveable? | Reason |
|---|---|---|
| `typeDropdownExpanded` | ✅ Yes | Transient UI state, no business meaning |
| `colorPickerScrollPosition` | ✅ Yes | UI-only state |
| `dialogState` | ❌ No — use ViewModel | Business state, needs testability |
| `formState` | ❌ No — use ViewModel | Business state, needs validation |

---

## Section 7: Event Architecture

### 7.1 User Events (UI → ViewModel)

```kotlin
sealed interface AccountEvent {
  // List actions
  data object LoadAccounts : AccountEvent
  data class OnAccountOverflow(val account: AccountEntity) : AccountEvent
  
  // Add flow
  data object OnAddAccount : AccountEvent
  data class OnSaveNewAccount(val form: AccountFormState) : AccountEvent
  
  // Edit flow
  data class OnEditAccount(val account: AccountEntity) : AccountEvent
  data class OnSaveEditedAccount(val account: AccountEntity, val form: AccountFormState) : AccountEvent
  
  // Delete flow
  data class OnRequestDelete(val account: AccountEntity) : AccountEvent
  data class OnConfirmDelete(val account: AccountEntity) : AccountEvent
  
  // Archive flow
  data class OnRequestArchive(val account: AccountEntity) : AccountEvent
  data class OnConfirmArchive(val account: AccountEntity) : AccountEvent
  data class OnUnarchiveAccount(val account: AccountEntity) : AccountEvent
  
  // Form events
  data class OnFormChange(val form: AccountFormState) : AccountEvent
  
  // UI events
  data object OnDismissDialog : AccountEvent
  data object OnDismissSnackbar : AccountEvent
}
```

### 7.2 Side Effects (ViewModel → UI)

```kotlin
sealed interface AccountSideEffect {
  data class ShowSnackbar(val message: String, val actionLabel: String? = null) : AccountSideEffect
  data class NavigateBack(val message: String? = null) : AccountSideEffect
}
```

**Why SideEffect?** Snackbar and navigation are fire-and-forget actions that should not be part of state. Using `Channel<SideEffect>` or `SharedFlow` with `LaunchedEffect` in the Screen ensures they're consumed exactly once.

### 7.3 Event Flow Diagram

```
User taps FAB
  → AccountEvent.OnAddAccount
  → ViewModel: dialogState = Add
  → Screen: shows AccountFormDialog

User fills form, taps Save
  → AccountEvent.OnSaveNewAccount(form)
  → ViewModel: 
      1. Validate form via AccountValidator
      2. If invalid → update formState.errors
      3. If valid → AddAccountUseCase(form)
         → Repository.insertAccount()
         → Room inserts → Flow emits
      4. On success → dialogState = None
                    → snackbarMessage = "حساب «{name}» ایجاد شد"
  → Screen: dialog closes, Snackbar appears

User taps undo on Snackbar
  → AccountEvent.OnConfirmDelete(recentAccount) [for undo]
  → ViewModel: DeleteAccountUseCase(recentAccount)
```

### 7.4 Event Categories

| Category | Events | Handler |
|---|---|---|
| Navigation | OnAddAccount, OnEditAccount, OnDismissDialog | ViewModel → dialogState |
| CRUD | OnSaveNewAccount, OnSaveEditedAccount, OnConfirmDelete, OnConfirmArchive | ViewModel → UseCase → Repository |
| Form | OnFormChange | ViewModel → formState update |
| Feedback | OnDismissSnackbar | ViewModel → snackbarMessage = null |
| Overflow | OnAccountOverflow | ViewModel → dialogState = OverflowMenu |

---

## Section 8: Data Flow

### 8.1 Create Account

```
User fills form
  → AccountEvent.OnSaveNewAccount(AccountFormState)
  → ViewModel.onEvent()
  → AccountValidator.validate(form)
    → [invalid] → formState.errors = mapOf("name" to "نام حساب الزامی است")
    → [valid] → continue
  → AddAccountUseCase(form.toAccountEntity())
  → HesabyarRepository.insertAccount(entity)
  → AccountDao.insert(entity) → Room INSERT
  → Room emits updated getAllAccounts() Flow
  → ViewModel.accounts StateFlow updates
  → Screen LazyColumn recomposes with new item
  → SideEffect: ShowSnackbar("حساب «{name}» ایجاد شد", "واگردانی")
```

### 8.2 Update Account

```
User edits form
  → AccountEvent.OnSaveEditedAccount(account, form)
  → ViewModel.onEvent()
  → AccountValidator.validate(form)
    → [invalid] → formState.errors
    → [valid] → continue
  → UpdateAccountUseCase(account.copy(...))
  → HesabyarRepository.updateAccount(updated)
  → AccountDao.update(updated) → Room UPDATE
  → Room emits updated Flow
  → Screen recomposes
  → SideEffect: ShowSnackbar("حساب «{name}» به‌روزرسانی شد")
```

### 8.3 Delete Account

```
User taps Delete in overflow
  → AccountEvent.OnRequestDelete(account)
  → ViewModel: dialogState = PendingDelete(account)
  → LaunchedEffect triggers DeleteCheckUseCase(account.id)
  → DeleteCheckUseCase → Repository.getTransactionCountForAccount(id)
    → [count > 0] → dialogState = TransactionWarning(account)
    → [count == 0] → dialogState = DeleteConfirmation(account)

User confirms delete
  → AccountEvent.OnConfirmDelete(account)
  → DeleteAccountUseCase(account)
  → HesabyarRepository.deleteAccount(account) → Room DELETE
  → Room emits updated Flow → list updates
  → SideEffect: ShowSnackbar("حساب «{name}» حذف شد", "واگردانی")
  
User taps undo (within Snackbar duration)
  → Re-insert account via AddAccountUseCase(deletedAccount)
```

### 8.4 Archive Account

```
User taps Archive in overflow
  → AccountEvent.OnRequestArchive(account)
  → dialogState = ArchiveConfirmation(account)
  → ConfirmDialog shown

User confirms archive
  → AccountEvent.OnConfirmArchive(account)
  → ArchiveAccountUseCase(account)
  → account.copy(isArchived = true)
  → Repository.updateAccount(archived)
  → Room emits updated Flow
  → Account disappears from getActiveAccounts() (Dashboard)
  → Account remains in getAllAccounts() (Management list)
  → SideEffect: ShowSnackbar("حساب «{name}» آرشیو شد", "واگردانی")
```

### 8.5 Unarchive Account (NEW)

```
User taps "فعال‌سازی مجدد" on archived account
  → AccountEvent.OnUnarchiveAccount(account)
  → UnarchiveAccountUseCase(account)
  → account.copy(isArchived = false)
  → Repository.updateAccount(restored)
  → Room emits updated Flow
  → Account reappears in getActiveAccounts()
  → SideEffect: ShowSnackbar("حساب «{name}» فعال شد")
```

### 8.6 Dashboard/Analytics Sync

After any account CRUD operation:
1. Room `getAllAccounts()` Flow emits new list
2. `DashboardViewModel.accounts` collects this Flow (already wired)
3. `AnalyticsViewModel.accounts` collects this Flow (already wired)
4. `AccountSelector` recomposes with updated account list
5. `AccountBalanceCard` recomputes via `GetDashboardDataUseCase`
6. **No manual sync required** — reactive architecture handles it

---

## Section 9: Domain Model

### 9.1 Entities

| Entity | Type | Location | Description |
|---|---|---|---|
| `AccountEntity` | Room Entity | `data/account/` | Persistence model (id, name, type, fields, color, isArchived, displayOrder, timestamps) |
| `AccountFormState` | UI Model | `ui/` | Form input state (all fields as Strings, errors map) |
| `AccountFormModel` | Domain Model | `domain/model/` | Validated form data (typed fields, no errors) |

### 9.2 Value Objects

| Value Object | Description | Validation |
|---|---|---|
| `AccountName` | Non-blank trimmed string | Not blank, max 100 chars |
| `AccountType` | Enum: BANK, CASH_WALLET, SAVINGS_INVESTMENT, OTHER | Must be valid enum |
| `BankName` | Optional string | Max 100 chars if present |
| `CardNumber` | Optional numeric string | 16 digits if present |
| `AccountNumber` | Optional string | Max 20 chars if present |
| `IBAN` | Optional string starting with IR | Regex: `^IR\d{24}$` if present |
| `InitialBalance` | Long (Rial) | Any Long value |
| `AccountColor` | Long (ARGB) | Must be in ACCOUNT_PICKER_COLORS palette |

### 9.3 Business Rules

> **Note:** These business rules should be enforced in Rust (via `validation.rs` or equivalent core logic), with Kotlin-side `Validator`/`ViewModel`/`UseCase` code limited to surfacing Rust's validation results or handling persistence/UI state. New rules MUST NOT be implemented in Kotlin. See `architecture/ADR-001-rust-sole-implementation.md`.

| Rule | Description | Enforcement |
|---|---|---|
| BR-01 | Account name is required | Validator |
| BR-02 | Account names should be unique | Validator (warning, not blocking) |
| BR-03 | Default type is BANK | (legacy/UI-state, not a business rule) |
| BR-04 | Default color is GREEN_500 | Design system constant |
| BR-05 | New account gets next displayOrder | (legacy/UI-state, not a business rule) |
| BR-06 | Balance starts at initialBalance | (legacy/persistence, not a business rule) |
| BR-07 | Delete blocked if transactions exist | (legacy — account CRUD is Kotlin-only, not routed through Rust core) |
| BR-08 | Archive is soft-disable, not delete | (legacy — account CRUD is Kotlin-only, not routed through Rust core) |
| BR-09 | Archived accounts excluded from dashboard | Rust core (`dashboard.rs`; `analytics.rs` mirrors the same gate) — the computation excludes archived accounts based on the `include_archived` parameter. The FFI wrapper (`ffi/mod.rs`) and the Kotlin FFI adapter (`RustBridge`) only forward the `include_archived` boolean and apply no business logic |
| BR-10 | Unarchive restores to active state | (legacy — account CRUD is Kotlin-only, not routed through Rust core) |
| BR-11 | All monetary values in Rial | Entity constraint |
| BR-12 | timestamps auto-set on create/update | (legacy/persistence, not a business rule) |

> **Note:** BR-07, BR-08, and BR-10 are marked as legacy because account CRUD runs through UseCase → Repository → Room with no Rust involvement. These are real business rules (not UI-state or persistence defaults) tracked for future Rust migration — see `../plans/README.md` entry 010.

### 9.4 Validation Rules

| Field | Rule | Error Message (Persian) |
|---|---|---|
| `name` | Required, non-blank, max 100 | "نام حساب الزامی است" / "نام حساب نمی‌تواند بیش از ۱۰۰ کاراکتر باشد" |
| `type` | Required enum | "نوع حساب را انتخاب کنید" |
| `bankName` | Optional, max 100 | "نام بانک نمی‌تواند بیش از ۱۰۰ کاراکتر باشد" |
| `cardNumber` | Optional, 16 digits | "شماره کارت باید ۱۶ رقم باشد" |
| `accountNumber` | Optional, max 20 | "شماره حساب نمی‌تواند بیش از ۲۰ کاراکتر باشد" |
| `iban` | Optional, regex `^IR\d{24}$` | "شماره IBAN نامعتبر است" |
| `initialBalance` | Valid Long | "موجودی اولیه نامعتبر است" |
| `color` | In palette | — |

### 9.5 Account Lifecycle State Machine

```
                    ┌──────────────┐
                    │   CREATED    │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
              ┌─────│    ACTIVE    │◄────┐
              │     └──────┬───────┘     │
              │            │             │
         Edit│       Archive│       Unarchive
              │            │             │
              │     ┌──────▼───────┐     │
              │     │  ARCHIVED    │─────┘
              │     └──────┬───────┘
              │            │
              │       Delete│ (if no transactions)
              │            │
              │     ┌──────▼───────┐
              │     │   DELETED    │
              │     └──────────────┘
              │
              ▼
        (stays ACTIVE with updated data)
```

### 9.6 Relationships

```
Account (1) ──has──► (N) Transaction (via accountId)
Account (1) ──has──► (N) Transaction (via destinationAccountId, for transfers)
```

- Account is a **parent** entity for Transactions
- Deleting an Account with Transactions is **blocked** (BR-07)
- Archiving an Account **hides** its Transactions from dashboard (but they remain in DB)

### 9.7 Future Extensibility

| Future Feature | Impact on Model | Preparation |
|---|---|---|
| Multi-currency | Add `currency: String` field | Keep `initialBalance` as Long; add currency conversion layer |
| Shared accounts | Add `sharedWith: List<UserId>` | Abstract ownership into separate entity |
| Crypto wallet | New `AccountType.CRYPTO_WALLET` | Enum extension; no schema change |
| Credit card | New `AccountType.CREDIT_CARD` + credit limit field | Optional fields in entity |
| Loan account | New `AccountType.LOAN_ACCOUNT` + interest rate | Separate entity or extended fields |
| Cloud sync | Add `syncId: String` + `lastSyncedAt: Long` | Non-breaking additive columns |
| Budget per account | Add `monthlyBudget: Long` to account | Non-breaking additive column |

---

## Section 10: Public API

### 10.1 Public API (Accessible from outside the module)

| Symbol | Type | Why Public |
|---|---|---|
| `AccountEntity` | Data class | Used by Dashboard, Analytics, Transactions, Backup |
| `AccountType` | Enum | Used by AccountTypeIcon, Dashboard, Analytics |
| `AccountViewModel` | ViewModel | Injected by MainActivity |
| `AccountManagementScreen` | Composable | Called by MainActivity |
| `AccountSelector` | Composable | Used by Dashboard, Analytics |
| `AccountBalanceCard` | Composable | Used by Dashboard |
| `AccountTypeIcon` | Composable | Used by Dashboard, Transactions |

### 10.2 Internal API (Within the account feature only)

| Symbol | Type | Why Internal |
|---|---|---|
| `AccountEvent` | Sealed class | ViewModel event protocol |
| `AccountUiState` | Data class | ViewModel state shape |
| `AccountFormState` | Data class | Form input state |
| `AccountDialogState` | Sealed class | Dialog lifecycle |
| `AddAccountUseCase` | Class | Business operation |
| `UpdateAccountUseCase` | Class | Business operation |
| `DeleteAccountUseCase` | Class | Business operation |
| `ArchiveAccountUseCase` | Class | Business operation |
| `UnarchiveAccountUseCase` | Class | Business operation |
| `AccountValidator` | Object | Validation logic |
| `AccountDao` | Interface | Room DAO |
| `AccountRepository` | Interface | Data contract |

### 10.3 Private API (Within a single file)

| Symbol | Type | Why Private |
|---|---|---|
| `FormErrors` | Type alias | Implementation detail of validation |
| `AccountFormModel` | Data class | Intermediate validated model |
| Color picker internals | Composables | Account-specific rendering |

---

## Section 11: Design System

### 11.1 Spacing

All spacing uses `SpacingTokens`:

| Context | Token | Value |
|---|---|---|
| Card internal padding | `SpacingTokens.md` | 12.dp |
| Card horizontal margin | `SpacingTokens.lg` | 16.dp |
| List item gap | `SpacingTokens.sm` | 8.dp |
| Form field gap | `SpacingTokens.md` | 12.dp |
| Dialog padding | `SpacingTokens.xl` | 24.dp |
| Color swatch gap | `SpacingTokens.xs` | 4.dp |
| Section gap | `SpacingTokens.lg` | 16.dp |

### 11.2 Typography

| Element | Style | Weight |
|---|---|---|
| Account name (list) | `bodyMedium` | Bold |
| Account type (list) | `labelSmall` | Medium |
| Bank name (list) | `labelSmall` | Regular |
| Balance (list) | `bodySmall` | Regular |
| Form section label | `labelMedium` | Regular |
| Dialog title | `titleMedium` | Bold |
| Empty state title | `titleMedium` | Regular |
| Empty state description | `bodyMedium` | Regular |
| Snackbar text | `bodyMedium` | Regular |
| Status badge | `labelSmall` | Medium |

### 11.3 Color Usage

| Element | Color Source | Notes |
|---|---|---|
| Account icon background | `account.color.toComposeColor()` | User-selected |
| Account icon tint | `account.color.toComposeColor()` | Same as background |
| Account type label | `account.color.toComposeColor()` | Semantic link |
| Selected color swatch | `Color.White` check icon | Contrast guaranteed |
| Delete button tint | `MaterialTheme.colorScheme.error` | Destructive semantic |
| Archive button tint | `MaterialTheme.colorScheme.onSurfaceVariant` | Neutral semantic |
| Form field error | `MaterialTheme.colorScheme.error` | M3 standard |
| Snackbar background | `MaterialTheme.colorScheme.inverseSurface` | M3 standard |
| Empty state icon | `MaterialTheme.colorScheme.onSurfaceVariant` | M3 standard |

### 11.4 Shapes

| Element | Token | Value |
|---|---|---|
| Account card | `ShapeTokens.Medium` | 12.dp rounded |
| Color swatch | `CircleShape` | Full circle |
| Dialog | `ShapeTokens.XLarge` | 24.dp rounded |
| Form fields | `ShapeTokens.Medium` | 12.dp rounded |
| Buttons | `ShapeTokens.Full` | 9999.dp (pill) |
| Status badge | `ShapeTokens.Full` | Pill |

### 11.5 Animations & Motion

| Interaction | Animation | Duration |
|---|---|---|
| Dialog open/close | M3 default `AlertDialog` transition | ~300ms |
| Snackbar appear/disappear | M3 default `Snackbar` transition | ~300ms in, 5000ms hold |
| Color swatch selection | `animateColorAsState` | 200ms |
| List item add/remove | `LazyColumn` default `animateItemPlacement` | ~300ms |
| Form section expand/collapse | `animateContentSize` | 200ms |
| Overflow menu appear | `DropdownMenu` default transition | ~200ms |

### 11.6 Elevation

| Element | Token | Value |
|---|---|---|
| Account card | `ElevationTokens.Level0` | 0.dp (flat) |
| Dialog surface | `ElevationTokens.Level3` | Standard dialog elevation |
| FAB | M3 default | Standard FAB elevation |
| Snackbar | M3 default | Standard snackbar elevation |

### 11.7 Icons

| Element | Icon | Size | Source |
|---|---|---|---|
| Add FAB | `Icons.Filled.Add` | `Dimens.FABIconSize` | Material |
| Back navigation | `Icons.Filled.ArrowForward` | `Dimens.IconMedium` | Material (RTL-aware) |
| Overflow | `Icons.Filled.MoreVert` | `Dimens.IconMedium` | Material |
| Edit action | `Icons.Filled.Edit` | `Dimens.IconSmall` | Material |
| Archive action | `Icons.Filled.Archive` | `Dimens.IconSmall` | Material |
| Delete action | `Icons.Filled.Delete` | `Dimens.IconSmall` | Material |
| Check (selected) | `Icons.Filled.Check` | `Dimens.IconSmall` | Material |
| Close (dialog) | `Icons.Filled.Close` | `Dimens.IconMedium` | Material |
| Bank account | `Icons.Filled.AccountBalance` | `Dimens.IconMedium` | Via `AccountType.icon()` |
| Cash wallet | `Icons.Filled.Wallet` | `Dimens.IconMedium` | Via `AccountType.icon()` |
| Savings | `Icons.Filled.Savings` | `Dimens.IconMedium` | Via `AccountType.icon()` |
| Other | `Icons.Filled.MoreHoriz` | `Dimens.IconMedium` | Via `AccountType.icon()` |

### 11.8 Touch Targets

All interactive elements must have minimum 48.dp touch target:

| Element | Current Size | Minimum Target |
|---|---|---|
| Overflow IconButton | 48.dp ✅ | 48.dp |
| Color swatch | 28.dp ❌ | Must wrap in 48.dp clickable |
| Dropdown menu item | M3 default ✅ | ≥48.dp |
| List card | Full width ✅ | Full width |
| FAB | 56.dp ✅ | 56.dp |

### 11.9 Accessibility

| Requirement | Implementation |
|---|---|
| Content descriptions | Every icon has `contentDescription` in Persian |
| Semantic headings | Empty state title uses `Modifier.semantics { heading() }` |
| Focus order | Top-to-bottom, left-to-right (RTL: right-to-left) |
| Color contrast | All text meets WCAG AA (4.5:1 for body text) |
| Screen reader labels | Form fields labeled via `HesabyarInputField.label` |
| Live region | Error messages announced via `Modifier.semantics { liveRegion = LiveRegion.Polite }` |
| Minimum touch target | 48.dp for all interactive elements |

### 11.10 Dark Theme

All colors derive from `MaterialTheme.colorScheme` — automatic dark mode support. No manual color overrides. The account-specific colors (from user picker) are used as tints only, not backgrounds, ensuring contrast in both themes.

### 11.11 RTL Support

- All layouts use `Arrangement`, `Alignment`, and `Modifier.padding` — no manual `start`/`end` with hardcoded direction
- Back arrow uses `Icons.Filled.ArrowForward` (renders as left-pointing in RTL)
- Numeric fields (`cardNumber`, `iban`) use default layout direction — numbers render LTR within RTL context (standard behavior)
- `CurrencyFormatter` prepends LRM (`\u200E`) for correct number rendering

---

## Section 12: Product Design

### 12.1 First-Time User Experience

```
┌─────────────────────────────────────┐
│         ↩ مدیریت حساب‌ها            │
├─────────────────────────────────────┤
│                                     │
│            💰 (icon)                │
│                                     │
│      حسابی ثبت نشده است             │
│                                     │
│  حساب‌ها به شما کمک می‌کنند          │
│  تراکنش‌ها را دسته‌بندی کنید و       │
│  موجودی هر حساب را جداگانه          │
│  مدیریت کنید.                       │
│                                     │
│     [ + ایجاد اولین حساب ]          │
│                                     │
└─────────────────────────────────────┘
```

- Descriptive empty state with illustration
- CTA button (not just FAB) to create first account
- Explanation of what accounts are for

### 12.2 Daily Usage

**Adding an account (3 taps):**
1. Tap FAB (+)
2. Fill name + select type (bank fields auto-shown)
3. Tap Save

**Editing an account (4 taps):**
1. Tap ⋮ on account card
2. Tap "ویرایش"
3. Modify fields
4. Tap Save

**Archiving an account (3 taps):**
1. Tap ⋮ on account card
2. Tap "آرشیو"
3. Confirm in dialog

**Deleting an account (4 taps):**
1. Tap ⋮ on account card
2. Tap "حذف"
3. Confirm in dialog
4. (Optional) Tap "واگردانی" on Snackbar

### 12.3 Power-User Workflow

- **Bulk archive:** Long-press to enter selection mode → select multiple → batch archive (future)
- **Drag to reorder:** Long-press + drag to reorder accounts (future, `displayOrder` field exists)
- **Quick add:** FAB with predefined templates (future)

### 12.4 Large Dataset Workflow

- **Search/filter:** `SectionHeader` with search field above account list (future)
- **Grouping:** Group by type (Bank, Cash, Savings, Other) with collapsible sections (future)
- **Pagination:** LazyColumn handles virtualization automatically

### 12.5 Accessibility Workflow

- Screen reader announces: "حساب «حساب جاری بانک ملی»، نوع بانکی، موجودی ۵٬۰۰۰٬۰۰۰ تومان"
- TalkBack users can navigate form fields sequentially
- Color picker announces: "رنگ سبز، انتخاب شده" / "رنگ آبی، انتخاب نشده"

### 12.6 Empty State

```
Icon: Icons.Filled.AccountBalance (large, onSurfaceVariant)
Title: "حسابی ثبت نشده است"
Description: "حساب‌ها به شما کمک می‌کنند تراکنش‌ها را دسته‌بندی کنید."
Action: "ایجاد حساب" button → opens Add dialog
```

### 12.7 Loading State

- **Initial load:** Skeleton shimmer for list items (future enhancement)
- **Save operation:** Button shows `CircularProgressIndicator` (already supported by `HesabyarButton.loading`)
- **Delete check:** Small progress indicator in the dialog while checking transaction count

### 12.8 Error State

- **Form validation:** Inline error text below each invalid field
- **Server/DB error:** Snackbar with error message + "تلاش مجدد" action
- **Network error:** Not applicable (offline-first)

### 12.9 Success State

- **Account created:** Snackbar "حساب «{name}» ایجاد شد" + "واگردانی" action
- **Account updated:** Snackbar "حساب «{name}» به‌روزرسانی شد"
- **Account deleted:** Snackbar "حساب «{name}» حذف شد" + "واگردانی" action
- **Account archived:** Snackbar "حساب «{name}» آرشیو شد" + "واگردانی" action
- **Account restored:** Snackbar "حساب «{name}» فعال شد"

### 12.10 Recovery Flows

| Scenario | Recovery |
|---|---|
| Accidental delete | Snackbar undo (re-insert) within 5 seconds |
| Accidental archive | Snackbar undo (unarchive) within 5 seconds |
| Form data loss (rotation) | Preserved via ViewModel state |
| App killed during form | Preserved via SavedStateHandle |
| Duplicate account created | User can edit or delete; warning shown |

### 12.11 Offline Experience

- All operations are local (Room) — no network dependency
- Rust computation has Kotlin fallback — works without native library
- Backup/restore works offline via file export/import

---

## Section 13: Scalability

### 13.1 New Account Types

**Impact:** Add enum value to `AccountType`, add icon mapping in `AccountType.icon()`, add display name.

**Files to change:** 3-4 files  
**Schema change:** None (type stored as String in Rust, enum in Kotlin)

### 13.2 New Account Fields

**Impact:** Add field to `AccountEntity`, add to form, add to Rust `Account` struct (if needed for computation).

**Files to change:**  
- `AccountEntity.kt` — add field
- `AccountFormContent.kt` — add form field
- `AccountValidator.kt` — add validation
- `RustMappers.kt` — add mapping (if Rust needs it)
- `models/mod.rs` — add field (if Rust needs it)

**Schema change:** Room `@ColumnInfo(defaultValue = ...)` — non-breaking migration

### 13.3 Multi-Currency Support

**Impact:** Each account has its own currency. Balance calculation must convert.

**Architecture preparation:**
- Store amounts in a base currency (Rial) in DB
- Add `currency: String` field to `AccountEntity`
- Currency conversion in UseCase layer, not in ViewModel or UI
- `CurrencyFormatter` already supports unit switching

### 13.4 Shared Accounts

**Impact:** Multiple users can own the same account.

**Architecture preparation:**
- Add `ownerId: Long` field to `AccountEntity`
- Filter accounts by current user in DAO query
- No structural change needed

### 13.5 Cloud Sync

**Impact:** Accounts sync across devices.

**Architecture preparation:**
- Add `remoteId: String` and `lastSyncedAt: Long` to `AccountEntity`
- Repository layer handles sync logic
- UI layer unchanged

### 13.6 Feature Module Extraction

**Impact:** If the project moves to multi-module architecture.

**Architecture preparation:**
- Clean layer boundaries already designed
- Domain layer has zero Android dependencies
- `AccountEntity` and `AccountType` are the only shared data models
- UI components can be extracted to a `:feature:account` module with minimal changes

---

## Section 14: Testing Strategy

### 14.1 Unit Tests

| Target | Test Type | What to Test |
|---|---|---|
| `AccountValidator` | Pure unit test | All validation rules, edge cases |
| `AddAccountUseCase` | Unit test with fake repo | Insertion, validation, error handling |
| `UpdateAccountUseCase` | Unit test with fake repo | Update, timestamp update |
| `DeleteAccountUseCase` | Unit test with fake repo | Delete, transaction check, error handling |
| `ArchiveAccountUseCase` | Unit test with fake repo | Archive, unarchive |
| `AccountViewModel` | Unit test with fake useCases | Event handling, state transitions, side effects |

### 14.2 Compose UI Tests

| Target | Test Type | What to Test |
|---|---|---|
| `AccountManagementScreen` | Compose test | Empty state, list rendering, FAB click |
| `AccountFormDialog` | Compose test | Form rendering, field input, validation errors, save button enable/disable |
| `AccountColorPicker` | Compose test | Color selection, visual feedback |
| `AccountOverflowMenu` | Compose test | Menu items, click handlers |
| `AccountDeleteDialog` | Compose test | Warning vs confirmation, button actions |
| Full add flow | Integration test | FAB → form → save → list updated |
| Full edit flow | Integration test | Overflow → edit → form → save → list updated |
| Full delete flow | Integration test | Overflow → delete → confirm → list updated |
| Full archive flow | Integration test | Overflow → archive → confirm → list updated |

### 14.3 DAO Tests

| Target | Test Type | What to Test |
|---|---|---|
| `AccountDao.insert` | Android unit test | Insert returns correct ID |
| `AccountDao.update` | Android unit test | Update modifies correct fields |
| `AccountDao.delete` | Android unit test | Delete removes record |
| `AccountDao.getActiveAccounts` | Android unit test | Excludes archived |
| `AccountDao.getAllAccounts` | Android unit test | Includes archived |
| `AccountDao.getTransactionCountForAccount` | Android unit test | Counts source + destination |

### 14.4 Integration Tests

| Flow | What to Test |
|---|---|
| Add → Dashboard sync | New account appears in AccountSelector and AccountBalanceCard |
| Edit → Dashboard sync | Edited name/color reflects in dashboard |
| Delete → Dashboard sync | Deleted account removed from selector |
| Archive → Dashboard sync | Archived account hidden from dashboard, visible in management |
| Unarchive → Dashboard sync | Restored account reappears in dashboard |

### 14.5 Snapshot Tests (Roborazzi)

| Component | Snapshot |
|---|---|
| AccountManagementScreen | Empty state |
| AccountManagementScreen | List with 3 accounts |
| AccountFormDialog | Empty form (add mode) |
| AccountFormDialog | Pre-filled form (edit mode) |
| AccountFormDialog | Form with validation errors |
| AccountColorPicker | Default selection |
| AccountDeleteDialog | Confirmation |
| AccountDeleteDialog | Transaction warning |

### 14.6 Accessibility Tests

| Test | Tool |
|---|---|
| Touch target size ≥ 48dp | Manual / Compose test |
| Content descriptions present | `assertExists` with semantics |
| Focus order correct | TalkBack walkthrough |
| Color contrast | Automated contrast checker |

---

## Section 15: Refactor Roadmap

### Phase 1: Foundation (Low Risk)

**Goal:** Extract domain logic and create testable foundation without changing UI.

| Step | Files Affected | Risk | Rollback |
|---|---|---|---|
| 1.1 Extract `AccountDao` from `Daos.kt` | `data/account/AccountDao.kt` | Zero — additive | Delete new file |
| 1.2 Extract `AccountRepository` interface | `data/account/AccountRepository.kt` | Zero — additive | Delete new file |
| 1.3 Extract `AccountRepositoryImpl` | `data/account/AccountRepositoryImpl.kt` | Low — move code | Revert imports |
| 1.4 Create `AccountValidator` | `domain/validation/AccountValidator.kt` | Zero — new file | Delete new file |
| 1.5 Create `AddAccountUseCase` | `domain/usecase/account/AddAccountUseCase.kt` | Zero — new file | Delete new file |
| 1.6 Create `UpdateAccountUseCase` | `domain/usecase/account/UpdateAccountUseCase.kt` | Zero — new file | Delete new file |
| 1.7 Create `DeleteAccountUseCase` | `domain/usecase/account/DeleteAccountUseCase.kt` | Zero — new file | Delete new file |
| 1.8 Create `ArchiveAccountUseCase` | `domain/usecase/account/ArchiveAccountUseCase.kt` | Zero — new file | Delete new file |
| 1.9 Write unit tests for all UseCases + Validator | `app/src/test/.../account/` | Zero — test only | Delete test files |

**Validation:** `./gradlew test --rerun-tasks --no-daemon`

### Phase 2: State Architecture (Medium Risk)

**Goal:** Introduce proper state management in ViewModel.

| Step | Files Affected | Risk | Rollback |
|---|---|---|---|
| 2.1 Create `AccountEvent` sealed class | `ui/AccountEvent.kt` | Zero — new file | Delete new file |
| 2.2 Create `AccountUiState` data class | `ui/AccountUiState.kt` | Zero — new file | Delete new file |
| 2.3 Refactor `AccountViewModel` to use events + state | `ui/AccountViewModel.kt` | Medium — modifies existing | Git revert |
| 2.4 Add error handling (try-catch) to ViewModel | `ui/AccountViewModel.kt` | Low — additive | Git revert |
| 2.5 Add SideEffect channel for Snackbar | `ui/AccountViewModel.kt` | Low — additive | Git revert |
| 2.6 Write ViewModel unit tests | `app/src/test/.../AccountViewModelTest.kt` | Zero — new file | Delete new file |

**Validation:** `./gradlew test --rerun-tasks --no-daemon` + verify existing UI still works

### Phase 3: Component Extraction (Low Risk)

**Goal:** Extract UI components from the monolithic file.

| Step | Files Affected | Risk | Rollback |
|---|---|---|---|
| 3.1 Extract `AccountListCard` | `ui/components/account/AccountListCard.kt` | Low — extract | Revert |
| 3.2 Extract `AccountColorPicker` | `ui/components/account/AccountColorPicker.kt` | Low — extract | Revert |
| 3.3 Extract `AccountTypeDropdown` | `ui/components/account/AccountTypeDropdown.kt` | Low — extract | Revert |
| 3.4 Extract `AccountBankFields` | `ui/components/account/AccountBankFields.kt` | Low — extract | Revert |
| 3.5 Extract `AccountPreviewRow` | `ui/components/account/AccountPreviewRow.kt` | Low — extract | Revert |
| 3.6 Extract `AccountOverflowMenu` | `ui/components/account/AccountOverflowMenu.kt` | Low — extract | Revert |
| 3.7 Create `AccountStatusBadge` | `ui/components/account/AccountStatusBadge.kt` | Zero — new file | Delete new file |
| 3.8 Add `@Preview` to all new components | All new component files | Zero — additive | Delete preview code |

**Validation:** `./gradlew test --no-daemon` + visual verification

### Phase 4: Screen Refactor (Medium Risk)

**Goal:** Rewrite screen as thin shell with proper state management.

| Step | Files Affected | Risk | Rollback |
|---|---|---|---|
| 4.1 Rewrite `AccountManagementScreen` as thin shell | `ui/screens/account/AccountManagementScreen.kt` | Medium — rewrite | Git revert |
| 4.2 Create `AccountFormDialog` (new) | `ui/screens/account/AccountFormDialog.kt` | Low — new file | Delete new file |
| 4.3 Create `AccountDeleteDialog` (new) | `ui/screens/account/AccountDeleteDialog.kt` | Low — new file | Delete new file |
| 4.4 Create `AccountArchiveDialog` (new) | `ui/screens/account/AccountArchiveDialog.kt` | Low — new file | Delete new file |
| 4.5 Wire ViewModel state to Screen | `ui/screens/account/AccountManagementScreen.kt` | Medium — integration | Git revert |
| 4.6 Add Snackbar feedback | `ui/screens/account/AccountManagementScreen.kt` | Low — additive | Git revert |

**Validation:** `./gradlew test --rerun-tasks --no-daemon` + full manual QA

### Phase 5: UX Enhancements (Low Risk)

**Goal:** Add missing UX features.

| Step | Files Affected | Risk | Rollback |
|---|---|---|---|
| 5.1 Add unarchive option | `AccountEvent`, `AccountViewModel`, overflow menu | Low | Revert |
| 5.2 Add undo for delete/archive | `AccountViewModel`, Snackbar | Low | Revert |
| 5.3 Improve empty state | `AccountManagementScreen` | Zero | Revert |
| 5.4 Fix color picker touch targets | `AccountColorPicker` | Zero | Revert |
| 5.5 Fix OverflowMenu anchoring | `AccountManagementScreen` | Low | Revert |
| 5.6 Add form progressive disclosure (bank fields) | `AccountBankFields` | Low | Revert |

**Validation:** `./gradlew test --no-daemon` + manual QA + accessibility check

### Phase 6: Tests & Polish (Low Risk)

**Goal:** Complete test coverage and accessibility.

| Step | Files Affected | Risk | Rollback |
|---|---|---|---|
| 6.1 Write Compose UI tests for all flows | `app/src/test/.../account/` | Zero | Delete test files |
| 6.2 Write snapshot tests (Roborazzi) | `app/src/test/.../account/` | Zero | Delete test files |
| 6.3 Add accessibility semantics | All account components | Zero | Revert |
| 6.4 Add `testTag` to key elements | All account components | Zero | Revert |
| 6.5 Run full verification suite | — | Zero | — |

**Validation:** `./gradlew ktlintFormat detekt test --rerun-tasks --no-daemon`

### Rollback Strategy

Every phase is independently revertable via `git revert`. No phase depends on a previous phase's unreleased changes. Each phase produces a working, tested state.

### Deliverables Per Phase

| Phase | Deliverables |
|---|---|
| Phase 1 | AccountDao, AccountRepository, 5 UseCases, AccountValidator, unit tests |
| Phase 2 | AccountEvent, AccountUiState, refactored ViewModel, ViewModel tests |
| Phase 3 | 7 extracted components with @Preview |
| Phase 4 | Thin shell screen, 3 dialog composables, wired state |
| Phase 5 | Unarchive, undo, improved empty state, fixed touch targets |
| Phase 6 | Full test coverage, accessibility, snapshot tests |

---

## Section 16: Risks

### 16.1 Dependency Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Room migration breaks existing data | Low | High | `@ColumnInfo(defaultValue = ...)` for new fields; always test with production DB copy |
| Rust FFI binding regeneration fails | Medium | Medium | Run `./gradlew :app:generateAndFixBindings --no-daemon` after any Rust change |
| Hilt DI wiring breaks | Low | Medium | Verify `@Inject` constructors match existing DI modules |

### 16.2 Migration Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| ViewModel state behavior changes | Medium | Medium | Write comprehensive ViewModel tests BEFORE refactoring |
| Form state loss during migration | Medium | High | Test config change and process death scenarios at each phase |
| Existing tests break | Medium | Low | Run `--rerun-tasks` at each phase; fix tests incrementally |

### 16.3 Performance Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Additional recomposition from state changes | Low | Low | Profile with Layout Inspector; use `derivedStateOf` where needed |
| Larger APK from more classes | Negligible | Negligible | R8/ProGuard strips unused code |

### 16.4 Data Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Backup format incompatibility | Low | High | `AccountEntity` fields unchanged; new fields have defaults |
| Account deletion orphaning transactions | Low | High | `canDeleteAccount` check preserved; add DB foreign key constraint |

### 16.5 UX Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Users confused by new UI flow | Low | Medium | Preserve existing interaction patterns; only add new capabilities |
| Snackbar undo timing too short | Low | Low | Use 7-second duration for destructive actions |
| Overflow menu positioning change | Medium | Medium | Test on multiple screen sizes and orientations |

### 16.6 Testing Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Robolectric doesn't support Compose well | Medium | Medium | Use Compose Testing rules; run on emulator for critical paths |
| Rust JNI state leakage in tests | Medium | High | `forkEvery = 1` already configured; verify with `--rerun-tasks` |

---

## Section 17: Final Blueprint

### 17.1 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      PRESENTATION                           │
│                                                             │
│  AccountManagementScreen ─── AccountFormDialog              │
│       │                     AccountDeleteDialog             │
│       │                     AccountArchiveDialog            │
│       │                                                     │
│  ┌────▼────────────────────────────────────────────┐        │
│  │  AccountListCard  AccountColorPicker            │        │
│  │  AccountTypeDropdown  AccountBankFields         │        │
│  │  AccountPreviewRow  AccountOverflowMenu         │        │
│  │  AccountStatusBadge  ColorPickerGrid            │        │
│  │  EmptyState  SectionHeader  ConfirmDialog       │        │
│  │  HesabyarDialog  HesabyarButton  HesabyarCard  │        │
│  └─────────────────────────────────────────────────┘        │
├─────────────────────────────────────────────────────────────┤
│                    STATE + EVENTS                            │
│                                                             │
│  AccountUiState ◄──── AccountEvent ────► AccountSideEffect  │
│  (accounts, dialog, form, loading, error)    (snackbar)     │
├─────────────────────────────────────────────────────────────┤
│                      VIEWMODEL                              │
│                                                             │
│  AccountViewModel                                           │
│  ├── onEvent(AccountEvent)                                  │
│  ├── StateFlow<AccountUiState>                              │
│  ├── Channel<AccountSideEffect>                             │
│  └── SavedStateHandle (form persistence)                    │
├─────────────────────────────────────────────────────────────┤
│                      DOMAIN                                 │
│                                                             │
│  AddAccountUseCase  UpdateAccountUseCase                    │
│  DeleteAccountUseCase  ArchiveAccountUseCase                │
│  UnarchiveAccountUseCase  GetAccountsUseCase                │
│  AccountValidator  ValidationResult                         │
│  AccountFormModel                                           │
├─────────────────────────────────────────────────────────────┤
│                       DATA                                  │
│                                                             │
│  AccountRepository (interface)                              │
│  AccountRepositoryImpl                                      │
│  AccountDao (Room)                                          │
│  AccountEntity (Room Entity)                                │
├─────────────────────────────────────────────────────────────┤
│                       FFI                                   │
│                                                             │
│  RustBridge (read-only)  RustMappers                        │
│  Account (Rust struct)                                      │
├─────────────────────────────────────────────────────────────┤
│                  DOWNSTREAM CONSUMERS                       │
│                                                             │
│  DashboardViewModel ◄── allAccounts Flow                    │
│  AnalyticsViewModel ◄── allAccounts Flow                    │
│  AccountSelector ◄── DashboardViewModel                     │
│  AccountBalanceCard ◄── GetDashboardDataUseCase             │
│  AccountBreakdownCard ◄── GetAnalyticsUseCase               │
│  TransactionViewModel ◄── default accountId                 │
│  BackupViewModel ◄── AccountEntity serialization            │
└─────────────────────────────────────────────────────────────┘
```

### 17.2 Component Diagram

```
AccountManagementScreen (shell)
│
├── TopAppBar
│   ├── IconButton (back) → onBack
│   └── Text (title)
│
├── AccountManagementContent
│   ├── [empty] EmptyState
│   │   ├── Icon
│   │   ├── Text (title)
│   │   ├── Text (description)
│   │   └── HesabyarButton (action CTA)
│   │
│   └── [has data] LazyColumn
│       └── AccountListCard × N
│           ├── IconCircle (account type)
│           ├── Column
│           │   ├── Text (name)
│           │   ├── Row
│           │   │   ├── Text (type)
│           │   │   └── Text (bank name)
│           │   └── Text (balance)
│           └── IconButton (overflow) → AccountOverflowMenu
│
├── FloatingActionButton (add)
│
├── [Dialog: Add/Edit] AccountFormDialog
│   └── HesabyarDialog
│       └── AccountFormContent
│           ├── HesabyarInputField (name)
│           ├── AccountTypeDropdown
│           ├── AccountBankFields (conditional)
│           │   ├── HesabyarInputField (bankName)
│           │   ├── HesabyarInputField (cardNumber)
│           │   ├── HesabyarInputField (accountNumber)
│           │   └── HesabyarInputField (iban)
│           ├── HesabyarInputField (balance)
│           ├── AccountColorPicker → ColorPickerGrid
│           └── AccountPreviewRow
│
├── [Dialog: Delete] AccountDeleteDialog
│   └── ConfirmDialog
│
├── [Dialog: Archive] AccountArchiveDialog
│   └── ConfirmDialog
│
└── [Menu: Overflow] AccountOverflowMenu
    └── DropdownMenu
        ├── DropdownMenuItem (edit)
        ├── DropdownMenuItem (archive)
        └── DropdownMenuItem (delete)
```

### 17.3 State Diagram

```
                    ┌──────────────┐
                    │   INITIAL    │
                    │  isLoading   │
                    │  = true      │
                    └──────┬───────┘
                           │ LoadAccounts
                           ▼
                    ┌──────────────┐
              ┌────►│    IDLE      │◄──────────────────┐
              │     │  accounts:   │                    │
              │     │  List<...>   │                    │
              │     │  isLoading:  │                    │
              │     │  false       │                    │
              │     └──┬───┬───┬───┘                    │
              │        │   │   │                        │
         Undo │        │   │   │                        │
              │   Add  │   │   │  Edit                  │
              │        ▼   │   ▼                        │
              │  ┌─────────┐ ┌──────────┐               │
              │  │SAVING   │ │EDITING   │               │
              │  │isSaving │ │form: ... │               │
              │  │= true   │ │          │               │
              │  └────┬────┘ └────┬─────┘               │
              │       │           │ OnSave              │
              │       ▼           ▼                     │
              │  ┌────────────────────┐                 │
              │  │   VALIDATING       │                 │
              │  │   form errors?     │                 │
              │  └──┬──────────┬──────┘                 │
              │     │          │                        │
              │  invalid     valid                      │
              │     │          │                        │
              │     ▼          ▼                        │
              │  ┌───────┐  ┌──────────┐               │
              │  │ errors│  │ SAVING   │               │
              │  │ shown │  │ useCase  │               │
              │  └───┬───┘  └────┬─────┘               │
              │      │           │ success             │
              │      │           ▼                     │
              │      │     ┌──────────┐                │
              │      │     │ SUCCESS  │───► snackbar   │
              │      │     │ → IDLE   │                │
              │      │     └──────────┘                │
              │      │           │ error               │
              │      │           ▼                     │
              │      │     ┌──────────┐                │
              │      │     │ ERROR    │───► snackbar   │
              │      │     │ → IDLE   │                │
              │      │     └──────────┘                │
              │      │                                 │
              │   ┌──┴─────────────┐                   │
              │   │ DELETE PENDING  │                   │
              │   │ checking...    │                   │
              │   └──┬─────────┬───┘                   │
              │      │         │                       │
              │  can't    can delete                   │
              │  delete      │                         │
              │      │         ▼                       │
              │      ▼  ┌───────────┐                  │
              │  ┌──────┐│ CONFIRM   │                 │
              │  │WARN  ││ DELETE    │                 │
              │  │dialog│└─────┬─────┘                 │
              │  └──────┘      │ confirm               │
              │                ▼                       │
              │         ┌──────────┐                   │
              │         │ DELETED  │──► snackbar+undo ─┘
              │         └──────────┘
              │
              │   ┌──────────────┐
              └──►│ ARCHIVE      │──► confirm ──► snackbar+undo ──► IDLE
                  │ PENDING      │
                  └──────────────┘
```

### 17.4 Event Diagram

```
User Action          Event                     ViewModel Handler          Side Effect
─────────────────────────────────────────────────────────────────────────────────────
Tap FAB          → OnAddAccount          → dialogState = Add          → —
Tap ⋮            → OnAccountOverflow     → dialogState = OverflowMenu → —
Tap "ویرایش"     → OnEditAccount         → dialogState = Edit(acct)   → —
Tap "آرشیو"      → OnRequestArchive      → dialogState = ArchiveConf  → —
Tap "حذف"        → OnRequestDelete       → dialogState = PendingDel   → —
Fill form        → OnFormChange          → formState = newForm        → —
Tap Save         → OnSaveNewAccount      → validate → insert          → Snackbar ✓
Tap Save (edit)  → OnSaveEditedAccount   → validate → update          → Snackbar ✓
Confirm delete   → OnConfirmDelete       → deleteAccount              → Snackbar + undo
Confirm archive  → OnConfirmArchive      → archiveAccount             → Snackbar + undo
Tap undo         → OnUndoDelete          → re-insert account          → Snackbar ✓
Tap dismiss      → OnDismissDialog       → dialogState = None         → —
Swipe snackbar   → OnDismissSnackbar     → snackbarMessage = null     → —
```

### 17.5 Dependency Diagram

```
                    MainActivity
                   /            \
                  /              \
     AccountViewModel      DashboardViewModel
            |                       |
     AccountRepository        GetDashboardDataUseCase
            |                       |
       AccountDao             RustBridge
            |                       |
         Room DB            Rust (read-only)
            |
     AccountEntity ──────────► Account (Rust struct)
                                     |
                              RustMappers
```

### 17.6 Module Diagram

```
┌─────────────────────────────────────────────────┐
│                 :app (single module)              │
│                                                   │
│  ┌──────────────────────────────────────────┐     │
│  │  feature:account (logical)               │     │
│  │  ┌──────────┐  ┌──────────┐             │     │
│  │  │  Screen   │  │Components│             │     │
│  │  └─────┬────┘  └─────┬────┘             │     │
│  │        │              │                   │     │
│  │  ┌─────▼──────────────▼────┐             │     │
│  │  │      ViewModel          │             │     │
│  │  └───────────┬─────────────┘             │     │
│  │              │                           │     │
│  │  ┌───────────▼─────────────┐             │     │
│  │  │      UseCases           │             │     │
│  │  └───────────┬─────────────┘             │     │
│  │              │                           │     │
│  │  ┌───────────▼─────────────┐             │     │
│  │  │      Repository         │             │     │
│  │  └───────────┬─────────────┘             │     │
│  └──────────────┼───────────────────────────┘     │
│                 │                                  │
│  ┌──────────────▼───────────────────────────┐     │
│  │  shared:data (AccountDao, Room)          │     │
│  └──────────────┬───────────────────────────┘     │
│                 │                                  │
│  ┌──────────────▼───────────────────────────┐     │
│  │  shared:designsystem (Tokens, Colors)    │     │
│  └──────────────────────────────────────────┘     │
│                                                   │
│  ┌──────────────────────────────────────────┐     │
│  │  downstream consumers:                   │     │
│  │  DashboardViewModel, AnalyticsViewModel, │     │
│  │  TransactionViewModel, BackupViewModel   │     │
│  └──────────────────────────────────────────┘     │
└─────────────────────────────────────────────────┘
```

### 17.7 Lifecycle Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    ANDROID LIFECYCLE                          │
│                                                              │
│  Activity Created                                            │
│  │                                                           │
│  ├── ViewModel Created (Hilt)                                │
│  │   ├── AccountRepository injected                          │
│  │   ├── UseCases created                                    │
│  │   ├── StateFlow initialized                               │
│  │   └── SavedStateHandle restored (if process death)        │
│  │                                                           │
│  ├── Screen Composed                                         │
│  │   ├── Collect ViewModel StateFlow                         │
│  │   ├── Render list / empty state                           │
│  │   └── Render dialog (if dialogState != None)              │
│  │                                                           │
│  ├── User Interacts                                          │
│  │   ├── Event → ViewModel.onEvent()                         │
│  │   ├── ViewModel → UseCase → Repository → Room             │
│  │   ├── Room Flow → ViewModel StateFlow → Screen recompose  │
│  │   └── SideEffect → Snackbar / Navigation                  │
│  │                                                           │
│  ├── Config Change (rotation)                                │
│  │   ├── ViewModel survives                                  │
│  │   ├── StateFlow survives                                  │
│  │   ├── FormState survives (ViewModel)                      │
│  │   └── Screen recomposes with restored state               │
│  │                                                           │
│  ├── Process Death                                           │
│  │   ├── ViewModel destroyed                                 │
│  │   ├── SavedStateHandle preserves formState                │
│  │   ├── ViewModel recreated with SavedStateHandle           │
│  │   └── Screen recomposes from saved state                  │
│  │                                                           │
│  └── Activity Destroyed                                      │
│      ├── ViewModel.onCleared() called                        │
│      └── CoroutineScope cancelled                            │
└──────────────────────────────────────────────────────────────┘
```

---

## Appendix A: File Inventory (Target State)

| File | Lines (est.) | Purpose |
|---|---|---|
| `ui/AccountViewModel.kt` | ~80 | Event handling, state coordination |
| `ui/AccountUiState.kt` | ~40 | State data classes |
| `ui/AccountEvent.kt` | ~35 | Event sealed class |
| `ui/screens/account/AccountManagementScreen.kt` | ~80 | Thin shell |
| `ui/screens/account/AccountFormDialog.kt` | ~60 | Dialog wrapper |
| `ui/screens/account/AccountDeleteDialog.kt` | ~40 | Delete confirmation |
| `ui/screens/account/AccountArchiveDialog.kt` | ~30 | Archive confirmation |
| `ui/components/account/AccountListCard.kt` | ~60 | List item card |
| `ui/components/account/AccountFormContent.kt` | ~100 | Form body |
| `ui/components/account/AccountColorPicker.kt` | ~50 | Color grid |
| `ui/components/account/AccountTypeDropdown.kt` | ~40 | Type selector |
| `ui/components/account/AccountBankFields.kt` | ~60 | Bank fields |
| `ui/components/account/AccountPreviewRow.kt` | ~30 | Live preview |
| `ui/components/account/AccountOverflowMenu.kt` | ~40 | Overflow actions |
| `ui/components/account/AccountStatusBadge.kt` | ~20 | Active/archived badge |
| `ui/components/shared/ColorPickerGrid.kt` | ~40 | Generic color grid |
| `domain/usecase/account/AddAccountUseCase.kt` | ~20 | Create account |
| `domain/usecase/account/UpdateAccountUseCase.kt` | ~20 | Update account |
| `domain/usecase/account/DeleteAccountUseCase.kt` | ~25 | Delete account |
| `domain/usecase/account/ArchiveAccountUseCase.kt` | ~15 | Archive account |
| `domain/usecase/account/UnarchiveAccountUseCase.kt` | ~15 | Unarchive account |
| `domain/usecase/account/GetAccountsUseCase.kt` | ~15 | Reactive list |
| `domain/validation/AccountValidator.kt` | ~60 | Validation rules |
| `domain/validation/ValidationResult.kt` | ~15 | Result type |
| `domain/model/AccountFormModel.kt` | ~20 | Validated form |
| `data/account/AccountDao.kt` | ~30 | Room DAO |
| `data/account/AccountRepository.kt` | ~20 | Interface |
| `data/account/AccountRepositoryImpl.kt` | ~30 | Implementation |
| **Total** | **~1,035** | — |
| **Current total** | **~731 (screen) + 84 (VM) + 47 (entity) = ~862** | — |

**Net result:** ~173 more lines of code, but distributed across 29 files averaging ~36 lines each (vs. current 3 files averaging ~287 lines).

---

## Appendix B: Design Decisions Log

| Decision | Choice | Rationale |
|---|---|---|
| State ownership | ViewModel (not rememberSaveable) | Testability, survives process death, centralized |
| Form validation location | Domain layer (AccountValidator) | Pure Kotlin, no Android deps, independently testable |
| Overflow menu | DropdownMenu (not BottomSheet) | Consistent with Material list patterns; BottomSheet overkill for 3 items |
| Delete undo | Snackbar with action (not dialog) | Standard Material pattern; less disruptive |
| Color picker | Grid (not slider/palette) | Faster selection; curated palette ensures contrast |
| Dialog framework | HesabyarDialog (existing) | Consistent with project pattern; already has close button, scroll, actions |
| Unarchive | Overflow menu option | Consistent with archive; discoverable |
| `displayOrder` | Keep append-only for now | Drag-to-reorder is a separate feature; don't over-engineer |
| `icon` field | Keep but don't display | Backward-compatible; future extensibility for custom icons |
| Testing framework | JUnit + Robolectric + Compose Testing | Matches existing project setup |

---

*End of Architecture Blueprint*
