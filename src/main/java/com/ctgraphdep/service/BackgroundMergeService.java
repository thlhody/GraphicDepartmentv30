package com.ctgraphdep.service;

import com.ctgraphdep.model.User;
import com.ctgraphdep.worktime.service.WorktimeLoginMergeService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Background merge service that performs data merge operations asynchronously.
 * This service runs merge operations in a separate thread to avoid blocking login.
 */
@Service
public class BackgroundMergeService {

    @Autowired
    private WorktimeLoginMergeService worktimeLoginMergeService;

    @Autowired
    private RegisterMergeService registerMergeService;

    @Autowired
    private CheckRegisterService checkRegisterService;

    public BackgroundMergeService() {
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Perform full merge operations asynchronously in background.
     * This method runs on a separate thread and does not block login.
     */
    @Async("loginMergeTaskExecutor")
    public CompletableFuture<Void> performFullMergeAsync(User user) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting TRUE background merge operations for: %s (Thread: %s)",
                    user.getUsername(), Thread.currentThread().getName()));

            // Perform all merge operations
            performRoleBasedDataMerges(user);

            LoggerUtil.info(this.getClass(), String.format("TRUE background merge operations completed for: %s", user.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error during background merge operations for %s: %s",
                    user.getUsername(), e.getMessage()), e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Determine what data merges to perform based on user role.
     * This method performs the actual merge operations.
     */
    private void performRoleBasedDataMerges(User user) {
        String username = user.getUsername();
        String role = user.getRole();

        LoggerUtil.info(this.getClass(), String.format("Determining data merge operations for user %s with role: %s", username, role));

        // Always perform worktime merge
        LoggerUtil.info(this.getClass(), String.format("Performing worktime merge for: %s", username));
        worktimeLoginMergeService.performUserWorktimeLoginMerge(username);

        // Determine register merge pattern based on role
        boolean needsUserRegister = hasUserRegisterAccess(role);
        boolean needsCheckRegister = hasCheckRegisterAccess(role);

        if (needsUserRegister && needsCheckRegister) {
            LoggerUtil.info(this.getClass(), String.format("Performing normal register merge (part of both) for: %s", username));
            registerMergeService.performUserLoginMerge(username);

            LoggerUtil.info(this.getClass(), String.format("Performing check register merge (part of both) for: %s", username));
            checkRegisterService.performCheckRegisterLoginMerge(username);

        } else if (needsUserRegister) {
            LoggerUtil.info(this.getClass(), String.format("Performing user register merge only for: %s", username));
            registerMergeService.performUserLoginMerge(username);

        } else if (needsCheckRegister) {
            LoggerUtil.info(this.getClass(), String.format("Performing check register merge only for: %s", username));
            checkRegisterService.performCheckRegisterLoginMerge(username);
        }

        LoggerUtil.info(this.getClass(), String.format("Completed data merges for user %s", username));
    }

    /**
     * Check if user role has access to user register functionality.
     */
    private boolean hasUserRegisterAccess(String role) {
        return role.contains("USER") || role.contains("TEAM_LEADER") || role.contains("TL_CHECKING") || role.contains("ADMIN");
    }

    /**
     * Check if user role has access to check register functionality.
     */
    private boolean hasCheckRegisterAccess(String role) {
        return role.contains("CHECKING") || role.contains("TL_CHECKING") || role.contains("ADMIN");
    }
}