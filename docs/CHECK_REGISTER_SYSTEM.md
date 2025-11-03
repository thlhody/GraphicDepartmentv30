# Check Register System - Architecture & Flow Documentation

## Overview
The Check Register System is a time tracking and quality control system where users log their design check activities, which are then reviewed and approved by Team Leads (TL_CHECKING role).

---

## 1. Key Components

### 1.1 Roles & Permissions

| Role | Code | Permissions |
|------|------|-------------|
| **Checking User** | `CHECKING` | Can create/edit own check register entries |
| **User with Checking** | `USER_CHECKING` | Regular user + checking capabilities |
| **Team Lead Checking** | `TL_CHECKING` | Can review all check users + edit their check registers + manage check values |
| **Admin** | `ADMIN` | Full access |

### 1.2 Data Models

#### RegisterCheckEntry
```java
- entryId: Integer           // Unique identifier
- date: LocalDate            // Entry date
- omsId: String             // Order Management System ID
- productionId: String      // Production identifier
- designerName: String      // Designer who worked on it
- checkType: String         // Type of check (LAYOUT, GPT, PRODUCTION, etc.)
- articleNumbers: Integer   // Number of articles checked
- filesNumbers: Integer     // Number of files checked
- errorDescription: String  // Errors found (optional)
- approvalStatus: String    // APPROVED/PARTIALLY APPROVED/CORRECTION
- orderValue: Double        // Calculated value based on check type
- adminSync: String         // Status for merge logic (CheckingStatus enum)
```

#### MergingStatusConstants (Replaces CheckingStatus Enum)
```java
// Base statuses
USER_INPUT        // Initial state - user created entry
TEAM_INPUT        // Team lead created entry
ADMIN_INPUT       // Admin created entry

// Timestamped edit statuses
USER_EDITED_{timestamp}    // User edited existing entry
TEAM_EDITED_{timestamp}    // Team lead edited entry
ADMIN_EDITED_{timestamp}   // Admin edited entry

// Final approval statuses
TEAM_FINAL        // Team lead approved (final for user, editable by admin)
ADMIN_FINAL       // Admin final approval (immutable)

// Deletion statuses (tombstone system)
USER_DELETED_{timestamp}   // User marked for deletion
TEAM_DELETED_{timestamp}   // Team lead marked for deletion
ADMIN_DELETED_{timestamp}  // Admin marked for deletion

// Process statuses
USER_IN_PROCESS   // Active session (worktime only)
```

**Status Hierarchy**: USER_INPUT < USER_EDITED < TEAM_EDITED < TEAM_FINAL < ADMIN_FINAL

### 1.3 File Storage Structure

```
Network Path: \\grafubu\A_Registru graficieni\CTTT
Local Path: D:\serverlocalhome

dbj/
├── checkregister/
│   ├── user_check_register_{username}_{year}_{month}.json          // User's own entries
│   └── teamlead_check_register_{username}_{year}_{month}.json      // Team lead's edits for this user
└── checkvalues/
    └── user_check_values_{username}_{userId}.json                   // User-specific check type values
```

---

## 2. Check Values System

### 2.1 Purpose
Check values define how much each check type is worth (in units) for calculating work performance metrics.

### 2.2 Structure
```java
CheckValuesEntry:
- workUnitsPerHour: Double       // Target units/hour (default: 4.5)
- layoutValue: Double            // Value per layout (default: 1.0)
- kipstaLayoutValue: Double      // Value per Kipsta layout (default: 0.25)
- layoutChangesValue: Double     // Value per layout change (default: 0.25)
- gptArticlesValue: Double       // Value per GPT article (default: 0.1)
- productionValue: Double        // Value per production check (default: 0.1)
- reorderValue: Double           // Value per reorder (default: 0.1)
- sampleValue: Double            // Value per sample (default: 0.3)
- omsProductionValue: Double     // Value per OMS production (default: 0.1)
- kipstaProductionValue: Double  // Value per Kipsta production (default: 0.1)
```

### 2.3 Management
- **Who can edit**: `TL_CHECKING` role only
- **Per-user**: Each checking user has their own check values file
- **Auto-created**: If file doesn't exist, default values are created automatically
- **Cached**: Values are cached in `CheckValuesCacheManager` for performance

