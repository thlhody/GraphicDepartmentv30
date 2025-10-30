# Refactoring Plan: WorktimeDisplayService.java

## Current State Analysis

**File Size**: 1,274 lines
**Location**: `src/main/java/com/ctgraphdep/worktime/display/WorktimeDisplayService.java`

### Problems Identified

#### 1. **Multiple Responsibilities (Violates Single Responsibility Principle)**
The class handles at least 8 different concerns:
- DTO creation and conversion
- Summary calculations (regular time, overtime, totals)
- Time-off day counting
- Work day counting
- CR/ZS deduction calculations
- Entry detail response building (API responses)
- Display data preparation (for Thymeleaf views)
- Model preparation (Spring MVC)
- Input validation
- Data format conversion

#### 2. **Code Duplication (Violates DRY Principle)**

**A. Time-Off Type Counting (4 locations)**
- Lines 303-307: DTO-based counting (snDays++, coDays++, cmDays++)
- Lines 608-612: Entry-based counting
- Lines 766-783: Detailed counting with logging
- Lines 469-473: Stream-based statistics counting

**B. Work Day Counting Logic (3 locations)**
- Lines 312-338: Admin DTO summary calculation
- Lines 616-657: Month summary calculation
- Lines 749-912: Work time counts calculation

**C. CR/ZS Deduction Calculations (2 locations)**
- Lines 351-395: Admin summary calculation
- Lines 610-703: User month summary calculation

**D. Time-Off Type Label Mapping (1 location, but should be centralized)**
- Lines 1168-1183: Switch statement mapping codes to labels

#### 3. **Unclear Boundaries**
Hard to understand where one responsibility ends and another begins.

---

## Proposed Refactoring Plan

### Phase 1: Extract Calculation Logic (High Priority)

#### 1.1 Create `TimeOffDayCounter.java`
**Purpose**: Centralize all time-off day counting logic
**Location**: `com.ctgraphdep.worktime.display.counters.TimeOffDayCounter`

```java
public class TimeOffDayCounter {
    // Single method that handles all time-off counting
    public TimeOffDayCounts countTimeOffDays(List<WorkTimeTable> entries)

    // Overload for DTO-based counting
    public TimeOffDayCounts countTimeOffDays(Map<LocalDate, WorkTimeDisplayDTO> dtos)

    @Data
    public static class TimeOffDayCounts {
        private int snDays;
        private int coDays;  // Includes CE
        private int cmDays;
    }
}
```

**Impact**: Removes duplication from 4 locations
**Lines Saved**: ~60 lines

---

#### 1.2 Create `WorkDayCounter.java`
**Purpose**: Centralize work day counting logic
**Location**: `com.ctgraphdep.worktime.display.counters.WorkDayCounter`

```java
public class WorkDayCounter {
    // Counts regular work, ZS, CR, D as work days
    public int countWorkDays(List<WorkTimeTable> entries)

    // Overload for DTO-based counting
    public int countWorkDays(Map<LocalDate, WorkTimeDisplayDTO> dtos)

    // Helper: Determine if single entry is a work day
    private boolean isWorkDay(WorkTimeTable entry)
}
```

**Impact**: Removes duplication from 3 locations
**Lines Saved**: ~80 lines

---

#### 1.3 Create `OvertimeDeductionCalculator.java`
**Purpose**: Calculate CR/ZS deductions from overtime
**Location**: `com.ctgraphdep.worktime.display.calculators.OvertimeDeductionCalculator`

```java
public class OvertimeDeductionCalculator {
    // Calculate pending CR deductions
    public int calculateCRDeductions(List<WorkTimeTable> entries, int scheduleMinutes)

    // Calculate pending ZS deductions
    public int calculateZSDeductions(List<WorkTimeTable> entries)

    // Combined calculation
    public DeductionResult calculateDeductions(List<WorkTimeTable> entries, int scheduleMinutes)

    @Data
    public static class DeductionResult {
        private int crDeductions;
        private int zsDeductions;
        private int totalDeductions;
        private int crCount;
        private int zsCount;
    }
}
```

**Impact**: Removes duplication from 2 locations
**Lines Saved**: ~70 lines

---

#### 1.4 Create `WorkTimeSummaryCalculator.java`
**Purpose**: Calculate month/period summaries
**Location**: `com.ctgraphdep.worktime.display.calculators.WorkTimeSummaryCalculator`

```java
public class WorkTimeSummaryCalculator {
    private final TimeOffDayCounter timeOffDayCounter;
    private final WorkDayCounter workDayCounter;
    private final OvertimeDeductionCalculator deductionCalculator;

    // Calculate summary from entries (user view)
    public WorkTimeSummary calculateMonthSummary(
        List<WorkTimeTable> entries, int year, int month, User user)

    // Calculate summary from DTOs (admin view)
    public WorkTimeSummary calculateSummaryFromDTOs(
        Map<LocalDate, WorkTimeDisplayDTO> dtos, int totalWorkDays, User user)
}
```

**Impact**: Centralizes summary calculation logic
**Lines Saved**: ~100 lines (by delegating to counters)

---

### Phase 2: Extract Response Building Logic (Medium Priority)

#### 2.1 Create `EntryDetailResponseBuilder.java`
**Purpose**: Build API responses for entry details
**Location**: `com.ctgraphdep.worktime.display.response.EntryDetailResponseBuilder`

```java
public class EntryDetailResponseBuilder {
    // Main builder method
    public Map<String, Object> buildDetailedResponse(
        User user, LocalDate date, WorkTimeTable entry)

    // Build no-entry response
    public Map<String, Object> buildNoEntryResponse(
        User user, LocalDate date)

    // Private helpers (already exist, just move them):
    // - addTimeInformation()
    // - addWorkCalculations()
    // - addBreakInformation()
    // - addTimeOffInformation()
    // - addStatusInformation()
    // - addMetadata()
}
```

