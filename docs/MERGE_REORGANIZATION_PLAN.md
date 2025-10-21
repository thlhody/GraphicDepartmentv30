# Merge Package Reorganization Plan

**Generated**: 2025-10-21
**Purpose**: Consolidate scattered merge services into cohesive `merge` package
**Current State**: Merge logic scattered across 4 packages (merge, security, service, worktime)
**Target State**: Organized merge package with clear subpackage structure

---

## Executive Summary

The merge system is **architecturally scattered** with login-related merge services misplaced 
in `security` and `service` packages. This plan consolidates all merge orchestration into the `merge` package while maintaining domain-specific merge services in their respective domains.

**Key Issues:**
- ❌ `LoginMergeCacheService` in `security` package (should be in merge)
- ❌ `UserLoginMerge*` services in generic `service` package (should be in merge)
- ❌ `WorktimeLoginMergeService` in `worktime.service` (should be with other login merges)
- ✅ Core merge engine properly organized in `merge` package
- ✅ Domain-specific merge services (`RegisterMerge`, `WorktimeMerge`) in correct domains

**Solution:** Create `merge.login` subpackage for login orchestration, move misplaced services

---

## Current Architecture (As-Is)

### Package Distribution

```
com.ctgraphdep.merge/                    ✅ CORE ENGINE (GOOD)
├── constants/MergingStatusConstants.java       (378 lines) ✅ USED
├── engine/UniversalMergeEngine.java            (371 lines) ✅ USED
├── status/StatusAssignmentEngine.java          (239 lines) ✅ USED
├── status/StatusAssignmentResult.java          (82 lines)  ✅ USED
├── wrapper/GenericEntityWrapper.java           (87 lines)  ✅ USED
├── enums/EntityType.java                       (11 lines)  ✅ USED
├── service/UniversalMergeService.java          (233 lines) ❌ DEAD CODE
└── util/UniversalFinalizationUtil.java         (214 lines) ❌ DEAD CODE

com.ctgraphdep.security/                 ❌ MISPLACED
└── LoginMergeCacheService.java                 (174 lines) ⚠️ BELONGS IN MERGE

com.ctgraphdep.service/                  ❌ MISPLACED
├── UserLoginMergeService.java                  (68 lines)  ⚠️ BELONGS IN MERGE
├── UserLoginMergeServiceImpl.java              (357 lines) ⚠️ BELONGS IN MERGE
├── UserLoginCacheService.java                  (93 lines)  ⚠️ BELONGS IN MERGE
└── UserLoginCacheServiceImpl.java              (436 lines) ⚠️ BELONGS IN MERGE

com.ctgraphdep.worktime.service/         ❌ MISPLACED
└── WorktimeLoginMergeService.java              (460 lines) ⚠️ BELONGS IN MERGE

com.ctgraphdep.register.service/         ✅ DOMAIN-SPECIFIC (GOOD)
├── RegisterMergeService.java                   (621 lines) ✅ KEEP HERE
└── CheckRegisterService.java                   (600+ lines) ✅ KEEP HERE

com.ctgraphdep.worktime.service/         ✅ DOMAIN-SPECIFIC (GOOD)
└── WorktimeMergeService.java                   (185 lines) ✅ KEEP HERE
```

### Total Files
- **Core engine**: 6 files (1,168 lines active + 447 lines dead code)
- **Misplaced services**: 6 files (1,588 lines)
- **Domain services**: 3 files (1,406+ lines)
- **Total**: 15 files, ~4,609 lines

