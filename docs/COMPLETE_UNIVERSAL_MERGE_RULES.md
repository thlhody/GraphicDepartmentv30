# 📋 Complete Universal Merge Rules Documentation

**Date:** 2025-11-03
**Status:** ✅ Complete

## Status System Overview

```
STATUS HIERARCHY (Priority: High → Low)
===========================================

LEVEL 4: FINAL STATES (Immutable)
├── ADMIN_FINAL           → Absolutely immutable, highest authority
└── TEAM_FINAL            → Team finalized (admin can override)

LEVEL 3: TIMESTAMPED EDITS (Versioned)
├── ADMIN_EDITED_[timestamp]  → Admin modified with timestamp
├── TEAM_EDITED_[timestamp]   → Team lead modified with timestamp
└── USER_EDITED_[timestamp]   → User modified with timestamp

LEVEL 2: PROTECTED STATES (Worktime only)
└── USER_IN_PROCESS       → Active user session (protected from external changes)

LEVEL 1: BASE INPUT STATES
├── ADMIN_INPUT          → Admin created entry (priority 3)
├── TEAM_INPUT           → Team lead created entry (priority 2)
└── USER_INPUT           → User created entry (priority 1)

SPECIAL: DELETION STATES (Tombstones)
├── ADMIN_DELETED_[timestamp]
├── TEAM_DELETED_[timestamp]
└── USER_DELETED_[timestamp]
```

## Universal Merge Engine Rules (Applied in Order)

```java
// Rule Order (First match wins)
// UniversalMergeEngine.java - Lines 25-192
```

### ═══════════════════════════════════════════════════════════════════════════
### RULE 1: FINAL_STATE_ABSOLUTE (Lines 25-47)
### ═══════════════════════════════════════════════════════════════════════════

**Condition**: Either entry is ADMIN_FINAL or TEAM_FINAL

**Resolution**:
- If both are final:
  - `ADMIN_FINAL > TEAM_FINAL`
- If only one is final:
  - Final entry ALWAYS wins

**Examples**:
```
ADMIN_FINAL vs USER_EDITED_123456 → ADMIN_FINAL wins
TEAM_FINAL vs ADMIN_FINAL → ADMIN_FINAL wins
```

---

### ═══════════════════════════════════════════════════════════════════════════
### RULE 2: VERSIONED_EDIT_COMPARISON (Lines 53-79)
### ═══════════════════════════════════════════════════════════════════════════

**Condition**: Either entry has timestamped edit status

**Resolution**:
- Extract timestamps from both statuses
- If timestamps are different:
  - **NEWER timestamp wins**
- If timestamps are EQUAL (conflict):
  - Apply Editor Priority: **ADMIN > TEAM > USER**

**Examples**:
```
USER_EDITED_100 vs USER_EDITED_200 → USER_EDITED_200 wins (newer)
ADMIN_EDITED_100 vs USER_EDITED_100 → ADMIN_EDITED_100 wins (admin priority)
TEAM_EDITED_150 vs USER_EDITED_200 → USER_EDITED_200 wins (newer)
ADMIN_EDITED_150 vs TEAM_EDITED_200 → TEAM_EDITED_200 wins (newer)
```

---

### ═══════════════════════════════════════════════════════════════════════════
### RULE 3: USER_INPUT_OVERRIDES_IN_PROCESS (Lines 85-96) [WORKTIME ONLY]
### ═══════════════════════════════════════════════════════════════════════════

**Condition**: One is USER_INPUT, other is USER_IN_PROCESS

**Resolution**: USER_INPUT wins (completed work beats in-progress)

**Examples**:
```
USER_INPUT vs USER_IN_PROCESS → USER_INPUT wins
```

---

### ═══════════════════════════════════════════════════════════════════════════
### RULE 4: USER_IN_PROCESS_PROTECTION (Lines 98-117) [WORKTIME ONLY]
### ═══════════════════════════════════════════════════════════════════════════

**Condition**: Either entry is USER_IN_PROCESS (and no USER_INPUT present)

**Resolution**: USER_IN_PROCESS wins (protects active sessions)

**Examples**:
```
USER_IN_PROCESS vs ADMIN_INPUT → USER_IN_PROCESS wins
USER_IN_PROCESS vs TEAM_EDITED_123 → USER_IN_PROCESS wins
```

---

