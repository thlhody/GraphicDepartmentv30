package com.ctgraphdep.security;

import com.ctgraphdep.service.AuthenticationService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CustomAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationService authService;

    public CustomAuthenticationProvider(AuthenticationService authService) {
        this.authService = authService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        LoggerUtil.info(this.getClass(), "Starting authentication process for user: " + username);

        try {
            // Try online authentication first
            UserDetails userDetails = authService.authenticateUser(username, password, false);
            LoggerUtil.info(this.getClass(), "Online authentication successful for user: " + username);
            return createAuthenticationToken(userDetails, password);
        } catch (UsernameNotFoundException e) {
            LoggerUtil.warn(this.getClass(), "Username not found: " + username);
            return handleOfflineAuthentication(username, password, e);
        } catch (BadCredentialsException e) {
            LoggerUtil.warn(this.getClass(), "Invalid credentials provided for user: " + username);
            return handleOfflineAuthentication(username, password, e);
        } catch (DisabledException e) {
            LoggerUtil.warn(this.getClass(), "Account disabled for user: " + username);
            return handleOfflineAuthentication(username, password, e);
        } catch (AuthenticationException e) {
            LoggerUtil.warn(this.getClass(), "Authentication failed for user: " + username + ". Reason: " + e.getMessage());
            return handleOfflineAuthentication(username, password, e);
        }
    }

    private Authentication handleOfflineAuthentication(String username, String password, AuthenticationException originalException) {
        // Check if offline mode is available
        if (authService.getAuthenticationStatus().isOfflineModeAvailable()) {
            try {
                LoggerUtil.info(this.getClass(), "Online authentication failed, attempting offline mode for: " + username);
                UserDetails userDetails = authService.authenticateUser(username, password, true);
                LoggerUtil.info(this.getClass(), "Offline authentication successful for user: " + username);
                return createAuthenticationToken(userDetails, password);
            } catch (UsernameNotFoundException e) {
                LoggerUtil.error(this.getClass(), "Username not found in offline mode: " + username);
                throw new UsernameNotFoundException("User not found in online or offline mode: " + username);
            } catch (BadCredentialsException e) {
                LoggerUtil.error(this.getClass(), "Invalid credentials in offline mode for user: " + username);
                throw new BadCredentialsException("Invalid credentials in online and offline mode");
            } catch (AuthenticationException e) {
                LoggerUtil.error(this.getClass(), "Offline authentication failed for user: " + username + ". Reason: " + e.getMessage());
                throw new AuthenticationException("Authentication failed in both online and offline mode") {};
            }
        } else {
            LoggerUtil.error(this.getClass(), "Online authentication failed and offline mode is not available for user: " + username);
            throw originalException;
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