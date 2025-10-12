# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**CTGraphDep Web Application (CTTT)** - A Spring Boot-based time tracking, project management, and team coordination system with dual-location synchronization (local and network paths).

- **Version**: 7.2.1
- **Framework**: Spring Boot 3.2.0
- **Java**: 17
- **Build Tool**: Maven
- **Database**: H2 (in-memory)
- **Frontend**: Thymeleaf templates with HTML/CSS/JavaScript

---

# Part 1: Installer (Inno Setup & PowerShell Scripts)

## Overview

The installer system uses **Inno Setup** (Windows) with PowerShell automation scripts for installation, updates, and uninstallation.

## Build Process

The Maven build automatically creates the Windows installer:

```bash
# Package creates both JAR and installer
mvn package
```

**Build flow:**
1. Compiles application → `target/ctgraphdep-web.jar`
2. Copies JAR to `installer/dist/bin/ctgraphdep-web.jar`
3. Runs Inno Setup compiler → `target/installer-output/CTTT_Setup_7.2.1.exe`

## Installer Configuration

**Main file:** `installer/setup.iss`

**Key settings:**
- **App ID**: `{38166b65-a6ca-4a09-a9cb-0f5f497c5dca}` (never change - used for updates)
- **Default Install Dir**: `C:\Program Files\CreativeTimeAndTaskTracker`
- **Default Network Path**: `\\grafubu\A_Registru graficieni\CTTT`
- **Compression**: LZMA2 ultra64 with solid compression
- **Privileges**: Admin required
- **Architecture**: 64-bit compatible

**Installer types supported:**
- New Installation
- Update Existing Installation
- Reinstall (complete refresh)
- Uninstall

## PowerShell Scripts

Located in `installer/dist/scripts/`:

### Core Installation Scripts

**`install.ps1`** - Main installation script
- Creates directory structure
- Configures application properties
- Sets up network paths
- Initializes logging
- Creates Windows startup shortcut
- Requires admin privileges

**`update.ps1`** - Updates existing installation
- Backs up configuration
- Replaces JAR file
- Preserves user settings and data
- Migrates configuration if needed

**`reinstall.ps1`** - Complete reinstallation
- Uninstalls existing version
- Clears all configuration
- Performs fresh install
- Resets all settings to defaults

**`uninstall.ps1`** - Removes application
- Stops running processes
- Removes application files
- Cleans up configuration
- Removes startup shortcuts
- Optional: Preserves user data

### Utility Scripts

**`start-app.ps1`** - Application launcher
- Starts the Spring Boot JAR
- Configures JVM parameters
- Sets up system tray mode (non-headless)
- Redirects logs to configured location

**`configure-port.ps1`** - Port configuration
- Validates port availability
- Updates `application.properties`
- Handles port conflicts
- Default: 8447

**`create-hosts.ps1`** - Hosts file management
- Adds `CTTT` hostname entry
- Maps to 127.0.0.1
- Enables access via http://CTTT:8447
- Requires admin privileges

**`create-ssl.ps1`** - SSL certificate generation
- Generates self-signed certificates (if needed)
- Currently SSL is disabled in development

**`test-network.ps1`** - Network path validation
- Tests network path accessibility
- Validates permissions
- Reports connection status
- Used during installation to verify network path

**`log-manager.ps1`** - Log rotation and management
- Rotates log files (10MB limit, 10 files)
- Archives old logs
- Cleans up expired logs
- Used by other scripts

**`show-startup-notification.ps1`** - Startup notification
- Shows notification when app starts
- Used by startup shortcut

**`cttt-NewStartupShortcut.ps1`** - Startup shortcut creation
- Creates Windows startup folder shortcut
- Configures to run on user login
- Hidden window mode

## Installation Directory Structure

```
C:\Program Files\CreativeTimeAndTaskTracker\
├── bin/
│   └── ctgraphdep-web.jar          # Main application JAR
├── config/
│   └── application.properties       # Configuration overrides
├── graphics/
│   └── ct3logoicon.ico              # Application icon
├── logs/
│   └── ctgraphdep-logger.log        # Application logs
└── scripts/
    └── *.ps1                         # PowerShell scripts
```

## Configuration During Installation

The installer prompts for:
1. **Installation directory** (default: `C:\Program Files\CreativeTimeAndTaskTracker`)
2. **Network path** (default: `\\grafubu\A_Registru graficieni\CTTT`)
3. **Installation type** (new, update, reinstall, uninstall)

These values are written to `config/application.properties`:
```properties
app.paths.network=\\grafubu\A_Registru graficieni\CTTT
app.home=C:\Program Files\CreativeTimeAndTaskTracker
server.port=8447
```

## Important Implementation Rules

**When modifying installer:**
- Never change the App ID in `setup.iss` - breaks update detection
- Always increment version number in both `pom.xml` and `setup.iss`
- Test all installation types (new, update, reinstall, uninstall)
- Preserve user data during updates
- Validate network paths before proceeding

