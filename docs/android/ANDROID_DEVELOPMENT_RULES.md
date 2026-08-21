<!-- Dokumentasi ini ditulis dalam Bahasa Indonesia. -->

# ANDROID_DEVELOPMENT_RULES.md

**Proyek:** Android Cargo Manifest App

## 1. Source of Truth

Before modifying code:

1. Read the relevant Spesifikasi.
2. Inspect the Saat Ini Kode Sumber.
3. Confirm the actual Implementasi.
4. Make the smallest safe change.

Do not code from memory or from an old ZIP.

## 2. Build Before and After

For a meaningful code change:

```text
Saat Ini Build state
    ↓
Change
    ↓
Build
    ↓
Test affected Fitur
```

If the Proyek cannot Build because the Gradle wrapper/toolchain is incomplete, document that limitation instead of claiming a successful Build.

## 3. No Blind File Deletion

Do not delete a Kotlin File merely because another screen appears to replace it.

Before deletion:

- Pencarian references;
- inspect Manifest registration;
- inspect navigation;
- confirm no imports/usages remain;
- confirm the File is truly obsolete.

## 4. Data Storage Rule

Do not introduce or preserve duplicated persistence without a reason.

Saat Ini Proyek has both Room and SharedPreferences. Treat migration as a deliberate Proyek task, not an automatic side effect of another Fitur.

## 5. Room Rule

Room should be preferred for structured, queryable application data.

SharedPreferences may remain for genuinely small settings until a documented migration is Selesai.

## 6. UI State Rule

Temporary UI state should remain in UI/ViewModel state as appropriate.

Important state that must survive configuration change/process recreation should have an explicit persistence strategy.

Do not assume `remember` alone is sufficient for durable state.

## 7. Latar Belakang Work Rule

Do not run heavy operations on the UI thread.

Examples:

- Apache POI workbook processing;
- large Excel imports/exports;
- OCR processing;
- large filesystem scans;
- Basis Data operations;
- network calls.

## 8. Excel Safety

Never modify the master Template accidentally.

Never overwrite an existing Manifest without explicit approval.

Read/Pencarian operations should be read-only.

## 9. Fitur Completion Rule

A Fitur is `[x] Selesai` only after:

```text
Kebutuhan clear
↓
Implementasi
↓
Build
↓
Functional test
↓
Regression check
↓
Documentation update
```

## 10. Ruang Lingkup Rule

Do not add unrelated Fitur while fixing a bug.

Separate:

- bug fix;
- refactor;
- Fitur;
- optimization.

## 11. Documentation Rule

After a meaningful architectural or Alur Kerja change:

- update the relevant MD;
- update `ANDROID_PROJECT_STATUS.md`;
- record the reason when the change affects Arsitektur or Ruang Lingkup.

## 12. Error Rule

Never claim a Build or test passed unless it was actually executed successfully.

Report blockers precisely.

## 13. GitHub Rule

Keep generated/Build output out of source control unless explicitly required.

Use GitHub Actions as a verification/release mechanism, not as a substitute for understanding the local Proyek state.
