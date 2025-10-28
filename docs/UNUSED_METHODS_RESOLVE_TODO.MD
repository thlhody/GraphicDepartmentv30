
# Unused methods throughout the program there is no need to check for usages because i have done that.

## 1. I made a comprehensive list of methods that the IDE and my search throughout the program are showing they are not used. You just need to search each on and provide a solution for them

MonthlyOvertimeConsolidationService.detectShortDays(username,userId,year,month) - not used

PathConfig.revalidateLocalAccess() - not used
PathConfig.verifyUserDirectories() - not used
PathConfig.verifyAdminDirectories() - not used

CheckBonusDataService.readTeamLeadCheckBonusFromNetworkOnly( year, month) - not used

SyncResult.java - whole class not used

StatusAssignmentResult.getChangeDescription() - not used
StatusAssignmentResult.wasProtected() - not used

public class GenericEntityWrapper<T> implements UniversalMergeEngine.UniversalMergeableEntity - IDE says: Class can be record class


AuthenticationService:
// ========================================================================
// PUBLIC API METHODS FOR COORDINATION
// ========================================================================

    /**
     * Get current login optimization status
     */
    public String getLoginOptimizationStatus() {
        return loginMergeStrategy.getStatus();
    }
    /**
     * Check if there are pending merge operations for a user
     */
    public boolean hasPendingMerges(String username) {
        return loginMergeService.hasPendingMerges(username);
    }
    /**
     * Get the number of pending merge operations
     */
    public int getPendingMergeCount() {
        return loginMergeService.getPendingMergeCount();
    }
    /**
     * Force retry of pending merges (for manual intervention)
     */
    public void retryPendingMerges() {
        loginMergeService.retryPendingMerges();
    }
    /**
     * Force full cache refresh for a user (for manual intervention)
     */
    public void forceFullCacheRefresh(String username) {
        loginCacheService.forceFullCacheRefresh(username);
    }


    CustomAuthenticationProvider.java - IDE says: Class can be record class

    AllUsersCacheEntry.updateUserInfo(String name, String role, Integer employeeId, Integer schedule, Integer paidHolidayDays) - not used
    AllUsersCacheEntry.initializeFromUserData(String username, Integer userId, String name, String role, String defaultStatus) - not used
    AllUsersCacheEntry.isAdminRole(String role) - not used

    MainDefaultUserContextCache.invalidateCache() - not used
    MainDefaultUserContextService.getElevatedAdminUser() - not used

    MetricsCacheService.hasStandardHoursCached(String username, int year, int month) - not used
    MetricsCacheService.hasLiveWorkHoursCached(String username, int year, int month) - not used
    MetricsCacheService.invalidateUser(String username) - not used
    MetricsCacheService.clearAll() - not used
    MetricsCacheService.getCacheStats() - not used

    RegisterCacheEntry.getEntryCount() - not used
    RegisterCacheEntry.getMonthKey() - not used

    RegisterCheckCacheEntry.getMonthKey() - not used
    RegisterCheckCacheService.setCurrentUser(String username, Integer userId)
    RegisterCheckCacheService.deleteEntry(String username, Integer userId, Integer entryId, int year, int month)
    RegisterCheckCacheService.clearAllCache()
    RegisterCheckCacheService.getCacheStatus()
    
    LoggerUtil.error(this.getClass(), String.format("Error writing cache to file for %s - %d/%d: %s", cacheEntry.getUsername(), cacheEntry.getMonth(), cacheEntry.getYear(), e.getMessage()), e); IDE says : Method invocation 'getUsername' may produce 'NullPointerException'

    SessionCacheEntry.updateCalculatedValues(WorkUsersSessionsStates session, String source) - not used
    SessionCacheEntry.isDirty() - not used
    SessionCacheEntry.markClean(long writeTimestamp) - not used
    SessionCacheEntry.getTimeSinceLastWrite() - not used
    SessionCacheEntry.getCacheVersion() - not used
    SessionCacheEntry.getDataSource() - not used
    SessionCacheEntry.isActiveSession() - not used
    SessionCacheService.isSessionCacheHealthy(String username) - not used

    TimeOffCacheEntry.isDirty() - not used
    TimeOffCacheEntry.markClean() - not used
    TimeOffCacheEntry.getYearKey() - not used

    TimeOffCacheService.addTimeOffToCacheWithoutBalanceUpdate(String username, Integer userId, int year, List<LocalDate> dates, String timeOffType) - IDE says: Return value of the method is never used
    TimeOffCacheService.cleanupExpiredSessions()
    TimeOffCacheService.getSummary(String username, int year)
    TimeOffCacheService.hasActiveSession(String username, int year)
    TimeOffCacheService.getCacheStatistics()
    TimeOffCacheService.buildSummaryFromTracker(TimeOffTracker tracker, String username, int year) - IDE says: Parameter 'year' is never used

    WorktimeCacheEntry.addEntry(WorkTimeTable entry) - not used
    WorktimeCacheEntry.removeEntry(Integer userId, java.time.LocalDate date) - not used
    WorktimeCacheEntry.isDirty() - not used
    WorktimeCacheEntry.markClean() - not used
    WorktimeCacheEntry.getMonthKey() - not used
    WorktimeCacheService.switchUserToMonth(String username, Integer userId, int newYear, int newMonth) - not used
    WorktimeCacheService.invalidateAllUserSessions(String username) - not used
    WorktimeCacheService.cleanupExpiredSessions() - not used
    WorktimeCacheService.hasActiveMonthSession(String username, int year, int month) - not used
    WorktimeCacheService.getCacheStatistics() - not used
    WorktimeCacheService.getUserCacheStatus(String username) - not used
    WorktimeCacheService.forceRefreshMonthFromFile(String username, Integer userId, int year, int month) - not used


    ServiceResult.validationError(String message) - not used
    ServiceResult.getDataOptional() - not used
    ServiceResult.getDataOrDefault(T defaultValue) - not used
    ServiceResult.ifSuccess(Consumer<T> action) - not used
    ServiceResult.ifFailure(Consumer<String> action) - not used
    ServiceResult.flatMap(Function<T, ServiceResult<U>> mapper) - not used
    ServiceResult.combine(ServiceResult<T>... results) - not used
    ServiceResult.combinePartial(ServiceResult<T>... results) - not used

    ValidatioNServiceResult.getAllErrorsAsString() - not used
    ValidatioNServiceResult.getAllErrorMessages() - not used
    ValidatioNServiceResult.toServiceResult(T data) - not used

    CalculationService.minutesToHH(Integer minutes) - not used
    CalculationService.calculateDiscardedMinutes(int totalMinutes, int schedule) - not used
    CalculationService.isLunchBreakDeducted(int minutes, int schedule) - not used
    CalculationService.calculateWorkDays(Integer year, Integer month) - not used
    CalculationService.formatDateTime(LocalDateTime dateTime) - not used
    CalculationService.isValidEntryForCalculation(WorkTimeTable entry) - not used
    CalculationService.hasExceededMaxWorkHours(WorkUsersSessionsStates session, LocalDateTime currentTime) - not used

    OnlineMetricsService.forceRefreshMetrics() - not used

    ActivateHourlyMonitoringCommand.java - whole class not used why does this command is not used

    GetCurrentSessionQuery.java  - IDE says: Class can be record class
    GetUnresolvedEntriesQuery.java - IDE says: Class can be record class
    NavigationContextQuery.java - IDE says: Class can be record class
    UnresolvedWorkTimeQuery.java - IDE says: Class can be record class
    WorkScheduleQuery.java - IDE says: Class can be record class
    WorktimeResolutionQuery.java - IDE says: Class can be record class

    public static WorkUsersSessionsStates updateSession(WorkUsersSessionsStates session, Consumer<SessionUpdateBuilder> updates) - IDE says: Return value of the method is never used

    SessionEntityBuilder.SessionUpdateBuilder(WorkUsersSessionsStates session).lastTemporaryStopTime(LocalDateTime time) - not used

    SessionSpecialDayDetector.updateWorktimeEntryWithSpecialDayLogic(WorkUsersSessionsStates session, String username, Integer userId, SessionContext context) - not used
    SessionSpecialDayDetector.needsSpecialProcessing(DayType dayType) - not used

    SessionValidator.isCompletedSession(WorkUsersSessionsStates session, Class<?> loggerClass) - not used

    SessionContext.calculateTotalTempStopMinutes(WorkUsersSessionsStates session, LocalDateTime currentTime) - not used

    CommandExecutorUtil.executeCommandWithDefault(String commandName, Class<?> loggerClass, Supplier<T> execution, T defaultValue) - not used


### For most of them you need to understand the methods and find out if they can be used , as for the ones that handle Cache i think its better to analyze the Utility Controller and figure out if they can be used  by user to clean/reset cache and make full use of cache methods from cache services. As for The CalculationService methods check where they  can be used since they are made from CalculateWorkHoursUtil.