**PowerShell scripts:**
- All must require PowerShell 5.1+
- Installation/uninstallation scripts require admin privileges (`#Requires -RunAsAdministrator`)
- Use `Write-Log` function from `log-manager.ps1` for consistent logging
- Handle errors gracefully with user-friendly messages
- Always validate paths before file operations

**Inno Setup sections:**
- `[Setup]` - Application metadata and installer behavior
- `[Files]` - Files to include in installer
- `[Run]` - Commands to execute during installation
- `[UninstallRun]` - Commands to execute during uninstallation
- `[Code]` - Pascal scripting for custom logic

---

# Part 2: Backend (Java/Spring Boot Application)

## Essential Commands

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Run single test class
mvn test -Dtest=BonusCalculatorTest

# Run single test method
mvn test -Dtest=BonusCalculatorTest#testMethodName

# Run application
mvn spring-boot:run

# Package (creates JAR + installer)
mvn package
```

### Application Access
- **URL**: http://localhost:8447 or http://CTTT:8447
- **Default Credentials**: admin/admin
- **Database**: H2 in-memory (no console exposed)

## Architecture Overview

### Core System Concepts

**Dual-Location Synchronization**: The application operates with both local and network file storage paths, synchronizing data between them. This enables offline work with automatic sync when network becomes available.

- **Local Path**: `D:\serverlocalhome` (or configured install dir) - Primary working directory
- **Network Path**: `\\THLHODY-PC\servernetworktest\CTTT` (configurable) - Shared team storage
- **Sync Interval**: 1 hour (configurable via `app.sync.interval`)
- **Fallback Logic**: Always reads from local first, falls back to network if local unavailable

**Universal Merge System**: All data entities (worktime, register, check register) use a timestamp-based merge engine for conflict resolution across local/network and user/admin/team contexts.

### Package Structure

```
com.ctgraphdep/
├── Application.java              # Main entry point (@SpringBootApplication)
├── config/                       # Spring configuration classes
│   ├── SecurityConfig.java       # Spring Security configuration
│   ├── AsyncConfig.java          # Async task executor
│   ├── SchedulingConfiguration.java  # Scheduled tasks
│   └── WebMvcConfig.java         # Web MVC configuration
├── controller/                   # MVC controllers
│   ├── base/                     # BaseController with common functionality
│   ├── admin/                    # Admin-only endpoints
│   ├── user/                     # User endpoints
│   ├── team/                     # Team lead endpoints
│   └── api/                      # REST API endpoints
├── model/                        # Data models and DTOs
│   ├── RegisterEntry.java        # Project registration entry
│   ├── WorktimeEntry.java        # Worktime tracking entry
│   ├── RegisterCheckEntry.java   # Check register entry
│   ├── User.java                 # User model
│   └── dto/                      # Data transfer objects
├── service/                      # Business logic layer
│   ├── UserLoginMergeServiceImpl.java  # Login merge coordinator
│   ├── SessionService.java       # User session management
│   ├── SessionMonitorService.java  # Session monitoring/sync
│   └── cache/                    # Caching services
├── security/                     # Authentication and authorization
│   ├── CustomAuthenticationProvider.java  # Custom auth logic
│   ├── CustomUserDetailsService.java  # Loads users from JSON files
│   └── AuthenticationService.java  # Authentication orchestration
├── merge/                        # Universal merge engine
│   ├── engine/
│   │   └── UniversalMergeEngine.java  # Enum-based merge rules
│   ├── constants/
│   │   └── MergingStatusConstants.java  # Status value constants
│   ├── enums/
│   │   └── EntityType.java       # Entity type enumeration
│   └── wrapper/
│       └── GenericEntityWrapper.java  # Wrapper interface for entities
├── fileOperations/               # File I/O abstraction
│   └── DataAccessService.java    # Dual-location file operations
├── session/                      # Session tracking
├── worktime/                     # Worktime tracking logic
│   └── service/
│       └── WorktimeLoginMergeService.java  # Worktime merge on login
├── register/                     # Project registration management
│   ├── service/
│   │   ├── UserRegisterService.java  # User register operations
│   │   ├── AdminRegisterService.java  # Admin register operations
│   │   ├── RegisterMergeService.java  # Register merge logic
│   │   └── CheckRegisterService.java  # Check register operations
│   └── util/
│       └── RegisterWrapperFactory.java  # Adapter for merge engine
├── checkregister/                # Registration validation workflow
│   └── util/
│       └── CheckRegisterWrapperFactory.java  # Adapter for merge engine
├── dashboard/                    # Dashboard calculations
├── monitoring/                   # Health checks and network monitoring
│   ├── NetworkStatusMonitor.java  # Network availability tracking
│   └── events/
│       └── NetworkStatusChangedEvent.java  # Network status change event
├── notification/                 # Alert queue and delivery
├── tray/                         # System tray integration
│   └── CTTTSystemTray.java       # System tray icon and menu
├── utils/                        # Utilities
│   ├── LoggerUtil.java           # Logging wrapper
│   └── DateUtil.java             # Date handling utilities
└── validation/                   # Custom validators
    ├── TimeValidationService.java  # Period validation
    └── TimeValidationFactory.java  # Validation command factory