### 2.4 Calculation Logic
```javascript
// Example: LAYOUT with 10 articles
orderValue = 10 * 1.0 = 10.0

// Example: GPT with 5 articles and 3 files
orderValue = (5 * 0.1) + (3 * 0.1) = 0.8
```

---

## 3. Data Flow & Merge Logic

### 3.1 User Creates Entry

**Flow:**
1. User opens `/user/check-register`
2. Fills form with check details
3. Submits → `CheckRegisterController.saveEntry()`
4. Entry created with status: `CHECKING_INPUT`
5. Saved to: `user_check_register_{username}_{year}_{month}.json`
6. Cached in `RegisterCheckCacheService`

**File State:**
```json
{
  "entryId": 1,
  "date": "2025-01-15",
  "omsId": "1234/25/AB-GREEN",
  "checkType": "LAYOUT",
  "articleNumbers": 10,
  "filesNumbers": 5,
  "orderValue": 10.0,
  "adminSync": "CHECKING_INPUT"
}
```

### 3.2 Team Lead Reviews Entry

**Flow:**
1. Team Lead opens `/team/check-register`
2. Selects user from tabs
3. Clicks "Initialize" button → `TeamCheckRegisterController.initializeTeamCheckRegister()`
4. Copies user entries to team lead file: `teamlead_check_register_{username}_{year}_{month}.json`
5. All entries initially set to `CHECKING_INPUT`

**Flow (Edit):**
1. Team Lead clicks Edit on an entry
2. Modifies values (e.g., changes articleNumbers from 10 to 8)
3. Clicks "Update" → `TeamCheckRegisterController.updateEntry()`
4. Entry status changes to: `TL_EDITED`
5. Saved to: `teamlead_check_register_{username}_{year}_{month}.json`

**Team Lead File State:**
```json
{
  "entryId": 1,
  "date": "2025-01-15",
  "omsId": "1234/25/AB-GREEN",
  "checkType": "LAYOUT",
  "articleNumbers": 8,      // Changed from 10 to 8
  "filesNumbers": 5,
  "orderValue": 8.0,        // Recalculated
  "adminSync": "TL_EDITED"  // Status updated
}
```

### 3.3 User Login Merge

**When**: User logs in the next day

**Flow:**
1. User logs in → `UserLoginMergeServiceImpl.performUserLoginMerge()`
2. For checking roles, triggers: `CheckRegisterService.performCheckRegisterLoginMerge()`
3. Merge logic executes:
   - Reads `user_check_register_{username}_{year}_{month}.json` (local)
   - Reads `teamlead_check_register_{username}_{year}_{month}.json` (network)
   - Calls `CheckRegisterService.mergeEntries()` using `UniversalMergeEngine`

**Merge Rules** (via `UniversalMergeEngine`):
```
Priority (highest to lowest):
1. TL_EDITED > CHECKING_INPUT     // Team lead edits override user input
2. TL_CHECK_DONE > CHECKING_INPUT // Team lead approval overrides user input
3. TL_BLANK = DELETE              // Team lead marked for deletion
4. ADMIN_DONE > ALL               // Admin final approval (future)
```

**Result:**
- User file is overwritten with merged data
- Cache is cleared to force reload
- User now sees team lead's changes

**User File After Merge:**
```json
{
  "entryId": 1,
  "date": "2025-01-15",
  "omsId": "1234/25/AB-GREEN",
  "checkType": "LAYOUT",
  "articleNumbers": 8,      // Updated from team lead
  "filesNumbers": 5,
  "orderValue": 8.0,        // Updated from team lead
  "adminSync": "TL_EDITED"  // Status preserved
}
```

---

## 4. Controllers & Service Layer

### 4.1 CheckRegisterController (User)
**Path**: `/user/check-register`

**Key Methods:**
- `showCheckRegister()` - Display user's own check register
- `saveEntry()` - Create new entry (status: CHECKING_INPUT)
- `updateEntry()` - Update existing entry (only if status = CHECKING_INPUT)
- `deleteEntry()` - Delete entry (only if status = CHECKING_INPUT)
- `exportToExcel()` - Export entries to Excel

