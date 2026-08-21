<!-- Dokumentasi ini ditulis dalam Bahasa Indonesia. -->

# ANDROID_PROJECT_STATUS.md

**Proyek:** Android Cargo Manifest App  
**Status:** Existing Proyek — baseline audit  
**Terakhir Diperbarui:** 21 August 2026

## 1. Saat Ini Baseline

The uploaded Proyek contains:

- Jetpack Compose UI;
- multiple Activities;
- Room databases for Cargo and Manifest Pencarian;
- BTB entities/DAO/Repository;
- Apache POI Excel processing;
- CameraX + ML Kit OCR;
- n8n client;
- separate Flight Tracking Activity;
- existing Manifest Pencarian;
- bundled Excel templates.

## 2. Documentation Status

- [x] Android Pengembangan plan created
- [x] Android Arsitektur documentation created
- [x] Android Alur Kerja Spesifikasi created
- [x] Android Pengembangan Aturan created
- [x] Android Proyek Status created
- [ ] Full final Arsitektur review
- [ ] Build verification after restoring a complete Gradle wrapper

## 3. Known Architectural Debt

### Persistence duplication

Room exists, but SharedPreferences are still used for structured operational state.

Observed preference areas include:

```text
stowing_prefs
btb_reference
btb_reference_status
cargo_photos
stowing_draft
cargo_archive
manifest_settings
```

This does not mean they should all be deleted immediately.

Berikutnya step: map each preference to its Tujuan and decide:

```text
Keep as setting
Migrate to Room
Replace with File storage
Remove as obsolete
```

### Multiple data stores

The Proyek has separate Room databases:

```text
CargoDatabase
ManifestDatabase
```

This is not automatically wrong, but it should be reviewed to ensure boundaries are intentional and queries do not require unnecessary synchronization between databases.

## 4. Saat Ini Fitur

| Area | Present |
|---|---|
| Main Menu | Yes |
| Manifest Cargo | Yes |
| Stowing | Yes |
| BTB | Yes |
| Scale OCR | Yes |
| BTB Label | Yes |
| Manifest Pencarian | Yes |
| Flight Tracking | Yes |
| n8n integration | Yes |
| Excel templates | Yes |

## 5. Build Verification

The uploaded ZIP contains `gradlew`, but the Gradle wrapper JAR is not present in the archive.

Therefore the Proyek could not be verified with:

```text
./gradlew assembleDebug
```

The attempted Build failed because:

```text
org.gradle.wrapper.GradleWrapperMain
```

was unavailable.

This is a Repository/package completeness issue, not evidence that the Kotlin source necessarily fails to compile.

## 6. Immediate Berikutnya Steps

1. Review this baseline documentation.
2. Restore/verify a complete Gradle wrapper.
3. Build the Saat Ini Proyek without functional changes.
4. Record actual compiler/runtime errors.
5. Map persistence usage.
6. Decide the Room/SharedPreferences migration plan.
7. Only then implement the Berikutnya requested Fitur.

## 7. Saat Ini Checkpoint

```text
SOURCE AUDIT        ✅
DOCUMENTATION       ✅
Build VERIFICATION  ⚠️ BLOCKED BY MISSING WRAPPER JAR
Fitur Pengembangan ⏸️ WAITING FOR BASELINE Build
```

## 8. Resume Protocol

When this Proyek is uploaded again:

```text
Read ANDROID_PROJECT_STATUS.md
        ↓
Read relevant Spesifikasi
        ↓
Inspect source
        ↓
Verify Build
        ↓
Continue from Saat Ini checkpoint
```

Do not assume a Fitur is complete merely because its source File exists.