```

### Key Architectural Patterns

#### 1. Universal Merge Engine

**Location:** `merge/engine/UniversalMergeEngine.java`

Enum-based rule engine for merging conflicting data entries between local/network and user/admin/team contexts.

**Priority levels (highest to lowest):**
1. **Final State** - Locked entries (ADMIN_FINAL, TEAM_FINAL)
2. **Versioned Edits** - Timestamped edits with conflict resolution
3. **Protected States** - Special states (USER_IN_PROCESS for worktime)
4. **Base States** - Initial input states

**Status format:** `{ROLE}_{ACTION}_{TIMESTAMP}`

**Status Constants** (`merge/constants/MergingStatusConstants.java`):
```
USER_INPUT              - Initial creation by user
USER_EDITED_[ts]        - User edit with epoch timestamp
ADMIN_EDITED_[ts]       - Admin edit with epoch timestamp
TEAM_EDITED_[ts]        - Team lead edit with epoch timestamp
ADMIN_FINAL             - Admin locked (cannot be overridden)
TEAM_FINAL              - Team locked (admin can override)
USER_IN_PROCESS         - Active session (worktime only)
```

**Conflict resolution rules:**
- Timestamped edits: Newer timestamp wins
- Equal timestamps: Admin > Team > User
- Final states always win
- USER_IN_PROCESS cannot be overridden by admin (user must close session)

**Entity adapters:**
- `RegisterWrapperFactory` - Adapts RegisterEntry
- `CheckRegisterWrapperFactory` - Adapts RegisterCheckEntry
- `WorktimeWrapperFactory` - Adapts WorktimeEntry

All implement `GenericEntityWrapper` interface.

#### 2. Data Access Layer

**Location:** `fileOperations/DataAccessService.java`

Abstracts all file operations with dual-location support and network fallback.

**Key methods:**
- `loadData(path, clazz)` - Reads JSON, tries local first, falls back to network
- `saveData(path, data)` - Writes JSON to both local and network
- `syncToNetwork(localPath, networkPath)` - Explicit sync operation
- `isNetworkAvailable()` - Tests network path accessibility

**JSON persistence:** Uses Jackson for serialization/deserialization

**Backup strategy:**
- 3-level backups: level1_low (3 files), level2_medium (5 files), level3_high (10 files)
- 30-day retention
- Automatic backup on destructive operations
- Extension: `.bak`

#### 3. Service Result Pattern

**Location:** `service/result/ServiceResult.java`

All service methods return `ServiceResult<T>` instead of throwing exceptions.

**Error types:**
- `VALIDATION_ERROR` - Input validation failed
- `SYSTEM_ERROR` - File I/O or system errors
- `BUSINESS_ERROR` - Business logic violations
- `NOT_FOUND` - Entity not found
- `UNAUTHORIZED` - Permission denied

**Usage example:**
```java
ServiceResult<List<RegisterEntry>> result = userRegisterService.loadMonthEntries(username, userId, year, month);

if (result.isSuccess()) {
    List<RegisterEntry> entries = result.getData();
    // Process entries
} else {
    String errorMessage = result.getErrorMessage();
    // Handle error based on result.getErrorType()
}
```

Controllers use `handleServiceError()` helper to convert `ServiceResult` errors to user-friendly messages.

#### 4. Login Merge Coordination

**Location:** `service/UserLoginMergeServiceImpl.java`

Orchestrates all merge operations when users log in, ensuring user sees latest admin/team decisions.

**User data access patterns:**
- `NORMAL_REGISTER_ONLY` - USER, TEAM_LEADER roles
  - Performs register merge + worktime merge
- `CHECK_REGISTER_ONLY` - CHECKING role
  - Performs check register merge only
- `BOTH_REGISTERS` - USER_CHECKING, TL_CHECKING roles
  - Performs both register merges + worktime merge
- `NO_MERGES` - Admin or unknown roles

**Network-aware features:**
- Queues pending merges when network unavailable
- Listens for `NetworkStatusChangedEvent`
- Automatically retries when network becomes available
- Async execution for all merge operations

**Merge flow on login:**
1. `UserLoginMergeServiceImpl.performLoginMerges(username, role)`
2. Determines user access pattern based on role
3. Executes appropriate merges asynchronously:
   - `RegisterMergeService.performUserLoginMerge()` - Admin → User register merge
   - `CheckRegisterService.performCheckRegisterLoginMerge()` - Team → User check register merge
   - `WorktimeLoginMergeService.performUserWorktimeLoginMerge()` - Admin → User worktime merge

#### 5. Controller Hierarchy

**Base controller:** `controller/base/BaseController.java`

Provides common functionality:
- User retrieval from authentication
- Model attribute population
- Date utilities (current date/time)
- Period validation
- Service result error handling

**Controller organization:**
- `admin/` - Admin-only endpoints (registration approval, worktime review, bonus management)
  - `@PreAuthorize("hasRole('ROLE_ADMIN')")`
- `user/` - User endpoints (session tracking, registration submission)
  - `@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_TEAM_LEADER', ...)")`
