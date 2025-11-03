# Merge Logic: Step-by-Step Explanation

**Date:** 2025-11-03
**Status:** ✅ Complete

---

## Overview

The merge system handles three entity types with different approval hierarchies:

| Entity Type | Participants | Approval Flow |
|-------------|-------------|---------------|
| **Worktime** | User ↔ Admin | User creates/edits → Admin reviews/approves |
| **Register** | User ↔ Admin | User creates/edits → Admin reviews/approves |
| **CheckRegister** | User ↔ Team Lead ↔ Admin | User creates → Team Lead reviews/edits ↔ User responds → Admin analyzes/finalizes |

---

## Part 1: Worktime & Register (User ↔ Admin Only)

### Step-by-Step Flow

#### **Step 1: User Creates Entry**
```
Action: User creates a new worktime/register entry
Location: User's local file (D:\serverlocalhome\dbj\user\...)
Status: USER_INPUT
File: worktime_john_2024_03.json
```

**Example Entry:**
```json
{
  "entryId": 1,
  "date": "2024-03-15",
  "hoursWorked": 8.0,
  "adminSync": "USER_INPUT"
}
```

---

#### **Step 2: User Edits Their Own Entry**
```
Action: User modifies their entry (before admin reviews it)
Location: User's local file
Status: USER_EDITED_1710515234567 (timestamp when edited)
```

**Updated Entry:**
// Changed from 8.0
```json
{
  "entryId": 1,
  "date": "2024-03-15",
  "hoursWorked": 8.5,  
  "adminSync": "USER_EDITED_1710515234567"
}
```

**Merge Rule Applied:**
- If user has multiple edits, **newest timestamp wins**
- `USER_EDITED_1710515234567` beats `USER_INPUT`

---

#### **Step 3: Data Syncs to Network**
```
Action: Automatic sync (every 30 minutes for sessions, 1 hour for register)
Location: Network path (\\THLHODY-PC\servernetworktest\CTTT\dbj\user\...)
Status: USER_EDITED_1710515234567 (preserved during sync)
```

**No merge conflict** - just copying user's local file to network.

---

#### **Step 4: Admin Loads User Data**
```
Action: Admin opens admin worktime/register page
Trigger: AdminRegisterService.loadUserDataForAdmin(username, year, month)
Merge: User Network File → Admin Local File

Flow:
1. Load user file from network: worktime_john_2024_03.json
2. Load admin's previous edits (if any) from admin local file
3. Merge using UniversalMergeEngine
4. Save merged result to admin local file
```

**Admin sees:**
```json
{
  "entryId": 1,
  "date": "2024-03-15",
  "hoursWorked": 8.5,
  "adminSync": "USER_EDITED_1710515234567"
}
```

---

#### **Step 5: Admin Edits Entry (First Time)**
```
Action: Admin modifies the entry
Location: Admin local file (D:\serverlocalhome\dbj\admin\...)
Status: ADMIN_EDITED_1710520000000 (timestamp when admin edited)
```

**Admin's Edit:**
// Admin corrects back to 8.0
```json
{
  "entryId": 1,
  "date": "2024-03-15",
  "hoursWorked": 8.0,  
  "adminSync": "ADMIN_EDITED_1710520000000"
}
```

**Merge Rule Applied:**
- `ADMIN_EDITED_1710520000000` (newer timestamp) beats `USER_EDITED_1710515234567`
- Admin's decision takes precedence

---

#### **Step 6: Admin Saves Changes**
```
Action: Admin clicks save
Flow:
1. Save to admin local file (D:\serverlocalhome\dbj\admin\...)
2. Sync to admin network file (\\network\dbj\admin\...)
3. Entry remains ADMIN_EDITED_1710520000000
```

**Admin network file now contains admin's decision.**

---

