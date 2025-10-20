# CLAUDE.md

Guidance for Claude Code when working with this repository.

## Project Overview

**CTGraphDep (CTTT)** - Spring Boot time tracking and project management system with dual-location sync (local + network).

- **Version**: 7.2.1 | **Framework**: Spring Boot 3.2.0 | **Java**: 17 | **Build**: Maven | **DB**: H2 | **Frontend**: Thymeleaf

---

# Part 1: Installer (Inno Setup)

## Build Process
```bash
mvn package  # Creates JAR + installer → target/installer-output/CTTT_Setup_7.2.1.exe
```

## Key Configuration (`installer/setup.iss`)
- **App ID**: `{38166b65-a6ca-4a09-a9cb-0f5f497c5dca}` (NEVER CHANGE)
- **Default Install**: `C:\Program Files\CreativeTimeAndTaskTracker`
- **Network Path**: `\\grafubu\A_Registru graficieni\CTTT`

## PowerShell Scripts (`installer/dist/scripts/`)

**Core:**
- `install.ps1` - Creates structure, configures paths, creates startup shortcut
- `update.ps1` - Updates JAR, preserves config
- `uninstall.ps1` - Removes app, cleans config
- `start-app.ps1` - Launches Spring Boot JAR in system tray mode

**Utilities:**
- `configure-port.ps1` - Port config (default 8447)
- `create-hosts.ps1` - Adds CTTT hostname → 127.0.0.1
- `test-network.ps1` - Network path validation
- `log-manager.ps1` - Log rotation (10MB, 10 files)

## Rules
- Never change App ID
- Increment version in `pom.xml` AND `setup.iss`
- All scripts require PowerShell 5.1+
- Use `Write-Log` from `log-manager.ps1`
- Preserve user data during updates

---

# Part 2: Backend (Spring Boot)

## Essential Commands
```bash
mvn clean compile           # Compile
mvn test                    # All tests
mvn test -Dtest=ClassName   # Single test
mvn spring-boot:run         # Run app
mvn package                 # Build JAR + installer
```

**Access**: http://localhost:8447 or http://CTTT:8447 | **Credentials**: admin/admin

## Architecture

### Dual-Location Sync
- **Local**: `D:\serverlocalhome` (primary)
- **Network**: `\\THLHODY-PC\servernetworktest\CTTT` (shared)
- **Sync**: Every 1 hour
- **Fallback**: Read local → fallback to network

### Package Structure
```
com.ctgraphdep/
├── Application.java                    # Main entry
├── config/                             # Spring config (Security, Async, Scheduling)
├── controller/                         # MVC controllers
│   ├── base/BaseController.java        # Common functionality
│   ├── admin/                          # Admin endpoints
│   ├── user/                           # User endpoints
│   └── team/                           # Team lead endpoints
├── model/                              # Data models (RegisterEntry, WorktimeEntry, etc.)
├── service/                            # Core business logic
│   ├── UserLoginMergeServiceImpl.java  # Login merge coordinator
│   ├── UserService.java                # User operations
│   ├── AdminBonusService.java          # Bonus calculations
│   ├── cache/                          # Caching services
│   └── result/                         # Service result patterns
├── session/
│   ├── service/                        # Session management
│   │   ├── SessionService.java         # Session lifecycle
│   │   ├── SessionMonitorService.java  # Session monitoring
│   │   └── SessionMidnightHandler.java # Midnight session handling
│   ├── commands/                       # Session command pattern
│   └── query/                          # Session queries
├── worktime/
│   ├── service/                        # Worktime services
│   │   ├── WorktimeOperationService.java
│   │   ├── WorktimeMergeService.java
│   │   └── TeamOperationService.java
│   └── display/                        # Worktime display logic
│       ├── WorktimeDisplayService.java # Display formatting
│       └── StatusDTOConverter.java     # DTO conversion
├── register/
│   ├── service/                        # Register services
│   │   ├── AdminCheckRegisterService.java
│   │   ├── CheckRegisterService.java
│   │   ├── CheckValuesService.java
│   │   ├── RegisterMergeService.java
│   │   ├── UserRegisterService.java
│   │   └── AdminRegisterService.java
│   └── util/                           # Register utilities
│       └── CheckRegisterWrapperFactory.java
├── security/                           # Authentication
│   └── CustomAuthenticationProvider.java
├── merge/                              # Universal merge engine
│   ├── engine/UniversalMergeEngine.java # Enum-based merge rules
│   ├── service/UniversalMergeService.java
│   └── constants/MergingStatusConstants.java
├── fileOperations/
│   ├── DataAccessService.java          # Dual-location file I/O
│   └── service/                        # File operation services
│       ├── BackupService.java
│       ├── FileReaderService.java
│       ├── FileWriterService.java
│       └── SyncFilesService.java
├── notification/service/               # Notification services
├── dashboard/service/                  # Dashboard services
├── monitoring/NetworkStatusMonitor.java
├── tray/CTTTSystemTray.java            # System tray integration
└── utils/LoggerUtil.java
```