- `team/` - Team lead endpoints (check register approval)
  - `@PreAuthorize("hasAnyRole('ROLE_TEAM_LEADER', 'ROLE_TL_CHECKING')")`
- `api/` - REST API endpoints (JSON responses)

### Data Flow Examples

#### User Session Workflow

1. User starts work → `SessionService.startSession(username)`
2. Creates `session_{username}_{year}.json` in local path
3. Entry status: `USER_IN_PROCESS`
4. `SessionMonitorService` monitors active sessions (scheduled task)
5. Periodic sync to network path every 30 minutes
6. Admin can view via `AdminWorktimeService.loadUserWorktime()`
7. Admin cannot modify `USER_IN_PROCESS` entries (merge engine blocks)
8. User stops work → `SessionService.stopSession(username)`
9. Status changes to `USER_INPUT`
10. Admin can now edit → status becomes `ADMIN_EDITED_{timestamp}`

#### Registration Approval Flow

1. **User creates entry:**
   - `UserRegisterController.saveEntry()` → `UserRegisterService.saveEntry()`
   - Entry saved to `registru_{username}_{year}_{month}_{day}.json`
   - Status: `USER_INPUT`

2. **Team lead reviews:**
   - `TeamCheckRegisterController` loads user entries
   - Team lead adds review entry in `check_registru_{username}_{year}_{month}_{day}.json`
   - Status: `TEAM_INPUT` or `TEAM_EDITED_{timestamp}`

3. **Admin final approval:**
   - `AdminRegisterController` loads both user and team check entries
   - Admin edits/approves entries
   - Status: `ADMIN_EDITED_{timestamp}` or `ADMIN_FINAL`
   - Saved to `admin_registru_{username}_{year}_{month}_{day}.json` on network

4. **User login merge:**
   - `UserLoginMergeServiceImpl.performLoginMerges(username, role)`
   - `RegisterMergeService.performUserLoginMerge(username)`
   - Loads admin network file → merges with user local file → saves to user local
   - `UniversalMergeEngine` resolves conflicts (admin decisions win)
   - User sees updated entries with admin changes

#### Admin Force Override

Admin can force all entries to become final (nuclear option):

```java
AdminRegisterService.confirmAllAdminChanges(username, year, month)
```

- Sets all entries to `ADMIN_FINAL` status
- Cannot be overridden by user or team
- Used to resolve sync conflicts or enforce decisions

### Configuration Notes

**File path patterns** (defined in `application.properties`):
```properties
dbj.dir.format.session=session_%s_%d.json
dbj.dir.format.worktime=worktime_%s_%d_%02d.json
dbj.dir.format.register=registru_%s_%d_%d_%02d.json
dbj.dir.format.check.register=check_registru_%s_%d_%d_%02d.json
dbj.dir.format.admin.register=admin_registru_%s_%d_%d_%02d.json
```

Format parameters: `String.format(pattern, username, year, month, day)`

**Sync intervals:**
```properties
app.sync.interval=3600000                    # 1 hour
app.session.sync.interval=1800000            # 30 minutes
app.status.update.interval=600000            # 10 minutes
app.session.monitoring.interval=5            # 5 minutes (dev), 30 (prod)
```

**Development mode settings:**
```properties
spring.thymeleaf.cache=false                 # Disable template caching
spring.devtools.restart.enabled=true         # Enable hot reload
spring.thymeleaf.prefix=file:src/main/resources/templates/
spring.web.resources.static-locations=file:src/main/resources/static/
logging.level.com.ctgraphdep=DEBUG
```

### System Tray Integration

**Location:** `tray/CTTTSystemTray.java`

The application runs in system tray (non-headless mode):
- Icon shows application status
- Right-click menu: Open App, Settings, Exit
- Notification support for important events
- Graceful shutdown handler

**Requirements:**
```java
System.setProperty("java.awt.headless", "false");
app.setHeadless(false);
```

**Startup:** Initialized after Spring context ready (`@EventListener(ApplicationReadyEvent.class)`)

### Security Implementation

**Custom authentication:** `security/CustomAuthenticationProvider.java`

- User data stored in JSON files (not database)
- File location: `dbj/login/users/user_{username}_{year}.json`
- Password validation via custom provider
- Session timeout: 30 minutes

**Roles:**
- `ROLE_ADMIN` - Full access
- `ROLE_USER` - Basic user access
- `ROLE_TEAM_LEADER` - Team management
- `ROLE_CHECKING` - Check register access only
- `ROLE_USER_CHECKING` - User + checking access
- `ROLE_TL_CHECKING` - Team leader + checking access

**Role-based endpoints:** Use `@PreAuthorize` annotations on controllers

### Testing Strategy

