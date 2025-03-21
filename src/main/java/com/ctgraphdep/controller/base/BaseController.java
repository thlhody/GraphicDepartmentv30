package com.ctgraphdep.controller.base;

import com.ctgraphdep.model.SyncFolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Optional;

@ControllerAdvice
public abstract class BaseController {
    private final UserService userService;
    private final FolderStatusService folderStatusService;

    protected BaseController(UserService userService, FolderStatusService folderStatusService) {
        this.userService = userService;
        this.folderStatusService = folderStatusService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @ModelAttribute("syncStatus")
    public SyncFolderStatus addSyncStatus() {
        return folderStatusService.getStatus();
    }

    protected User getUser(UserDetails userDetails) {
        if (userDetails == null) {
            LoggerUtil.error(this.getClass(), "Attempt to get user with null userDetails");
            return null;
        }

        Optional<User> userOpt = userService.getUserByUsername(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            LoggerUtil.error(this.getClass(),
                    String.format("User not found for username: %s", userDetails.getUsername()));
            return null;
        }
        return userOpt.get();
    }

    protected UserService getUserService() {
        return userService;
    }
}