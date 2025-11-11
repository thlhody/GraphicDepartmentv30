# HTML Migration Testing Checklist
**Project:** GraphicDepartmentv30 - JavaScript Refactoring
**Branch:** `claude/javascript-refactoring-011CUqBhABp4dY6DhxX8xGH7`
**Testing Date:** _________________
**Tester:** _________________

---

## 🧪 Testing Instructions

**Before Testing:**
1. Pull latest code: `git pull origin claude/javascript-refactoring-011CUqBhABp4dY6DhxX8xGH7`
2. Clear browser cache completely (Ctrl+Shift+Delete)
3. Hard refresh each page (Ctrl+F5 or Ctrl+Shift+R)
4. Check browser console for errors (F12)

**Marking Results:**
- ✅ = Works perfectly
- ⚠️ = Works but has minor issues
- ❌ = Broken / Not working
- ➖ = Not tested / Skipped

**Recording Issues:**
- Note any console errors
- Describe what doesn't work
- Include steps to reproduce

---

## Phase 4.1 - Core Pages & Layout (12 files)

### Layout & Authentication

#### `layout/default.html` - Main Layout
- [✅] **Status:** ___
- [✅] Page loads without errors
- [✅] Header navigation visible
- [✅] Footer displays correctly
- [✅] Toast notification system works
- [✅] Network status indicator appears
- [✅] ES6 modules load (check console: "✅ Core modules loaded")
**Issues/Notes:**
```


```

#### `login.html` - Login Page
- [✅] **Status:** ___
- [✅] Page loads and displays login form
- [✅] Username/password fields work
- [✅] Login button submits form
- [✅] Error messages display for invalid credentials
- [✅] Successful login redirects to dashboard
- [✅] ES6 module loads (check console)
**Issues/Notes:**
```


```

#### `about.html` - About Page
- [✅] **Status:** ___
- [✅] Page loads without errors
- [✅] Content displays correctly
- [✅] Links work
- [✅] Standalone JS loads (non-ES6 module)
**Issues/Notes:**
```


```

### Dashboard Pages (6 variants)

#### `dashboard/admin/dashboard.html` - Admin Dashboard
- [ ] **Status:** ___
- [✅] Dashboard loads with admin-specific widgets
- [✅] Quick stats display correct numbers
- [✅] All admin action buttons work
- [✅] Navigation links to admin pages work
- [✅] ES6 module loads (check console: "✅ Dashboard - ES6 module loaded")
**Issues/Notes:**
```


```

#### `dashboard/user/dashboard.html` - User Dashboard
- [ ] **Status:** ___
- [ ] Dashboard loads with user-specific widgets
- [ ] Current session info displays
- [ ] Quick actions work (start session, add entry, etc.)
- [ ] Recent entries display
- [ ] ES6 module loads
**Issues/Notes:**
```


```

#### `dashboard/team-lead/dashboard.html` - Team Lead Dashboard
- [ ] **Status:** ___
- [ ] Team overview widgets display
- [ ] Team member list loads
- [ ] Check register links work
- [ ] Team stats visible
- [ ] ES6 module loads
**Issues/Notes:**
```


```

#### `dashboard/checking/dashboard.html` - Checking Dashboard
- [ ] **Status:** ___
- [ ] Check-specific widgets display
- [ ] Pending checks visible
- [ ] Navigation to check pages works
- [ ] ES6 module loads
**Issues/Notes:**
```


```

#### `dashboard/user-checking/dashboard.html` - User+Checking Dashboard
- [ ] **Status:** ___
- [ ] Combined widgets display (user + checking)
- [ ] All user actions work
- [ ] All checking actions work
- [ ] ES6 module loads
**Issues/Notes:**
```


```

#### `dashboard/team-checking/dashboard.html` - Team+Checking Dashboard
- [ ] **Status:** ___
- [ ] Combined widgets display (team + checking)
- [ ] Team management works
- [ ] Checking features work
- [ ] ES6 module loads
**Issues/Notes:**
```


```

### Utility Pages

#### `logs/viewer.html` - Log Viewer
- [ ] **Status:** ___
- [ ] Log viewer loads
- [ ] Log entries display
- [ ] Filter/search works
- [ ] Pagination works
- [ ] Auto-refresh works (if enabled)
- [ ] ES6 module loads
**Issues/Notes:**
```


```

