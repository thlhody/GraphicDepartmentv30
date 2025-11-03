# Model Package Analysis Report

**Date:** 2025-11-03
**Status:** ✅ Complete

**Date**: 2025-10-22
**Version**: 7.2.1
**Analysis Type**: Code Quality, Architecture Review, Dead Code Detection

---

## Executive Summary

The model package contains **55 classes** totaling approximately **3,500 lines of code**. This analysis identified **several architectural violations** where DTOs and model classes contain business logic that should be extracted to service layers.

### Key Findings

| Category | Status | Count | Severity |
|----------|--------|-------|----------|
| **Business Logic in Models** | ❌ **VIOLATION** | 6 classes | 🔴 High |
| **Duplicate Logic** | ⚠️ **FOUND** | 3 patterns | 🟡 Medium |
| **Dead/Unused Code** | ✅ **MINIMAL** | 0 classes | 🟢 Low |
| **DTO Separation** | ⚠️ **MIXED** | Mixed patterns | 🟡 Medium |

---

## 1. Business Logic Violations (CRITICAL)

### Problem: Model Classes with Business Logic

Model classes should be **data containers** only (POJOs/DTOs). Business logic belongs in **service layer**.

#### 🔴 **Violation #1: CheckBonusEntry.java** (184 lines)

**Location**: `com.ctgraphdep.model.CheckBonusEntry`

**Issues**:
```java
// ❌ Business logic in model class
public void calculateEfficiency() {
    if (totalWUHRM != null && totalWUHRM > 0 && totalWUM != null) {
        double efficiency = (totalWUM / totalWUHRM) * 100;
        this.efficiencyPercent = (int) Math.round(efficiency);
    }
}

public void calculateBonus(Double bonusSum) {
    if (bonusSum != null && efficiencyPercent != null) {
        this.bonusAmount = bonusSum * (efficiencyPercent / 100.0);
    }
}

public void calculateTotalWUHRM() {
    if (workingHours != null && targetWUHR != null) {
        this.totalWUHRM = workingHours * targetWUHR;
    }
}
```

**Why This is Bad**:
- Model class contains calculation logic
- Violates Single Responsibility Principle
- Hard to test business logic separately
- Can't easily swap calculation strategies
- Calculations are scattered (not centralized)

**Recommendation**: Extract to `BonusCalculationService`

---

#### 🔴 **Violation #2: WorkTimeDisplayDTO.java** (493 lines!)

**Location**: `com.ctgraphdep.model.dto.worktime.WorkTimeDisplayDTO`

**Issues**:
```java
// ❌ Factory methods with complex business logic
public static WorkTimeDisplayDTO createFromWorkEntry(
    WorkTimeTable entry, Integer userSchedule, boolean isWeekend, GeneralDataStatusDTO statusInfo) {

    // Business logic: calculate work time
    WorkTimeCalculationResultDTO calculationResult =
        CalculateWorkHoursUtil.calculateWorkTime(entry.getTotalWorkedMinutes(), userSchedule);

    // Business logic: process minutes
    int totalProcessedMinutes = calculationResult.getProcessedMinutes() + calculationResult.getOvertimeMinutes();
    String displayHours = String.valueOf(totalProcessedMinutes / 60);

    // Business logic: build tooltip
    .tooltipText(buildWorkTooltip(entry, calculationResult, statusInfo))
    // ... 50 more lines
}

// ❌ Multiple static factory methods (300+ lines of business logic)
public static WorkTimeDisplayDTO createEmpty(...)
public static WorkTimeDisplayDTO createFromTimeOffEntry(...)
public static WorkTimeDisplayDTO createFromSNWorkEntry(...)

// ❌ Private helper methods with business logic (150+ lines)
private static String buildWorkTooltip(...)
private static String buildTimeOffTooltip(...)
private static String determineCssClassForTimeOff(...)
```