#### **Step 7: User Logs In (Merge Admin → User)**
```
Action: User logs in next day
Trigger: UserLoginMergeServiceImpl.performLoginMerges(username, role)
Calls: RegisterMergeService.performUserLoginMerge(username)
      WorktimeLoginMergeService.performUserWorktimeLoginMerge(username)

Merge Process:
1. Load admin network file: admin_registru_john_2024_03.json
2. Load user local file: registru_john_2024_03.json
3. Merge using UniversalMergeEngine
4. Save merged result to user local file
```

**Merge Logic:**
```
Admin Entry: ADMIN_EDITED_1710520000000 (hoursWorked: 8.0)
User Entry:  USER_EDITED_1710515234567  (hoursWorked: 8.5)

Comparison:
- Admin timestamp: 1710520000000
- User timestamp:  1710515234567
- Admin timestamp is NEWER → Admin wins

Result: hoursWorked = 8.0, status = ADMIN_EDITED_1710520000000
```

**User now sees admin's correction.**

---

#### **Step 8: User Edits Again (After Admin Review)**
```
Action: User disagrees with admin's correction, edits again
Location: User local file
Status: USER_EDITED_1710525000000 (new timestamp)
```

**User's New Edit:**
// User changes back to 8.5
```json
{
  "entryId": 1,
  "date": "2024-03-15",
  "hoursWorked": 8.5,  
  "adminSync": "USER_EDITED_1710525000000"
}
```

**Merge Rule:**
- `USER_EDITED_1710525000000` (newer) beats `ADMIN_EDITED_1710520000000`
- **User's newer edit wins!**

---

#### **Step 9: Admin Loads Again (Sees User's New Edit)**
```
Action: Admin opens worktime page again
Trigger: Admin load merge
Result: Admin sees user changed it back to 8.5
```

**Admin sees:**
```json
{
  "entryId": 1,
  "date": "2024-03-15",
  "hoursWorked": 8.5,
  "adminSync": "USER_EDITED_1710525000000"
}
```

**Back-and-forth continues...**

---

#### **Step 10: Admin Final Decision (Nuclear Option)**
```
Action: Admin decides to force finalize
Trigger: AdminRegisterService.confirmAllAdminChanges(username, year, month)
Status: ADMIN_FINAL (no timestamp)
```

**Admin's Final Entry:**
```json
{
  "entryId": 1,
  "date": "2024-03-15",
  "hoursWorked": 8.0,
  "adminSync": "ADMIN_FINAL"
}
```

**Merge Rule:**
- `ADMIN_FINAL` beats **EVERYTHING** (highest priority)
- User can no longer override this
- **IMPORTANT**: Frontend blocks user from editing ADMIN_FINAL entries (see Step 11)

---

#### **Step 11: User Tries to Edit (After ADMIN_FINAL) - BLOCKED BY FRONTEND**
```
Action: User attempts to double-click on ADMIN_FINAL entry
Frontend Check: StatusDisplayModule reads data-status-modifiable="false"
Result: Edit is BLOCKED - user cannot modify the field
```

**Frontend Protection:**
```javascript
// Status Display Module checks:
data-status-modifiable="false"  // Set by backend
data-status-final="true"

// InlineEditingModule blocks editing:
if (cell.classList.contains('status-locked')) {
    showError('Cannot Edit', 'Entry is locked by admin');
    return false;  // EDIT BLOCKED
}
```

**Visual Indicators User Sees:**
- 🔒 Lock icon in Status column
- No pencil edit icon on hover
- Tooltip: "This entry is finalized and cannot be edited"
- If user tries to double-click: Toast error message appears

**Result:** User **CANNOT** create `USER_EDITED_1710530000000` entry because the frontend prevents all editing actions on ADMIN_FINAL entries.

---

