# Backup Format

## Overview

Hesabyar uses a JSON-based backup format. Backups contain all user data and can be restored via the Settings screen.

---

## JSON Structure

```json
{
  "version": 1,
  "timestamp": 1700000000000,
  "appVersion": "1.0",
  "categories": [...],
  "transactions": [...],
  "loans": [...],
  "installments": [...],
  "paymentHistories": [...],
  "settings": {
    "darkMode": true
  }
}
```

---

## Fields

### Root

| Field       | Type   | Description                    |
|-------------|--------|--------------------------------|
| version     | Int    | Backup format version (always 1) |
| timestamp   | Long   | Backup creation time (ms)      |
| appVersion  | String | App version that created backup |
| categories  | Array  | Category records               |
| transactions| Array  | Transaction records            |
| loans       | Array  | Loan records                   |
| installments| Array  | Installment records            |
| paymentHistories | Array | Payment history records    |
| settings    | Object | App settings                   |

### Transaction

```json
{
  "id": 1,
  "type": "EXPENSE",
  "categoryId": 1,
  "amount": 5000000,
  "description": "خرید مواد غذایی",
  "personName": "",
  "date": 1700000000000,
  "dueDate": 0,
  "installmentId": 0
}
```

### Loan

```json
{
  "id": 1,
  "personName": "علی",
  "type": "DEBTOR",
  "originalAmount": 10000000,
  "remainingAmount": 5000000,
  "description": "قرض دادن به علی",
  "date": 1700000000000,
  "isSettled": false
}
```

### Installment

```json
{
  "id": 1,
  "title": "قسط ماشین",
  "amount": 2000000,
  "dueDate": 1700000000000,
  "isPaid": false,
  "reminderEnabled": true,
  "notes": ""
}
```

### PaymentHistory

```json
{
  "id": 1,
  "loanId": 1,
  "amount": 1000000,
  "date": 1700000000000,
  "notes": "بازپرداخت جزئی"
}
```

### Category

```json
{
  "id": 1,
  "name": "خوراک",
  "key": "Food",
  "icon": "Restaurant",
  "color": 1308622848,
  "type": "EXPENSE",
  "isDefault": true
}
```

---

## Restore Modes

### REPLACE

Deletes all existing data and replaces with backup contents.

**Use when:** Restoring from a full backup or switching devices.

### MERGE

Updates existing categories by key, appends new transactions/loans/installments.

**Use when:** Combining data from multiple sources.

---

## Validation Rules

- Version must be ≥ 1
- Transaction amounts must be > 0
- Transaction types must be "EXPENSE" or "INCOME"
- Loan amounts must be > 0
- Loan types must be "DEBTOR" or "CREDITOR"
- Installment amounts must be > 0

Invalid backups are rejected with error messages before restore.

---

## Compatibility

- Backups from v1.x are compatible with current version
- Older backups without categories field are handled gracefully (default categories used)
- All monetary values are in Rial (Long)
