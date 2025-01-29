package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController extends BaseController {

    public HomeController(UserService userService, FolderStatusService folderStatusService) {
        super(userService, folderStatusService);
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        // If user is authenticated, redirect to appropriate dashboard
        if (authentication != null && authentication.isAuthenticated()) {
            if (authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                return "redirect:/admin";
            }
            return "redirect:/user";
        }
        return "redirect:/login";
    }
}