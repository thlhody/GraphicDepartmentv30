# Multi-Branch Implementation Strategy for Checking System

Here's a comprehensive multi-branch strategy based on my first response, breaking down the implementation of 
the checking system into focused, manageable branches:

## Branch 1: `feature/checking-roles-permissions` **done**

**Purpose:** Set up the foundation by implementing roles and permissions.

**Files to modify:**
- `PermissionConfiguration.java` - Add new checking-related permissions **done**
- `PermissionFilterService.java` - Add new permission constants **done**
- `SecurityConfig.java` - Update security configuration for new roles **done**

**Check paths

Check - getLocalCheckRegisterPath - @Value("${dbj.user.check.register}") - @Value("${dbj.dir.format.check.register}") - writeUserCheckRegister/readUserCheckRegister
Check - getNetworkCheckRegisterPath - @Value("${dbj.user.check.register}") - @Value("${dbj.dir.format.check.register}") - readUserCheckRegister

Lead - getNetworkCheckLeadRegisterPath -  @Value("${dbj.admin.check.register}") - @Value("${dbj.dir.format.admin.check.register}") - readNetworkCheckRegister
Lead - getLocalCheckLeadRegisterPath - @Value("${dbj.admin.check.register}") - @Value("${dbj.dir.format.admin.check.register}") - writeLocalLeadCheckRegister/readLocalLeadCheckRegister

Lead/Admin Check - getLocalCheckBonusPath - @Value("${dbj.admin.bonus}") - @Value("${dbj.dir.format.admin.check.bonus}") - writeLocalLeadCheckBonus/readLocalLeadCheckBonus (with sync) 
Lead/Admin Check - getNetworkCheckBonusPath - @Value("${dbj.admin.bonus}") - @Value("${dbj.dir.format.admin.check.bonus}") - writeNetworkLeadCheckBonus/readNetworkLeadCheckBonus (no sync)

how the logic works: 
User(check) writes in the check_registru_%s_%d_%d_%02d.json locally then the file gets sync to network, 
User(check) reads the check_registru_%s_%d_%d_%02d.json locally for the specific user(check), and network for other users(check).
Lead reads any user(check) check_registru_%s_%d_%d_%02d.json from network writes it locally in lead_check_registru_%s_%d_%d_%02d.json and syncs it to network,
Lead then reads the locally created file from the user(check) lead_check_registru_%s_%d_%d_%02d.json this file gets updated from the specific user(check) from network.
Lead makes changes to the user(check) local lead_check_registru_%s_%d_%d_%02d.json saves it locally then this file gets sync to network.
Lead will calculate bonus from the lead_check_registru_%s_%d_%d_%02d.json and save it locally with sync to network like this lead_check_bonus_%d_%02d.json.
Lead will read the local lead_check_bonus_%d_%02d.json.
Admin will read the network lead_check_bonus_%d_%02d.json and will save it locally as admin_check_bonus_%d_%02d.json this file has no network sync and only read locally.

Each type of work is worth a certain number of points:
Values
LAYOUT	1.0
KIPSTA LAYOUT	0.25
LAYOUT CHANGES	0.25
GPT (Articles)	0.1
GPT (Pieces)	0.1
PRODUCTION	0.1
REORDER	0.1
SAMPLE	0.3
OMS PRODUCTION	0.1
KIPSTA PRODUCTION	0.1

i will give you the logic from excel on how to process these there they are filtered by checkType,
here for each entry will calculate the value of it based on the points provided

NO OF ARTICLES (NORMAL ORDER)=SUMIF(Registry!E:E,"LAYOUT",Registry!F:F)
E - check type
@JsonProperty("checkType")
private String checkType;
F - no of articles
@JsonProperty("articleNumbers")
private Integer articleNumbers;


NO OF ARTICLES (KIPSTA LAYOUT)=SUMIF(Registry!E:E,"KIPSTA LAYOUT",Registry!F:F)
E - check type
@JsonProperty("checkType")
private String checkType;
F - no of articles
@JsonProperty("articleNumbers")
private Integer articleNumbers;

NO OF ARTICLES (LAYOUT CHANGES)=SUMIF(Registry!E:E,"LAYOUT CHANGES",Registry!F:F)
E - check type
@JsonProperty("checkType")
private String checkType;
F - no of articles
@JsonProperty("articleNumbers")
private Integer articleNumbers;

NO OF ARTICLES (GPT)=SUMIF(Registry!E:E,"GPT",Registry!F:F)
E - check type
@JsonProperty("checkType")
private String checkType;
F - no of articles
@JsonProperty("articleNumbers")
private Integer articleNumbers;

