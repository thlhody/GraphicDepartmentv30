package com.ctgraphdep.security;

import com.ctgraphdep.service.CheckValuesCacheManager;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

    private final CheckValuesCacheManager checkValuesCacheManager;

    public CustomLogoutSuccessHandler(CheckValuesCacheManager checkValuesCacheManager) {
        this.checkValuesCacheManager = checkValuesCacheManager;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        try {
            // Clear all cached check values when any user logs out
            checkValuesCacheManager.clearAllCachedCheckValues();

            // Log the logout
            if (authentication != null) {
                LoggerUtil.info(this.getClass(),
                        "User logged out and cleared check values cache: " + authentication.getName());
            } else {
                LoggerUtil.info(this.getClass(), "Session ended and cleared check values cache");
            }

            // Redirect to home page (or login page)
            response.sendRedirect(request.getContextPath() + "/");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during logout: " + e.getMessage(), e);
            // Default redirect if there's an error
            response.sendRedirect(request.getContextPath() + "/");
        }
    }
}