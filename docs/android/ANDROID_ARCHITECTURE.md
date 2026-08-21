# ANDROID_ARCHITECTURE.md

**Project:** Android Cargo Manifest App  
**Purpose:** Describe the actual architectural baseline and boundaries

## 1. High-Level Structure

```text
MainActivity
    ↓
MainMenuScreen
    ├── CargoAppScreen
    │     ↓
    │   CargoViewModel
    │     ↓
    │   CargoDatabase / CargoDao
    │
    ├── ManifestSearchScreen
    │     ↓
    │   ManifestSearchViewModel
    │     ↓
    │   ManifestDatabase / ManifestDao
    │
    ├── StowingActivity
    │     ↓
    │   StowingViewModel
    │
    ├── BuktiTimbangActivity
    │     ↓
    │   BTB components / repositories
    │
    └── FlightTrackingActivity
```

## 2. UI Layer

Major UI files currently include:

- `MainMenuScreen.kt`
- `CargoAppScreen.kt`
- `ManifestSearchScreen.kt`
- `BtbCheckDialog.kt`
- Activity-based screens for Stowing, BTB, OCR, BTB label, and Flight Tracking.

Compose is used for the main menu, Manifest UI, and Manifest Search.

## 3. State / Presentation Layer

Current ViewModels include:

- `CargoViewModel`
- `ManifestSearchViewModel`
- `BtbViewModel`
- `StowingViewModel`

ViewModels currently perform both presentation-state management and some persistence/file orchestration. Future refactoring should move reusable data/file operations toward repository/service boundaries where justified.

## 4. Persistence Layer

### Cargo

```text
CargoViewModel
    ↓
CargoDao
    ↓
CargoDatabase
```

### Manifest Search

```text
ManifestSearchViewModel
    ↓
ManifestDao
    ↓
ManifestDatabase
```

### BTB

The project contains:

- `BtbDao`
- `BtbEntity`
- `BtbRepository`
- `BtbPhotoEntity`

### Important Current Condition

The project is **not yet a single-source-of-truth architecture**.

SharedPreferences are still used for several areas, including:

- `stowing_prefs`
- `btb_reference`
- `btb_reference_status`
- `cargo_photos`
- `stowing_draft`
- `cargo_archive`
- `manifest_settings`

This must be treated as known architectural debt, not silently removed during unrelated feature work.

## 5. Excel Layer

`ExcelUtils.kt` and `ManifestExcelImporter.kt` handle Excel-related processing.

Assets currently include:

- `template_manifest.xlsx`
- `Bukti_Timbang_Barang_BTB.xlsx`
- `STOWINGAN_PAG_TEMPLATE.xlsx`

Apache POI is used for workbook processing.

## 6. OCR Layer

Current OCR-related components:

```text
CameraX
   ↓
ScaleOcrActivity / BtbOcrScanner
   ↓
ML Kit Text Recognition
   ↓
Weight / BTB processing
```

## 7. External Integration

`N8nClient.kt` provides the Android-side n8n integration.

The n8n workflow/documentation is kept outside the core UI architecture.

## 8. Navigation Boundary

Flight Tracking is deliberately a separate Activity and is not part of the Cargo Manifest form flow.

This separation should be preserved unless a future requirement explicitly changes it.

## 9. Architectural Rules

- UI should not directly own long-running file/database work.
- ViewModels should expose state to UI and coordinate operations.
- Database access remains off the main thread.
- Excel processing remains off the main thread.
- Do not introduce a second persistence mechanism without documenting the reason.