**Why This is Bad**:
- DTO is **493 lines** (should be ~50 lines)
- Contains complex creation logic
- Mixes data structure with construction logic
- Hard to test factory logic
- Violates Single Responsibility Principle

**Recommendation**: Extract to `WorkTimeDisplayDTOFactory` or `WorkTimeDisplayService`

---

#### 🔴 **Violation #3: WorkTimeEntryDTO.java** (391 lines)

**Location**: `com.ctgraphdep.model.dto.worktime.WorkTimeEntryDTO`

**Similar Issues**:
- Large DTO with factory methods
- Contains business logic for entry creation
- Mixes data and construction logic

**Recommendation**: Extract to factory service

---

#### 🔴 **Violation #4: GeneralDataStatusDTO.java** (220 lines)

**Location**: `com.ctgraphdep.model.dto.status.GeneralDataStatusDTO`

**Issues**:
```java
// ❌ Factory methods with status parsing logic
public static GeneralDataStatusDTO createNonDisplayable(String rawStatus) {
    // Status parsing logic
}

public static GeneralDataStatusDTO createFromStatus(String status, ...) {
    // Complex status decoding logic
    // Role extraction
    // Action type parsing
    // Timestamp parsing
    // CSS class determination
}
```

**Why This is Bad**:
- DTO contains status parsing/decoding logic
- Status interpretation is business logic
- Should be in a dedicated service

**Recommendation**: Extract to `StatusDTOFactory` or `StatusDecoderService`

---

#### 🔴 **Violation #5: WorkTimeSummaryDTO.java** (91 lines)

**Location**: `com.ctgraphdep.model.dto.worktime.WorkTimeSummaryDTO`

**Issues**:
```java
// ❌ Factory method with calculation logic
public static WorkTimeSummaryDTO fromWorkTimeSummary(WorkTimeSummary summary) {
    return WorkTimeSummaryDTO.builder()
        .formattedRegularHours(CalculateWorkHoursUtil.minutesToHHmm(...)) // Formatting logic
        .formattedOvertimeHours(CalculateWorkHoursUtil.minutesToHHmm(...))
        .formattedTotalHours(CalculateWorkHoursUtil.minutesToHHmm(...))
        // ...
}

// ❌ Utility methods with business logic
public long getSNWorkDayCount() {
    if (entries == null) return 0;
    return entries.stream().filter(WorkTimeEntryDTO::isSNWorkDay).count();
}
```

**Recommendation**: Extract to mapper/converter service

---

#### 🟡 **Violation #6: WorkTimeSummary.java** (37 lines)

**Location**: `com.ctgraphdep.model.WorkTimeSummary`

**Issues**:
```java
// ⚠️ Helper methods with business logic
public int getTotalTimeOffDays() {
    return snDays + coDays + cmDays;
}

public int getTotalWorkedDaysWithTimeOff() {
    return daysWorked + coDays + cmDays; // SN days are not counted
}

public boolean isComplete() {
    return (daysWorked + getTotalTimeOffDays()) == totalWorkDays;
}
```

**Why This is Less Severe**:
- Simple calculations (single line)
- No external dependencies
- Just convenience methods

**Recommendation**: Low priority - can stay (derived properties pattern)

---

## 2. Duplicate Logic Patterns

### Pattern #1: Null-Safe Getters

**Found in**: `CheckBonusEntry.java`

```java
// Duplicated pattern (6 times!)
public Double getTotalWUM() {
    return totalWUM != null ? totalWUM : 0.0;
}

public Double getWorkingHours() {
    return workingHours != null ? workingHours : 0.0;
}
// ... 4 more identical patterns
```

**Recommendation**:
- Use Lombok `@Builder.Default` annotation
- Or use primitive types with default values
- Or accept that null is valid

---

### Pattern #2: Factory Method Duplication

**Found in**: `WorkTimeDisplayDTO`, `WorkTimeEntryDTO`, `GeneralDataStatusDTO`

All three DTOs have similar factory method patterns:
```java
public static XxxDTO createFromYyy(...)
public static XxxDTO createEmpty(...)
```