**Security:**
- Users can only access their own data
- Cannot edit/delete entries with status ≠ CHECKING_INPUT

### 4.2 TeamCheckRegisterController (Team Lead)
**Path**: `/team/check-register`

**Key Methods:**
- `showTeamCheckRegister()` - Display team check register with user tabs
- `initializeTeamCheckRegister()` - Copy user entries to team lead file (preserves status)
- `markAllEntriesAsChecked()` - Bulk approve all entries (status: TEAM_FINAL, skips ADMIN_FINAL only)
- `markSingleEntryAsTeamFinal()` - Mark individual entry as TEAM_FINAL (via badge clicking)
- `createEntry()` - Create new entry for user (status: TEAM_INPUT)
- `updateEntry()` - Edit user's entry (status: TEAM_EDITED_{timestamp})
- `markEntryForDeletion()` - Mark entry for deletion (status: TEAM_DELETED_{timestamp})

**Security:**
- Only `TL_CHECKING` and `ADMIN` roles
- Can access any checking user's data
- Direct network reads (no cache)

**Initialization Flow:**
- Always requires "Initialize" button click before viewing
- Uses `showContent` query parameter to control view state
- Creates empty team register even if user has no entries

### 4.3 CheckRegisterService
**Core service for all check register operations**

**Key Methods:**
- `loadMonthEntries()` - Load entries with role-based access control
- `loadTeamCheckRegister()` - Load team lead view (direct network read)
- `initializeTeamCheckRegister()` - Initialize team register from user data (preserves statuses)
- `saveUserEntry()` - Save user's own entry (via cache, handles USER_INPUT vs USER_EDITED)
- `updateTeamEntry()` - Update entry as team lead (direct write)
- `markAllEntriesAsChecked()` - Mark all entries as TEAM_FINAL (skips ADMIN_FINAL only)
- `markSingleEntryAsTeamFinal()` - Mark individual entry as TEAM_FINAL
- `performCheckRegisterLoginMerge()` - Merge team lead changes into user file
- `mergeEntries()` - Universal merge engine wrapper

**Status Logic Changes (October 2025):**
- Entry initialization preserves original status (uses `copyEntry()` not `copyEntryWithUserInput()`)
- User edits: New entries → USER_INPUT, existing entries → USER_EDITED_{timestamp}
- Mark All Checked: Only skips ADMIN_FINAL, marks all others as TEAM_FINAL
- Tombstone deletion: Entries marked with deletion status, never physically deleted

### 4.4 CheckValuesService
**Manages check type values for users**

**Key Methods:**
- `getUserCheckValues()` - Get or create user's check values
- `saveUserCheckValues()` - Save updated check values (TL_CHECKING only)
- `getAllCheckUsers()` - Get all users with checking roles
- `getAllCheckValues()` - Get check values for all checking users

---

## 5. Frontend Components

### 5.1 check-register.html (User View)
**Path**: `/user/check-register`

**Features:**
- Month/year selector
- Form to create/edit entries
- Table showing user's entries
- Inline edit/copy/delete buttons
- Search functionality (Ctrl+F)
- Export to Excel
- Metrics summary (efficiency, totals)

**JavaScript**: `check-register.js`
- `CheckRegisterFormHandler` - Form submission, validation, entry editing
- `CheckRegisterSummaryHandler` - Live metrics calculation
- `SearchHandler` - Client-side search functionality

### 5.2 team-check-register.html (Team Lead View)
**Path**: `/team/check-register`

**Features:**
- User tabs (all checking users)
- Initialize button (copies user → team lead file, always required)
- Mark All as Checked button (marks all entries as TEAM_FINAL except ADMIN_FINAL)
- Clickable status badges (click to mark individual entry as TEAM_FINAL)
- Form to create/edit entries for selected user
- Table showing user's entries with status column
- Sequential entry numbering (reverse order, newest on top)
- Inline edit/copy/delete buttons

**JavaScript**: Same `check-register.js` (with `IS_TEAM_VIEW = true`)

