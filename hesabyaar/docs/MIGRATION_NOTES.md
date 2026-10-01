# Migration Notes

## Room Database Migrations

### v1 → v2: Double to Long (Toman to Rial)

**Date:** Initial migration

**What changed:**

- All monetary columns converted from `Double` (Toman) to `Long` (Rial)
- Values multiplied by 1000 during migration

**Affected tables and columns:**

- `transactions`: `amount`
- `loans`: `originalAmount`, `remainingAmount`
- `installments`: `amount`
- `payment_history`: `amount`

**SQL approach:** CREATE new table → INSERT with CAST(amount * 1000 AS INTEGER) → DROP old → RENAME new

**Data preservation:** All existing data preserved with 1000x multiplier

---

### v2 → v3: Categories Table + categoryId

**Date:** Added category system

**What changed:**

- Created `categories` table with 8 default categories
- Recreated `transactions` table with `categoryId` (Long) replacing `category` (String)
- Migrated string category values to numeric IDs using CASE/WHEN mapping

**Category mapping:**

| Old String      | New categoryId |
|-----------------|---------------|
| Food            | 1             |
| Transportation  | 2             |
| Shopping        | 3             |
| Bills           | 4             |
| Installments    | 5             |
| Loans           | 6             |
| Income          | 7             |
| Other (default) | 8             |

**Data preservation:** All transactions preserved with correct category references

---

## AI Config Migration

### Legacy SharedPreferences → EncryptedSharedPreferences

**What changed:**

- AI provider configs moved from plain `SharedPreferences` to `EncryptedSharedPreferences`
- Uses AES-256-GCM encryption for values, AES-256-SIV for keys

**Migration process:**

1. Check if migration already completed (`migration_complete_v1` flag)
2. Read all data from legacy prefs
3. Write to encrypted prefs
4. Set migration flag
5. Clear legacy prefs

**Data preserved:** All AI configs, active config ID, online mode setting, model cache

---

## Breaking Changes

None. All migrations are backward-compatible and preserve existing data.

---

## Rollback Strategy

Room migrations are one-way. If a migration fails:

1. The app will crash on startup
2. User must uninstall and reinstall (data loss)
3. Backup/restore can recover data from a previous backup file

**Recommendation:** Always test migrations on a copy of production data before releasing.