#### **Step 12: Hypothetical Scenario (If User Bypassed Frontend)**
```
Action: User logs in next time
Merge Process:
1. Load admin network file: ADMIN_FINAL (hoursWorked: 8.0)
2. Load user local file: USER_EDITED_1710530000000 (hoursWorked: 9.0)
3. UniversalMergeEngine comparison

Merge Logic:
- Admin status: ADMIN_FINAL
- User status: USER_EDITED_1710530000000
- Rule: ADMIN_FINAL beats ALL versioned edits
- Result: Admin's entry wins

Final Result: hoursWorked = 8.0, status = ADMIN_FINAL
```

**User's edit is overwritten. Admin decision is final.**

---

## Part 2: CheckRegister (User ↔ Team Lead ↔ Admin)

### Step-by-Step Flow

#### **Step 1: User Creates Register Entry**
```
Action: User creates worktime entry in normal register
Location: User local file (registru_john_2024_03.json)
Status: USER_INPUT
```

**Entry:**
```json
{
  "entryId": 1,
  "date": "2024-03-15",
  "orderId": "ORD-12345",
  "clientName": "ABC Corp",
  "hoursWorked": 8.5,
  "adminSync": "USER_INPUT"
}
```

---

#### **Step 2: Team Lead Opens Check Register**
```
Action: Team lead reviews user's register entries
Trigger: TeamCheckRegisterController loads user register
Location: Team lead sees user's entries from network
```

**Team lead reviews the entry and decides it needs correction.**

---

#### **Step 3: Team Lead Creates Check Entry**
```
Action: Team lead adds a check register entry
Location: Check register file (check_registru_john_2024_03.json)
Status: TEAM_INPUT
```

**Check Entry Created:**
// Links to user's entry
// Team lead changes 8.5 → 8.0
```json
{
  "checkEntryId": 101,
  "linkedRegistryEntryId": 1,  
  "date": "2024-03-15",
  "orderId": "ORD-12345",
  "approvedHours": 8.0, 
  "comments": "Overtime not approved for this client",
  "adminSync": "TEAM_INPUT"
}
```

---

#### **Step 4: Check Entry Syncs to Network**
```
Action: Automatic sync
Location: Network path (check_registru_john_2024_03.json)
Status: TEAM_INPUT preserved
```

---

#### **Step 5: User Logs In (Sees Team Lead's Review)**
```
Action: User logs in
Trigger: UserLoginMergeServiceImpl.performLoginMerges(username, role)
         (role contains ROLE_USER_CHECKING)
Calls: CheckRegisterService.performCheckRegisterLoginMerge(username)

Merge Process:
1. Load team check register from network
2. Load user's local check register (if exists)
3. Merge using UniversalMergeEngine
4. Save to user local
```

**User sees:**
// Team lead's correction
```json
{
  "checkEntryId": 101,
  "linkedRegistryEntryId": 1,
  "approvedHours": 8.0, 
  "comments": "Overtime not approved for this client",
  "adminSync": "TEAM_INPUT"
}
```

---

#### **Step 6: User Responds to Team Lead**
```
Action: User disagrees, edits the check entry
Location: User local check register file
Status: USER_EDITED_1710515000000
```

**User's Response:**
// User insists on 8.5
```json
{
  "checkEntryId": 101,
  "linkedRegistryEntryId": 1,
  "approvedHours": 8.5,  
  "comments": "This client requires detailed work, 8.5 hours is correct",
  "adminSync": "USER_EDITED_1710515000000"
}
```

**Syncs to network.**

---

#### **Step 7: Team Lead Loads Again (Sees User's Response)**
```
Action: Team lead opens check register again
Trigger: Team load merge
Merge: User network check register → Team local

Result: Team lead sees user's response with timestamp 1710515000000
```

**Team lead sees user's justification and newer timestamp wins.**

---

#### **Step 8: Team Lead Responds Back**
```
Action: Team lead edits again with newer timestamp
Status: TEAM_EDITED_1710520000000
```

**Team Lead's Response:**
// Team lead compromises
```json
{
  "checkEntryId": 101,
  "linkedRegistryEntryId": 1,
  "approvedHours": 8.25, 
  "comments": "Agreed to 8.25 hours based on your explanation",
  "adminSync": "TEAM_EDITED_1710520000000"
}
```