**Server Context Variables** (passed to JavaScript):
```javascript
const IS_TEAM_VIEW = true;
const SELECTED_USER = /*[[${selectedUser?.username}]]*/ null;
const SELECTED_USER_ID = /*[[${selectedUser?.userId}]]*/ null;
const CURRENT_YEAR = /*[[${currentYear}]]*/ null;
const CURRENT_MONTH = /*[[${currentMonth}]]*/ null;
```

**Badge Clicking Feature:**
- Status badges are clickable (except ADMIN_FINAL)
- Hover effect shows badge is clickable
- Click → confirmation dialog → mark as TEAM_FINAL
- Uses server context variables (not URL parameters) for form submission

### 5.3 check-register-fragment.html
**Shared fragment used by both views**

**Key differences based on `isTeamView` flag:**
- Form action URL prefix (`/user/check-register` vs `/team/check-register`)
- Hidden fields for username/userId (team view only)
- Status column visibility (always shown now)
- Entry numbering: Sequential display using `${stat.size - stat.index}`
- Clickable status badges (team view only, except ADMIN_FINAL)
- Edit restrictions removed (users can edit all entries, tombstone handles conflicts)

**Recent Changes (October 2025):**
- Fixed duplicate status columns (removed one)
- Fixed table header colspan (updated to 13)
- Fixed Thymeleaf syntax errors (literal substitution for confirm dialogs)
- Refactored complex T() expressions to use #strings.startsWith()
- Added clickable-badge CSS class for interactive status badges

---

## 6. Merge Engine Integration

### 6.1 UniversalMergeEngine
**Location**: `com.ctgraphdep.merge.engine.UniversalMergeEngine`

**Purpose**: Unified conflict resolution for all entity types

**Usage in Check Register:**
```java
GenericEntityWrapper<RegisterCheckEntry> userWrapper =
    CheckRegisterWrapperFactory.createWrapperSafe(userEntry);
GenericEntityWrapper<RegisterCheckEntry> teamWrapper =
    CheckRegisterWrapperFactory.createWrapperSafe(teamEntry);

GenericEntityWrapper<RegisterCheckEntry> resultWrapper =
    UniversalMergeEngine.merge(userWrapper, teamWrapper, EntityType.CHECK_REGISTER);

RegisterCheckEntry mergedEntry = resultWrapper.getEntity();
```

### 6.2 CheckRegisterWrapperFactory
**Location**: `com.ctgraphdep.register.util.CheckRegisterWrapperFactory`

**Purpose**: Adapter for RegisterCheckEntry to work with UniversalMergeEngine

**Methods:**
- `createWrapperSafe()` - Create wrapper, handle null entries
- `getEntryId()` - Extract entity ID
- `getStatus()` - Extract status for merge logic
- `getEntity()` - Extract RegisterCheckEntry

---

## 7. Current State Summary (Updated October 2025)

### What Works:
✅ User can create check register entries (status: USER_INPUT)
✅ User can edit entries (status: USER_EDITED_{timestamp})
✅ Team lead can initialize team check register (preserves statuses)
✅ Team lead can mark all entries as checked (TEAM_FINAL, skips ADMIN_FINAL only)
✅ Team lead can mark individual entries via badge clicking (TEAM_FINAL)
✅ Team lead update button works correctly (status: TEAM_EDITED_{timestamp})
✅ Check values system fully functional
✅ Per-user check values cached and used for calculations
✅ Merge engine correctly resolves conflicts with status hierarchy
✅ Login merge triggers successfully
✅ Status preservation during initialization
✅ Tombstone deletion system (entries marked, not removed)
✅ Sequential entry numbering (reverse order, newest on top)
✅ Edit restrictions removed (users can edit all, merge handles conflicts)

### What's Missing:
❌ Admin approval flow not fully implemented (ADMIN_FINAL status exists but no UI)
❌ Team lead cannot add check-register entries for themselves
❌ No merge between team lead's own check register and their team review files
❌ No audit trail/history for status changes

---

## 8. ~~Issue Analysis: Team Lead Update Button Not Working~~ ✅ RESOLVED (October 2025)