**Current test coverage:**
- `src/test/java/BonusCalculatorTest.java` - Bonus calculation logic
- `src/test/java/NotificationTest.java` - Notification system
- Most testing is manual via web interface

**When adding tests:**
- Use JUnit 5 (`@Test`, `@BeforeEach`, `@AfterEach`)
- Use Mockito for mocking (`@Mock`, `@InjectMocks`)
- Mock `DataAccessService` to avoid file I/O
- Use `@SpringBootTest` for integration tests
- Test merge scenarios with various status combinations
- Test network fallback logic

**Running tests:**
```bash
mvn test                                    # All tests
mvn test -Dtest=BonusCalculatorTest         # Single test class
mvn test -Dtest=BonusCalculatorTest#testCalculateBonus  # Single test method
```

## Common Development Scenarios

### Adding a New Entity Type

1. Create model class in `model/` package with Lombok annotations
2. Create service in `service/` or dedicated package (e.g., `register/service/`)
3. Create wrapper factory implementing `GenericEntityWrapper` interface
4. Add entity type to `merge/enums/EntityType.java`
5. Update `UniversalMergeEngine` if entity needs special merge rules
6. Create controller in appropriate `controller/` subdirectory
7. Add Thymeleaf templates in `resources/templates/`
8. Add JavaScript in `resources/static/js/`
9. Add CSS in `resources/static/css/`

### Modifying Merge Behavior

All merge logic is centralized in `UniversalMergeEngine` enum. The enum-based rule system evaluates rules top-to-bottom (first match wins).

**To modify:**
1. Locate the relevant enum constant in `UniversalMergeEngine`
2. Update the condition lambda (when rule applies)
3. Update the resolver lambda (how to merge)
4. Ensure timestamp extraction works for custom status formats
5. Test with various status combinations

**Adding new rule:**
```java
NEW_RULE(
    (entry1, entry2, entityType) -> {
        // Condition: when does this rule apply?
        return entry1.hasSpecialCondition();
    },
    (entry1, entry2, entityType) -> {
        // Resolver: which entry wins?
        return entry1;  // or custom merge logic
    }
)
```

### Adding New Configuration Properties

1. Add property to `src/main/resources/application.properties`
2. Access via `@Value("${property.name}")` in Spring components
3. For complex configuration, create `@Configuration` class in `config/` package
4. Use `@ConfigurationProperties` for grouped properties

### Working with File Operations

**Always use `DataAccessService` instead of direct file I/O:**

```java
// Reading
ServiceResult<List<RegisterEntry>> result = dataAccessService.loadData(path, RegisterEntry.class);

// Writing (with backup and sync)
ServiceResult<Void> result = dataAccessService.saveData(path, entries);

// Explicit sync
dataAccessService.syncToNetwork(localPath, networkPath);
```

**Never:**
- Use `java.io.File` directly
- Use `Files.readString()` or `Files.writeString()`
- Access network path without fallback logic

## Important Implementation Rules

### Merge System
- **Never bypass** `UniversalMergeEngine` for data merging
- **Always use** `MergingStatusConstants` for status values (never hardcode strings)
- Timestamps must be epoch milliseconds for proper comparison
- Admin always wins on equal timestamps
- Never override `USER_IN_PROCESS` status (worktime only)
- Use wrapper factories to adapt entities for merge engine

### File Operations
- **Never use** `java.io.File` directly - always use `DataAccessService`
- Always handle network path failures gracefully (fallback to local)
- Backup before destructive operations
- Use configured path patterns from `application.properties`
- JSON files use UTF-8 encoding

### Service Layer
- All service methods return `ServiceResult<T>` (never throw exceptions)
- Use appropriate error types (VALIDATION_ERROR, SYSTEM_ERROR, etc.)
- Log errors with context (username, operation, timestamp)
- Validate inputs before processing
- Handle null/empty data gracefully

### Controller Layer
- Extend `BaseController` for common functionality
- Use `@PreAuthorize` for role-based access control
- Return appropriate HTTP status codes
- Handle `ServiceResult` errors with user-friendly messages
- Never expose internal error details to users
- Always provide fallback data on errors (empty lists, default values)

### Security
- Role-based access enforced via Spring Security
- User details loaded from JSON files, not database
- Session timeout is 30 minutes
- Password validation in `CustomAuthenticationProvider`
- Never log passwords or sensitive data

### Logging
- Use `LoggerUtil` wrapper class, not SLF4J directly
- Format: `LoggerUtil.info(this.getClass(), "message")`
- Include context: username, operation, timestamp
- Log file: `${app.home}/logs/ctgraphdep-logger.log`
- Rotation: 10MB per file, 10 files retained
- Log levels: INFO (production), DEBUG (development)

