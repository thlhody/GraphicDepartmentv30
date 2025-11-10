# JavaScript Refactoring Plan - CTGraphDep

**Date**: 2025-11-04
**Total Legacy Code**: ~12,223 lines across 24+ files
**Status**: Analysis Complete - Awaiting Review & Approval

---

## Executive Summary

The legacy JavaScript directory contains substantial technical debt with:
- **Massive code duplication** (constants repeated 5+ times)
- **Inconsistent patterns** (jQuery + vanilla JS mixed)
- **No clear module structure**
- **Large monolithic files** (1,900+ lines)
- **Scattered dependencies** and unclear organization

**Recommended approach**: Modular refactoring with shared libraries, ES6 modules, and clear separation of concerns.

---

## Table of Contents

1. [Current State Analysis](#current-state-analysis)
2. [Critical Issues](#critical-issues)
3. [Code Duplication Matrix](#code-duplication-matrix)
4. [Dependency Analysis](#dependency-analysis)
5. [Proposed Architecture](#proposed-architecture)
6. [Refactoring Phases](#refactoring-phases)
7. [File-by-File Breakdown](#file-by-file-breakdown)
8. [Migration Strategy](#migration-strategy)
9. [Success Metrics](#success-metrics)

---

## Current State Analysis

### File Statistics

| File | Lines | Primary Purpose | Issues |
|------|-------|----------------|--------|
| `register-user.js` | 1,949 | User register form | Multiple responsibilities, duplicated constants |
| `register-admin.js` | 1,407 | Admin register management | State management embedded, duplicated logic |
| `check-register.js` | 1,071 | Check register handling | Duplicated validation, constants |
| `worktime-admin.js` | 1,090 | Worktime administration | Complex time-off logic duplicated |
| `utility-core.js` | 625 | Utility coordinator | jQuery-dependent, monolithic |
| `time-management-core.js` | 519 | Time management | Should be split into modules |
| `session-time-management-integration.js` | 524 | Session + time integration | Tight coupling |
| `check-values.js` | 539 | Check value calculation | Duplicated constants |
| `check-bonus.js` | 461 | Bonus calculation | Duplicated CHECK_TYPE_VALUES |
| `check-bonus-fragment.js` | 497 | Bonus fragment | Near-duplicate of check-bonus.js |
| `session-enhanced.js` | 490 | Enhanced session | Overlapping with session.js |
| `admin-bonus.js` | 347 | Admin bonus handling | Duplicates bonus logic |
| `dashboard.js` | 334 | Dashboard auto-refresh | Good candidate for reuse |
| `toast-alerts.js` | 321 | Toast system | Conflicts with default.js alerts |
| `login.js` | 300 | Login page handling | Optimization-focused, good structure |
| `viewer.js` | 301 | Data viewer | Minimal duplication |
| `constants.js` | 259 | Constants definition | **INCOMPLETE** - only partial constants |
| Others | <300 each | Various utilities | Varying quality |

**Total**: 12,223 lines

---

## Critical Issues

### 1. **Constants Duplication** (SEVERITY: HIGH)

Constants are defined in **6+ different files**:

```javascript
// Found in: constants.js, register-user.js, register-admin.js, worktime-admin.js, etc.
const ACTION_TYPE_VALUES = {
    'ORDIN': 2.5,
    'REORDIN': 1.0,
    'CAMPION': 2.5,
    // ... repeated 6 times with slight variations
};

const CHECK_TYPE_VALUES = {
    'LAYOUT': 1.0,
    'KIPSTA LAYOUT': 0.25,
    // ... repeated 5 times
};
```

**Impact**:
- Changes require updating 6 files
- Risk of inconsistency
- ~500 lines of duplicate code

### 2. **Helper Functions Duplicated** (SEVERITY: HIGH)

Functions like `getTimeOffLabel()`, `getStatusLabel()`, `getTimeOffIcon()`, `getTimeOffDescription()` appear in:
- `constants.js` (lines 163-235)
- `worktime-admin.js` (lines 722-797)
- Inline in multiple other files

**Impact**: ~300 lines of duplicate logic

### 3. **Mixed jQuery + Vanilla JS** (SEVERITY: MEDIUM)

```javascript
// jQuery in utility-core.js, register-user.js
$(document).ready(function() { ... });
$('.select2').select2({ ... });

// Vanilla JS in login.js, dashboard.js, check-register.js
document.addEventListener('DOMContentLoaded', function() { ... });
document.querySelector('.table tbody');
```

**Impact**:
- Confusion about which API to use
- Performance inconsistency
- Bundle size

### 4. **No Module System** (SEVERITY: HIGH)

Files use inconsistent patterns:

```javascript
// Pattern 1: Class-based (modern)
class RegisterFormHandler { ... }

// Pattern 2: IIFE (intermediate)
const RegisterUser = (function() { 'use strict'; ... })();

// Pattern 3: Global functions (legacy)
function showEditor(cell) { ... }

// Pattern 4: Direct execution
document.addEventListener('DOMContentLoaded', function() { ... });
```

**Impact**:
- No clear dependency management
- Global namespace pollution
- Difficult testing
- Unclear initialization order

### 5. **Large Monolithic Files** (SEVERITY: MEDIUM)

- `register-user.js`: 4 classes + utility functions (1,949 lines)
- `register-admin.js`: Complex state management (1,407 lines)
- `check-register.js`: 3 classes + helpers (1,071 lines)

**Impact**:
- Hard to maintain
- Difficult to test
- Long load times
- Poor code splitting

### 6. **Validation Logic Duplicated** (SEVERITY: MEDIUM)

Form validation repeated in:
- `register-user.js` (RegisterFormHandler.validateForm)
- `check-register.js` (CheckRegisterFormHandler.validateForm)
- Inline validation in multiple forms

**Impact**: ~200 lines of duplicate validation

### 7. **Toast/Alert Systems** (SEVERITY: LOW-MEDIUM)

Two competing implementations:
1. `toast-alerts.js`: Full `ToastAlertSystem` class (321 lines)
2. `default.js`: Bootstrap-based auto-dismiss (28 lines)

**Impact**: Inconsistent UI, confusion

---

## Code Duplication Matrix

| Code Type | Files Containing Duplicate | Est. Duplicate Lines | Priority |
|-----------|---------------------------|---------------------|----------|
| `ACTION_TYPE_VALUES` | 6 files | ~150 | P0 - Critical |
| `CHECK_TYPE_VALUES` | 5 files | ~100 | P0 - Critical |
| `COMPLEXITY_PRINT_PREPS` | 3 files | ~40 | P0 - Critical |
| Time-off helpers | 4 files | ~300 | P0 - Critical |
| Status helpers | 3 files | ~80 | P1 - High |
| Form validation | 5 files | ~200 | P1 - High |
| AJAX patterns | 8 files | ~250 | P1 - High |
| Search handlers | 3 files | ~600 | P2 - Medium |
| Modal management | 4 files | ~150 | P2 - Medium |
| Date formatting | 6 files | ~100 | P3 - Low |

**Total estimated duplicate code**: ~1,970 lines (16% of codebase)

---

## Dependency Analysis

### Core Dependencies

```
Hierarchy:
  constants.js (should be foundation - currently incomplete)
    ↓
  utility-core.js (depends on jQuery, toast system)
    ↓
  Page-specific handlers (register-user.js, worktime-admin.js, etc.)
    ↓
  UI components (toast-alerts.js, modal handlers, search)
```

### Library Dependencies

1. **jQuery** - Used in: utility-core.js, register-user.js (Select2), some legacy code
2. **Bootstrap** - All files (modals, toasts, form validation)
3. **Select2** - register-user.js (print prep multi-select)
4. **Chart.js** - statistics.js
5. **Vanilla JS** - Most modern files (login.js, dashboard.js, check-register.js)

### Actual Loading Order Issues

Files are currently loaded individually in HTML templates without clear dependency management:

```html
<!-- Current approach in templates -->
<script src="/js/legacy/constants.js"></script>
<script src="/js/legacy/toast-alerts.js"></script>
<script src="/js/legacy/utility-core.js"></script>
<script src="/js/legacy/register-user.js"></script>
<!-- etc. -->
```

**Problems**:
- No guarantee of load order
- Each file loaded separately (no bundling)
- Constants incomplete, so files redefine them
- Race conditions possible

---

## Proposed Architecture

### Module Structure

```
src/main/resources/static/js/
├── core/
│   ├── constants.js          ← Single source of truth
│   ├── config.js              ← Application config
│   ├── api.js                 ← AJAX/fetch wrapper
│   └── utils.js               ← Common utilities
├── services/
│   ├── timeOffService.js      ← Time-off type management
│   ├── statusService.js       ← Status badge management
│   ├── validationService.js   ← Form validation
│   └── calculationService.js  ← Complexity/bonus calculations
├── components/
│   ├── ToastNotification.js   ← Unified toast system
│   ├── Modal.js               ← Modal wrapper
│   ├── SearchModal.js         ← Reusable search
│   └── FormHandler.js         ← Base form handler
├── features/
│   ├── register/
│   │   ├── RegisterForm.js
│   │   ├── RegisterSummary.js
│   │   ├── RegisterSearch.js
│   │   └── index.js           ← Feature entry point
│   ├── checkRegister/
│   │   ├── CheckForm.js
│   │   ├── CheckSummary.js
│   │   └── index.js
│   ├── worktime/
│   │   ├── WorktimeEditor.js
│   │   ├── WorktimeDisplay.js
│   │   └── index.js
│   ├── session/
│   │   ├── SessionManager.js
│   │   ├── SessionMonitor.js
│   │   └── index.js
│   ├── bonus/
│   │   ├── BonusCalculator.js
│   │   └── index.js
│   ├── dashboard/
│   │   ├── DashboardRefresh.js
│   │   └── index.js
│   └── login/
│       ├── LoginForm.js
│       └── index.js
└── legacy/                    ← Keep for reference during migration
    └── [all current files]
```

### Module Pattern

Use ES6 modules with explicit exports/imports:

```javascript
// core/constants.js
export const ACTION_TYPE_VALUES = {
    'ORDIN': 2.5,
    'REORDIN': 1.0,
    // ... single definition
};

export const CHECK_TYPE_VALUES = {
    'LAYOUT': 1.0,
    // ... single definition
};

// services/timeOffService.js
import { TIME_OFF_TYPES } from '../core/constants.js';

export class TimeOffService {
    static getLabel(type) { ... }
    static getIcon(type) { ... }
    static getDescription(type) { ... }
    static validate(value) { ... }
}

// features/register/RegisterForm.js
import { ACTION_TYPE_VALUES } from '../../core/constants.js';
import { FormHandler } from '../../components/FormHandler.js';
import { ValidationService } from '../../services/validationService.js';

export class RegisterForm extends FormHandler {
    constructor() { ... }
}
```

### Constants Organization

```javascript
// core/constants.js - Single source of truth

// Action type complexity values
export const ACTION_TYPE_VALUES = new Map([
    ['ORDIN', 2.5],
    ['REORDIN', 1.0],
    ['CAMPION', 2.5],
    ['PROBA STAMPA', 2.5],
    ['ORDIN SPIZED', 2.0],
    ['CAMPION SPIZED', 2.0],
    ['PROBA S SPIZED', 2.0],
    ['PROBA CULOARE', 2.5],
    ['CARTELA CULORI', 2.5],
    ['CHECKING', 3.0],
    ['DESIGN', 2.5],
    ['DESIGN 3D', 3.0],
    ['PATTERN PREP', 2.5],
    ['IMPOSTARE', 0.0],
    ['OTHER', 2.5]
]);

// Check type values
export const CHECK_TYPE_VALUES = new Map([
    ['LAYOUT', 1.0],
    ['KIPSTA LAYOUT', 0.25],
    ['LAYOUT CHANGES', 0.25],
    ['GPT', 0.1],
    ['PRODUCTION', 0.1],
    ['REORDER', 0.1],
    ['SAMPLE', 0.3],
    ['OMS PRODUCTION', 0.1],
    ['KIPSTA PRODUCTION', 0.1]
]);

// Print prep complexity
export const COMPLEXITY_PRINT_PREPS = new Map([
    ['SBS', 0.5],
    ['NN', 0.5],
    ['NAME', 0.5],
    ['NUMBER', 0.5],
    ['FLEX', 0.5],
    ['BRODERIE', 0.5],
    ['OTHER', 0.5]
]);

export const NEUTRAL_PRINT_PREPS = new Map([
    ['DIGITAL', 0.0],
    ['GPT', 0.0],
    ['LAYOUT', 0.0],
    ['FILM', 0.0]
]);

// Time-off types with metadata
export const TIME_OFF_TYPES = new Map([
    ['SN', { label: 'National Holiday', icon: 'bi-calendar-event text-success', allowsWork: true }],
    ['CO', { label: 'Vacation', icon: 'bi-airplane text-info', allowsWork: true }],
    ['CM', { label: 'Medical Leave', icon: 'bi-heart-pulse text-warning', allowsWork: true }],
    ['W', { label: 'Weekend Work', icon: 'bi-calendar-week text-secondary', allowsWork: true }],
    ['CR', { label: 'Recovery Leave', icon: 'bi-battery-charging text-success', allowsWork: false }],
    ['CN', { label: 'Unpaid Leave', icon: 'bi-dash-circle text-secondary', allowsWork: false }],
    ['D', { label: 'Delegation', icon: 'bi-briefcase text-primary', allowsWork: false }],
    ['CE', { label: 'Event Leave', icon: 'bi-gift text-danger', allowsWork: true }]
]);

// Status types
export const STATUS_TYPES = new Map([
    ['USER_DONE', { label: 'User Completed', class: 'text-success', badge: 'bg-success' }],
    ['ADMIN_EDITED', { label: 'Admin Modified', class: 'text-warning', badge: 'bg-warning' }],
    ['USER_IN_PROCESS', { label: 'In Progress', class: 'text-info', badge: 'bg-info' }],
    ['ADMIN_BLANK', { label: 'Admin Blank', class: 'text-secondary', badge: 'bg-secondary' }],
    ['TEAM_EDITED', { label: 'Team Edited', class: 'text-primary', badge: 'bg-primary' }],
    ['ADMIN_FINAL', { label: 'Admin Final', class: 'text-dark', badge: 'bg-dark' }]
]);

// Helper functions (keep as exports for backward compatibility)
export function addActionType(key, value) {
    ACTION_TYPE_VALUES.set(key, value);
}

export function removeActionType(key) {
    ACTION_TYPE_VALUES.delete(key);
}

// ... similar helpers for other maps
```

### Benefits of New Architecture

1. **Single Source of Truth**: Constants defined once
2. **Easy Maintenance**: Add/remove by updating one file
3. **Type Safety**: Can add TypeScript later with minimal changes
4. **Tree-shakeable**: Only import what's needed
5. **Testable**: Easy to mock and test
6. **Clear Dependencies**: Import graph shows relationships
7. **Better IDE Support**: Autocomplete, refactoring tools work

---

## Refactoring Phases

### Phase 1: Foundation (Week 1-2) - CRITICAL

**Goal**: Establish core infrastructure

1. **Create `core/constants.js`** with ALL constants as Maps
   - Migrate from legacy `constants.js`
   - Add all duplicated constants from other files
   - Document each constant with JSDoc comments

2. **Create `services/timeOffService.js`**
   - Move all time-off helper functions
   - Consolidate duplicate logic

3. **Create `services/statusService.js`**
   - Move status-related helpers
   - Badge class generation

4. **Create `components/ToastNotification.js`**
   - Unify `toast-alerts.js` and `default.js` alerts
   - Single API for all notifications

5. **Create `core/api.js`**
   - Wrap fetch/AJAX calls
   - CSRF token handling
   - Error handling

**Deliverables**:
- 5 new core/service files
- Unit tests for each
- Documentation

**Estimated effort**: 40-60 hours

---

### Phase 2: Components (Week 3-4)

**Goal**: Create reusable UI components

1. **Create `components/FormHandler.js`**
   - Base class for all forms
   - Common validation
   - CSRF handling
   - Submit/reset logic

2. **Create `components/SearchModal.js`**
   - Reusable search component
   - Used by register-user, check-register, etc.

3. **Create `components/Modal.js`**
   - Wrapper around Bootstrap modals
   - Consistent API

4. **Create `services/validationService.js`**
   - Form field validation
   - Error message display
   - Validation rules

**Deliverables**:
- 4 reusable components
- Reduced duplication by ~600 lines
- Component documentation

**Estimated effort**: 30-40 hours

---

### Phase 3: Register Feature (Week 5-6)

**Goal**: Refactor register-related files

1. **Split `register-user.js` (1,949 lines) into**:
   - `features/register/RegisterForm.js` (~400 lines)
   - `features/register/RegisterSummary.js` (~250 lines)
   - `features/register/RegisterSearch.js` (~350 lines)
   - `features/register/AjaxHandler.js` (~300 lines)
   - `features/register/index.js` (entry point, ~50 lines)

2. **Refactor `register-admin.js` (1,407 lines) into**:
   - `features/register/admin/AdminRegisterView.js` (~500 lines)
   - `features/register/admin/AdminRegisterState.js` (~300 lines)
   - `features/register/admin/BonusCalculator.js` (~400 lines)
   - `features/register/admin/index.js` (~50 lines)

3. **Eliminate duplication**:
   - Use shared `constants.js`
   - Use shared `ValidationService`
   - Use shared `FormHandler` base

**Deliverables**:
- Register feature fully modularized
- ~1,000 lines of duplication removed
- Feature documentation

**Estimated effort**: 50-60 hours

---

### Phase 4: Check Register & Worktime (Week 7-8)

**Goal**: Refactor check-register and worktime features

1. **Split `check-register.js` (1,071 lines) into**:
   - `features/checkRegister/CheckForm.js`
   - `features/checkRegister/CheckSummary.js`
   - `features/checkRegister/CheckSearch.js`
   - `features/checkRegister/index.js`

2. **Split `worktime-admin.js` (1,090 lines) into**:
   - `features/worktime/WorktimeEditor.js`
   - `features/worktime/WorktimeDisplay.js`
   - `features/worktime/FinalizationHandler.js`
   - `features/worktime/index.js`

3. **Consolidate bonus calculation**:
   - `features/bonus/BonusCalculator.js` (unify admin-bonus, check-bonus, check-bonus-fragment)

**Deliverables**:
- Check register & worktime modularized
- Bonus logic consolidated
- ~800 lines duplication removed

**Estimated effort**: 50-60 hours

---

### Phase 5: Utilities & Polish (Week 9-10)

**Goal**: Handle remaining files and polish

1. **Refactor utility files**:
   - `utility-core.js` → Remove jQuery dependency
   - Session files → Consolidate into `features/session/`
   - Time management → Consolidate into `features/timeManagement/`

2. **Polish smaller files**:
   - `dashboard.js` → `features/dashboard/`
   - `login.js` → `features/login/`
   - `statistics.js` → `features/statistics/`

3. **Build system setup**:
   - Add bundler (Rollup or esbuild)
   - Code splitting
   - Minification
   - Source maps

4. **Documentation**:
   - API documentation
   - Migration guide
   - Component usage examples

**Deliverables**:
- All features modularized
- Build system configured
- Complete documentation

**Estimated effort**: 40-50 hours

---

### Phase 6: Testing & Migration (Week 11-12)

**Goal**: Test thoroughly and migrate production

1. **Unit tests**: Test all services and components
2. **Integration tests**: Test feature modules
3. **E2E tests**: Test critical user flows
4. **Performance testing**: Compare bundle sizes, load times
5. **Update HTML templates**: Change script includes
6. **Deploy to staging**: Full system test
7. **Production deployment**: Gradual rollout

**Deliverables**:
- Test suite (>80% coverage)
- Performance report
- Production deployment

**Estimated effort**: 60-80 hours

---

## File-by-File Breakdown

### High Priority (Phase 1-3)

| File | Current Size | Refactored Size | Action | New Location |
|------|-------------|----------------|--------|--------------|
| `constants.js` | 259 lines | 400 lines | **Expand & consolidate** | `core/constants.js` |
| `register-user.js` | 1,949 lines | ~1,350 lines (split) | **Split into 5 files** | `features/register/` |
| `register-admin.js` | 1,407 lines | ~1,250 lines (split) | **Split into 4 files** | `features/register/admin/` |
| `worktime-admin.js` | 1,090 lines | ~900 lines (split) | **Split into 4 files** | `features/worktime/` |
| `check-register.js` | 1,071 lines | ~800 lines (split) | **Split into 4 files** | `features/checkRegister/` |
| `utility-core.js` | 625 lines | 200 lines | **Simplify, remove jQuery** | `core/utils.js` |
| `toast-alerts.js` | 321 lines | 250 lines | **Consolidate** | `components/ToastNotification.js` |

### Medium Priority (Phase 4-5)

| File | Current Size | Action | New Location |
|------|-------------|--------|--------------|
| `time-management-core.js` | 519 lines | **Modularize** | `features/timeManagement/` |
| `session-time-management-integration.js` | 524 lines | **Split** | `features/session/` + `features/timeManagement/` |
| `check-values.js` | 539 lines | **Simplify** | `features/checkRegister/CheckValues.js` |
| `admin-bonus.js` | 347 lines | **Consolidate** | `features/bonus/BonusCalculator.js` |
| `check-bonus.js` | 461 lines | **Consolidate** | `features/bonus/BonusCalculator.js` |
| `check-bonus-fragment.js` | 497 lines | **Consolidate** | `features/bonus/BonusCalculator.js` |
| `session-enhanced.js` | 490 lines | **Merge** | `features/session/SessionManager.js` |
| `dashboard.js` | 334 lines | **Minor cleanup** | `features/dashboard/DashboardRefresh.js` |
| `login.js` | 300 lines | **Minor cleanup** | `features/login/LoginForm.js` |

### Low Priority (Phase 6)

| File | Current Size | Action | New Location |
|------|-------------|--------|--------------|
| `register-search.js` | 281 lines | **Integrate** | `features/register/RegisterSearch.js` |
| `resolution.js` | 298 lines | **Keep minimal** | `features/resolution/index.js` |
| `viewer.js` | 301 lines | **Keep minimal** | `features/viewer/index.js` |
| `statistics.js` | 127 lines | **Keep minimal** | `features/statistics/index.js` |
| `status.js` | 213 lines | **Integrate** | `services/statusService.js` |
| `team-stats.js` | 103 lines | **Keep minimal** | `features/teamStats/index.js` |
| `about.js` | 72 lines | **Keep as-is** | `features/about/index.js` |
| `default.js` | 28 lines | **Keep** | Keep in root (global utilities) |
| `session.js` | 5 lines | **Remove** (trivial) | N/A |
| `standalone-time-management.js` | 62 lines | **Merge** | `features/timeManagement/` |

### Subdirectories

**`legacy/tm/` (Time Management modules)**:
- Move all to `features/timeManagement/components/`
- Maintain module structure

**`legacy/um/` (Utility Management modules)**:
- Integrate into `core/` and `services/`

---

## Migration Strategy

### Approach: Parallel Development

1. **Keep legacy/ directory intact** during development
2. **Build new structure in parallel**
3. **Feature-by-feature migration**:
   - Migrate one feature at a time
   - Test thoroughly
   - Update corresponding HTML template
4. **Gradual rollout**: Use feature flags if needed
5. **Remove legacy/** only after full migration

### Backward Compatibility

Create adapter/shim layer for gradual migration:

```javascript
// adapters/legacyBridge.js
import { ACTION_TYPE_VALUES } from '../core/constants.js';

// Provide legacy global variables for old code still loading
window.ActionTypeConstansts = { /* legacy format */ };
window.ACTION_TYPE_VALUES = Object.fromEntries(ACTION_TYPE_VALUES);

// ... etc for all legacy globals
```

### HTML Template Updates

**Before** (legacy):
```html
<script src="/js/legacy/constants.js"></script>
<script src="/js/legacy/toast-alerts.js"></script>
<script src="/js/legacy/register-user.js"></script>
```

**After** (modular):
```html
<!-- Option 1: Bundle approach -->
<script type="module" src="/js/bundles/register.bundle.js"></script>

<!-- Option 2: Native ES modules -->
<script type="module">
    import { RegisterFeature } from '/js/features/register/index.js';
    RegisterFeature.init();
</script>
```

### Testing Strategy

For each refactored module:

1. **Unit tests**: Test isolated functions/classes
2. **Integration tests**: Test module interactions
3. **Visual regression tests**: Ensure UI unchanged
4. **Performance tests**: Measure load time, bundle size
5. **User acceptance tests**: Manual testing of workflows

### Rollback Plan

1. Keep legacy/ directory until full migration complete
2. Feature flags to toggle new vs old code
3. Quick rollback by reverting HTML script includes
4. Monitor error logs for regression

---

## Success Metrics

### Code Quality Metrics

| Metric | Current | Target | How to Measure |
|--------|---------|--------|----------------|
| Total Lines | 12,223 | <9,000 | Line count |
| Duplicate Lines | ~1,970 (16%) | <500 (5%) | SonarQube/ESLint |
| Average File Size | 509 lines | <300 lines | File statistics |
| Cyclomatic Complexity | High (many >50) | <20 per function | ESLint complexity rule |
| Test Coverage | 0% | >80% | Jest/Vitest coverage |
| Dependencies per file | Unclear | <5 imports | Module graph analysis |

### Performance Metrics

| Metric | Current | Target | How to Measure |
|--------|---------|--------|----------------|
| Initial Bundle Size | ~450KB (est) | <200KB | Webpack bundle analyzer |
| Gzip Bundle Size | ~120KB (est) | <60KB | Build output |
| Page Load Time | Variable | <1.5s (avg) | Lighthouse/WebPageTest |
| Time to Interactive | Variable | <2s | Lighthouse |
| First Contentful Paint | Variable | <1s | Lighthouse |

### Maintenance Metrics

| Metric | Current | Target | How to Measure |
|--------|---------|--------|----------------|
| Time to Add Constant | 6 file edits | 1 file edit | Developer survey |
| Time to Fix Bug | Variable | <2 hours (avg) | Issue tracker |
| Build Time | N/A (no build) | <5s | Build logs |
| Developer Onboarding | ~2 weeks | ~3 days | Survey |

---

## Risks & Mitigation

### Risk 1: Breaking Changes

**Risk**: Refactoring breaks existing functionality
**Probability**: Medium
**Impact**: High
**Mitigation**:
- Extensive testing (unit, integration, E2E)
- Keep legacy code parallel during migration
- Feature-by-feature rollout
- Rollback plan ready

### Risk 2: Timeline Overrun

**Risk**: Refactoring takes longer than estimated
**Probability**: Medium
**Impact**: Medium
**Mitigation**:
- Prioritize critical issues first
- Deliver in phases
- Stop-gate decisions at end of each phase
- Can halt after Phase 3 if needed

### Risk 3: Performance Regression

**Risk**: New code is slower than old code
**Probability**: Low
**Impact**: Medium
**Mitigation**:
- Performance testing in each phase
- Bundle size monitoring
- Load time comparison
- Profiling tools

### Risk 4: Developer Resistance

**Risk**: Team prefers old patterns
**Probability**: Low-Medium
**Impact**: Medium
**Mitigation**:
- Clear documentation
- Training sessions
- Show benefits (easier debugging, faster development)
- Gradual adoption

### Risk 5: Hidden Dependencies

**Risk**: Discover unknown dependencies during migration
**Probability**: Medium
**Impact**: Low-Medium
**Mitigation**:
- Thorough code analysis upfront
- Dependency graph visualization
- Test coverage catches issues early
- Iterative refactoring allows course correction

---

## Next Steps (Decision Required)

### Option A: Full Refactoring (Recommended)

**Timeline**: 12 weeks
**Effort**: ~340-400 hours
**Cost**: High (dedicated developer time)
**Benefits**:
- Eliminate 16% code duplication
- Modern, maintainable codebase
- Faster feature development going forward
- Better performance

### Option B: Partial Refactoring (Phased)

**Timeline**: 6 weeks (Phases 1-3 only)
**Effort**: ~170-200 hours
**Cost**: Medium
**Benefits**:
- Address critical duplication
- Foundation for future work
- Immediate quality improvements
- Stop-gate at Phase 3, continue later

### Option C: Targeted Fixes Only

**Timeline**: 2 weeks
**Effort**: ~50-60 hours
**Cost**: Low
**Benefits**:
- Fix constants duplication only
- Quick wins
- Minimal risk
- Leaves large files as-is

---

## Appendix

### A. Example: Before/After Constants Usage

**Before** (duplicated across files):

```javascript
// In register-user.js
const ACTION_TYPE_VALUES = {
    'ORDIN': 2.5,
    'REORDIN': 1.0,
    // ...
};
const complexity = ACTION_TYPE_VALUES[actionType];

// In register-admin.js (duplicate!)
const ACTION_TYPE_VALUES = {
    'ORDIN': 2.5,
    'REORDIN': 1.0,
    // ...
};

// In check-register.js (duplicate!)
const CHECK_TYPE_VALUES = {
    'LAYOUT': 1.0,
    // ...
};
```

**After** (single source):

```javascript
// core/constants.js (single definition)
export const ACTION_TYPE_VALUES = new Map([
    ['ORDIN', 2.5],
    ['REORDIN', 1.0],
    // ...
]);

// features/register/RegisterForm.js
import { ACTION_TYPE_VALUES } from '../../core/constants.js';
const complexity = ACTION_TYPE_VALUES.get(actionType);

// features/register/admin/AdminRegisterView.js
import { ACTION_TYPE_VALUES } from '../../../core/constants.js';
// Use the same map

// features/checkRegister/CheckForm.js
import { CHECK_TYPE_VALUES } from '../../core/constants.js';
// Single source
```

**Benefits**:
- One place to update
- No sync issues
- Easy to add/remove
- Type-safe with TypeScript later

### B. Example: Before/After Time-Off Helpers

**Before** (duplicated in 4 files):

```javascript
// In worktime-admin.js
function getTimeOffLabel(timeOffType) {
    if (!timeOffType) return timeOffType;
    if (timeOffType.startsWith('ZS-')) {
        const missingHours = timeOffType.split('-')[1];
        return `Short Day (missing ${missingHours}h)`;
    }
    switch (timeOffType.toUpperCase()) {
        case 'SN': return 'National Holiday';
        case 'CO': return 'Vacation';
        // ... 60 more lines
    }
}

// Duplicated in constants.js, register-admin.js, etc.
```

**After** (single service):

```javascript
// services/timeOffService.js
import { TIME_OFF_TYPES } from '../core/constants.js';

export class TimeOffService {
    static getLabel(timeOffType) {
        if (!timeOffType) return '';

        if (timeOffType.startsWith('ZS-')) {
            const hours = timeOffType.split('-')[1];
            return `Short Day (missing ${hours}h)`;
        }

        const base = timeOffType.split(':')[0];
        return TIME_OFF_TYPES.get(base)?.label || timeOffType;
    }

    static getIcon(timeOffType) {
        const base = timeOffType.split(':')[0];
        return TIME_OFF_TYPES.get(base)?.icon || 'bi-calendar-x';
    }

    static allowsWork(timeOffType) {
        const base = timeOffType.split(':')[0];
        return TIME_OFF_TYPES.get(base)?.allowsWork || false;
    }

    static validate(value) {
        // Centralized validation logic
    }
}

// Usage:
import { TimeOffService } from '../../services/timeOffService.js';
const label = TimeOffService.getLabel('SN');
const icon = TimeOffService.getIcon('CO');
```

**Benefits**:
- Single implementation
- Easy to test
- Consistent behavior
- Can add features (like descriptions) once

### C. Example: Module Dependency Graph

```
Core Layer:
  constants.js
  config.js
  api.js
  utils.js
      ↓
Service Layer:
  timeOffService.js      (depends on: constants)
  statusService.js       (depends on: constants)
  validationService.js   (depends on: constants, utils)
  calculationService.js  (depends on: constants)
      ↓
Component Layer:
  ToastNotification.js   (depends on: utils)
  Modal.js               (depends on: utils)
  FormHandler.js         (depends on: api, validationService)
  SearchModal.js         (depends on: api, Modal)
      ↓
Feature Layer:
  register/RegisterForm.js       (depends on: FormHandler, validationService, calculationService)
  register/RegisterSummary.js    (depends on: constants, utils)
  worktime/WorktimeEditor.js     (depends on: timeOffService, api)
  checkRegister/CheckForm.js     (depends on: FormHandler, calculationService)
  // etc.
```

### D. Estimated Timeline (Gantt-style)

```
Week 1-2:   Phase 1 (Foundation) ████████████
Week 3-4:   Phase 2 (Components) ████████████
Week 5-6:   Phase 3 (Register)   ████████████
Week 7-8:   Phase 4 (Check/WT)   ████████████
Week 9-10:  Phase 5 (Utilities)  ████████████
Week 11-12: Phase 6 (Testing)    ████████████

Critical Path:
  Phase 1 → Phase 2 → Phase 3 → Phase 6

Can be Parallelized:
  Phase 4 & Phase 5 (different developers)
```

---

## Conclusion

The current JavaScript codebase has significant technical debt (16% duplication, inconsistent patterns, large files). A structured refactoring will:

1. **Eliminate ~2,000 lines of duplicate code**
2. **Improve maintainability** (clear module structure)
3. **Enhance performance** (code splitting, tree-shaking)
4. **Reduce bugs** (single source of truth)
5. **Speed up development** (reusable components)

**Recommended approach**: **Option B - Phased Refactoring (Phases 1-3)**
- 6 weeks timeline
- Addresses critical issues
- Foundation for future improvements
- Can extend to full refactoring later

**Next step**: Review this plan and decide which option to proceed with.

---

**Document Status**: DRAFT - Awaiting Review
**Last Updated**: 2025-11-04
**Contact**: [Your Name/Team]