### ~~The Problem~~ (FIXED):
~~When team lead updates an entry, page redirected to TL's own check-register page instead of updating selected user's entry.~~

### Root Cause Analysis:

**Form submission flow:**
```html
<!-- Fragment: check-register-fragment.html line 9 -->
<form id="checkRegisterForm" th:action="${urlPrefix + '/entry'}" method="post">
```

When editing an entry, JavaScript modifies the form action to include the entry ID:
```javascript
// check-register.js line 304
this.form.action = `/user/check-register/entry/${entryId}`;
```

**Problem**: The JavaScript always uses `/user/check-register/entry/${entryId}` regardless of whether it's team view or user view!

**Team Lead Controller Mapping:**
```java
// TeamCheckRegisterController.java line 438
@PostMapping("/entry/{entryId}")
public String updateEntry(@PathVariable Integer entryId, ...)
```

**Expected URL for team view**: `/team/check-register/entry/{entryId}`
**Actual URL being called**: `/user/check-register/entry/{entryId}`

This causes:
1. Request goes to wrong controller (UserCheckRegisterController instead of TeamCheckRegisterController)
2. User controller doesn't have username/userId in path parameters
3. Falls back to current authenticated user (the team lead)
4. Tries to update team lead's own register instead of selected user's register
5. Redirect URL points back to team lead's own register

### The Fix:
The JavaScript in `check-register.js` needs to be aware of the `urlPrefix` and use it dynamically:

```javascript
// Current (WRONG):
this.form.action = `/user/check-register/entry/${entryId}`;

// Should be (CORRECT):
this.form.action = `${this.defaultUrl}/${entryId}`;
// where defaultUrl is set based on context (user vs team view)
```

---

## 9. Recommendations for Implementation

### Phase 1: Fix Critical Bug (Immediate)
1. ✅ Fix team lead update button JavaScript issue
2. ✅ Test team lead editing flow end-to-end
3. ✅ Verify merge works after team lead edits

### Phase 2: Team Lead Self-Registration (Next)
1. Add TL_CHECKING check register page for their own entries
2. Separate routes: `/team/my-check-register` vs `/team/check-register` (review)
3. Allow TL to add entries for themselves
4. Merge TL's own entries at login (same merge flow)

### Phase 3: Admin Approval Flow (Future)
1. Add admin check register review page
2. Implement ADMIN_DONE status
3. Add admin override capability
4. Admin can approve/reject team lead decisions

### Phase 4: Enhancements (Optional)
1. Add history/audit trail for edits
2. Add comments/notes feature for team leads
3. Add bulk edit capabilities
4. Add approval workflow notifications

---

## 10. Development Approach Recommendation

### Backend First Approach (Recommended)

**Reasoning:**
1. **Service layer is solid** - CheckRegisterService has good architecture
2. **Controllers need minimal changes** - Mostly routing fixes
3. **Merge logic is complete** - UniversalMergeEngine handles everything
4. **Database operations work** - File I/O layer is tested

**Steps:**
1. Fix team lead controller routing (5 min)
2. Test team lead update flow (10 min)
3. Add TL own register endpoints (30 min)
4. Test merge for TL own entries (20 min)
5. **Then** fix frontend JavaScript (15 min)
6. **Finally** add UI for TL own register (30 min)

**Why this order:**
- Backend changes are isolated and testable
- Frontend depends on correct backend routing
- Can test with Postman/curl before touching UI
- Reduces debugging complexity

### Alternative: Frontend First Approach (Not Recommended)

**Why not:**
- Frontend can't be properly tested without correct backend routes
- JavaScript fixes won't work if backend routing is wrong
- More back-and-forth debugging
- Higher risk of breaking existing functionality

---

## 11. Testing Strategy

### Manual Testing Checklist:
- [ ] User creates entry → status CHECKING_INPUT
- [ ] User can edit own entry with CHECKING_INPUT status
- [ ] User cannot edit entry with TL_EDITED status
- [ ] Team lead initializes user register
- [ ] Team lead edits user entry → status changes to TL_EDITED
- [ ] Team lead marks entry for deletion → status TL_BLANK
- [ ] Team lead marks all as checked → status TL_CHECK_DONE
- [ ] User logs in → merge triggers
- [ ] User sees team lead's changes after login
- [ ] Check values are loaded correctly
- [ ] Order value calculated correctly based on check type