### Async Operations
- Use `@Async` for long-running operations (merges, sync)
- Configure thread pool in `AsyncConfig`
- Return `CompletableFuture<T>` for coordination
- Handle exceptions in async methods (don't propagate to caller)
- Log async operation start/completion

### Network Operations
- Always check network availability before network operations
- Queue operations when network unavailable
- Listen for `NetworkStatusChangedEvent` to retry
- Use debounce/jitter to avoid network storms
- Timeout network operations appropriately

## Recent Major Refactorings

See `MERGE_SYSTEM_REFACTOR_SUMMARY.md` for details on the Universal Merge Engine migration completed in v7.2.1.

**Key changes:**
- Removed deprecated `SyncStatusMerge`, `RegisterMergeRule`, `CheckRegisterMergeRule` enums
- Migrated all merge operations to `UniversalMergeEngine`
- Implemented timestamp-based conflict resolution
- Added `ADMIN_FINAL` force-override capability
- Introduced `ServiceResult<T>` pattern across all services
- Refactored login merge logic into dedicated `UserLoginMergeServiceImpl`
- Moved register services to `register/` package
- Created wrapper factories for entity adaptation

---

# Part 3: Frontend (HTML/CSS/JavaScript)

## Overview

Frontend uses **Thymeleaf** templates with vanilla JavaScript (no frameworks like React/Vue) and custom CSS.

## Template Structure

**Location:** `src/main/resources/templates/`

```
templates/
├── layout/
│   ├── main.html               # Main layout with header/footer
│   └── fragments/              # Reusable fragments
├── admin/                      # Admin pages
│   ├── register-admin.html     # Admin register view
│   ├── worktime-admin.html     # Admin worktime view
│   └── bonus.html              # Bonus management
├── user/                       # User pages
│   ├── register.html           # User register entry
│   ├── session.html            # Session tracking
│   └── settings.html           # User settings
├── team/                       # Team lead pages
│   └── check-register.html     # Check register approval
├── dashboard/                  # Dashboard pages
│   └── dashboard.html          # Main dashboard
├── status/                     # Status pages
│   └── status.html             # System status view
├── login.html                  # Login page
├── about.html                  # About page
└── utility.html                # Utility tools
```

### Thymeleaf Layout System

**Main layout:** `templates/layout/main.html`

Uses Thymeleaf Layout Dialect (`layout:decorate`, `layout:fragment`)

**Page structure:**
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/main}">
<head>
    <title>Page Title</title>
    <!-- Page-specific styles -->
</head>
<body>
    <div layout:fragment="content">
        <!-- Page content here -->
    </div>

    <th:block layout:fragment="scripts">
        <!-- Page-specific scripts -->
    </th:block>
</body>
</html>
```

**Common model attributes** (added by `BaseController`):
- `version` - Application version
- `currentDateTime` - Current date/time
- `currentUser` - Logged-in user object
- `userName` - User's display name
- `folderStatus` - Network/local folder status
- `networkAvailable` - Boolean network status

### Thymeleaf Expressions

**Variable expressions:** `${...}`
```html
<span th:text="${userName}">Default Name</span>
```

**Selection expressions:** `*{...}` (within `th:object`)
```html
<form th:object="${entry}">
    <input type="text" th:field="*{orderId}" />
</form>
```

**URL expressions:** `@{...}`
```html
<a th:href="@{/user/register(year=${year}, month=${month})}">Link</a>
```

**Iteration:** `th:each`
```html
<tr th:each="entry : ${entries}">
    <td th:text="${entry.date}"></td>
</tr>
```

**Conditionals:** `th:if`, `th:unless`
```html
<div th:if="${errorMessage}" th:text="${errorMessage}"></div>
```

**Fragments:** `th:replace`, `th:insert`
```html
<div th:replace="~{layout/fragments/alerts :: alert-banner}"></div>
```

## Static Resources

**Location:** `src/main/resources/static/`

```
static/
├── css/
│   ├── base/                   # Base styles
│   │   ├── reset.css           # CSS reset
│   │   └── variables.css       # CSS custom properties
│   ├── layouts/                # Layout styles
│   │   ├── main-layout.css     # Main layout grid
│   │   └── header.css          # Header styles
│   ├── components/             # Component styles
│   │   ├── buttons.css         # Button styles
│   │   ├── forms.css           # Form styles
│   │   ├── tables.css          # Table styles
│   │   └── modals.css          # Modal styles
│   ├── features/               # Feature-specific styles
│   ├── utilities/              # Utility classes
│   │   ├── spacing.css         # Margin/padding utilities
│   │   └── text.css            # Text utilities
│   └── vendor/                 # Third-party CSS
├── js/
│   ├── default.js              # Global JavaScript
│   ├── register-user.js        # User register functionality
│   ├── register-admin.js       # Admin register functionality
│   ├── check-register.js       # Check register functionality
│   ├── session.js              # Session tracking
│   ├── dashboard.js            # Dashboard calculations
│   ├── time-management-core.js # Time management core
│   └── tm/                     # Time management modules
│       ├── calculator.js       # Time calculations
│       ├── ui.js               # UI components
│       └── api.js              # API client
├── images/                     # Image assets
├── icons/                      # Icon assets
└── favicon.ico
```

## CSS Architecture

### CSS Custom Properties (Variables)

**Location:** `css/base/variables.css`

```css
:root {
    /* Colors */
    --color-primary: #3498db;
    --color-success: #2ecc71;
    --color-danger: #e74c3c;
    --color-warning: #f39c12;

    /* Typography */
    --font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
    --font-size-base: 14px;

    /* Spacing */
    --spacing-xs: 4px;
    --spacing-sm: 8px;
    --spacing-md: 16px;
    --spacing-lg: 24px;

    /* Layout */
    --header-height: 60px;
    --sidebar-width: 250px;
}
```

### Component Styles

**Naming convention:** BEM-like (Block Element Modifier)

```css
/* Block */
.register-form { }