**Merge Rule:**
- `TEAM_EDITED_1710520000000` (newer) beats `USER_EDITED_1710515000000`
- Team lead's newer timestamp wins

---

#### **Step 9: User Logs In Again (Sees Compromise)**
```
Action: User logs in
Merge: Team network check register → User local check register
Result: User sees team lead's compromise (8.25 hours)
```

**User accepts compromise. No further edits.**

---

#### **Step 10: Admin Loads Check Register (Analysis Phase)**
```
Action: Admin reviews check register entries
Trigger: AdminCheckRegisterService.loadCheckRegisterData(username, year, month)
Merge: Team network check register → Admin local check register

Admin sees:
- User's original entry (8.5 hours)
- Team lead's check entry (8.25 hours approved)
- Back-and-forth discussion in comments
```

---

#### **Step 11: Admin Analyzes and Finalizes**

**Scenario A: Admin Agrees with Team Lead**
```
Action: Admin confirms team lead's decision
Status: ADMIN_FINAL
```

**Admin's Final Entry:**
// Confirms team lead's decision
```json
{
  "checkEntryId": 101,
  "linkedRegistryEntryId": 1,
  "approvedHours": 8.25, 
  "comments": "Approved at 8.25 hours. Decision is final.",
  "adminSync": "ADMIN_FINAL"
}
```

**Result:**
- `ADMIN_FINAL` beats all timestamps
- Neither user nor team lead can override
- Entry is locked

---

**Scenario B: Admin Finds Issue, Sends Back to Team Lead**
```
Action: Admin rejects team lead's decision
Status: ADMIN_EDITED_1710525000000 (with newer timestamp)
```

**Admin's Edit:**
// Admin changes to 8.0
```json
{
  "checkEntryId": 101,
  "linkedRegistryEntryId": 1,
  "approvedHours": 8.0, 
  "comments": "Company policy: no partial hours. Change to 8.0 or 9.0.",
  "adminSync": "ADMIN_EDITED_1710525000000"
}
```

**Merge Rule:**
- `ADMIN_EDITED_1710525000000` (newer) beats `TEAM_EDITED_1710520000000`
- Admin's timestamp is newer → Admin wins
- BUT: Status is `ADMIN_EDITED`, not `ADMIN_FINAL`
- Team lead can still respond

---

#### **Step 12: Team Lead Sees Admin's Feedback**
```
Action: Team lead loads check register
Merge: Admin network → Team local
Result: Team lead sees admin's correction and policy note
```

**Team lead updates:**
// Team lead accepts admin's policy
```json
{
  "checkEntryId": 101,
  "linkedRegistryEntryId": 1,
  "approvedHours": 8.0, 
  "comments": "Adjusted to 8.0 per company policy",
  "adminSync": "TEAM_EDITED_1710530000000"
}
```

**Syncs to network for admin's next review.**

---

#### **Step 13: Admin Reviews Again and Finalizes**
```
Action: Admin loads, sees team lead followed policy
Trigger: AdminCheckRegisterService.confirmAllAdminChanges(...)
Status: ADMIN_FINAL
```

**Final Entry:**
```json
{
  "checkEntryId": 101,
  "linkedRegistryEntryId": 1,
  "approvedHours": 8.0,
  "comments": "Final approval at 8.0 hours.",
  "adminSync": "ADMIN_FINAL"
}
```

**Process complete. Entry is locked.**

---

## Part 3: Special Case - USER_IN_PROCESS (Worktime Only)

### Step-by-Step Flow

#### **Step 1: User Starts Work Session**
```
Action: User clicks "Start Work" button
Trigger: SessionService.startSession(username)
Location: session_john_2024.json
Status: USER_IN_PROCESS
```