### ═══════════════════════════════════════════════════════════════════════════
### RULE 5: BASE_INPUT_HIERARCHY (Lines 123-139)
### ═══════════════════════════════════════════════════════════════════════════

**Condition**: Both entries are base input statuses

**Resolution**: Apply priority hierarchy
- `ADMIN_INPUT (priority 3) > TEAM_INPUT (priority 2) > USER_INPUT (priority 1)`

**Examples**:
```
ADMIN_INPUT vs USER_INPUT → ADMIN_INPUT wins
TEAM_INPUT vs USER_INPUT → TEAM_INPUT wins
ADMIN_INPUT vs TEAM_INPUT → ADMIN_INPUT wins
```

---

### ═══════════════════════════════════════════════════════════════════════════
### RULE 6: VERSIONED_BEATS_BASE (Lines 145-155)
### ═══════════════════════════════════════════════════════════════════════════

**Condition**: One is versioned edit, other is base input

**Resolution**: Versioned edit ALWAYS wins

**Examples**:
```
USER_EDITED_123 vs ADMIN_INPUT → USER_EDITED_123 wins
TEAM_EDITED_456 vs USER_INPUT → TEAM_EDITED_456 wins
ADMIN_EDITED_789 vs USER_INPUT → ADMIN_EDITED_789 wins
```

---

### ═══════════════════════════════════════════════════════════════════════════
### RULE 7: PROTECTED_BEATS_BASE (Lines 157-168) [WORKTIME ONLY]
### ═══════════════════════════════════════════════════════════════════════════

**Condition**: One is USER_IN_PROCESS, other is base input (not USER_INPUT)

**Resolution**: USER_IN_PROCESS wins

**Examples**:
```
USER_IN_PROCESS vs ADMIN_INPUT → USER_IN_PROCESS wins
USER_IN_PROCESS vs TEAM_INPUT → USER_IN_PROCESS wins
```

---

### ═══════════════════════════════════════════════════════════════════════════
### RULE 8: SINGLE_ENTRY_FALLBACK (Lines 174-182)
### ═══════════════════════════════════════════════════════════════════════════

**Condition**: One entry is null

**Resolution**: Return the non-null entry

**Examples**:
```
null vs USER_INPUT → USER_INPUT wins
ADMIN_EDITED_123 vs null → ADMIN_EDITED_123 wins
```

---

### ═══════════════════════════════════════════════════════════════════════════
### RULE 9: DEFAULT_FALLBACK (Lines 184-192)
### ═══════════════════════════════════════════════════════════════════════════

**Condition**: No other rule matched (should never happen)

**Resolution**: Return entry1 with warning log

---

## Key Implementation Details

### ✅ Check Register (CORRECT Implementation)

**File**: `CheckRegisterService.java` - Lines 421, 568, 1130

```java
// When team saves:
entry.setAdminSync(MergingStatusConstants.createTeamEditedStatus());

// When user saves:
entry.setAdminSync(MergingStatusConstants.createUserEditedStatus());
```

### ✅ User Register (CORRECT Implementation - FIXED)

**File**: `UserRegisterService.java` - Line 174

```java
// When user edits existing entry:
entry.setAdminSync(MergingStatusConstants.createUserEditedStatus());
```

### ✅ Admin Register (FIXED Implementation)

**File**: `AdminRegisterService.java` - Lines 309-335, 758-792

```java
// Get the current adminSync status from the client
String currentAdminSync = data.get("adminSync") != null ?
        data.get("adminSync").toString() : MergingStatusConstants.USER_INPUT;

// Determine the new adminSync status (uses helper method)
String newAdminSync = determineAdminSyncStatusForSave(currentAdminSync);
entry.setAdminSync(newAdminSync);
```

**Helper Method** (Lines 758-792):
```java
private String determineAdminSyncStatusForSave(String currentStatus) {
    // Preserve ADMIN_FINAL (immutable)
    if (MergingStatusConstants.ADMIN_FINAL.equals(currentStatus)) {
        return currentStatus;
    }

    // Admin can override TEAM_FINAL
    if (MergingStatusConstants.TEAM_FINAL.equals(currentStatus)) {
        return MergingStatusConstants.createAdminEditedStatus();
    }

    // Preserve USER_IN_PROCESS (user actively working)
    if (MergingStatusConstants.USER_IN_PROCESS.equals(currentStatus)) {
        return currentStatus;
    }

    // For all other cases: create ADMIN_EDITED timestamp
    return MergingStatusConstants.createAdminEditedStatus();
}
```