ORDER (GPT)=SUMIF(Registry!E:E,"GPT",Registry!G:G)
E - check type
@JsonProperty("checkType")
private String checkType;
G - no of files checked
@JsonProperty("filesNumbers")
private Integer filesNumbers;

ORDER (NORMAL)==SUMIF(Registry!E:E,"PRODUCTION",Registry!G:G)+SUMIF(Registry!E:E,"REORDER",Registry!G:G)
E - check type
@JsonProperty("checkType")
private String checkType;
G - no of files checked
@JsonProperty("filesNumbers")
private Integer filesNumbers;

SAMPLE=SUMIF(Registry!E:E,"SAMPLE",Registry!G:G)
E - check type
@JsonProperty("checkType")
private String checkType;
G - no of files checked
@JsonProperty("filesNumbers")
private Integer filesNumbers;

OMS PRODUCTION=SUMIF(Registry!E:E,"OMS PRODUCTION",Registry!G:G)
E - check type
@JsonProperty("checkType")
private String checkType;
G - no of files checked
@JsonProperty("filesNumbers")
private Integer filesNumbers;

KIPSTA PRODUCTION=SUMIF(Registry!E:E,"KIPSTA PRODUCTION",Registry!G:G)
E - check type
@JsonProperty("checkType")
private String checkType;
G - no of files checked
@JsonProperty("filesNumbers")
private Integer filesNumbers;

**Tasks:**
- Add roles: `CHECKING`, `USER_CHECKING`, `TL_CHECKING`
- Define permissions for checking operations
- Update security rules to accommodate new roles

## Branch 2: `feature/checking-models`

**Purpose:** Create data models and enums for the checking system.

**Files to create/modify:**
- `CheckingStatus.java` - Implement status enum (currently empty)
- `CheckingEntry.java` - Create model similar to RegisterEntry
- `CheckingMergeRule.java` - Create merge rules for synchronization

**Tasks:**
- Define checking status workflow states
- Create checking entry model with all required fields
- Implement merge rules for handling conflicts

## Branch 3: `feature/checking-path-config`

**Purpose:** Update path configuration for storing checking files.

**Files to modify:**
- `PathConfig.java` - Add checking paths and methods

**Tasks:**
- Add directory paths for checking files
- Add file format strings for checking files
- Implement methods to get local/network checking paths
- Create path utilities for admin checking

## Branch 4: `feature/checking-data-access`

**Purpose:** Implement data access methods for the checking system.

**Files to modify:**
- `DataAccessService.java` - Add checking-related methods

**Tasks:**
- Implement read/write methods for user checking
- Implement read/write methods for admin checking
- Add synchronization methods between local and network
- Handle error cases and backups

## Branch 5: `feature/checking-services`

**Purpose:** Create service layer for checking business logic.

**Files to create:**
- `UserCheckingService.java` - Core checking service
- `CheckingValidationService.java` - Validation logic

**Tasks:**
- Implement checking entry loading and saving
- Create validation rules for checking entries
- Implement business logic for checking workflow
- Add methods for analyzing checking data

## Branch 6: `feature/checking-dashboard-cards`

**Purpose:** Add checking cards to dashboards.

**Files to modify:**
- `DashboardConfigurationManager.java` - Add checking cards

**Tasks:**
- Create user checking dashboard card
- Create team lead checking dashboard card
- Create admin checking dashboard card
- Configure card properties, icons, and permissions

## Branch 7: `feature/checking-user-controller`

**Purpose:** Implement user checking controller.

**Files to create:**
- `UserCheckingController.java` - User checking functionality
- `checking.html` - User checking template
- `checking-user.js` - User checking JavaScript
- `checking-user.css` - User checking styles

**Tasks:**
- Create controller endpoints for user checking operations
- Implement user interface for checking entries
- Add form handling for creating/editing checking entries
- Implement client-side validation

## Branch 8: `feature/checking-admin-controller`

**Purpose:** Implement admin checking controller.

**Files to create:**
- `AdminCheckingController.java` - Admin checking functionality
- `checking-admin.html` - Admin checking template
- `checking-admin.js` - Admin checking JavaScript
- `checking-admin.css` - Admin checking styles

**Tasks:**
- Create admin controller for managing all checking entries
- Implement admin interface with advanced features
- Add approval/rejection functionality
- Implement filtering and sorting

## Branch 9: `feature/checking-teamlead-controller`

**Purpose:** Implement team lead checking controller.

**Files to create:**
- `TeamLeadCheckingController.java` - Team lead checking functionality
- `checking-teamlead.html` - Team lead checking template
- `checking-teamlead.js` - Team lead checking JavaScript