/* Element */
.register-form__input { }

/* Modifier */
.register-form__input--disabled { }
```

### Utility Classes

**Location:** `css/utilities/`

```html
<!-- Spacing utilities -->
<div class="mt-2 mb-3 px-4">  <!-- margin-top, margin-bottom, padding-x -->

<!-- Text utilities -->
<span class="text-bold text-success text-center">

<!-- Display utilities -->
<div class="d-flex justify-between align-center">
```

## JavaScript Architecture

### Global JavaScript

**Location:** `js/default.js`

Loaded on all pages, provides:
- CSRF token handling for AJAX requests
- Global utility functions
- Toast notification system
- Modal management
- Form validation helpers

**CSRF token setup:**
```javascript
// All AJAX requests automatically include CSRF token
const token = document.querySelector('meta[name="_csrf"]').getAttribute('content');
const header = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

fetch('/api/endpoint', {
    method: 'POST',
    headers: {
        [header]: token,
        'Content-Type': 'application/json'
    },
    body: JSON.stringify(data)
});
```

### Page-Specific JavaScript

**Pattern:** Each major page has its own JavaScript file that exports a namespace object.

**Example:** `js/register-user.js`

```javascript
const RegisterUser = (function() {
    'use strict';

    // Private variables
    let currentYear, currentMonth;

    // Private functions
    function init() {
        setupEventListeners();
        loadEntries();
    }

    function setupEventListeners() {
        document.getElementById('add-entry-btn').addEventListener('click', showAddModal);
    }

    // Public API
    return {
        init: init,
        refreshEntries: loadEntries
    };
})();

// Initialize when DOM ready
document.addEventListener('DOMContentLoaded', RegisterUser.init);
```

### Common JavaScript Patterns

#### 1. Toast Notifications

```javascript
// Success toast
showToast('Entry saved successfully', 'success');

// Error toast
showToast('Failed to save entry', 'error');

// Warning toast
showToast('Network unavailable', 'warning');

// Info toast
showToast('Loading data...', 'info');
```

#### 2. Modal Management

```javascript
// Show modal
showModal('add-entry-modal');

// Hide modal
hideModal('add-entry-modal');

// Modal with callback
showModal('confirm-delete-modal', function() {
    // Handle confirm action
});
```

#### 3. AJAX Requests

```javascript
// GET request
fetchData('/api/entries', { year: 2024, month: 3 })
    .then(data => {
        // Handle success
    })
    .catch(error => {
        showToast('Failed to load data', 'error');
    });

// POST request
postData('/user/register/entry', formData)
    .then(response => {
        showToast('Entry saved', 'success');
        refreshEntries();
    })
    .catch(error => {
        showToast('Save failed', 'error');
    });
```

#### 4. Form Validation

```javascript
// Client-side validation
function validateRegisterEntry(formData) {
    const errors = [];

    if (!formData.date) {
        errors.push('Date is required');
    }

    if (!formData.orderId || formData.orderId.trim() === '') {
        errors.push('Order ID is required');
    }

    if (formData.graphicComplexity < 0 || formData.graphicComplexity > 10) {
        errors.push('Graphic complexity must be between 0 and 10');
    }

    return errors;
}

// Show validation errors
function showValidationErrors(errors) {
    const errorContainer = document.getElementById('validation-errors');
    errorContainer.innerHTML = errors.map(err => `<li>${err}</li>`).join('');
    errorContainer.style.display = 'block';
}
```

#### 5. Dynamic Table Updates

```javascript
// Refresh table with new data
function updateEntriesTable(entries) {
    const tbody = document.querySelector('#entries-table tbody');
    tbody.innerHTML = ''; // Clear existing rows

    entries.forEach(entry => {
        const row = createTableRow(entry);
        tbody.appendChild(row);
    });
}

function createTableRow(entry) {
    const row = document.createElement('tr');
    row.innerHTML = `
        <td>${formatDate(entry.date)}</td>
        <td>${entry.orderId}</td>
        <td>${entry.clientName}</td>
        <td class="actions">
            <button onclick="editEntry(${entry.entryId})">Edit</button>
            <button onclick="deleteEntry(${entry.entryId})">Delete</button>
        </td>
    `;
    return row;
}
```

### Time Management Module

**Location:** `js/time-management-core.js` + `js/tm/`

Modular time tracking system with:
- **Calculator** (`tm/calculator.js`) - Time calculations and validations
- **UI** (`tm/ui.js`) - UI component management
- **API** (`tm/api.js`) - Backend API client
- **Storage** - Local storage management

**Usage:**
```javascript
// Initialize time management
TimeManagement.init({
    containerId: 'time-management-container',
    username: currentUser,
    readonly: false
});

