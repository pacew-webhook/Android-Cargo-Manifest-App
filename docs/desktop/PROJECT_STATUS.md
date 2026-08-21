# PROJECT_STATUS.md

**Project:** Android Cargo Manifest App — Desktop/Windows  
**Purpose:** Central development checkpoint  
**Status:** Planning  
**Last Updated:** 21 August 2026

---

# 1. How to Use This File

`PROJECT_STATUS.md` is the **single central checkpoint** for the Desktop project.

It records:

- what has been completed;
- what is currently in progress;
- what is blocked;
- what should be done next;
- the last verified state.

This file must be updated whenever a feature is officially completed.

---

# 2. Development Rule

A feature is **not considered completed** merely because code has been written.

A feature may be marked `[x]` only after:

```text
Requirement
    ↓
Implementation
    ↓
Build
    ↓
Test
    ↓
Behavior matches specification
    ↓
Documentation updated
    ↓
[x] Completed
```

The next feature should not begin until the current feature is considered sufficiently verified, unless an explicit dependency requires parallel work.

---

# 3. Current Phase

```text
PHASE 0 — PLANNING & SPECIFICATION
```

Current objective:

> Finalize the Desktop requirements before implementation begins.

---

# 4. Completed Planning

## Project Concept

- [x] Desktop application concept defined
- [x] Desktop scope separated from Android
- [x] Desktop positioned as an assistant around Excel
- [x] Excel remains the main Manifest working environment
- [x] PTI/BTB/Stowing management excluded from MVP

## Documentation

- [x] `DESKTOP_DEVELOPMENT_PLAN_v2.md`
- [x] `DEVELOPMENT_RULES.md`
- [x] `DESKTOP_TECHNICAL_SPEC.md`
- [x] `DESKTOP_WORKFLOW_SPEC.md`
- [x] `PROJECT_STATUS.md`

## Search Architecture

- [x] Folder/file search selected for MVP
- [x] Excel content search selected for MVP
- [x] Search example `Tripleks Tony` defined
- [x] SQLite not required for MVP
- [x] FTS5 not required for MVP
- [x] SQLite/FTS5 reserved as future optimization

## Manifest Workflow

- [x] Daily Manifest workflow defined
- [x] Automatic current-date concept defined
- [x] Flight handling defined
- [x] File naming rule defined
- [x] Year/month archive structure defined
- [x] Duplicate protection defined
- [x] Template-copy workflow defined
- [x] Excel opening workflow defined

---

# 5. Current In Progress

```text
[ ] Final review of all Desktop specifications
```

The review must verify consistency between:

```text
DESKTOP_DEVELOPMENT_PLAN
        ↕
DEVELOPMENT_RULES
        ↕
DESKTOP_TECHNICAL_SPEC
        ↕
DESKTOP_WORKFLOW_SPEC
```

---

# 6. Next Development Steps

After the final specification review:

```text
1. Finalize requirements
        ↓
2. Create `DESKTOP_UI_SPEC.md`
        ↓
3. Define Desktop project structure
        ↓
4. Create initial Desktop project
        ↓
5. Build Manifest Creation prototype
        ↓
6. Verify prototype
        ↓
7. Implement Archive Search
        ↓
8. Verify Search
        ↓
9. Implement remaining approved MVP features
        ↓
10. Testing
        ↓
11. Windows packaging
        ↓
12. `.exe` release
```

---

# 7. Feature Status

| Feature | Status | Verification |
|---|---|---|
| Desktop concept | Completed | Planning review |
| Development plan | Completed | Documented |
| Development rules | Completed | Documented |
| Technical specification | Completed | Documented |
| Workflow specification | Completed | Documented |
| Project status tracking | Completed | This file |
| Final specification review | In Progress | Pending |
| UI specification | Not Started | Pending |
| Desktop project structure | Not Started | Pending |
| Manifest creation prototype | Not Started | Pending |
| Template validation | Not Started | Pending implementation |
| Date handling | Not Started | Pending implementation |
| Flight handling | Not Started | Pending implementation |
| Duplicate protection | Not Started | Pending implementation |
| Archive folder creation | Not Started | Pending implementation |
| Excel opening | Not Started | Pending implementation |
| File-name search | Not Started | Pending implementation |
| Excel content search | Not Started | Pending implementation |
| `Tripleks Tony` search | Not Started | Pending implementation |
| Search filters | Not Started | Pending implementation |
| Background search | Not Started | Pending implementation |
| Search cancellation | Not Started | Pending implementation |
| Archive browsing | Not Started | Pending implementation |
| Error handling | Not Started | Pending implementation |
| Windows packaging | Not Started | Pending implementation |
| `.exe` build | Not Started | Pending implementation |

