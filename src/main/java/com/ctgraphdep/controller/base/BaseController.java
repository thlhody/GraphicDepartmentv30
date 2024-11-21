package com.ctgraphdep.controller.base;

import com.ctgraphdep.model.SyncFolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@Slf4j
@ControllerAdvice
public abstract class BaseController {
    private final UserService userService;
    private final FolderStatusService folderStatusService;

    protected BaseController(UserService userService, FolderStatusService folderStatusService) {
        this.userService = userService;
        this.folderStatusService = folderStatusService;
    }

    @ModelAttribute("syncStatus")
    public SyncFolderStatus addSyncStatus() {
        return folderStatusService.getStatus();
    }

    protected User getUser(UserDetails userDetails) {
        return userService.getUserByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    protected User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails userDetails) {
            return getUser(userDetails);
        }
        throw new RuntimeException("No authenticated user found");
    }

    protected UserService getUserService() {
        return userService;
    }
}