
# TIME OFF TYPE IMPROVEMENT — AI LOGIC REFERENCE

## Overview

This document defines how the **time management system** should handle all *Time Off Types* for both backend (`Java`) and frontend (`HTML/JS/CSS`) logic.  

It serves as a **reference and directive for the AI assistant** responsible for maintaining, refactoring, and fixing source code across modules such as:
- `CalculateWorkHoursUtil`
- `CalculationService`
- `TimeInputModule`
- `time-management-core.js`
- related UI components (Add Time Off modal, Work Table view)

The AI assistant should ensure:
- Functional consistency between backend and frontend.  
- Correct automatic field selection for each time-off type.  
- Accurate overtime calculations and display updates.  
- Stable UI logic for user/admin interactions.  

---

## Time Off Codes Reference

| Code | Label (RO) | English Meaning | Rules Summary |
|------|-------------|-----------------|----------------|
| `SN` | Sărbătoare Națională | National Holiday | Admin only; no work time; fixed non-editable |
| `CO` | Concediu de odihnă | Paid Leave / Vacation | Users can work overtime; correct as is |
| `CM` | Concediu medical | Medical Leave | Requires medical proof; users can work overtime; no form required |
| `W`  | Weekend | Weekend | Auto-created when user works on weekend; work counts as overtime |
| `CR` | Concediu Recuperare | Recovery Leave (Paid from Overtime) | Auto-select field 3 “Concediu fără plată / Învoire” + “cu recuperare”; users cannot work |
| `CN` | Concediu Neplătit | Unpaid Leave | Auto-select field 3 “Concediu fără plată / Învoire” + “fără recuperare”; users cannot work |
| `CE` | Concediu pentru evenimente speciale | Event Leave (Marriage/Birth/Death) | Auto-select field 2; user must add reason; user can work overtime |
| `D`  | Delegație | Delegation / Business Trip | Normal work day; no field required; cannot overlap with other time-off |
| `ZS` | Zi Scurtă | Short Day (from Overtime) | Workday shorter than schedule; difference covered from overtime |

---

## General Behavior Logic

### Auto Field Selection (Frontend)

When the user presses **“Add Time Off”**, the system must automatically pre-select the correct field based on the chosen time-off type.

**AI Tasks:**
- Ensure auto-selection logic in `Add Time Off` modal is consistent with mapping below.

**Mapping Logic:**

```pseudo
IF timeOffType == "CO" THEN selectField(1)
IF timeOffType == "CM" THEN selectField(2)
IF timeOffType == "CE" THEN selectField(2)
IF timeOffType == "CR" THEN selectField(3, "cu recuperare")
IF timeOffType == "CN" THEN selectField(3, "fără recuperare")
IF timeOffType == "D"  THEN noFieldRequired()
```

---

## Logic by Type

### 1. `CO` — Concediu de odihnă (Paid Leave)
- Current logic correct.  
- Users can add work time; all such work counts as **overtime**.  
- No code changes required.

**AI Tasks:**
- Maintain logic integrity for CO.
- Ensure UI permits overtime entry on CO days.

---

### 2. `CM` — Concediu medical (Medical Leave)
- Treated similar to `CO`.  
- Requires no form if doctor proof exists.  
- Work is allowed and counts as **overtime**.

**AI Tasks:**
- Verify backend correctly skips form validation for CM.  
- Ensure frontend handles CM identical to CO for overtime logic.

---

### 3. `CE` — Concediu pentru evenimente speciale (Event Leave)
- Auto-selects **Field 2**.  
- Requires user input for reason (text input field).  
- No time or overtime deduction.  
- User can work overtime like CO/CM.

**Pseudo-code:**

```pseudo
IF timeOffType == "CE"
    selectField(2)
    enableReasonInput(true)
    allowWorkEntries(true)
    noOvertimeDeduction()
```

**AI Tasks:**
- Ensure Add Time Off modal auto-selects Field 2.  
- Validate text input required when CE selected.  
- Keep overtime logic same as CO/CM.

---

### 4. `CR` — Concediu Recuperare (Recovery Leave, Paid from Overtime)
- Users **cannot work** on these days.  
- Selecting CR auto-fills:
  - Field 3 “Concediu fără plată / Învoire”
  - Recovery type “cu recuperare”  