**Active Session Entry:**
```json
{
  "sessionId": 1,
  "username": "john",
  "startTime": "2024-03-15T08:00:00",
  "endTime": null,
  "status": "active",
  "adminSync": "USER_IN_PROCESS"
}
```

---

#### **Step 2: Admin Tries to Edit Active Session**
```
Action: Admin opens worktime page, sees user's active session
Trigger: Admin tries to modify user's active session
```

**Merge Rule Applied:**
- `USER_IN_PROCESS` is a **protected state**
- Admin cannot override this status
- Merge engine blocks admin edits
- **Only user can close their own session**

**Admin sees but cannot modify:**
// Protected
```json
{
  "sessionId": 1,
  "username": "john",
  "startTime": "2024-03-15T08:00:00",
  "endTime": null,
  "status": "active",
  "adminSync": "USER_IN_PROCESS" 
}
```

---

#### **Step 3: User Ends Session**
```
Action: User clicks "Stop Work" button
Trigger: SessionService.stopSession(username)
Status: USER_INPUT (no longer protected)
```

**Completed Session:**
```json
{
  "sessionId": 1,
  "username": "john",
  "startTime": "2024-03-15T08:00:00",
  "endTime": "2024-03-15T17:30:00",
  "hoursWorked": 8.5,
  "status": "completed",
  "adminSync": "USER_INPUT"
}
```

**Now admin can edit** (follows normal USER ↔ ADMIN flow from Part 1).

---

## Part 4: Merge Engine Priority Rules

### Priority Hierarchy (Highest to Lowest)

```
Level 4: FINAL STATES (Highest Priority)
├── ADMIN_FINAL          → Beats everything
└── TEAM_FINAL           → Beats all except ADMIN_FINAL

Level 3: VERSIONED EDITS (Timestamp Comparison)
├── ADMIN_EDITED_[ts]    → If timestamps equal, admin wins
├── TEAM_EDITED_[ts]     → If timestamps equal, team wins over user
└── USER_EDITED_[ts]     → Newer timestamp wins

Level 2: PROTECTED STATES (Special Rules)
└── USER_IN_PROCESS      → Cannot be overridden by admin

Level 1: BASE STATES (Lowest Priority)
├── ADMIN_INPUT          → Initial admin creation
├── TEAM_INPUT           → Initial team creation
└── USER_INPUT           → Initial user creation
```

---

### Merge Scenarios

#### **Scenario 1: Admin Final vs User Edit**
```
Entry 1: ADMIN_FINAL (hoursWorked: 8.0)
Entry 2: USER_EDITED_1710999999999 (hoursWorked: 9.0)

Merge Result: Entry 1 wins
Reason: ADMIN_FINAL (Level 4) beats USER_EDITED (Level 3)
```

---

#### **Scenario 2: Team Final vs Admin Edit**
```
Entry 1: TEAM_FINAL (hoursWorked: 8.0)
Entry 2: ADMIN_EDITED_1710999999999 (hoursWorked: 9.0)

Merge Result: Entry 1 wins (TEAM_FINAL is Level 4)
To override: Admin must use ADMIN_FINAL
```

---

#### **Scenario 3: Same Timestamp, Different Roles**
```
Entry 1: USER_EDITED_1710500000000 (hoursWorked: 8.0)
Entry 2: ADMIN_EDITED_1710500000000 (hoursWorked: 9.0)

Merge Result: Entry 2 wins
Reason: Timestamps equal → Admin role wins
```

---

#### **Scenario 4: Newer User vs Older Admin**
```
Entry 1: ADMIN_EDITED_1710500000000 (hoursWorked: 8.0)
Entry 2: USER_EDITED_1710600000000 (hoursWorked: 9.0)

Merge Result: Entry 2 wins
Reason: User timestamp is newer (1710600000000 > 1710500000000)
```

---

#### **Scenario 5: User In Process vs Admin Edit**
```
Entry 1: USER_IN_PROCESS (session active)
Entry 2: ADMIN_EDITED_1710999999999 (admin tries to modify)

Merge Result: Entry 1 wins
Reason: USER_IN_PROCESS (Level 2) is protected state
Admin must wait for user to close session
```

