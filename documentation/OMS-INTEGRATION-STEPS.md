Step-by-Step OMS Integration Process

Phase 1: File-Based Security Storage

1.1 Create OMS Credential Storage
- Create OmsCredentialFileManager.java
- Store encrypted credentials in {user.home}/.cttt/oms-credentials.enc
- Use AES encryption with application-specific key
- Store: username, encrypted password, JWT token, token expiry

1.2 OMS Authentication Flow
- User clicks "Connect to OMS" button
- Modal appears with username/password fields
- Credentials encrypted and saved to local file
- JWT token obtained and stored
- Connection status updated in UI

Phase 2: User-Specific JSON Data Files

2.1 Local JSON Structure
// {user.home}/.cttt/oms-data/{username}-oms.json
{
"user": "username",
"lastSync": "2025-09-23T10:30:00Z",
"connectionStatus": "connected",
"workQueue": [
{
"orderId": "OMS25-257603",
"dueDate": "2025-09-24T09:34:55Z",
"priority": true,
"state": "OrderReview",
"client": "craftnorway",
"graphicId": "3189/23/LN"
}
],
"todayTasks": 5,
"overdueTasks": 2,
"syncErrors": []
}

2.2 Network Sync Location
- Sync to existing network folder: {network.path}/oms-data/{username}-oms.json
- Use existing sync mechanism from SyncFilesService
- Real-time sync when JSON updates

Phase 3: Background Data Refresh

3.1 OMS Data Fetching Service
- OmsDataRefreshService.java - scheduled service
- Reads credentials from encrypted file
- Fetches user orders, todos, and status
- Processes and writes to local JSON
- Triggers network sync

3.2 Refresh Triggers
- Manual refresh button click
- Scheduled refresh (every 15 minutes)
- Application startup (if credentials exist)
- Dashboard page load

Phase 4: Display Integration

4.1 HTML Fragment Creation
- Create oms-widget.html fragment
- Shows work queue, due dates, priorities
- Displays connection status and last sync time
- Include refresh button

4.2 Integration Points
- User Dashboard: Main OMS widget
- Work pages: Small status indicator
- Header/navigation: Task count badge
- Settings page: OMS configuration

Phase 5: Team Leader Monitoring

5.1 Team OMS Dashboard Card
- Read all {username}-oms.json files from network
- Aggregate data: total tasks, overdue items, connection status
- Show per-user summary with drill-down capability
- Real-time updates as files sync

5.2 Monitoring Features
- Who's connected to OMS
- Task distribution across team
- Overdue alerts by user
- Sync status monitoring

Phase 6: Implementation Flow

6.1 File Structure Creation
src/main/java/com/ctgraphdep/oms/
├── config/OmsConfig.java
├── service/
│   ├── OmsCredentialFileManager.java
│   ├── OmsAuthenticationService.java
│   ├── OmsDataRefreshService.java
│   └── OmsNetworkSyncService.java
├── model/
│   ├── OmsCredentials.java
│   ├── OmsUserData.java
│   └── dto/OmsWidgetDTO.java
└── controller/
├── OmsConnectionController.java
└── api/OmsApiController.java

6.2 Frontend Structure
templates/fragments/
├── oms-widget.html
├── oms-connection-modal.html
└── team-oms-dashboard.html

static/js/
├── oms-connection.js
├── oms-widget.js
└── team-oms-monitor.js

Phase 7: User Experience Flow

7.1 First Time Setup
1. User opens CTTT app
2. Sees "Connect to OMS" button in dashboard
3. Clicks button → connection modal opens
4. Enters OMS credentials
5. System authenticates and stores encrypted credentials
6. JSON file created and synced to network
7. OMS widget becomes active

7.2 Daily Usage
1. User opens app → automatic OMS data refresh
2. Widget shows current work queue
3. Manual refresh updates in real-time
4. Team leader sees updated data automatically
5. Background sync keeps data current

Phase 8: Error Handling & Recovery

8.1 Connection Issues
- Token expiry → auto re-authenticate
- Network issues → show offline status
- Invalid credentials → prompt for re-entry
- Sync failures → retry mechanism

8.2 File Management
- Backup credentials before updates
- Cleanup old JSON data
- Handle file corruption gracefully
- Validate JSON structure on read

Phase 9: Configuration Options

9.1 User Settings
- OMS connection enabled/disabled
- Refresh frequency (5, 15, 30 minutes)
- Widget display preferences
- Network sync enabled/disabled

9.2 Admin Settings
- Global OMS integration toggle
- Network path configuration
- Security policies
- Monitoring thresholds

This approach leverages your existing file sync infrastructure while adding OMS integration seamlessly into the current workflow.