### Key Patterns

#### 1. Universal Merge Engine (`UniversalMergeEngine.java`)

Enum-based rule engine for conflict resolution.

**Status format**: `{ROLE}_{ACTION}_{TIMESTAMP}`

**Status constants**:
- `USER_INPUT` - Initial creation
- `USER_EDITED_[ts]` - User edit with timestamp
- `ADMIN_EDITED_[ts]` - Admin edit
- `TEAM_EDITED_[ts]` - Team edit
- `ADMIN_FINAL` - Admin locked (unoverridable)
- `TEAM_FINAL` - Team locked (admin can override)
- `USER_IN_PROCESS` - Active session (worktime only)

**Rules** (first match wins):
1. Final states always win
2. Timestamped edits: newer wins
3. Equal timestamps: Admin > Team > User
4. USER_IN_PROCESS cannot be overridden

**Entity adapters**: `RegisterWrapperFactory`, `CheckRegisterWrapperFactory`, `WorktimeWrapperFactory`

#### 2. Data Access Layer (`DataAccessService.java`)

All file operations go through this service.

```java
// Reading (local first, fallback to network)
ServiceResult<List<RegisterEntry>> result = systemAvailabilityService.loadData(path, RegisterEntry.class);

// Writing (both local + network)
ServiceResult<Void> result = systemAvailabilityService.saveData(path, entries);
```

**Backup**: 3 levels (level1: 3 files, level2: 5 files, level3: 10 files), 30-day retention

#### 3. Service Result Pattern (`ServiceResult.java`)

Services return `ServiceResult<T>` instead of throwing exceptions.

**Error types**: VALIDATION_ERROR, SYSTEM_ERROR, BUSINESS_ERROR, NOT_FOUND, UNAUTHORIZED

```java
if (result.isSuccess()) {
    List<RegisterEntry> entries = result.getData();
} else {
    String error = result.getErrorMessage();
}
```

#### 4. Login Merge Coordination (`UserLoginMergeServiceImpl.java`)

Orchestrates merges when users log in.

**Patterns by role**:
- `NORMAL_REGISTER_ONLY` (USER, TEAM_LEADER): register + worktime merge
- `CHECK_REGISTER_ONLY` (CHECKING): check register merge only
- `BOTH_REGISTERS` (USER_CHECKING, TL_CHECKING): all merges
- `NO_MERGES` (ADMIN): none

Network-aware: queues when offline, retries when network available.

### Data Flow Examples

#### User Session Workflow
1. `session.service.SessionService.startSession()` → creates `session_{user}_{year}.json`
2. Status: `USER_IN_PROCESS`
3. `session.service.SessionMonitorService` syncs every 30 min
4. Admin can view but not modify (merge engine blocks)
5. `session.service.SessionService.stopSession()` → status: `USER_INPUT`
6. Admin can now edit → `ADMIN_EDITED_{timestamp}`

#### Registration Approval Flow
1. User creates entry → `registru_{user}_{year}_{month}_{day}.json` (status: USER_INPUT)
2. Team reviews → `check_registru_...json` (status: TEAM_EDITED_{ts})
3. Admin approves → `admin_registru_...json` (status: ADMIN_FINAL)
4. User login → `register.service.RegisterMergeService` merges admin → user local
5. User sees updated entries

#### Worktime Display Flow
1. Raw worktime data loaded by `worktime.service.WorktimeOperationService`
2. `worktime.display.WorktimeDisplayService` formats for presentation
3. `worktime.display.StatusDTOConverter` converts to DTOs for frontend
4. Controllers use display service for consistent formatting across pages

### Configuration (`application.properties`)

**File patterns**:
```properties
dbj.dir.format.session=session_%s_%d.json
dbj.dir.format.worktime=worktime_%s_%d_%02d.json
dbj.dir.format.register=registru_%s_%d_%d_%02d.json
dbj.dir.format.check.register=check_registru_%s_%d_%d_%02d.json
dbj.dir.format.admin.register=admin_registru_%s_%d_%d_%02d.json
```

**Intervals**:
```properties
app.sync.interval=3600000                # 1 hour
app.session.sync.interval=1800000        # 30 minutes
app.session.monitoring.interval=5        # 5 min (dev), 30 (prod)
```

### Security

**Roles**: ROLE_ADMIN, ROLE_USER, ROLE_TEAM_LEADER, ROLE_CHECKING, ROLE_USER_CHECKING, ROLE_TL_CHECKING

User data in JSON files: `dbj/login/users/user_{username}_{year}.json`

Session timeout: 30 minutes