---

## Summary Table

| Entity | Participants | Back-and-Forth | Finalization | File Locations |
|--------|--------------|----------------|--------------|----------------|
| **Worktime** | User ↔ Admin | User edits → Admin edits → User edits... | Admin uses ADMIN_FINAL | `dbj/user/userworktime/` <br> `dbj/admin/adminworktime/` |
| **Register** | User ↔ Admin | User edits → Admin edits → User edits... | Admin uses ADMIN_FINAL | `dbj/user/userregister/` <br> `dbj/admin/adminregister/` |
| **CheckRegister** | User ↔ Team ↔ Admin | User creates → Team reviews ↔ User responds → Admin finalizes or sends back | Admin uses ADMIN_FINAL over TEAM_FINAL | `dbj/user/checkregister/` <br> `dbj/admin/checkregister/` |

**Key Takeaway:** The system uses **timestamps** for conflict resolution in back-and-forth scenarios, with **role-based precedence** when timestamps are equal, and **FINAL states** to lock decisions permanently.

---

## Frontend Protection Against Editing Locked Entries

### How Frontend Blocks ADMIN_FINAL and TEAM_FINAL Edits

The frontend implements **multi-layer protection** to prevent users from editing locked entries:

#### **Layer 1: Backend Sets Modifiability Flag**
```java
// Backend: WorktimeStatusInfo or similar
//statusInfo.setModifiable(false);  // For ADMIN_FINAL/TEAM_FINAL
//statusInfo.setFinal(true);
```

#### **Layer 2: Template Receives Status Attributes**
```html
<!-- time-management-fragment.html -->
<tr class="worktime-entry"
    th:attr="data-status-modifiable=${record.statusInfo?.isModifiable},
             data-status-final=${record.statusInfo?.isFinal}">
```

**Result:** `<tr data-status-modifiable="false" data-status-final="true">`

#### **Layer 3: JavaScript Reads Attributes and Locks Cells**
```javascript
// status-display-module.js
const statusModifiable = row.dataset.statusModifiable === 'true';
const statusFinal = row.dataset.statusFinal === 'true';

if (!statusModifiable) {
    cell.classList.add('status-locked');
    cell.setAttribute('title', 'Entry is finalized and cannot be edited');

    if (statusFinal) {
        row.classList.add('status-final');  // Visual styling
    }
}
```

#### **Layer 4: Edit Prevention on Double-Click**
```javascript
// inline-editing-module.js
function canEditCell(cell) {
    // Check if cell is locked by status
    if (cell.classList.contains('status-locked')) {
        showError('Cannot Edit', 'This field cannot be edited due to its current status');
        return false;  // BLOCKS EDITING
    }
    return true;
}
```

### Visual Indicators for Users

When an entry has `ADMIN_FINAL` or `TEAM_FINAL` status:

1. **🔒 Lock Icon** appears in the Status column
2. **No Pencil Icon** shows on hover over editable cells
3. **Tooltip Message**: "This entry is finalized and cannot be edited"
4. **Error Toast**: If user double-clicks, shows error: "Cannot Edit - Entry is locked by admin"
5. **Row Styling**: Row may have distinct background color indicating locked status

### Code References

**Template:** `src/main/resources/templates/user/fragments/time-management-fragment.html:273-279`
**Status Module:** `src/main/resources/static/js/tm/status-display-module.js:191-223`
**Edit Module:** `src/main/resources/static/js/tm/inline-editing-module.js:163-183`

### Important Note

**Frontend protection is the FIRST line of defense.** The backend merge engine is the SECOND line. Even if a user bypasses the frontend (e.g., using browser dev tools to manipulate the DOM), the merge engine will still reject their edits on the next login merge, restoring the ADMIN_FINAL or TEAM_FINAL version.