### Integration Testing:
- [ ] Test with multiple users simultaneously
- [ ] Test network fallback (disconnect network share)
- [ ] Test with different roles (CHECKING, USER_CHECKING, TL_CHECKING)
- [ ] Test merge with conflicting changes
- [ ] Test cache invalidation after merge

---

## 12. File Paths Reference

```
Controllers:
- src/main/java/com/ctgraphdep/controller/user/CheckRegisterController.java
- src/main/java/com/ctgraphdep/controller/team/TeamCheckRegisterController.java

Services:
- src/main/java/com/ctgraphdep/register/service/CheckRegisterService.java
- src/main/java/com/ctgraphdep/register/service/CheckValuesService.java

Models:
- src/main/java/com/ctgraphdep/model/RegisterCheckEntry.java
- src/main/java/com/ctgraphdep/model/CheckValuesEntry.java
- src/main/java/com/ctgraphdep/enums/CheckingStatus.java

Frontend:
- src/main/resources/templates/user/check-register.html
- src/main/resources/templates/user/team-check-register.html
- src/main/resources/templates/user/fragments/check-register-fragment.html
- src/main/resources/static/js/check-register.js

Merge Engine:
- src/main/java/com/ctgraphdep/merge/engine/UniversalMergeEngine.java
- src/main/java/com/ctgraphdep/register/util/CheckRegisterWrapperFactory.java
```

---

## 13. Recent Updates (October 2025)

### Major Changes Implemented:

#### Status System Overhaul
- Migrated from `CheckingStatus` enum to `MergingStatusConstants` with timestamp support
- Implemented status hierarchy: USER_INPUT < USER_EDITED < TEAM_EDITED < TEAM_FINAL < ADMIN_FINAL
- Added tombstone deletion system (entries marked, never physically deleted)

#### Team Lead Functionality Enhancements
1. **Initialization Flow**: Always requires "Initialize" button press before viewing
2. **Status Preservation**: Initialization preserves original statuses (not overwriting to USER_INPUT)
3. **Mark All Checked**: Only skips ADMIN_FINAL entries, marks all others as TEAM_FINAL
4. **Individual Badge Clicking**: Click status badges to mark individual entries as TEAM_FINAL
5. **Fixed Update Button**: Correctly routes to team controller, sets TEAM_EDITED_{timestamp}

#### Frontend Improvements
1. **Sequential Numbering**: Entries numbered 1,2,3... (reverse order, newest on top)
2. **Edit Restrictions Removed**: Users can edit all entries, merge engine handles conflicts
3. **Clickable Badges**: Interactive status badges with hover effects (team view only)
4. **Thymeleaf Refactoring**: Fixed syntax errors, simplified complex expressions
5. **Server Context Variables**: Pass data from Thymeleaf to JavaScript (not URL parameters)

#### Service Layer Updates
1. **saveUserEntry()**: Distinguishes new entries (USER_INPUT) from updates (USER_EDITED_{timestamp})
2. **markSingleEntryAsTeamFinal()**: New method for individual entry approval via badge clicking
3. **markAllEntriesAsChecked()**: Updated logic to only skip ADMIN_FINAL entries

### Files Modified:
- **Backend**: `CheckRegisterService.java`, `TeamCheckRegisterController.java`
- **Frontend**: `check-register-fragment.html`, `check-register.js`, `team-check-register.html`

### Technical Patterns Established:
1. **Thymeleaf to JavaScript**: `const VALUE = /*[[${serverVariable}]]*/ null;`
2. **Context-Aware URLs**: Use `IS_TEAM_VIEW` flag to determine correct endpoint
3. **Status Transitions**: New entry → USER_INPUT, edit → USER_EDITED_{timestamp}
4. **Tombstone System**: Mark entries as deleted with timestamp, never remove from file

---

**Document Version**: 2.0
**Last Updated**: 2025-10-14
**Author**: System Analysis

**Date:** 2025-11-03
**Status:** ✅ Complete