### System Tray
- Non-headless mode (`java.awt.headless=false`)
- Right-click: Open App, Settings, Exit
- Graceful shutdown handler

## Implementation Rules

### Merge System
- **Never bypass** `UniversalMergeEngine`
- **Always use** `MergingStatusConstants` (never hardcode)
- Timestamps: epoch milliseconds
- Admin wins on equal timestamps
- Never override `USER_IN_PROCESS`
- Use wrapper factories

### File Operations
- **Never use** `java.io.File` directly → use `DataAccessService`
- Always handle network failures (fallback to local)
- Backup before destructive operations
- Use configured patterns from `application.properties`

### Service Layer
- Return `ServiceResult<T>` (never throw)
- Use appropriate error types
- Log with context (username, operation, timestamp)
- Validate inputs
- Handle null/empty gracefully

### Controller Layer
- Extend `BaseController`
- Use `@PreAuthorize` for roles
- Handle `ServiceResult` errors with user-friendly messages
- Never expose internal errors
- Provide fallback data on errors

### Logging
- Use `LoggerUtil`, not SLF4J directly
- Format: `LoggerUtil.info(this.getClass(), "message")`
- Include context: username, operation, timestamp
- Rotation: 10MB, 10 files

### Async Operations
- Use `@Async` for long operations
- Return `CompletableFuture<T>`
- Handle exceptions (don't propagate)
- Log start/completion

---

# Part 3: Frontend (Thymeleaf)

## Structure

**Templates**: `src/main/resources/templates/`
- `layout/main.html` - Main layout
- `admin/` - Admin pages (register-admin.html, worktime-admin.html, bonus.html)
- `user/` - User pages (register.html, session.html, settings.html)
- `team/` - Team pages (check-register.html)
- `dashboard/dashboard.html`
- `login.html`

**Static**: `src/main/resources/static/`
- `css/` - Base, layouts, components, utilities
- `js/` - default.js (global), page-specific JS
- `images/`, `icons/`

## Thymeleaf Layout

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/main}">
<head><title>Page Title</title></head>
<body>
    <div layout:fragment="content">
        <!-- Content here -->
    </div>
    <th:block layout:fragment="scripts">
        <!-- Scripts here -->
    </th:block>
</body>
</html>
```

**Common model attributes** (from `BaseController`):
- `version`, `currentDateTime`, `currentUser`, `userName`, `networkAvailable`

## CSS Architecture

**Variables** (`css/base/variables.css`):
```css
:root {
    --color-primary: #3498db;
    --color-success: #2ecc71;
    --spacing-md: 16px;
    --header-height: 60px;
}
```

**Naming**: BEM-like (`.register-form`, `.register-form__input`, `.register-form__input--disabled`)

**Utilities**: `mt-2`, `mb-3`, `px-4`, `text-bold`, `d-flex`

## JavaScript Architecture

### Global JS (`js/default.js`)
- CSRF token handling
- Toast notifications: `showToast(message, type)`
- Modal management: `showModal(id)`, `hideModal(id)`
- Form validation helpers

### Page-Specific Pattern
```javascript
const RegisterUser = (function() {
    'use strict';

    function init() {
        setupEventListeners();
        loadEntries();
    }

    return { init, refreshEntries: loadEntries };
})();

document.addEventListener('DOMContentLoaded', RegisterUser.init);
```

### CSRF for AJAX
```javascript
const token = document.querySelector('meta[name="_csrf"]').content;
const header = document.querySelector('meta[name="_csrf_header"]').content;

fetch('/api/endpoint', {
    method: 'POST',
    headers: { [header]: token, 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
});
```

## Development Scenarios

### Add New Page
1. Create template in appropriate directory
2. Extend `layout:decorate="~{layout/main}"`
3. Create CSS in `css/`
4. Create JS in `js/`
5. Add controller endpoint
6. Add navigation link (if needed)

### Add AJAX Endpoint
1. Create REST controller with `@ResponseBody`
2. Return `ResponseEntity<T>`
3. Use `ServiceResult<T>` in service
4. Create JS client function
5. Handle success/error in JS

## Best Practices

**CSS**:
- Mobile-first responsive
- Use CSS variables
- BEM naming
- Shallow selectors (max 3 levels)

**JavaScript**:
- IIFE pattern
- `'use strict'`
- Handle errors gracefully
- Validate inputs
- Cache DOM queries
- Use event delegation

**Thymeleaf**:
- Extend main layout
- Use fragments
- `th:text` for text (auto-escapes)
- Include CSRF in forms
- Use `@{/path}` for URLs

---

## Summary

Three main parts:
1. **Installer** - Inno Setup + PowerShell
2. **Backend** - Spring Boot with dual-location sync + universal merge engine
3. **Frontend** - Thymeleaf + vanilla JS + modular CSS

Time tracking and project management with offline capability and auto-sync.