**Impact**: Removes 10+ small private methods from main service
**Lines Saved**: ~200 lines

---

#### 2.2 Create `TimeOffLabelMapper.java`
**Purpose**: Map time-off codes to display labels
**Location**: `com.ctgraphdep.worktime.display.mappers.TimeOffLabelMapper`

```java
public class TimeOffLabelMapper {
    // Get display label for time-off type
    public String getTimeOffLabel(String timeOffType)

    // Get status label
    public String getStatusLabel(String adminSync)

    // Get CSS class for status
    public String getStatusClass(String adminSync)

    // Check if special day type
    public boolean isSpecialDayType(String timeOffType)
}
```

**Impact**: Centralizes all label/display mappings
**Lines Saved**: ~60 lines
**Bonus**: Single place to update when adding new types

---

### Phase 3: Extract Data Preparation Logic (Low Priority)

#### 3.1 Create `DisplayDataPreparationService.java`
**Purpose**: Prepare data for Thymeleaf views
**Location**: `com.ctgraphdep.worktime.display.preparation.DisplayDataPreparationService`

```java
public class DisplayDataPreparationService {
    // Prepare combined display data (worktime + timeoff)
    public Map<String, Object> prepareCombinedDisplayData(
        User user, int year, int month)

    // Prepare worktime display data
    public Map<String, Object> prepareWorktimeDisplayData(
        User user, List<WorkTimeTable> data, int year, int month)

    // Prepare day headers for calendar
    public List<Map<String, String>> prepareDayHeaders(YearMonth yearMonth)

    // Prepare model for admin view
    public void prepareWorkTimeModelWithDTOs(
        Model model, int year, int month, Integer selectedUserId,
        List<User> users, Map<Integer, Map<LocalDate, WorkTimeTable>> entriesMap)
}
```

**Impact**: Separates view preparation from business logic
**Lines Saved**: ~150 lines from main service

---

### Phase 4: Simplify Main Service (Final Step)

After Phases 1-3, `WorktimeDisplayService` becomes a **coordinator** that:
- Delegates calculations to calculator classes
- Delegates counting to counter classes
- Delegates response building to builder classes
- Delegates data preparation to preparation service

**Estimated Final Size**: ~400-500 lines (down from 1,274)
**Total Lines Saved**: ~770 lines moved to specialized classes

---

## Recommended Order of Execution

1. **Start with Phase 1** (Calculation Logic)
   - Reason: Highest duplication, most immediate benefit
   - Order: TimeOffDayCounter → WorkDayCounter → OvertimeDeductionCalculator → WorkTimeSummaryCalculator

2. **Then Phase 2** (Response Building)
   - Reason: Clear boundaries, easy to extract
   - Order: TimeOffLabelMapper → EntryDetailResponseBuilder

3. **Finally Phase 3** (Data Preparation)
   - Reason: Depends on Phase 1/2 being complete
   - Order: DisplayDataPreparationService

---

## Package Structure After Refactoring

```
com.ctgraphdep.worktime.display/
├── WorktimeDisplayService.java          (400-500 lines, coordinator)
├── StatusDTOConverter.java              (existing, unchanged)
├── counters/
│   ├── TimeOffDayCounter.java           (~80 lines)
│   └── WorkDayCounter.java              (~100 lines)
├── calculators/
│   ├── OvertimeDeductionCalculator.java (~100 lines)
│   └── WorkTimeSummaryCalculator.java   (~150 lines)
├── response/
│   └── EntryDetailResponseBuilder.java  (~250 lines)
├── mappers/
│   └── TimeOffLabelMapper.java          (~80 lines)
└── preparation/
    └── DisplayDataPreparationService.java (~200 lines)
```

---

## Benefits

### 1. **Maintainability**
- Each class has ONE clear responsibility
- Easy to find where specific logic lives
- Changes are localized to one class

### 2. **Testability**
- Each class can be unit tested independently
- Mock dependencies easily
- Clear inputs/outputs

### 3. **Readability**
- Class names clearly describe purpose
- Methods are shorter and focused
- Less cognitive load

### 4. **Extensibility**
- Adding new time-off types: Update TimeOffTypeRegistry + TimeOffDayCounter
- Adding new calculations: Add to appropriate calculator
- No need to hunt through 1,274 lines

### 5. **Reusability**
- Counters can be used by other services
- Calculators can be shared across modules
- Response builders can be extended for API versioning

---

## Risk Assessment

### Low Risk
- **Phase 1 (Counters/Calculators)**: Pure logic extraction, easy to test
- **Phase 2 (Response/Mappers)**: Clear boundaries, minimal dependencies

### Medium Risk
- **Phase 3 (Preparation Service)**: Touches Spring MVC layer, needs careful testing

### Mitigation Strategy
1. Extract one class at a time
2. Write unit tests for extracted class BEFORE removing from main service
3. Run integration tests after each extraction
4. Keep git commits small (one extracted class per commit)

---

## Estimated Effort

- **Phase 1**: 4-6 hours (with testing)
- **Phase 2**: 2-3 hours (with testing)
- **Phase 3**: 3-4 hours (with testing)
- **Total**: ~10-13 hours

---

## Alternative: Minimal Refactoring

If full refactoring is too much, start with **Phase 1 only**:
- Extract the 3 counter/calculator classes
- This alone removes ~210 lines of duplication
- Provides immediate value with minimal risk
- Can do remaining phases later as time permits

---

## Questions Before Starting?

1. Do you want to proceed with full refactoring or minimal approach?
2. Should I start with Phase 1 (counters/calculators)?
3. Any specific concerns or constraints I should know about?
