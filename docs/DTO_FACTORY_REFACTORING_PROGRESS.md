# DTO Factory Refactoring Progress

**Date**: 2025-10-22
**Status**: Phase 1 - In Progress
**Goal**: Extract business logic from DTOs into dedicated factory services

---

## Completed Steps

### ✅ Step 1: Created Factory Services

**WorkTimeDisplayDTOFactory.java** (~450 lines)
- Location: `src/main/java/com/ctgraphdep/service/dto/WorkTimeDisplayDTOFactory.java`
- Methods:
  - `createEmpty()` - Empty cell DTOs
  - `createFromWorkEntry()` - Regular work day DTOs
  - `createFromTimeOffEntry()` - Time off DTOs (CO, CM, SN without work)
  - `createFromSNWorkEntry()` - National Holiday with work
  - `createFromCOWorkEntry()` - Vacation with work
  - `createFromCMWorkEntry()` - Medical Leave with work
  - `createFromWWorkEntry()` - Weekend with work
- Helper methods:
  - Date formatting
  - CSS class determination
  - Tooltip building (work, time off, special cases)

**WorkTimeEntryDTOFactory.java** (~220 lines)
- Location: `src/main/java/com/ctgraphdep/service/dto/WorkTimeEntryDTOFactory.java`
- Methods:
  - `fromWorkTimeTable()` - Main conversion method
- Helper methods:
  - `handleSpecialDayConversion()` - SN/CO/CM/W with work
  - `handleRegularTimeOffConversion()` - Regular time off
  - `handleRegularWorkDayConversion()` - Normal work days
  - `isSpecialDayType()` - Type checking

---

## Next Steps

### ⏳ Step 2: Refactor DTOs to Pure Data Containers

Need to remove all static factory methods and business logic from:

1. **WorkTimeDisplayDTO.java** (493 → ~80 lines)
   - Remove all `public static` factory methods
   - Remove all `private static` helper methods
   - Keep only:
     - Field declarations
     - Lombok annotations (@Data, @Builder)
     - Javadoc

2. **WorkTimeEntryDTO.java** (391 → ~120 lines)
   - Remove `fromWorkTimeTable()` static method
   - Remove `handleSpecialDayConversion()` static method
   - Remove `handleRegularTimeOffConversion()` static method
   - Remove `handleRegularWorkDayConversion()` static method
   - Remove `isSpecialDayTypeStatic()` static method
   - Keep:
     - Field declarations
     - Lombok annotations
     - Instance methods (getters that compute from fields):
       - `isSpecialDayWithWork()`
       - `isSpecialDayType()`
       - `isSNWorkDay()`
       - `getTimeOffDisplayClass()`
       - `getTimeOffTooltip()`
       - `getOvertimeDisplayClass()`
       - `getOvertimeTooltip()`
     - `setTotalOvertimeMinutes()` method (business logic, but needed for builder)
     - `generateSpecialDayWorkDisplay()` private method

### ⏳ Step 3: Find and Update All Usages

Need to find all places that call:

```java
// Old pattern (static methods on DTO)
WorkTimeDisplayDTO.createEmpty(...)
WorkTimeDisplayDTO.createFromWorkEntry(...)
WorkTimeDisplayDTO.createFromTimeOffEntry(...)
WorkTimeDisplayDTO.createFromSNWorkEntry(...)
WorkTimeDisplayDTO.createFromCOWorkEntry(...)
WorkTimeDisplayDTO.createFromCMWorkEntry(...)
WorkTimeDisplayDTO.createFromWWorkEntry(...)

WorkTimeEntryDTO.fromWorkTimeTable(...)
```

Replace with:

```java
// New pattern (factory service injection)
@Service
public class SomeService {
    private final WorkTimeDisplayDTOFactory displayFactory;
    private final WorkTimeEntryDTOFactory entryFactory;

    // Constructor injection...

    public void someMethod() {
        // Use factory methods
        WorkTimeDisplayDTO dto = displayFactory.createEmpty(...);
        WorkTimeEntryDTO entry = entryFactory.fromWorkTimeTable(...);
    }
}
```

### ⏳ Step 4: Test Build

Run `mvn clean compile` to ensure:
- All imports are correct
- No compilation errors
- All usages updated

---

## Files to Search and Update

Use grep to find usages:

```bash
# Find WorkTimeDisplayDTO factory usages
grep -r "WorkTimeDisplayDTO\.create" --include="*.java" src/main/java

# Find WorkTimeEntryDTO factory usages
grep -r "WorkTimeEntryDTO\.from" --include="*.java" src/main/java
```

Expected files to update:
- `WorktimeDisplayService.java` (main user)
- Possibly other services or controllers

---

## Architecture Improvements

### Before
```
WorkTimeDisplayDTO (493 lines)
├── Data fields (30 lines)
├── Static factory methods (300 lines)
└── Static helper methods (163 lines)
```

### After
```
WorkTimeDisplayDTO (~80 lines)
└── Data fields only

WorkTimeDisplayDTOFactory (450 lines) @Service
├── Factory methods (public)
└── Helper methods (private)
```

### Benefits
✅ Separation of concerns (data vs logic)
✅ Testable factory logic (can inject mocks)
✅ Reusable across services
✅ Can add caching/optimization
✅ Clear dependency injection

---

## Remaining Work

- [ ] Remove static methods from WorkTimeDisplayDTO
- [ ] Remove static methods from WorkTimeEntryDTO
- [ ] Find all usages (grep search)
- [ ] Update all services to use factory injection
- [ ] Run tests
- [ ] Verify build passes
- [ ] Update documentation

---

## Notes

### Instance Methods to Keep in WorkTimeEntryDTO

The following methods are **NOT** factory methods - they're instance methods that compute derived values from existing fields. These should stay in the DTO:

```java
// ✅ KEEP - Instance methods (computed properties)
public boolean isSpecialDayWithWork()
public boolean isSpecialDayType()
public boolean isSNWorkDay()
public String getTimeOffDisplayClass()
public String getTimeOffTooltip()
public String getOvertimeDisplayClass()
public String getOvertimeTooltip()
public void setTotalOvertimeMinutes(Integer totalOvertimeMinutes)
private void generateSpecialDayWorkDisplay()
```

These are **derived properties** (computed from fields), not factory logic. They're acceptable in DTOs because they don't create new instances - they just provide convenient access to computed values.

### Static Methods to Remove

```java
// ❌ REMOVE - Static factory methods
public static WorkTimeEntryDTO fromWorkTimeTable(...)
private static void handleSpecialDayConversion(...)
private static void handleRegularTimeOffConversion(...)
private static void handleRegularWorkDayConversion(...)
private static boolean isSpecialDayTypeStatic(...)
```

---

**Status**: Factory services created, ready for DTO cleanup and usage updates.