### Issues
1. **Architectural confusion**: Login merge logic split between 3 packages
2. **Wrong ownership**: `LoginMergeCacheService` in security (it's merge strategy, not auth)
3. **Discovery difficulty**: Hard to find all login merge code
4. **Maintenance burden**: Changes require editing 3 different packages
5. **Dead code**: 447 lines of unused code (28% of core package)

---

## Target Architecture (To-Be)

### Recommended: Option 1 - Moderate Reorganization

```
com.ctgraphdep.merge/
├── constants/
│   └── MergingStatusConstants.java             ✅ KEEP (378 lines)
│
├── engine/
│   └── UniversalMergeEngine.java               ✅ KEEP (371 lines)
│
├── enums/
│   └── EntityType.java                         ✅ KEEP (11 lines)
│
├── status/
│   ├── StatusAssignmentEngine.java             ✅ KEEP (239 lines)
│   └── StatusAssignmentResult.java             ✅ KEEP (82 lines)
│
├── wrapper/
│   └── GenericEntityWrapper.java               ✅ KEEP (87 lines)
│
└── login/                                       🆕 NEW SUBPACKAGE
    ├── LoginMergeOrchestrator.java             🔄 MOVE + RENAME (from service.UserLoginMergeServiceImpl)
    ├── LoginMergeStrategy.java                 🔄 MOVE + RENAME (from security.LoginMergeCacheService)
    ├── LoginCacheOrchestrator.java             🔄 MOVE (from service.UserLoginCacheServiceImpl)
    ├── WorktimeLoginMerge.java                 🔄 MOVE (from worktime.service.WorktimeLoginMergeService)
    └── interfaces/
        ├── LoginMergeService.java              🔄 MOVE (from service.UserLoginMergeService)
        └── LoginCacheService.java              🔄 MOVE (from service.UserLoginCacheService)

com.ctgraphdep.register.service/         ✅ KEEP (DOMAIN-SPECIFIC)
├── RegisterMergeService.java                   ✅ KEEP (621 lines)
└── CheckRegisterService.java                   ✅ KEEP (600+ lines)

com.ctgraphdep.worktime.service/         ✅ KEEP (DOMAIN-SPECIFIC)
└── WorktimeMergeService.java                   ✅ KEEP (185 lines)
```

### Package Responsibilities

**`merge.constants`** - Status constants and utilities
- Provides status constants (USER_INPUT, ADMIN_FINAL, etc.)
- Status validation and timestamp extraction
- Used by all merge components

**`merge.engine`** - Core merge algorithm
- Enum-based merge rules (FINAL wins, timestamp comparison, role priority)
- Entity-agnostic merge logic
- Used by all domain merge services

**`merge.status`** - Status assignment logic
- Determines correct status for operations
- Protection rules (USER_IN_PROCESS protection)
- Role-based status assignment

**`merge.wrapper`** - Entity adapters
- Generic wrapper for any entity type
- Status normalization (old → new format)
- Used by wrapper factories

**`merge.login`** - Login merge orchestration (**NEW**)
- Coordinates all merge operations during user login
- Role-based merge patterns (NORMAL_REGISTER_ONLY, BOTH_REGISTERS, etc.)
- Network-aware operations with retry queue
- Cache refresh strategies (full vs fast)
- Worktime-specific login merge logic

**Domain packages** - Domain-specific merge services
- `register.service.RegisterMergeService` - Register entry merging
- `worktime.service.WorktimeMergeService` - Worktime entry merging
- `register.service.CheckRegisterService` - Check register merging
- Use core merge engine for conflict resolution

---

## Migration Details

### Phase 1: Cleanup Dead Code (Low Risk)

**Delete entirely unused files:**

1. **`merge/service/UniversalMergeService.java`** (233 lines)
   - Status: 0 references in codebase
   - Risk: NONE
   - Reason: Abandoned prototype, never integrated

2. **`merge/util/UniversalFinalizationUtil.java`** (214 lines)
   - Status: 0 references in codebase
   - Risk: NONE
   - Reason: Unused utility, finalization handled elsewhere

**Impact**: -447 lines (28% reduction in merge package)

---

### Phase 2: Create New Package Structure (No Risk)

**Create new directories:**

```bash
mkdir -p "src/main/java/com/ctgraphdep/merge/login"
mkdir -p "src/main/java/com/ctgraphdep/merge/login/interfaces"
```

**Impact**: No code changes, just directory structure

---

### Phase 3: Move and Rename Files (Medium Risk)

**File movements with renaming:**

| Old Path | New Path | New Name | Lines | Dependencies |
|----------|----------|----------|-------|--------------|
| `security/LoginMergeCacheService.java` | `merge/login/LoginMergeStrategy.java` | ✏️ RENAME | 174 | 5 files |
| `service/UserLoginMergeServiceImpl.java` | `merge/login/LoginMergeOrchestrator.java` | ✏️ RENAME | 357 | 8 files |
| `service/UserLoginMergeService.java` | `merge/login/interfaces/LoginMergeService.java` | ⚠️ INTERFACE | 68 | 3 files |
| `service/UserLoginCacheServiceImpl.java` | `merge/login/LoginCacheOrchestrator.java` | ✏️ RENAME | 436 | 6 files |
| `service/UserLoginCacheService.java` | `merge/login/interfaces/LoginCacheService.java` | ⚠️ INTERFACE | 93 | 2 files |
| `worktime/service/WorktimeLoginMergeService.java` | `merge/login/WorktimeLoginMerge.java` | ✏️ RENAME | 460 | 4 files |

**Total**: 6 files moved, ~1,588 lines, ~28 dependent files to update

---

### Phase 4: Update Dependencies (High Impact)

**Files requiring import updates:**

#### Authentication & Security (3 files)
- `security/CustomAuthenticationProvider.java` → Update imports for `LoginMergeOrchestrator`
- `security/AuthenticationService.java` → Update imports for `LoginMerge*`
- `session/service/SessionMidnightHandler.java` → Update import for `LoginMergeStrategy`

#### Service Layer (5 files)
- `service/UserService.java` → Update imports if referencing login merge
- `dashboard/service/DashboardService.java` → Update imports if using cache service
- `notification/service/NotificationService.java` → May reference login merge

#### Controllers (8 files)
- `controller/admin/AdminRegisterController.java` → Update imports
- `controller/user/UserRegisterController.java` → Update imports
- `controller/user/SessionController.java` → Update imports
- `controller/team/TeamLeaderController.java` → Update imports
- `controller/DashboardController.java` → Update imports

#### Worktime Package (4 files)
- `worktime/service/WorktimeOperationService.java` → Update imports
- `worktime/commands/*.java` → May reference login merge

#### Register Package (3 files)
- `register/service/RegisterMergeService.java` → No changes (uses engine only)
- `register/service/CheckRegisterService.java` → No changes (uses engine only)
- `register/service/AdminRegisterService.java` → May reference login merge

#### Tests (5+ files)
- All test files referencing moved services need import updates

**Estimated**: ~28 files requiring import path updates

---

## Implementation Steps

### Step 1: Pre-Migration Verification

```bash
# 1. Verify dead code has 0 references
grep -rn "UniversalMergeService" --include="*.java" src/ | grep -v "UniversalMergeService.java"
grep -rn "UniversalFinalizationUtil" --include="*.java" src/ | grep -v "UniversalFinalizationUtil.java"

# Should return 0 results

# 2. Find all usages of services to be moved
grep -rn "LoginMergeCacheService" --include="*.java" src/
grep -rn "UserLoginMergeService" --include="*.java" src/
grep -rn "UserLoginCacheService" --include="*.java" src/
grep -rn "WorktimeLoginMergeService" --include="*.java" src/

# Record all files that need import updates
```

### Step 2: Delete Dead Code

```bash
# Delete unused files
rm "src/main/java/com/ctgraphdep/merge/service/UniversalMergeService.java"
rm "src/main/java/com/ctgraphdep/merge/util/UniversalFinalizationUtil.java"

# Remove empty directories
rmdir "src/main/java/com/ctgraphdep/merge/service/" 2>/dev/null
rmdir "src/main/java/com/ctgraphdep/merge/util/" 2>/dev/null

# Verify compilation
mvn clean compile
```

### Step 3: Create New Package Structure

```bash
# Create new directories
mkdir -p "src/main/java/com/ctgraphdep/merge/login"
mkdir -p "src/main/java/com/ctgraphdep/merge/login/interfaces"

# Verify structure
ls -la "src/main/java/com/ctgraphdep/merge/login/"
```

### Step 4: Move and Rename Files

**Important**: Use IDE refactoring tools (IntelliJ IDEA "Move Class" and "Rename") to automatically update imports!

#### 4.1. Move LoginMergeCacheService → LoginMergeStrategy

```bash
# Manual approach (if not using IDE):
# 1. Copy file
cp "src/main/java/com/ctgraphdep/security/LoginMergeCacheService.java" \
   "src/main/java/com/ctgraphdep/merge/login/LoginMergeStrategy.java"

# 2. Edit new file:
#    - Change package to: com.ctgraphdep.merge.login
#    - Rename class: LoginMergeStrategy → LoginMergeStrategy
#    - Update JavaDoc to reflect new name
#    - Keep all method signatures identical

# 3. Delete old file after verifying compilation
rm "src/main/java/com/ctgraphdep/security/LoginMergeCacheService.java"
```

**Recommended**: Use IntelliJ IDEA:
1. Right-click on `LoginMergeCacheService.java` → Refactor → Move
2. Select target package: `com.ctgraphdep.merge.login`
3. Right-click on class name → Refactor → Rename → `LoginMergeStrategy`
4. IntelliJ will automatically update all imports across the project

#### 4.2. Move UserLoginMergeServiceImpl → LoginMergeOrchestrator

```bash
# Use IDE refactoring:
# 1. Move to merge.login
# 2. Rename class: LoginMergeOrchestrator → LoginMergeOrchestrator
# 3. Update interface reference from LoginMergeService → LoginMergeService
# 4. Update @Service annotation (keep as-is)
```

#### 4.3. Move UserLoginMergeService → merge.login.interfaces.LoginMergeService

```bash
# Use IDE refactoring:
# 1. Move to merge.login.interfaces
# 2. Rename interface: LoginMergeService → LoginMergeService
# 3. Update all implementing classes
```

#### 4.4. Move UserLoginCacheServiceImpl → LoginCacheOrchestrator

```bash
# Use IDE refactoring:
# 1. Move to merge.login
# 2. Rename class: LoginCacheOrchestrator → LoginCacheOrchestrator
# 3. Update interface reference
```

#### 4.5. Move UserLoginCacheService → merge.login.interfaces.LoginCacheService

```bash
# Use IDE refactoring:
# 1. Move to merge.login.interfaces
# 2. Rename interface: LoginCacheService → LoginCacheService
```

#### 4.6. Move WorktimeLoginMergeService → WorktimeLoginMerge

```bash
# Use IDE refactoring:
# 1. Move to merge.login
# 2. Rename class: WorktimeLoginMerge → WorktimeLoginMerge
# 3. Keep method signatures identical
```

### Step 5: Update Dependent Files

**Automatic (if using IDE refactoring):**
- IntelliJ IDEA will automatically update all imports
- Verify with: `mvn clean compile`

**Manual (if not using IDE):**

Update imports in these files:

```java
// OLD IMPORTS (REPLACE)

// NEW IMPORTS (USE THESE)
import com.ctgraphdep.merge.login.LoginMergeStrategy;
        import com.ctgraphdep.merge.login.interfaces.LoginMergeService;
        import com.ctgraphdep.merge.login.LoginMergeOrchestrator;
        import com.ctgraphdep.merge.login.interfaces.LoginCacheService;
        import com.ctgraphdep.merge.login.LoginCacheOrchestrator;
        import com.ctgraphdep.merge.login.WorktimeLoginMerge;
```

**Files to update** (search and replace):

1. **Authentication & Security**
   - `security/CustomAuthenticationProvider.java`
   - `security/AuthenticationService.java`
   - `session/service/SessionMidnightHandler.java`

2. **Service Layer**
   - Any services injecting login merge services

3. **Controllers**
   - All controllers using login merge or cache services

4. **Configuration**
   - Spring configuration files (if any explicitly reference these beans)

### Step 6: Verify Compilation and Tests

```bash
# 1. Clean and compile
mvn clean compile

# Should succeed with 0 errors

# 2. Run all tests
mvn test

# 3. Focus on merge-related tests
mvn test -Dtest="*Merge*"
mvn test -Dtest="*Login*"
mvn test -Dtest="*Status*"

# 4. Integration tests (if available)
mvn integration-test
```

### Step 7: Manual Testing

1. **Start application**
   ```bash
   mvn spring-boot:run
   ```

2. **Test user login**
   - Login as regular user
   - Verify register data loads
   - Check logs for merge operations

3. **Test admin operations**
   - Login as admin
   - Load register entries
   - Verify merge logic works

4. **Test team leader operations**
   - Login as team leader
   - Load check register
   - Verify merge logic works

5. **Test worktime operations**
   - Start/stop session
   - Verify worktime merge
   - Check status assignments

6. **Check logs**
   - Look for "merge" related log entries
   - Verify no errors or warnings
   - Confirm merge operations execute correctly

### Step 8: Update Documentation

**Update these files:**

1. **CLAUDE.md**
   - Update package structure diagram
   - Update merge package description
   - Add `merge.login` subpackage documentation

2. **JavaDoc**
   - Update class-level JavaDoc for moved classes
   - Add package-info.java for `merge.login` package
   - Update references in other classes

3. **README.md** (if exists)
   - Update architecture diagrams
   - Update package descriptions

---

## Risk Assessment

### Low Risk Components
- ✅ Core merge engine (no changes)
- ✅ Domain merge services (no changes)
- ✅ Dead code deletion (0 references)

### Medium Risk Components
- ⚠️ File movements (28 dependent files)
- ⚠️ Interface renames (affects DI)
- ⚠️ Import updates (many files)

### High Risk Components
- ❌ None (no breaking API changes)

### Mitigation Strategies

1. **Use IDE refactoring tools** → Automatically update all references
2. **Incremental approach** → Move one file at a time, verify compilation
3. **Git safety** → Commit after each successful move
4. **Comprehensive testing** → Run full test suite after each phase
5. **Rollback plan** → Git revert if issues found

---

## Testing Strategy

### Unit Tests

**Existing tests to verify:**
- `UniversalMergeEngineTest` - Core merge logic (should pass unchanged)
- `StatusAssignmentEngineTest` - Status assignment (should pass unchanged)
- `RegisterMergeServiceTest` - Register merge (should pass unchanged)
- `WorktimeMergeServiceTest` - Worktime merge (should pass unchanged)

**New tests to create:**
- `LoginMergeOrchestratorTest` - Login merge coordination
- `LoginMergeStrategyTest` - Merge strategy decisions
- `LoginCacheOrchestratorTest` - Cache coordination

### Integration Tests

**Scenarios to test:**
1. **User login with merge**
   - User logs in → triggers merge operations
   - Verify register data updated
   - Verify worktime data merged

2. **Admin operations**
   - Admin loads user register → triggers merge
   - Admin saves entries → verify status changes
   - Verify cache invalidation

3. **Team leader operations**
   - Team lead loads check register
   - Verify merge logic applies
   - Verify status protection

4. **Network failure scenarios**
   - Login when network unavailable
   - Verify queuing mechanism
   - Verify retry on network recovery

### Manual Smoke Tests

**Critical paths:**
1. User login → dashboard → verify data loads
2. Admin register → load user → edit → save
3. Team check → load user → review → approve
4. Worktime session → start → stop → verify merge
5. Cache refresh → verify performance

---

## Rollback Plan

### If Issues Found During Migration

**Step 1: Stop immediately**
- Do not proceed with additional moves

**Step 2: Git revert**
```bash
# Revert last commit
git revert HEAD

# Or reset to previous state
git reset --hard <commit-hash-before-migration>
```

**Step 3: Document issue**
- Record what went wrong
- Identify affected files
- Determine root cause

**Step 4: Fix or postpone**
- If minor issue → fix and retry
- If major issue → postpone migration, keep old structure

### If Issues Found After Deployment

**Immediate rollback:**
```bash
# Revert to previous version
git checkout <previous-stable-commit>
mvn clean package
# Redeploy
```

**Post-mortem:**
- Analyze failure logs
- Identify missed test cases
- Improve migration plan

---

## Benefits of Reorganization

### Developer Experience

**Before reorganization:**
- ❌ "Where's the login merge code?" → Search 3 packages
- ❌ "Why is LoginMergeCacheService in security?" → Confusion
- ❌ "How do I add a new login merge?" → Unclear where to add code
- ❌ 447 lines of dead code → Maintenance burden

**After reorganization:**
- ✅ "Where's the login merge code?" → `merge.login` package
- ✅ "Why is merge strategy in merge package?" → Clear ownership
- ✅ "How do I add a new login merge?" → Add to `merge.login`
- ✅ 0 lines of dead code → Clean codebase

### Code Quality

**Before:**
- Package coupling: `security` ↔ `service` ↔ `worktime`
- Unclear boundaries: Login merge split across packages
- Hard to refactor: Changes require editing 3 packages

**After:**
- Clear separation: `merge.login` owns login orchestration
- Clear boundaries: Login merge in one place
- Easy to refactor: Changes in single package

### Maintenance

**Before:**
- 15 files across 5 packages
- 447 lines dead code (28%)
- Scattered responsibilities

**After:**
- 14 files (1 less after cleanup)
- 0 lines dead code
- Consolidated responsibilities

---

## Alternative Approaches Considered

### Alternative 1: Keep Current Structure

**Pros:**
- ✅ No migration effort
- ✅ Zero risk

**Cons:**
- ❌ Architectural confusion persists
- ❌ Dead code remains
- ❌ Hard to maintain

**Verdict:** ❌ NOT RECOMMENDED - Technical debt will grow

---

### Alternative 2: Aggressive Centralization

**Move ALL merge services to `merge` package:**

```
merge/
├── login/       (login merge orchestration)
├── domain/      (ALL domain merge services)
│   ├── RegisterMerge.java
│   ├── WorktimeMerge.java
│   └── CheckRegisterMerge.java
├── engine/      (core merge engine)
└── ...
```

**Pros:**
- ✅ Complete centralization
- ✅ All merge code in one place

**Cons:**
- ❌ Breaks domain boundaries (register/worktime/checking)
- ❌ Creates tight coupling to merge package
- ❌ More files to move (~9 instead of 6)
- ❌ More dependencies to update (~40+ instead of 28)
- ❌ Violates DDD principles (domain services should live in domains)

**Verdict:** ❌ NOT RECOMMENDED - Over-centralization, breaks domain boundaries

---

### Alternative 3: Minimal Move (Only Fix Misplacements)

**Move only the obviously misplaced files:**
- `security.LoginMergeCacheService` → `merge.login.LoginMergeStrategy`
- Keep `service.UserLogin*` where they are (rename to `merge` prefix)
- Keep `worktime.service.WorktimeLoginMergeService` where it is

**Pros:**
- ✅ Minimal changes
- ✅ Low risk

**Cons:**
- ❌ Doesn't fully solve architectural issue
- ❌ Login merge still scattered (2 packages instead of 3)
- ❌ Confusing: Why some login merges in `service`, others in `merge.login`?

**Verdict:** ⚠️ PARTIAL SOLUTION - Fixes `LoginMergeCacheService` but doesn't consolidate login merge

---

## Recommended Approach: Option 1 (Moderate Reorganization)

**Summary:**
- Move login orchestration to `merge.login` (6 files)
- Keep domain merge services in domains (3 files)
- Delete dead code (2 files)
- Clear package boundaries

**Justification:**
1. **Fixes architectural issues** - Gets misplaced services to correct package
2. **Maintains domain separation** - Register/worktime merge stay in domains
3. **Clear ownership** - Login merge in one place, domain merge in domains
4. **Manageable effort** - 6 files moved, 28 dependencies updated
5. **Low risk** - No breaking API changes, all moves are relocations

---

## Success Criteria

### Technical Success
- ✅ All files compile without errors
- ✅ All tests pass (unit + integration)
- ✅ No regression in functionality
- ✅ All imports updated correctly

### Architectural Success
- ✅ Login merge code in `merge.login` package
- ✅ No merge services in `security` package
- ✅ Clear package responsibilities
- ✅ 0 lines of dead code

### Documentation Success
- ✅ CLAUDE.md updated
- ✅ JavaDoc updated
- ✅ Package structure documented

---

## Timeline Estimate

### Phase 1: Dead Code Cleanup
- **Effort**: 15 minutes
- **Risk**: Low
- **Activities**: Delete 2 files, verify compilation

### Phase 2: Package Structure Creation
- **Effort**: 5 minutes
- **Risk**: None
- **Activities**: Create directories

### Phase 3: File Movement and Renaming
- **Effort**: 2 hours
- **Risk**: Medium
- **Activities**: Move 6 files, rename classes, update package declarations

### Phase 4: Dependency Updates
- **Effort**: 1 hour (if using IDE) / 3 hours (if manual)
- **Risk**: Medium
- **Activities**: Update ~28 files with new imports

### Phase 5: Testing and Verification
- **Effort**: 2 hours
- **Risk**: Low
- **Activities**: Run tests, manual testing, verify functionality

### Phase 6: Documentation
- **Effort**: 1 hour
- **Risk**: None
- **Activities**: Update CLAUDE.md, JavaDoc, package-info

**Total Estimated Effort**: 6-8 hours

---

## Conclusion

This reorganization plan consolidates scattered merge services into a cohesive `merge` package structure while maintaining clear domain boundaries. The moderate approach (Option 1) balances architectural improvement with practical implementation effort.

**Key Outcomes:**
- ✅ **Cleaner architecture** - Login merge in `merge.login`, domain merge in domains
- ✅ **Better discoverability** - Clear package structure
- ✅ **Reduced dead code** - 447 lines removed (28% reduction)
- ✅ **Maintainability** - Easier to understand and modify
- ✅ **Low risk** - No breaking API changes, comprehensive testing

**Recommendation**: Proceed with Option 1 (Moderate Reorganization) using IDE refactoring tools for automatic import updates.

---

**Next Steps:**
1. Review this plan with team
2. Get approval for migration
3. Schedule migration window
4. Execute phases incrementally
5. Verify each phase before proceeding
6. Update documentation

---

**Generated by**: Claude Code Reorganization Analysis
**Date**: 2025-10-21
**Confidence**: HIGH (based on codebase analysis and dependency graph)
**Recommended Approach**: Option 1 (Moderate Reorganization)