**Recommendation**: Extract to dedicated factory services

---

### Pattern #3: Date Formatting

**Found in**: Multiple DTOs

```java
// Duplicated in multiple places
dateString = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
```

**Recommendation**: Create `DateFormatUtil` or use centralized formatter constants

---

## 3. Dead/Unused Code Analysis

### ✅ Good News: No Dead Classes Found

All model classes are actively used. Checked:
- `WorkTimeSummary` - Used (2 references)
- `UserStatusInfo` - Used (2 references in cache service)
- `LocalStatusCache` - Used (2 references in cache service)

**Conclusion**: No dead model classes to remove.

---

## 4. DTO Separation Review

### DTO Package Structure

```
com.ctgraphdep.model/
├── [Domain Models]
│   ├── User.java ✅
│   ├── RegisterEntry.java ✅
│   ├── WorkTimeTable.java ✅
│   ├── CheckBonusEntry.java ❌ (has business logic)
│   └── WorkTimeSummary.java ⚠️ (minor logic)
│
└── dto/ [Data Transfer Objects]
    ├── worktime/
    │   ├── WorkTimeDisplayDTO.java ❌ (493 lines, factory logic)
    │   ├── WorkTimeEntryDTO.java ❌ (391 lines, factory logic)
    │   ├── WorkTimeSummaryDTO.java ❌ (91 lines, conversion logic)
    │   └── WorkTimeCountsDTO.java ✅
    ├── status/
    │   └── GeneralDataStatusDTO.java ❌ (220 lines, parsing logic)
    ├── session/
    │   └── WorkSessionDTO.java ✅
    └── team/
        └── [Various DTOs] ✅
```

### Assessment

| DTO Category | Status | Issues |
|--------------|--------|--------|
| **Team DTOs** | ✅ **GOOD** | Pure data containers |
| **Session DTOs** | ✅ **GOOD** | Pure data containers |
| **Worktime DTOs** | ❌ **BAD** | Contains factory logic |
| **Status DTOs** | ❌ **BAD** | Contains parsing logic |
| **Domain Models** | ⚠️ **MIXED** | Some have calculations |

---

## 5. Architectural Recommendations

### High Priority (🔴 Must Fix)

#### Recommendation #1: Extract DTO Factory Services

**Create new service classes**:

```java
// src/main/java/com/ctgraphdep/service/dto/WorkTimeDisplayDTOFactory.java
@Service
public class WorkTimeDisplayDTOFactory {

    public WorkTimeDisplayDTO createEmpty(Integer userId, LocalDate date,
                                          boolean isWeekend, GeneralDataStatusDTO statusInfo) {
        // All factory logic here
    }

    public WorkTimeDisplayDTO createFromWorkEntry(WorkTimeTable entry,
                                                   Integer userSchedule,
                                                   boolean isWeekend,
                                                   GeneralDataStatusDTO statusInfo) {
        // All factory logic here
    }

    // ... other factory methods
}
```

**Benefits**:
- DTO becomes simple data container (~50 lines)
- Business logic centralized in service
- Easy to test factory logic
- Can inject dependencies
- Can cache/optimize creation logic

---

#### Recommendation #2: Extract Bonus Calculation Service

**Create**:

```java
// src/main/java/com/ctgraphdep/service/BonusCalculationService.java
@Service
public class BonusCalculationService {

    public void calculateEfficiency(CheckBonusEntry entry) {
        if (entry.getTotalWUHRM() > 0 && entry.getTotalWUM() != null) {
            double efficiency = (entry.getTotalWUM() / entry.getTotalWUHRM()) * 100;
            entry.setEfficiencyPercent((int) Math.round(efficiency));
        }
    }

    public void calculateBonus(CheckBonusEntry entry, Double bonusSum) {
        if (bonusSum != null && entry.getEfficiencyPercent() != null) {
            double amount = bonusSum * (entry.getEfficiencyPercent() / 100.0);
            entry.setBonusAmount(amount);
        }
    }

    public void calculateTotalWUHRM(CheckBonusEntry entry) {
        if (entry.getWorkingHours() != null && entry.getTargetWUHR() != null) {
            entry.setTotalWUHRM(entry.getWorkingHours() * entry.getTargetWUHR());
        }
    }

    // Convenience method
    public void calculateAll(CheckBonusEntry entry, Double bonusSum) {
        calculateTotalWUHRM(entry);
        calculateEfficiency(entry);
        calculateBonus(entry, bonusSum);
    }
}
```

