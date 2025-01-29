package com.ctgraphdep.security;

import com.ctgraphdep.service.AuthenticationService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class CustomAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationService authService;

    public CustomAuthenticationProvider(AuthenticationService authService) {
        this.authService = authService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        LoggerUtil.info(this.getClass(), "Starting authentication process for user: " + username);

        try {
            // Try online authentication first
            UserDetails userDetails = authService.authenticateUser(username, password, false);
            return createAuthenticationToken(userDetails, password);
        } catch (AuthenticationException e) {
            // If online authentication fails, try offline mode
            if (authService.getAuthenticationStatus().isOfflineModeAvailable()) {
                LoggerUtil.info(this.getClass(),
                        "Online authentication failed, attempting offline mode for: " + username);
                UserDetails userDetails = authService.authenticateUser(username, password, true);
                return createAuthenticationToken(userDetails, password);
            }
            throw e;
        }
    }

    private Authentication createAuthenticationToken(UserDetails userDetails, String password) {
        return new UsernamePasswordAuthenticationToken(
                userDetails,
                password,
                userDetails.getAuthorities()
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}