**Tasks:**
- Create controller for team lead checking operations
- Implement team-specific views and functionality
- Add team management features
- Implement reporting for team performance

## Branch 10: `feature/checking-export`

**Purpose:** Add export functionality for checking data.

**Files to create:**
- `UserCheckingExcelExporter.java` - Excel export for checking

**Tasks:**
- Implement Excel export for checking entries
- Create customized reporting formats
- Add summary statistics to exports
- Implement filtering for exports

## Branch 11: `feature/checking-integration`

**Purpose:** Final integration and testing.

**Files to potentially modify:**
- Various files for final adjustments
- `default.html` - Update navigation

**Tasks:**
- Merge all feature branches
- Resolve any integration issues
- Perform comprehensive testing
- Update navigation and menus
- Final UI polish

## Implementation Workflow

1. Start with `feature/checking-roles-permissions` as the foundation
2. Move on to `feature/checking-models` and `feature/checking-path-config`
3. Implement `feature/checking-data-access` and `feature/checking-services`
4. Add UI components with the controller branches
5. Finish with `feature/checking-export`
6. Final integration and testing in `feature/checking-integration`

This approach allows you to work on focused aspects of the feature while maintaining the ability to make changes to master independently. 
Each branch can be reviewed, tested, and merged individually, reducing risk and making the process more manageable.



I'll analyze the controllers to find redundant role checks and create a solution in the BaseController to handle redirects more elegantly. 
I'll also fix the dashboardUrl issue to ensure users are redirected to their appropriate dashboards.

## Analysis of Controller Role Checks

Many controllers are using redundant role checks in each method, when a class-level annotation would be more efficient. Additionally, the dashboardUrl determination is repeated across controllers instead of being centralized.

## Solution in BaseController

Let's add these helper methods to the BaseController:

```java
/**
 * Determines the appropriate dashboard URL based on user role
 * @param user The user
 * @return The dashboard URL for the user's role
 */
protected String getDashboardUrlForUser(User user) {
    if (user == null) return "/login";
    
    if (user.hasRole("ADMIN")) {
        return "/admin";
    } else if (user.hasRole("TEAM_LEADER")) {
        return "/team-lead";
    } else if (user.hasRole("TL_CHECKING")) {
        return "/team-checking";
    } else if (user.hasRole("USER_CHECKING")) {
        return "/user-checking";
    } else if (user.hasRole("CHECKING")) {
        return "/checking";
    } else {
        return "/user";
    }
}

/**
 * Gets the current user and adds common model attributes
 * @param userDetails Spring Security UserDetails
 * @param model Spring Model
 * @return The current user or null if authentication failed
 */
protected User prepareUserAndCommonModelAttributes(UserDetails userDetails, Model model) {
    if (userDetails == null) return null;
    
    User currentUser = getUser(userDetails);
    if (currentUser == null) return null;
    
    // Add common attributes
    String dashboardUrl = getDashboardUrlForUser(currentUser);
    model.addAttribute("dashboardUrl", dashboardUrl);
    model.addAttribute("currentSystemTime", getStandardCurrentDateTime().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    model.addAttribute("user", currentUser);
    
    return currentUser;
}
```

## Updated Approach for Controllers

Here's how to refactor controllers to use class-level authorization and the new helper methods:

### 1. UserSessionController Example

```java
@Controller
@RequestMapping("/user/session")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_TEAM_LEADER', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
public class UserSessionController extends BaseController {
    
    // Constructor and other fields
    
    @GetMapping
    public String getSessionPage(
            @AuthenticationPrincipal UserDetails userDetails,
            Model model,
            RedirectAttributes redirectAttributes,
            @RequestParam(name = "skipResolutionCheck", required = false, defaultValue = "false") boolean skipResolutionCheck) {

        try {
            LoggerUtil.info(this.getClass(), "Loading session page at " + getStandardCurrentDateTime());

            // Use the new helper method instead of checkUserAccess
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Rest of the method (no need for dashboard URL calculation)
            // ...

            return "user/session";
        } catch (Exception e) {
            // Error handling
        }
    }
    
    // Other methods similarly updated
}
```

### 2. UserTimeOffController Example

```java
@Controller
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_TEAM_LEADER', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
@RequestMapping("/user/timeoff")
public class UserTimeOffController extends BaseController {
    
    // Fields and constructor
    
    @GetMapping
    public String showTimeOffPage(
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        try {
            LoggerUtil.info(this.getClass(), "Accessing time off page at " + getStandardCurrentDateTime());

            // Use helper method