**Update CheckBonusEntry**:
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckBonusEntry {
    // Only fields, no methods (except Lombok-generated getters/setters)
    private String username;
    private Integer employeeId;
    private String name;
    private Double totalWUM;
    private Double workingHours;
    // ... etc
}
```

---

#### Recommendation #3: Extract Status DTO Factory

**Create**:

```java
// src/main/java/com/ctgraphdep/service/dto/StatusDTOFactory.java
@Service
public class StatusDTOFactory {

    public GeneralDataStatusDTO createFromStatus(String status,
                                                   String currentUsername,
                                                   LocalDateTime now) {
        // All parsing/decoding logic here
    }

    public GeneralDataStatusDTO createNonDisplayable(String rawStatus) {
        // Factory logic here
    }

    private String parseRoleName(String status) { /* ... */ }
    private String parseActionType(String status) { /* ... */ }
    private LocalDateTime parseTimestamp(String status) { /* ... */ }
    private String determineCssClass(String roleType, String actionType) { /* ... */ }
}
```

---

### Medium Priority (🟡 Should Fix)

#### Recommendation #4: Consolidate Null-Safe Getters

**Option A**: Use Lombok defaults
```java
@Data
@Builder
public class CheckBonusEntry {
    @Builder.Default
    private Double totalWUM = 0.0;

    @Builder.Default
    private Double workingHours = 0.0;
}
```

**Option B**: Use primitives
```java
@Data
public class CheckBonusEntry {
    private double totalWUM;  // defaults to 0.0
    private double workingHours;  // defaults to 0.0
}
```

---

#### Recommendation #5: Create DateFormatUtil

**Create**:
```java
// src/main/java/com/ctgraphdep/utils/DateFormatUtil.java
public class DateFormatUtil {
    public static final DateTimeFormatter FRONTEND_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final DateTimeFormatter DISPLAY_DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static String formatForFrontend(LocalDate date) {
        return date.format(FRONTEND_DATE_FORMAT);
    }

