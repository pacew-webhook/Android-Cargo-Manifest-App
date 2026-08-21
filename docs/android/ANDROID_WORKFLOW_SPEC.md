<!-- Dokumentasi ini ditulis dalam Bahasa Indonesia. -->

# ANDROID_WORKFLOW_SPEC.md

**Proyek:** Android Cargo Manifest App  
**Tujuan:** Define the operational Alur Kerja represented by the Saat Ini application

## 1. Main Navigation

```text
Open App
  ↓
Main Menu
  ├── Data Manifest Cargo
  ├── Data Stowingan Palet
  ├── Pencarian Basis Data Manifest
  ├── Bukti Timbang Barang
  └── Flight Tracking (optional/separate)
```

## 2. Manifest Alur Kerja

Saat Ini conceptual flow:

```text
Stowing / imported Cargo data
        ↓
CargoViewModel
        ↓
Manifest grouping / editing
        ↓
Manifest display
        ↓
Excel export / processing
```

Manifest grouping currently uses fields including PTI, Pelanggan, and Description.

## 3. BTB Alur Kerja

The application contains a dedicated BTB module:

```text
Bukti Timbang Barang
        ↓
BTB data / photos / OCR
        ↓
Validation / label handling
        ↓
Excel or downstream processing
```

The exact operational Aturan should follow the user's actual Cargo process rather than assuming PTI and BTB are always interchangeable.

## 4. Stowing Alur Kerja

The Proyek contains a separate Stowing Activity and ViewModel.

The Saat Ini Implementasi uses persisted Stowing data and Excel-related processing.

## 5. Manifest Historical Pencarian

The Saat Ini Android Proyek already has a Manifest Pencarian Fitur.

Conceptual flow:

```text
Pencarian Screen
    ↓
Pencarian ViewModel
    ↓
Manifest Pencarian data
    ↓
Results
    ↓
Open / inspect relevant Manifest
```

The Pencarian Arsitektur must be verified against the actual Implementasi before replacing it with a different design.

## 6. Flight Tracking

Flight Tracking is optional and separated from the Cargo Alur Kerja:

```text
Main Menu
   ↓
Flight Tracking Activity
```

It must not become a mandatory step for creating or processing a Manifest.

## 7. Excel Principle

Excel remains an important operational artifact.

The application may:

- import Excel;
- generate/export Excel;
- open/share Excel files;
- use bundled templates.

The application must not silently alter user-owned Manifest files during read/Pencarian operations.

## 8. Masa Depan Alur Kerja Changes

Any new PTI/BTB/Pelanggan reconciliation Alur Kerja must first be documented here before large-scale Implementasi.

Avoid adding automation that assumes all staff follow the same manual process when field practice is known to vary.
