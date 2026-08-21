<!-- Dokumentasi ini ditulis dalam Bahasa Indonesia. -->

# ANDROID_DEVELOPMENT_PLAN.md

**Proyek:** Android Cargo Manifest App  
**Tujuan:** Master Pengembangan direction for the Android application  
**Status:** Existing Proyek — documentation baseline

## 1. Saat Ini Ruang Lingkup

The Android app currently contains these functional areas:

- Cargo Manifest
- Stowing / pallet data
- Bukti Timbang Barang (BTB)
- Scale OCR
- BTB label handling
- Manifest historical Pencarian
- Optional Flight Tracking
- n8n integration

The Android app remains the operational application. Desktop Pengembangan is documented separately under `docs/desktop/`.

## 2. Pengembangan Priorities

### Priority 1 — Stability
- Keep the Proyek buildable.
- Fix compile/runtime regressions before adding unrelated Fitur.
- Preserve working Excel import/export behavior.

### Priority 2 — Data consistency
- Define one authoritative source for each data domain.
- Reduce duplicated state between Room, SharedPreferences, files, and in-memory state.
- Preserve data across process death where appropriate.

### Priority 3 — Alur Kerja correctness
- Manifest, BTB, Stowing, and Pencarian must reflect the actual Cargo Alur Kerja.
- Do not force an idealized Alur Kerja that conflicts with field practice.

### Priority 4 — Performa
- Heavy Excel/OCR/Pencarian operations must not block the UI.
- Optimize only after measuring real bottlenecks.

### Priority 5 — Maintainability
- Keep UI, ViewModel, Repository/DAO, Basis Data, and File/Excel responsibilities separated.
- Avoid adding new storage mechanisms without documenting why.

## 3. Saat Ini Arsitektur Baseline

The Saat Ini codebase uses:

- Jetpack Compose for major UI surfaces.
- Activities for several operational modules.
- Room for Cargo and Manifest-related persistence.
- Apache POI for Excel processing.
- CameraX + ML Kit for OCR.
- n8n HTTP integration.
- SharedPreferences for several settings/legacy state areas.

This is the baseline to be improved, not an assumption that every Saat Ini Implementasi is final.

## 4. Non-Goals

Do not add complexity solely for Masa Depan possibilities.

Examples:

- Cloud backend without a defined Kebutuhan.
- Mandatory online dependency for core Cargo work.
- New Basis Data tables without a clear query/use case.
- Replacing Excel before the real Alur Kerja requires it.

## 5. Pengembangan Sequence

```text
Understand Saat Ini code
        ↓
Define Kebutuhan
        ↓
Implement smallest safe change
        ↓
Build
        ↓
Test affected Alur Kerja
        ↓
Update documentation/Status
        ↓
Berikutnya Fitur
```

## 6. Release Principle

A Fitur is complete only when its behavior is verified against its Kebutuhan and existing workflows remain intact.