- Each CR day deducts 8 hours (or schedule hours) from overtime balance.

**Pseudo-code:**

```pseudo
IF timeOffType == "CR"
    selectField(3, "cu recuperare")
    disableWorkEntry()
    lunchCheck = true
    actualTime = scheduleHours
    overtimeRemaining = totalOvertime - scheduleHours
    updateDisplay(overtimeRemaining)
```

**AI Tasks:**
- Fix modal logic to pre-select field 3 + recovery checkbox.  
- Ensure backend deducts hours from overtime correctly.  
- Fix missing `TimeInputModule` initialization if absent.  
- Synchronize UI and backend overtime after CR entries.

---

### 5. `CN` — Concediu Neplătit (Unpaid Leave)
- Similar to CR, but **no overtime deduction**.  
- Users cannot work on CN days.  

**Pseudo-code:**

```pseudo
IF timeOffType == "CN"
    selectField(3, "fără recuperare")
    disableWorkEntry()
    noOvertimeDeduction()
```

**AI Tasks:**
- Implement auto-selection for CN.  
- Verify CR and CN differentiation in backend.  
- Ensure HR reporting distinguishes CN as unpaid leave.

---

### 6. `ZS` — Zi Scurtă (Short Day)
- Shorter than full schedule; remaining time is subtracted from overtime.  
- UI should display `ZS-<missingHours>`  
  (e.g., `ZS-6` for 6 missing hours).  
- When calculating overtime:
  ```
  schedule + lunch = totalExpectedMinutes (e.g., 480 + 30 = 510)
  missing = totalExpectedMinutes - rawWorkMinutes
  missingHours = roundUp(missing / 60)
  overtimeRemaining = totalOvertime - missingHours
  ```

**Pseudo-code:**

```pseudo
IF timeOffType == "ZS"
    expected = scheduleMinutes + lunchMinutes
    missing = expected - rawWorkedMinutes
    missingHours = roundUp(missing / 60)
    display = "ZS-" + missingHours
    overtimeRemaining = totalOvertime - missingHours
    updateDisplay(overtimeRemaining)
```

**AI Tasks:**
- Update UI display logic for ZS (show missing hours).  
- Ensure `CalculateWorkHoursUtil` applies rounding correctly.  
- Validate automatic lunch deduction if under 4 hours.  
- Fix backend overtime calculation consistency.

---

### 7. `D` — Delegație (Delegation / Business Trip)
- Normal working day.  
- No field required.  
- Users cannot take other time-off types simultaneously.  
- Treated like `SN` (no editable work time).

**AI Tasks:**
- Validate D has no overlapping time-off entries.  
- Ensure form auto-skips fields and hides irrelevant inputs.

---

## Overtime and Totals Calculation

### Core Formula

The system must maintain consistent overtime totals:

```pseudo
totalOvertime = sum(all overtime from all workdays)
totalCR = sum(all CR days * scheduleHours)
totalZS = sum(all ZS missingHours)

remainingOvertime = totalOvertime - (totalCR + totalZS)
```

**AI Tasks:**
- Ensure overtime recalculations trigger after each CR/ZS update.  
- Keep totals synchronized between UI and backend cache.  
- Make admin and user views consistent.

---

## Additional Notes for Implementation

- Lunch time = 30 minutes; included in schedule unless raw time < 4 hours.  
- Rounding rules:  
  - Work rounding: down to nearest full hour.  
  - Missing time (ZS): round **up** to nearest hour.  
- `adminSync` and `userSync` states must remain consistent after each update.  
- Maintain compatibility with `WorkTimeCalculationResultDTO`.

---

## Validation Scenarios

- User can work overtime on: `CO`, `CM`, `SN`, `W`, `CE`.  
- User **cannot work** on: `CR`, `CN`, `D`.  
- ZS must dynamically reduce overtime upon `End` button press.  
- All modal selections must match type logic above.  

---

## Final Directive

The AI assistant must:
1. **Refactor and fix logic** in both backend (Java) and frontend (JS/HTML/CSS) according to this specification.  
2. **Auto-create missing methods** where pseudo-code defines new behavior.  
3. **Synchronize overtime totals and UI displays** in all views.  
4. **Ensure correctness** between user input, admin display, and overtime calculations.  
