<!-- Dokumentasi ini ditulis dalam Bahasa Indonesia. -->

# ANDROID_ARCHITECTURE.md

**Proyek:** Android Cargo Manifest App  
**Tujuan:** Describe the actual architectural baseline and boundaries

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

## 2. UI Lapisan

Major UI files currently include:

- `MainMenuScreen.kt`
- `CargoAppScreen.kt`
- `ManifestSearchScreen.kt`
- `BtbCheckDialog.kt`
- Activity-based screens for Stowing, BTB, OCR, BTB label, and Flight Tracking.

Compose is used for the main menu, Manifest UI, and Manifest Pencarian.

## 3. State / Presentation Lapisan

Saat Ini ViewModels include:

- `CargoViewModel`
- `ManifestSearchViewModel`
- `BtbViewModel`
- `StowingViewModel`

ViewModels currently perform both presentation-state management and some persistence/File orchestration. Masa Depan refactoring should move reusable data/File operations toward Repository/service boundaries where justified.

## 4. Persistence Lapisan

### Cargo

```text
CargoViewModel
    ↓
CargoDao
    ↓
CargoDatabase
```

### Manifest Pencarian

```text
ManifestSearchViewModel
    ↓
ManifestDao
    ↓
ManifestDatabase
```

### BTB

The Proyek contains:

- `BtbDao`
- `BtbEntity`
- `BtbRepository`
- `BtbPhotoEntity`

### Important Saat Ini Condition

The Proyek is **not yet a single-source-of-truth Arsitektur**.

SharedPreferences are still used for several areas, including:

- `stowing_prefs`
- `btb_reference`
- `btb_reference_status`
- `cargo_photos`
- `stowing_draft`
- `cargo_archive`
- `manifest_settings`

This must be treated as known architectural debt, not silently removed during unrelated Fitur work.

## 5. Excel Lapisan

`ExcelUtils.kt` and `ManifestExcelImporter.kt` handle Excel-related processing.

Assets currently include:

- `template_manifest.xlsx`
- `Bukti_Timbang_Barang_BTB.xlsx`
- `STOWINGAN_PAG_TEMPLATE.xlsx`

Apache POI is used for workbook processing.

## 6. OCR Lapisan

Saat Ini OCR-related components:

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

The n8n Alur Kerja/documentation is kept outside the core UI Arsitektur.

## 8. Navigation Boundary

Flight Tracking is deliberately a separate Activity and is not part of the Cargo Manifest form flow.

This separation should be preserved unless a Masa Depan Kebutuhan explicitly changes it.

## 9. Architectural Aturan

- UI should not directly own long-running File/Basis Data work.
- ViewModels should expose state to UI and coordinate operations.
- Basis Data access remains off the main thread.
- Excel processing remains off the main thread.
- Do not introduce a second persistence mechanism without documenting the reason.