---

## Status Transition Examples

| Current Status | User Action | New Status | Admin Action | New Status |
|---------------|-------------|------------|--------------|------------|
| `USER_INPUT` | Edits entry | `USER_EDITED_[ts]` | Edits entry | `ADMIN_EDITED_[ts]` |
| `USER_EDITED_123` | Edits again | `USER_EDITED_[new_ts]` | Edits entry | `ADMIN_EDITED_[ts]` |
| `ADMIN_EDITED_123` | Can't edit admin file | - | Edits entry | `ADMIN_EDITED_[new_ts]` |
| `TEAM_EDITED_123` | Can't override | - | Edits entry | `ADMIN_EDITED_[ts]` |
| `TEAM_FINAL` | Can't override | - | Edits entry | `ADMIN_EDITED_[ts]` ✅ |
| `ADMIN_FINAL` | Can't override | - | Preserves | `ADMIN_FINAL` |
| `USER_IN_PROCESS` | Active work | `USER_IN_PROCESS` | Preserves | `USER_IN_PROCESS` |

---

## The Problem That Was Fixed

### Root Cause of Admin CG Edit Loss

**SCENARIO**: Admin edits CG values and saves

#### ❌ Before Fix (BROKEN):
```
1. Admin loads entries → Status: USER_INPUT
2. Admin edits CG → Frontend sends back: USER_INPUT (unchanged!)
3. Admin saves → AdminRegisterService preserves: USER_INPUT
4. Saved to admin file with: USER_INPUT

5. Admin refreshes page → Loads again
6. Merge happens:
   User Network: USER_INPUT (original CG value)
   Admin Local: USER_INPUT (edited CG value)

7. UniversalMergeEngine:
   - Both are USER_INPUT (same status, same priority)
   - Falls through to DEFAULT_FALLBACK
   - Returns entry1 (user network) → ADMIN EDITS LOST! ❌
```

#### ✅ After Fix (WORKING):
```
1. Admin loads entries → Status: USER_INPUT
2. Admin edits CG → Backend determines new status
3. Admin saves → Backend sets: ADMIN_EDITED_[timestamp]
4. Saved to admin file with: ADMIN_EDITED_123456

5. Admin refreshes page → Loads again
6. Merge happens:
   User Network: USER_INPUT (original CG value)
   Admin Local: ADMIN_EDITED_123456 (edited CG value)

7. UniversalMergeEngine:
   - RULE 6: VERSIONED_BEATS_BASE
   - ADMIN_EDITED_123456 > USER_INPUT
   - Returns admin entry → ADMIN EDITS PRESERVED! ✅
```

---

## Complete Flow Verification

### Scenario 1: Admin Edits User Register

```
1. User creates 83 entries → Status: USER_INPUT
2. User syncs to network
3. Admin loads register → performAdminLoadMerge()
   - Merges user network with admin local
4. Admin edits 5 CG values → Backend: ADMIN_EDITED_123456
5. Admin saves → Written to admin file with ADMIN_EDITED_123456
6. Admin refreshes → Merge happens:
   - User network: USER_INPUT (original values)
   - Admin local: ADMIN_EDITED_123456 (edited values)
   - UniversalMergeEngine: ADMIN_EDITED_123456 wins
   - Result: Admin sees edited values ✅
7. User logs in next day → performUserLoginMerge()
   - Admin network: ADMIN_EDITED_123456
   - User local: USER_INPUT
   - UniversalMergeEngine: ADMIN_EDITED_123456 wins
   - Result: User sees admin's edited values ✅
```

### Scenario 2: User Edits Own Entry After Admin Edit

```
1. User creates entry → Status: USER_INPUT
2. User edits entry → Status: USER_EDITED_[timestamp]
3. Admin loads user register → Sees USER_EDITED_[ts]
4. Admin edits CG → Status: ADMIN_EDITED_[new_ts]
5. User loads register → Merge:
   - User local: USER_EDITED_[old_ts]
   - Admin network: ADMIN_EDITED_[new_ts]
   - UniversalMergeEngine: ADMIN_EDITED wins (newer timestamp)
   - Result: Admin override applied ✅
```

### Scenario 3: Protected Statuses