---

# 8. Current Blockers

None.

The project is currently waiting for specification review, not blocked by a technical error.

---

# 9. Important Decisions

## Decision 1 — Excel Remains the Main Work Tool

The Desktop application will not force all Cargo data entry into a new Desktop UI.

Desktop primarily handles:

```text
Manifest preparation
+
Manifest archive organization
+
Manifest search
+
Opening Excel
```

---

## Decision 2 — No Mandatory Database for Search

MVP search:

```text
Folder
 ↓
Excel files
 ↓
Read workbook
 ↓
Search content
```

SQLite/FTS5 is only considered if real-world performance testing proves it necessary.

---

## Decision 3 — No Automatic Data Duplication

The Desktop application should not make users enter the same Cargo information twice.

For example, this is outside the MVP goal:

```text
Desktop:
Customer
BTB
PTI
Koli
KG
Trademark
Invoice
...
       ↓
Excel:
Input the same information again
```

---

## Decision 4 — Desktop Does Not Replace Cargo Workflow

The Desktop application does not attempt to control:

```text
TIMBANGAN
    ↓
BTB
    ↓
ADMIN
    ↓
PTI
    ↓
MANIFEST
    ↓
STOWING
```

Those operational processes remain outside the Desktop MVP.

---

## Decision 5 — Simplicity Before Optimization

Do not introduce:

- databases;
- cloud services;
- servers;
- complex indexing;
- unnecessary automation;

unless the actual project requirement justifies them.

---

# 10. Completion Rules

Before marking any feature `[x]`, verify:

```text
[ ] Requirement is clear
[ ] Implementation exists
[ ] Project builds
[ ] Feature works
[ ] Edge cases are handled appropriately
[ ] Existing features still work
[ ] No unintended data changes
[ ] Documentation reflects the final behavior
```

---

# 11. Change Log

## 2026-08-21

- Created central project status document.
- Established planning phase checkpoint.
- Recorded Desktop scope and major architectural decisions.
- Recorded Folder + Excel Content Search as MVP search architecture.
- Recorded SQLite/FTS5 as future optimization only.
- Recorded Manifest preparation and archive workflow decisions.

---

# 12. Resume Point

When development is paused and later resumed, start from:

```text
CURRENT PHASE:
Planning & Specification

CURRENT TASK:
Final review of Desktop specifications

NEXT DOCUMENT:
DESKTOP_UI_SPEC.md
```

The latest verified state must always be read from this file before continuing implementation.

---

# 13. Project Continuation Protocol

When the project is provided again for continued development:

```text
1. Read PROJECT_STATUS.md
        ↓
2. Read the relevant specification
        ↓
3. Inspect current source code
        ↓
4. Compare implementation with specification
        ↓
5. Identify the current task
        ↓
6. Implement only the approved next step
        ↓
7. Build and test
        ↓
8. Update PROJECT_STATUS.md
        ↓
9. Continue to the next feature
```

Do not assume that a feature is complete only because its source files exist.

---

# 14. Final Principle

> **PROJECT_STATUS.md is the project's checkpoint, not the project's specification.**

Specifications define:

```text
WHAT
HOW
RULES
WORKFLOW
```

`PROJECT_STATUS.md` defines:

```text
WHERE WE ARE
WHAT IS DONE
WHAT IS BEING WORKED ON
WHAT COMES NEXT
```

This separation must be maintained throughout development.

---

# 15. Current Status Summary

```text
PLAN        ✅
RULES       ✅
TECH SPEC   ✅
WORKFLOW    ✅
STATUS      ✅

FINAL REVIEW 🔄
UI SPEC      ⏳
CODING       ⏳
TESTING      ⏳
EXE          ⏳
```

**Current checkpoint:** Final specification review before UI design and implementation.