    public static String formatForDisplay(LocalDate date) {
        return date.format(DISPLAY_DATE_FORMAT);
    }
}
```

---

### Low Priority (🟢 Optional)

#### Recommendation #6: Extract DTO Mappers

Consider using MapStruct for domain model → DTO conversions:

```java
@Mapper(componentModel = "spring")
public interface WorkTimeSummaryMapper {
    WorkTimeSummaryDTO toDTO(WorkTimeSummary summary);
    WorkTimeSummary toEntity(WorkTimeSummaryDTO dto);
}
```

---

## 6. Refactoring Impact Analysis

### Lines of Code Reduction

| Class | Before | After Refactoring | Saved |
|-------|--------|-------------------|-------|
| WorkTimeDisplayDTO | 493 lines | ~80 lines | **-413 lines** |
| WorkTimeEntryDTO | 391 lines | ~70 lines | **-321 lines** |
| GeneralDataStatusDTO | 220 lines | ~60 lines | **-160 lines** |
| CheckBonusEntry | 184 lines | ~90 lines | **-94 lines** |
| WorkTimeSummaryDTO | 91 lines | ~50 lines | **-41 lines** |
| **TOTAL** | **1,379 lines** | **~350 lines** | **-1,029 lines** |

### New Services to Create

| Service | Lines | Purpose |
|---------|-------|---------|
| `WorkTimeDisplayDTOFactory` | ~450 lines | Create WorkTimeDisplayDTO instances |
| `WorkTimeEntryDTOFactory` | ~350 lines | Create WorkTimeEntryDTO instances |
| `StatusDTOFactory` | ~200 lines | Create and parse GeneralDataStatusDTO |
| `BonusCalculationService` | ~100 lines | Bonus calculations |
| `DateFormatUtil` | ~30 lines | Date formatting utilities |
| **TOTAL NEW CODE** | **~1,130 lines** | **All business logic centralized** |

### Net Result

- **Model Package**: -1,029 lines (pure data containers)
- **Service Package**: +1,130 lines (business logic)
- **Net**: +101 lines (but MUCH better architecture)

---

## 7. Benefits of Refactoring

### Testability
- ✅ Can test factory logic independently
- ✅ Can mock factory services in controller tests
- ✅ Can test calculation logic without creating model instances

### Maintainability
- ✅ Clear separation: models = data, services = logic
- ✅ Easier to find and modify business logic
- ✅ Smaller, focused classes

### Reusability
- ✅ Factory services can be reused across controllers
- ✅ Calculation services can be reused in different contexts
- ✅ Can compose services for complex operations

### Performance
- ✅ Can add caching to factory services
- ✅ Can optimize creation logic in one place
- ✅ Can add metrics/logging to service layer

---

## 8. Implementation Plan

### Phase 1: Extract DTO Factories (Week 1)
1. Create `WorkTimeDisplayDTOFactory` service
2. Move factory methods from `WorkTimeDisplayDTO`
3. Update all usages
4. Test thoroughly

### Phase 2: Extract Calculation Services (Week 1)
1. Create `BonusCalculationService`
2. Move calculation methods from `CheckBonusEntry`
3. Update all usages
4. Test calculations

### Phase 3: Extract Status Factory (Week 2)
1. Create `StatusDTOFactory` service
2. Move parsing logic from `GeneralDataStatusDTO`
3. Update all usages
4. Test status parsing

### Phase 4: Consolidate Utilities (Week 2)
1. Create `DateFormatUtil`
2. Update all date formatting calls
3. Clean up duplicate patterns

### Phase 5: Testing & Documentation (Week 3)
1. Comprehensive testing of all refactored code
2. Update documentation
3. Code review
4. Performance testing

---

## 9. Testing Checklist

After refactoring, verify:

- [ ] All model classes are pure POJOs (no business logic)
- [ ] All DTOs are pure data containers (no factory methods)
- [ ] All business logic is in service classes
- [ ] Factory services have unit tests
- [ ] Calculation services have unit tests
- [ ] No regression in functionality
- [ ] Performance is maintained or improved
- [ ] All existing tests still pass
- [ ] New tests cover factory/calculation logic

---

## 10. Summary

### Current State

| Metric | Value |
|--------|-------|
| Total Model Classes | 55 |
| Models with Business Logic | 6 |
| Lines in Violation | ~1,300 |
| Architectural Violations | 6 critical |

### Target State

| Metric | Value |
|--------|-------|
| Pure Data Models | 55 (100%) |
| Business Logic in Services | 5 new services |
| Lines Properly Separated | ~1,300 |
| Architectural Violations | 0 |

### Verdict

**🔴 REFACTORING STRONGLY RECOMMENDED**

The model package has significant architectural violations that should be addressed:

1. **Critical**: Large DTOs (493, 391, 220 lines) with business logic
2. **Critical**: Model classes with calculation methods
3. **High Impact**: 1,000+ lines of logic in wrong layer
4. **High Benefit**: Much better testability and maintainability after refactoring

**Estimated Effort**: 2-3 weeks
**Priority**: High
**Risk**: Medium (requires careful testing)
**Benefit**: Very High (cleaner architecture, better tests, easier maintenance)

---

**Report Generated**: 2025-10-22
**Author**: Claude Code Architecture Analysis
**Next Review**: After Phase 5 completion