// Calculate worked hours
const workedHours = TimeManagement.calculator.calculateWorkedHours(startTime, endTime, breakMinutes);

// Update UI
TimeManagement.ui.updateDisplay(workedHours);
```

## Common Frontend Development Scenarios

### Adding a New Page

1. Create Thymeleaf template in appropriate directory
2. Extend main layout (`layout:decorate="~{layout/main}"`)
3. Define content fragment (`layout:fragment="content"`)
4. Create page-specific CSS in `css/`
5. Create page-specific JavaScript in `js/`
6. Add controller endpoint to serve the page
7. Add navigation link in layout header (if needed)

### Adding a New Form

1. Create form in Thymeleaf template with `th:action` and `th:object`
2. Add CSRF token: `<input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}" />`
3. Use Thymeleaf field binding: `th:field="*{propertyName}"`
4. Add client-side validation in JavaScript
5. Handle form submission in controller (POST endpoint)
6. Return appropriate redirect or error messages
7. Display success/error messages with flash attributes

### Adding a New AJAX Endpoint

1. Create REST controller method with `@ResponseBody`
2. Return `ResponseEntity<T>` with appropriate HTTP status
3. Handle authentication with `@AuthenticationPrincipal UserDetails`
4. Use `ServiceResult<T>` pattern in service layer
5. Create JavaScript client function to call endpoint
6. Handle success/error responses in JavaScript
7. Update UI based on response

### Adding a New Modal

1. Create modal HTML structure in template
2. Add modal trigger button with `data-modal-target` attribute
3. Style modal with CSS (use existing modal styles as base)
4. Initialize modal in JavaScript
5. Handle modal open/close events
6. Handle form submission within modal (if applicable)
7. Close modal on success

### Styling Best Practices

**Do:**
- Use CSS custom properties (variables) for colors, spacing, etc.
- Follow BEM-like naming convention for components
- Use utility classes for common styles
- Keep selectors shallow (avoid deep nesting)
- Mobile-first responsive design
- Use semantic HTML elements

**Don't:**
- Inline styles (use classes instead)
- !important (except for utilities)
- ID selectors for styling (use for JavaScript hooks)
- Deep selector nesting (max 3 levels)
- Browser-specific prefixes without autoprefixer

### JavaScript Best Practices

**Do:**
- Use IIFE pattern to avoid global namespace pollution
- Use `'use strict'` mode
- Handle errors gracefully (try-catch, promise .catch())
- Validate user input before sending to server
- Show loading states for async operations
- Use event delegation for dynamic elements
- Cache DOM queries

**Don't:**
- Pollute global namespace (use module pattern)
- Use inline event handlers (`onclick=""` in HTML)
- Ignore errors (always handle failures)
- Trust user input (always validate)
- Make synchronous AJAX requests
- Query DOM repeatedly (cache selectors)

## Important Implementation Rules

### Thymeleaf Templates
- Always extend main layout for consistency
- Use fragments for reusable components
- Escape user content by default (Thymeleaf does this automatically)
- Use `th:text` for text content, `th:utext` only for trusted HTML
- Always include CSRF token in forms
- Use Thymeleaf URL expressions for links (`@{/path}`)

### CSS
- Mobile-first responsive design (min-width media queries)
- Use CSS custom properties for theming
- Follow BEM-like naming convention
- Keep specificity low
- Use utility classes for common patterns
- Test in multiple browsers (Chrome, Firefox, Edge)

### JavaScript
- Always validate user input before submission
- Show loading states for async operations
- Handle network errors gracefully
- Use CSRF token for all mutating requests
- Cache DOM queries for better performance
- Use event delegation for dynamic content
- Avoid memory leaks (remove event listeners when needed)

### Accessibility
- Use semantic HTML elements (`<button>`, `<nav>`, `<main>`, etc.)
- Add ARIA labels where needed
- Ensure keyboard navigation works
- Provide text alternatives for images
- Use sufficient color contrast
- Test with screen readers

### Performance
- Minimize JavaScript execution on page load
- Lazy load images where appropriate
- Debounce search inputs
- Throttle scroll/resize handlers
- Minimize reflows/repaints
- Use CSS animations over JavaScript

---

## Summary

This codebase is organized into three main parts:

1. **Installer** - Windows installer using Inno Setup with PowerShell automation
2. **Backend** - Spring Boot application with dual-location sync and universal merge engine
3. **Frontend** - Thymeleaf templates with vanilla JavaScript and modular CSS

All three parts work together to provide a complete time tracking and project management solution with offline capability and automatic synchronization.