```
1. User starts session → Status: USER_IN_PROCESS
2. Admin loads user register → Sees USER_IN_PROCESS
3. Admin tries to edit → Status preserved: USER_IN_PROCESS
4. Merge: USER_IN_PROCESS always wins
   - Result: User work protected ✅

5. Entry has ADMIN_FINAL status
6. User tries to edit → Can't change ADMIN_FINAL
7. Admin loads → Status preserved: ADMIN_FINAL
   - Result: Admin lock preserved ✅
```

---

## Comparison: Before vs After Fix

| Feature | Check Register | User Register | Admin Register (Before) | Admin Register (After) |
|---------|---------------|---------------|------------------------|----------------------|
| **Team Save** | ✅ `createTeamEditedStatus()` | N/A | N/A | N/A |
| **User Save** | ✅ `createUserEditedStatus()` | ✅ `createUserEditedStatus()` | N/A | N/A |
| **Admin Save** | N/A | N/A | ❌ Preserves client status | ✅ `determineAdminSyncStatusForSave()` |
| **Merge Logic** | ✅ UniversalMergeEngine | ✅ UniversalMergeEngine | ✅ UniversalMergeEngine | ✅ UniversalMergeEngine |
| **Status Creation** | ✅ Automatic | ✅ Automatic | ❌ Missing | ✅ Automatic |
| **Admin Edits Persist** | N/A | N/A | ❌ **BROKEN** | ✅ **FIXED** |

---

## Summary

### ✅ Fix Applied Successfully

- **AdminRegisterService** now properly sets `ADMIN_EDITED_[timestamp]` when admin saves entries
- **UserRegisterService** already correctly sets `USER_EDITED_[timestamp]` when users edit
- **CheckRegisterService** already correctly sets `TEAM_EDITED_[timestamp]` when team edits
- **UniversalMergeEngine** rules properly handle all status conflicts
- **Merge preservation** works correctly - statuses are never lost during merges

### What Changed

- **File**: `src/main/java/com/ctgraphdep/register/service/AdminRegisterService.java`
- **Lines Modified**: 309-335
- **New Method**: `determineAdminSyncStatusForSave()` (lines 758-792)

### Expected Behavior

Now when admin edits user register entries (like CG values) and saves:
1. Backend automatically creates `ADMIN_EDITED_[timestamp]`
2. When admin or user reloads, merge engine recognizes admin's newer edits
3. Admin changes are preserved across page refreshes
4. User sees admin's changes on next login
5. Protected statuses (`ADMIN_FINAL`, `USER_IN_PROCESS`) remain protected

**The issue where admin CG edits were being lost is now FIXED!** 🎯

---

## Related Files

- `UniversalMergeEngine.java` - Core merge logic with 9 rules
- `MergingStatusConstants.java` - Status definitions and timestamp creation
- `AdminRegisterService.java` - Admin register operations (FIXED)
- `UserRegisterService.java` - User register operations (Already correct)
- `CheckRegisterService.java` - Check register operations (Already correct)
- `RegisterMergeService.java` - Merge orchestration for register entries
- `RegisterWrapperFactory.java` - Wrapper creation for merge engine

---

## UI Status Display

### Status Display Helper Methods

**File**: `RegisterEntry.java` - Lines 70-139

Two helper methods provide user-friendly status displays in the UI:

#### 1. `getStatusDisplay()` - User-Friendly Labels

Converts technical status codes to readable text:

| Raw Status | Display Text |
|------------|--------------|
| `USER_INPUT` | In Process |
| `USER_EDITED_[timestamp]` | User Edited |
| `ADMIN_EDITED_[timestamp]` | Admin Edited |
| `TEAM_EDITED_[timestamp]` | Team Edited |
| `ADMIN_INPUT` | Admin Created |
| `TEAM_INPUT` | Team Created |
| `USER_IN_PROCESS` | Working |
| `ADMIN_FINAL` | Admin Final |
| `TEAM_FINAL` | Team Final |
| `USER_DELETED_[timestamp]` | Deleted |
| `ADMIN_DELETED_[timestamp]` | Deleted |
| `TEAM_DELETED_[timestamp]` | Deleted |
| `null` or unknown | Unknown |