#### `update.html` - Update/Version Info
- [ ] **Status:** ___
- [ ] Page loads
- [ ] Version info displays
- [ ] Update history visible
- [ ] Static page (no scripts)
**Issues/Notes:**
```


```

---

## Phase 4.2 - User & Team Pages (8 files)

### User Pages

#### `user/register.html` - User Work Registry
- [ ] **Status:** ___
- [ ] Page loads with period selector
- [ ] Month/year navigation works
- [ ] Form fields populate correctly
- [ ] **Add Entry:** Can submit new entry
- [ ] **Edit Entry:** Can edit existing entry (pencil icon)
- [ ] **Copy Entry:** Can copy entry (copy icon)
- [ ] **Delete Entry:** Can delete entry (trash icon)
- [ ] **Export:** Export to Excel works
- [ ] Summary statistics calculate correctly
- [ ] Search modal opens (Ctrl+F)
- [ ] Search finds entries correctly
- [ ] ES6 module loads (check console: "✅ User Register - ES6 module loaded")
**Issues/Notes:**
```


```

#### `user/session.html` - Session Management
- [ ] **Status:** ___
- [ ] Page loads with current session info
- [ ] **Start Session:** Can start new session
- [ ] **Stop Session:** Can end active session
- [ ] **Resume Session:** Can resume after ending
- [ ] End time calculator works ("Use Recommended Time" button)
- [ ] Time management fragment loads below
- [ ] **Time-Off Form (in fragment):**
  - [ ] Form fields work
  - [ ] Single-day checkbox hides end date
  - [ ] "Add Time Off" button submits and opens modal
  - [ ] "Holiday Request" button opens modal directly
  - [ ] Page stays on session page (doesn't redirect to time-management)
- [ ] ES6 module loads (check console: "✅ User Session - ES6 module loaded")
**Issues/Notes:**
```


```

#### `user/time-management.html` - Time Management (Standalone)
- [ ] **Status:** ___
- [ ] Page loads with time-off table
- [ ] **Time-Off Form:**
  - [ ] Form fields work
  - [ ] Single-day checkbox hides end date
  - [ ] "Add Time Off" button submits and opens modal
  - [ ] "Holiday Request" button opens modal directly
  - [ ] Page reloads after submission (stays on time-management)
- [ ] **Time-Off Table:**
  - [ ] Displays all entries for the month
  - [ ] CO/CM/CE/CN/CR/D entries display correctly
  - [ ] Inline editing works (click cells)
  - [ ] Delete works (recycle bin icon)
- [ ] **Holiday Modal:**
  - [ ] Opens with correct data pre-filled
  - [ ] Allows editing dates/type
  - [ ] Export to image works
- [ ] ES6 module loads (check console: "✅ Time Management (Standalone) - ES6 module loaded")
**Issues/Notes:**
```


```

#### `user/check-values.html` - Check Values
- [ ] **Status:** ___
- [ ] Page loads with check values form
- [ ] Can view standard time values
- [ ] Can view check type values
- [ ] Form submissions work
- [ ] ES6 module loads
**Issues/Notes:**
```


```

#### `user/team-stats.html` - Team Statistics
- [ ] **Status:** ___
- [ ] Page loads with team stats
- [ ] Period selector works
- [ ] Statistics display correctly
- [ ] Charts render (if any)
- [ ] ES6 module loads
**Issues/Notes:**
```


```

#### `user/check-register.html` - Check Register (User View)
- [ ] **Status:** ___
- [ ] Page loads with period selector
- [ ] Check register table displays
- [ ] Summary statistics calculate
- [ ] **Form Submission:**
  - [ ] Can add check entry
  - [ ] Check type dropdown works
  - [ ] Approval status works
- [ ] **Table Actions:**
  - [ ] Edit works
  - [ ] Delete works
- [ ] Search modal works
- [ ] Stats auto-calculate after 500ms
- [ ] ES6 module loads (check console: "✅ Check Register (User View) - ES6 module loaded")
**Issues/Notes:**
```


```

#### `user/team-check-register.html` - Check Register (Team View)
- [ ] **Status:** ___
- [ ] Page loads with user tabs
- [ ] User selection works (tabs)
- [ ] **Initialize Register:** Can initialize for selected user
- [ ] **Mark All Checked:** Button works
- [ ] Check register loads for selected user
- [ ] Summary statistics calculate
- [ ] **Status Badge Handler:**
  - [ ] Can click badges to change approval status
  - [ ] Status changes save
- [ ] Check bonus fragment loads (if applicable)
- [ ] Stats auto-calculate after 500ms
- [ ] ES6 module loads (check console: "✅ Check Register (Team View) - ES6 module loaded")
**Issues/Notes:**
```


```

#### `user/settings.html` - User Settings
- [ ] **Status:** ___
- [ ] Page loads with user profile info
- [ ] Profile info displays correctly
- [ ] **Password Change:**
  - [ ] Current password field works
  - [ ] New password field works
  - [ ] Form submission works
  - [ ] Success/error messages display
- [ ] Static page (no ES6 modules)
**Issues/Notes:**
```


```

---

## Phase 4.3 - Admin & Status Pages (12 files)

### Admin Pages

#### `admin/register.html` - Admin Register Management
- [ ] **Status:** ___
- [ ] Page loads with period selector
- [ ] User dropdown populates
- [ ] **Load User Data:** Can load user's register
- [ ] Register entries display in table
- [ ] **Bulk Selection:**
  - [ ] Can select multiple entries (checkboxes)
  - [ ] Select all works
- [ ] **Inline Editing:**
  - [ ] Can edit graphic complexity (CG column)
  - [ ] Changes save
- [ ] **Actions:**
  - [ ] Mark selected works
  - [ ] Clear selected works
- [ ] Bonus calculator displays/calculates
- [ ] Summary statistics display
- [ ] Server data loaded (check console for window.serverData)
- [ ] ES6 module loads (check console: "✅ Admin Register - ES6 module loaded")
**Issues/Notes:**
```


```

#### `admin/worktime.html` - Admin Worktime Management
- [ ] **Status:** ___
- [ ] Page loads with calendar view
- [ ] Period selector works (month/year)
- [ ] **Calendar Display:**
  - [ ] All users listed (rows)
  - [ ] All days displayed (columns)
  - [ ] Work entries visible in cells
- [ ] **Inline Editing:**
  - [ ] Can click any cell to edit
  - [ ] Time entry modal opens
  - [ ] Can set start/end time
  - [ ] Can add temp stops
  - [ ] Can set time-off type
  - [ ] Changes save
- [ ] **Special Day Display:**
  - [ ] SN (holiday) displays correctly
  - [ ] CO (vacation) displays correctly
  - [ ] CM (medical) displays correctly
  - [ ] W (weekend) displays correctly
  - [ ] Special day + work time displays (e.g., "CO6" = 6h vacation work)
- [ ] **Summary Column:**
  - [ ] Days worked counts correctly
  - [ ] Time off days count correctly
- [ ] Legend displays at bottom
- [ ] ES6 module loads (check console: "✅ Admin Worktime - ES6 module loaded")
**Issues/Notes:**
```


```

#### `admin/bonus.html` - Admin Bonus Management
- [ ] **Status:** ___
- [ ] Page loads with period selector
- [ ] User dropdown populates
- [ ] **Load Data:** Can load bonus data
- [ ] Bonus calculations display
- [ ] Bonus breakdown shows
- [ ] Can view bonus details
- [ ] ES6 module loads (check console: "✅ Admin Bonus - ES6 module loaded")
**Issues/Notes:**
```


```

#### `admin/check-bonus.html` - Admin Check Bonus
- [ ] **Status:** ___
- [ ] Page loads with period selector
- [ ] User dropdown populates
- [ ] **Load Data:** Can load check bonus data
- [ ] Check bonus calculations display
- [ ] Bonus breakdown shows
- [ ] ES6 module loads (check console: "✅ Admin Check Bonus - ES6 module loaded")
**Issues/Notes:**
```


```

#### `admin/statistics.html` - Admin Statistics
- [ ] **Status:** ___
- [ ] Page loads with period selector
- [ ] **Charts Display:**
  - [ ] Client distribution chart renders
  - [ ] Action type chart renders
  - [ ] Print prep type chart renders
  - [ ] Monthly entries chart renders
  - [ ] Daily entries chart renders
- [ ] Chart.js library loads
- [ ] Server data populates charts (check console for window.clientData, etc.)
- [ ] Period selector reloads with new data
- [ ] ES6 module loads (check console: "✅ Admin Statistics - ES6 module loaded")
**Issues/Notes:**
```


```

#### `admin/holidays.html` - Holiday Management
- [ ] **Status:** ___
- [ ] Page loads
- [ ] Holiday list displays
- [ ] Can view holiday entries
- [ ] Static page (no ES6 modules)
**Issues/Notes:**
```


```

#### `admin/settings.html` - Admin Settings
- [ ] **Status:** ___
- [ ] Page loads
- [ ] Settings display
- [ ] User management table visible
- [ ] Can view/edit users
- [ ] Static page (no ES6 modules)
**Issues/Notes:**
```


```

### Status Pages

#### `status/status.html` - Status Dashboard
- [ ] **Status:** ___
- [ ] Page loads with user status table
- [ ] User list displays
- [ ] **Status Actions:**
  - [ ] Can activate/deactivate users
  - [ ] Can enable/disable users
  - [ ] Status changes save
- [ ] Spinner animations work
- [ ] ES6 module loads (check console: "✅ Status Dashboard - ES6 module loaded")
**Issues/Notes:**
```


```

#### `status/register-search.html` - Register Search
- [ ] **Status:** ___
- [ ] Page loads with search form
- [ ] **Search Fields:**
  - [ ] User dropdown works
  - [ ] Date range works
  - [ ] Client name search works
  - [ ] Order ID search works
- [ ] **Search Results:**
  - [ ] Results display correctly
  - [ ] Can click to view details
  - [ ] Pagination works
- [ ] ES6 module loads (check console: "✅ Register Search - ES6 module loaded")
**Issues/Notes:**
```


```

#### `status/check-register-status.html` - Check Register Status
- [ ] **Status:** ___
- [ ] Page loads
- [ ] User dropdown works
- [ ] Period selector works
- [ ] Check register status displays
- [ ] Redirect script works (inline JS)
- [ ] No ES6 module needed (inline scripts only)
**Issues/Notes:**
```


```

#### `status/worktime-status.html` - Worktime Status
- [ ] **Status:** ___
- [ ] Page loads
- [ ] Worktime table displays
- [ ] Temp stops toggle works (expandable details)
- [ ] Status indicators display correctly
- [ ] Toggle script works (inline JS)
- [ ] No ES6 module needed (inline scripts only)
**Issues/Notes:**
```


```

#### `status/timeoff-history.html` - Time-Off History
- [ ] **Status:** ___
- [ ] Page loads
- [ ] Time-off history table displays
- [ ] Date countdown displays ("X days ago" / "X days to go")
- [ ] Countdown updates dynamically
- [ ] Countdown script works (inline JS)
- [ ] No ES6 module needed (inline scripts only)
**Issues/Notes:**
```


```

---

## Phase 4.4 - Utility Pages (8 files)

#### `utility.html` - Admin Utilities (HYBRID)
- [ ] **Status:** ___
- [ ] Page loads with utility tabs
- [ ] ES6 coordinator loads (check console: "✅ Admin Utilities - ES6 coordinator loaded (hybrid mode)")
- [ ] Legacy jQuery modules load (check console for utility status)
- [ ] **System Overview:**
  - [ ] System health displays
  - [ ] Status summary shows
- [ ] **Individual Utilities (test each tab):**
  - [ ] **Backup Management:** Can create/restore backups
  - [ ] **Cache Monitoring:** Cache stats display
  - [ ] **Session Diagnostics:** Session info displays
  - [ ] **Merge Management:** Merge tools work
  - [ ] **System Health:** Health checks display
  - [ ] **Diagnostics:** System diagnostics work
  - [ ] **Quick Actions:** Action buttons work
- [ ] Auto-refresh works (5 min for status, 3 min for utilities)
- [ ] Global error handler catches errors
- [ ] Utility status checker shows all modules loaded
**Issues/Notes:**
```


```

#### Utility Fragments (7 files)
- [ ] **Status:** ___
- [ ] All fragments load via parent page (utility.html)
- [ ] Each fragment displays correctly in its tab
- [ ] Fragment-specific actions work
- [ ] No separate testing needed (tested via utility.html)
**Issues/Notes:**
```


```

---

## Cross-Cutting Concerns (Test Across All Pages)

### Cache-Busting
- [ ] **Status:** ___
- [ ] Changes to JS files reflect immediately after browser refresh
- [ ] No need to manually clear cache between code changes
- [ ] Timestamp appears in module URLs (check Network tab: `?v=11112025_724...`)
**Issues/Notes:**
```


```

### Console Errors
- [ ] **Status:** ___
- [ ] No JavaScript errors in console across all pages
- [ ] All ES6 modules load successfully
- [ ] Success messages appear: "✅ [Page Name] - ES6 module loaded"
**Issues/Notes:**
```


```

### Legacy Fallback (IE11 Testing - Optional)
- [ ] **Status:** ___
- [ ] Nomodule scripts load in IE11
- [ ] Warning messages appear: "⚠️ [Page Name] - Legacy fallback loaded"
- [ ] All functionality still works
**Issues/Notes:**
```


```

### Navigation
- [ ] **Status:** ___
- [ ] All internal links work
- [ ] Dashboard links navigate correctly
- [ ] Breadcrumb navigation works
- [ ] Back button works as expected
**Issues/Notes:**
```


```

### Forms & AJAX
- [ ] **Status:** ___
- [ ] All forms submit correctly
- [ ] AJAX submissions don't cause page redirects (when expected)
- [ ] Form validation works
- [ ] CSRF tokens included in POST requests
- [ ] Success/error messages display
**Issues/Notes:**
```


```

### Modals & Overlays
- [ ] **Status:** ___
- [ ] Search modals open/close correctly
- [ ] Holiday request modal works
- [ ] Edit modals populate with correct data
- [ ] Modal close buttons work (X, Cancel, backdrop click)
**Issues/Notes:**
```


```

### Toast Notifications
- [ ] **Status:** ___
- [ ] Success toasts display (green)
- [ ] Error toasts display (red)
- [ ] Warning toasts display (yellow)
- [ ] Info toasts display (blue)
- [ ] Toasts auto-dismiss after timeout
**Issues/Notes:**
```


```

---

## Backend Integration Tests

### Time-Off Operations
- [ ] **Status:** ___
- [ ] **Add CO to empty day (tombstone):** Entry created with CO, no work time
- [ ] **Add CO to day with work time:** Work time converted to overtime
- [ ] **Add temp stop to CO day:** Overtime calculation correct (not converted to regular)
- [ ] **Remove CO from day:** Time-off type removed, work time restored
- [ ] **Single-day checkbox:** End date matches start date
**Issues/Notes:**
```


```

### Overtime Calculations
- [ ] **Status:** ___
- [ ] **SN day (holiday) work:** Displayed as overtime, full hours only
- [ ] **CO day (vacation) work:** Displayed as overtime, full hours only
- [ ] **CM day (medical) work:** Displayed as overtime, full hours only
- [ ] **Temp stops on special days:** Overtime reduces by temp stop minutes
- [ ] **Regular day overtime:** Overtime calculated after 8h schedule
**Issues/Notes:**
```


```

### Merge System
- [ ] **Status:** ___
- [ ] User edits have status: USER_EDITED_[timestamp]
- [ ] Admin edits have status: ADMIN_EDITED_[timestamp]
- [ ] Team edits have status: TEAM_EDITED_[timestamp]
- [ ] Admin final locks entry: ADMIN_FINAL
- [ ] Active sessions have status: USER_IN_PROCESS (uneditable by admin)
**Issues/Notes:**
```


```

---

## Performance Tests

### Page Load Times
- [ ] **Status:** ___
- [ ] All pages load within acceptable time (< 2 seconds)
- [ ] Large tables load without freezing browser
- [ ] Charts render quickly
**Issues/Notes:**
```


```

### Memory Leaks
- [ ] **Status:** ___
- [ ] Extended use doesn't cause memory issues
- [ ] Navigation between pages doesn't accumulate memory
- [ ] Modals properly clean up after closing
**Issues/Notes:**
```


```

---

## Summary

### Statistics
- **Total Pages Tested:** ___ / 47
- **Pages Working Perfectly (✅):** ___
- **Pages with Minor Issues (⚠️):** ___
- **Pages Broken (❌):** ___
- **Pages Not Tested (➖):** ___

### Critical Issues Found
```




```

### Minor Issues Found
```




```

### Pages Requiring Immediate Attention
```




```

### Overall Assessment
```
[ ] All critical functionality works
[ ] Ready for production
[ ] Needs bug fixes before deployment
[ ] Requires significant work
```

---

## Next Steps

1. [ ] Fix all critical (❌) issues
2. [ ] Address minor (⚠️) issues
3. [ ] Re-test fixed pages
4. [ ] Document any workarounds
5. [ ] Update migration status
6. [ ] Prepare for production deployment

---

**Testing Completed:** _________________ (Date/Time)
**Signed Off By:** _________________
