package com.ctgraphdep.controller;

import com.ctgraphdep.service.AutoLoginService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/autologin")
public class AutoLoginController {

    private final AutoLoginService autoLoginService;

    @Autowired
    public AutoLoginController(AutoLoginService autoLoginService) {
        this.autoLoginService = autoLoginService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String handleAutoLogin(
            @RequestParam("token") String token,
            @RequestParam(value = "redirect", required = false) String redirectPath,
            HttpServletRequest request,
            HttpServletResponse response) {

        try {
            LoggerUtil.info(this.getClass(), "Auto-login request received" +
                    (redirectPath != null ? " with redirect to: " + redirectPath : ""));

            // Validate token and get authentication
            Authentication authentication = autoLoginService.validateToken(token);

            if (authentication != null) {
                // Set authentication in SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Get the current server's base URL
                String serverPort = String.valueOf(request.getServerPort());
                String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + serverPort;

                LoggerUtil.info(this.getClass(),
                        String.format("Auto-login successful for user: %s, redirecting with base URL: %s",
                                authentication.getName(), baseUrl));

                // If a specific redirect path was provided, use it
                if (redirectPath != null && !redirectPath.isEmpty()) {
                    // Ensure the path starts with a /
                    if (!redirectPath.startsWith("/")) {
                        redirectPath = "/" + redirectPath;
                    }

                    LoggerUtil.debug(this.getClass(), "Redirecting to specified path: " + redirectPath);
                    return "redirect:" + baseUrl + redirectPath;
                }

                // Otherwise redirect based on role with absolute URLs
                var authorities = authentication.getAuthorities();
                var roles = authorities.stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());

                if (roles.contains("ROLE_ADMIN")) {
                    LoggerUtil.debug(this.getClass(), "Redirecting to admin...");
                    return "redirect:" + baseUrl + "/admin";
                } else if (roles.contains("ROLE_TEAM_LEADER")) {
                    LoggerUtil.debug(this.getClass(), "Redirecting to team lead...");
                    return "redirect:" + baseUrl + "/team-lead";
                } else if (roles.contains("ROLE_USER")) {
                    LoggerUtil.debug(this.getClass(), "Redirecting to user...");
                    return "redirect:" + baseUrl + "/user";
                } else {
                    LoggerUtil.debug(this.getClass(), "No specific role found, redirecting to home...");
                    return "redirect:" + baseUrl + "/";
                }

            } else {
                LoggerUtil.warn(this.getClass(), "Invalid auto-login token");
                String serverPort = String.valueOf(request.getServerPort());
                String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + serverPort;
                return "redirect:" + baseUrl + "/login?error=invalid_token";
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during auto-login: " + e.getMessage());
            String serverPort = String.valueOf(request.getServerPort());
            String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + serverPort;
            return "redirect:" + baseUrl + "/login?error=auto_login_failed";
        }
    }
}