```java
public String getStatusDisplay() {
    if (adminSync == null) return "Unknown";

    // Timestamped edits
    if (MergingStatusConstants.isUserEditedStatus(adminSync)) return "User Edited";
    if (MergingStatusConstants.isAdminEditedStatus(adminSync)) return "Admin Edited";
    if (MergingStatusConstants.isTeamEditedStatus(adminSync)) return "Team Edited";

    // Deletions
    if (MergingStatusConstants.isDeletedStatus(adminSync)) return "Deleted";

    // Base statuses
    return switch (adminSync) {
        case USER_INPUT -> "In Process";
        case ADMIN_INPUT -> "Admin Created";
        case TEAM_INPUT -> "Team Created";
        case USER_IN_PROCESS -> "Working";
        case ADMIN_FINAL -> "Admin Final";
        case TEAM_FINAL -> "Team Final";
        default -> adminSync; // Fallback
    };
}
```

#### 2. `getStatusBadgeClass()` - Bootstrap CSS Classes

Returns appropriate Bootstrap badge color class:

| Status Type | CSS Class | Color |
|-------------|-----------|-------|
| `ADMIN_EDITED_*` | `bg-primary` | 🔵 Blue |
| `ADMIN_INPUT`, `ADMIN_FINAL` | `bg-primary` | 🔵 Blue |
| `TEAM_EDITED_*` | `bg-info` | 🔵 Cyan |
| `TEAM_INPUT`, `TEAM_FINAL` | `bg-info` | 🔵 Cyan |
| `USER_EDITED_*` | `bg-warning` | 🟡 Yellow |
| `USER_IN_PROCESS` | `bg-warning` | 🟡 Yellow |
| `USER_INPUT` | `bg-success` | 🟢 Green |
| `*_DELETED_*` | `bg-danger` | 🔴 Red |
| Unknown | `bg-secondary` | ⚫ Gray |

```java
public String getStatusBadgeClass() {
    if (adminSync == null) return "bg-secondary";

    // Timestamped edits - role-based colors
    if (MergingStatusConstants.isAdminEditedStatus(adminSync)) return "bg-primary";
    if (MergingStatusConstants.isTeamEditedStatus(adminSync)) return "bg-info";
    if (MergingStatusConstants.isUserEditedStatus(adminSync)) return "bg-warning";

    // Deletions
    if (MergingStatusConstants.isDeletedStatus(adminSync)) return "bg-danger";

    // Base statuses
    return switch (adminSync) {
        case USER_INPUT -> "bg-success";
        case ADMIN_INPUT, ADMIN_FINAL -> "bg-primary";
        case TEAM_INPUT, TEAM_FINAL -> "bg-info";
        case USER_IN_PROCESS -> "bg-warning";
        default -> "bg-secondary";
    };
}
```

### Template Usage

Both user and admin register templates use these helper methods:

**User Register** (`templates/user/register.html` - Lines 318-325):
```html
<span class="badge"
      th:text="${entry.statusDisplay}"
      th:classappend="${entry.statusBadgeClass}"
      th:title="${entry.adminSync}">
    In Process
</span>
```

**Admin Register** (`templates/admin/register.html` - Lines 249-256):
```html
<span class="badge"
      th:text="${entry.statusDisplay}"
      th:classappend="${entry.statusBadgeClass}"
      th:title="${entry.adminSync}">
    In Process
</span>
```

**Features**:
- ✅ Displays user-friendly text instead of raw status codes
- ✅ Color-coded by role (admin=blue, team=cyan, user=yellow/green)
- ✅ Tooltip shows raw technical status on hover
- ✅ Automatically handles all timestamped statuses
- ✅ Future-proof for new status types

---

## Additional Fixes Applied

### Fix 1: Change Detection for Admin Saves

**Problem**: When admin saved entries, ALL entries got `ADMIN_EDITED_[timestamp]`, even unchanged ones.

**Solution**: Added `hasEntryChanged()` method in `AdminRegisterService` (lines 808-834) that compares all editable fields to detect actual changes.

**Result**: Only entries that were actually modified by admin get the `ADMIN_EDITED_[timestamp]` status.

---

### Fix 2: Cache Staleness After Merge

**Problem**: After user login merge, cached data showed old values until manual page refresh.

**Solution**: Added explicit cache clear in `UserRegisterService.loadMonthEntries()` (lines 86-88) immediately after merge completes.

```java
if (!isCurrentMonth) {
    registerMergeService.performUserLoginMerge(username, userId, year, month);
    // Force cache reload to ensure fresh merged data
    registerCacheService.clearMonth(username, year, month);
}
```

**Result**: Users immediately see merged values (admin's edited CG values) without needing to refresh.

---

**Last Updated**: 2025-01-24
**Status**: ✅ FIXED - Admin register status handling, change detection, cache refresh, and UI display all working correctly
