# Hesabyar Roadmap

## Status Legend

- [x] Done
- [~] In Progress
- [ ] Planned
- [!] Blocked

---

# Core Accounting

- [x] Transactions
- [x] Loans
- [x] Installments
- [x] Dashboard

---

# AI

- [x] Gemini Integration
- [x] Smart Text Parsing (online + offline)
- [x] Multi Provider Support (Gemini, OpenRouter, Custom)
- [x] Offline AI Fallback (rule-based parser + advisor) — Rust-backed; Kotlin fallback is a permanent exception for the offline NLP parser only; budget advice/forecast Kotlin fallbacks are temporary (scheduled for removal per `plans/2026-08-19-rust-fallback-consolidation-plan.md` Phases 9–10)
- [x] Persian Amount Parser (token-based)
- [x] Money Detector (gate for amount parsing)
- [x] Category Inference (keyword-based)
- [x] Budget Advisor (AI + offline)
- [x] Budget Forecast (AI + offline)
- [ ] Voice Input
- [ ] Local AI Support (on-device model)

---

# Data

- [x] Room Database (v3)
- [x] Backup (JSON format)
- [x] Restore (replace + merge modes)
- [x] Excel Export (.xlsx)
- [ ] CSV Export
- [ ] Encrypted Backups

---

# Notifications

- [x] Installment Reminder (WorkManager)
- [x] Loan Reminder (WorkManager)
- [x] Mark Paid (notification action)
- [x] Boot Receiver (re-schedule alarms)
- [x] Reminder Settings (configurable)

---

# Analytics

- [x] Dashboard Summary (balance, monthly, debt)
- [x] Category Breakdown
- [x] Monthly Spending (Jalali months)
- [x] Debt/Credit Overview
- [x] Installment Progress
- [ ] Charts (visual graphs)
- [ ] Yearly Reports
- [ ] Export Reports

---

# Security

- [x] Encrypted API Keys (EncryptedSharedPreferences)
- [x] Build Secrets (.env)
- [x] No Hardcoded Keys
- [ ] Secure Backup (encryption)
- [ ] Database Encryption (SQLCipher)
- [ ] Biometric Auth

---

# Architecture

- [x] MVVM Pattern
- [x] Repository Pattern
- [x] Flow/StateFlow reactive data
- [x] Offline Parser Pipeline
- [x] AI Provider Abstraction
- [x] Use Cases Layer
- [x] Dependency Injection (Hilt)
- [x] Rust Core (sole location for NEW feature business logic, calculations, validation, and rule-driven data transformations; structural/type mapping such as DTO conversion and entity mapping stays Kotlin-permitted; permanent and temporary Kotlin fallbacks documented in ADR-001; non-exception fallbacks scheduled for removal per the consolidation plan)
- [x] Full Test Coverage

---

# Testing

- [x] Unit Tests (parser, calculations, backup, calendar, config)
- [x] Offline Parser Tests (PersianAmountParser, MoneyDetector, GeminiParser)
- [x] Analytics Tests
- [x] Category Tests
- [x] Reminder Tests
- [x] AI Cache Tests
- [x] Excel Exporter Tests
- [x] Budget Advisor Tests
- [x] Jalali Calendar Tests
- [x] Performance Optimizations (AppLogger, distinctUntilChanged)
- [ ] Integration Tests (Room database)
- [ ] UI Tests (Compose)

---

# Performance

- [x] AppLogger ring buffer (ArrayDeque)
- [x] DashboardViewModel distinctUntilChanged
- [x] AnalyticsViewModel distinctUntilChanged
- [x] Category keyword deduplication
- [ ] Lazy list performance
- [ ] Recomposition monitoring

---

# Documentation

- [x] README.md
- [x] Architecture Guide
- [x] Database Schema
- [x] Backup Format
- [x] AI Provider Guide
- [x] Build & Release Instructions
- [x] Migration Notes
- [x] Security Overview

---

# Future

- [ ] Cloud Sync
- [ ] Multi Device Support
- [ ] Shared Accounts
- [ ] Desktop Version
- [ ] Wear OS Support
- [ ] Widget Support
- [ ] Recurring Transactions
- [ ] Multi-Currency Support
- [ ] Receipt Scanning (OCR)
