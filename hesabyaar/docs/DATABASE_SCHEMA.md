# Database Schema

## Overview

Hesabyar uses Room (SQLite) as the primary data store. Current schema version: **3**.

All monetary values are stored as **Long (Rial)**. The UI displays values in **Toman** (Rial ÷ 10). The factor of 10 is defined in `CurrencyFormatter.fromRial()` and `rust/hesabyar-core/src/currency.rs`.

---

## Tables

### categories

| Column    | Type    | Description                          |
|-----------|---------|--------------------------------------|
| id        | Long    | Primary key, auto-generated          |
| name      | String  | Persian display name                 |
| key       | String  | Unique English key (e.g., "Food")    |
| icon      | String  | Material icon name                   |
| color     | Long    | ARGB color value                     |
| type      | String  | "EXPENSE", "INCOME", or "BOTH"      |
| isDefault | Boolean | True for built-in categories         |

**Default categories (8):**

| Key              | Persian Name | Type    |
|------------------|-------------|---------|
| Food             | خوراک       | EXPENSE |
| Transportation   | حمل و نقل   | EXPENSE |
| Shopping         | خرید        | EXPENSE |
| Bills            | قبوض        | EXPENSE |
| Installments     | اقساط       | EXPENSE |
| Loans            | وام و قرض   | BOTH    |
| Income           | درآمد       | INCOME  |
| Other            | سایر        | BOTH    |

---

### transactions

| Column      | Type    | Nullable | Description                    |
|-------------|---------|----------|--------------------------------|
| id          | Long    | No       | Primary key, auto-generated    |
| type        | String  | No       | "EXPENSE" or "INCOME"         |
| categoryId  | Long    | No       | FK → categories.id             |
| amount      | Long    | No       | Amount in Rial                 |
| description | String  | No       | Persian description            |
| personName  | String  | Yes      | Associated person name         |
| date        | Long    | No       | Transaction timestamp (ms)     |
| dueDate     | Long    | Yes      | Due date for scheduled txns    |
| installmentId| Long   | Yes      | FK → installments.id           |

---

### loans

| Column          | Type    | Nullable | Description                    |
|-----------------|---------|----------|--------------------------------|
| id              | Long    | No       | Primary key, auto-generated    |
| personName      | String  | No       | Person's name                  |
| type            | String  | No       | "DEBTOR" or "CREDITOR"        |
| originalAmount  | Long    | No       | Original amount in Rial        |
| remainingAmount | Long    | No       | Remaining amount in Rial       |
| description     | String  | No       | Loan description               |
| date            | Long    | No       | Loan creation timestamp (ms)   |
| isSettled       | Boolean | No       | Whether loan is fully paid     |

**Loan types:**

- `DEBTOR`: Someone owes you money (you are the creditor)
- `CREDITOR`: You owe someone money (you are the debtor)

---

### installments

| Column          | Type    | Nullable | Description                    |
|-----------------|---------|----------|--------------------------------|
| id              | Long    | No       | Primary key, auto-generated    |
| title           | String  | No       | Installment description        |
| amount          | Long    | No       | Payment amount in Rial         |
| dueDate         | Long    | No       | Due date timestamp (ms)        |
| isPaid          | Boolean | No       | Whether installment is paid    |
| reminderEnabled | Boolean | No       | Whether reminders are active   |
| notes           | String  | No       | Additional notes               |

---

### payment_history

| Column  | Type    | Nullable | Description                    |
|---------|---------|----------|--------------------------------|
| id      | Long    | No       | Primary key, auto-generated    |
| loanId  | Long    | No       | FK → loans.id                  |
| amount  | Long    | No       | Payment amount in Rial         |
| date    | Long    | No       | Payment timestamp (ms)         |
| notes   | String  | No       | Payment notes                  |

---

## Migrations

### v1 → v2: Double to Long (Toman to Rial)

All monetary columns converted from `Double` (Toman) to `Long` (Rial) by multiplying by 1000.

Affected tables:

- `transactions.amount`
- `loans.originalAmount`, `loans.remainingAmount`
- `installments.amount`
- `payment_history.amount`

### v2 → v3: Categories table + categoryId

- Created `categories` table with 8 default categories
- Recreated `transactions` table with `categoryId` (Long) replacing `category` (String)
- Migrated old string category values to corresponding category IDs

---

## Relationships

```
categories  ←── transactions.categoryId
installments ←── transactions.installmentId
loans       ←── payment_history.loanId
```

Note: Room does not enforce foreign key constraints in this schema. Referential integrity is maintained by application logic.
