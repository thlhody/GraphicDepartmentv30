package com.ctgraphdep.oms.service;

import com.ctgraphdep.oms.model.OmsCredentials;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class OmsAuthenticationService {
    @Value("${app.oms.base-url:https://backend.tboxlabs.com}")
    private String omsBaseUrl;

    @Autowired
    private OmsCredentialFileManager credentialFileManager;

    private final RestTemplate restTemplate;

    public OmsAuthenticationService() {
        this.restTemplate = new RestTemplate();
    }


    public Optional<String> getValidToken(String systemUsername) {
        try {
            Optional<OmsCredentials> credentialsOpt = credentialFileManager.loadCredentials(systemUsername);

            if (credentialsOpt.isEmpty()) {
                LoggerUtil.debug(OmsAuthenticationService.class, "No credentials found for user: " + systemUsername);
                return Optional.empty();
            }

            OmsCredentials credentials = credentialsOpt.get();

            // Check if we have a cookie-based token
            String jwtToken = credentials.getJwtToken();
            if (jwtToken != null && (jwtToken.startsWith("COOKIE:") || jwtToken.startsWith("COOKIES:"))) {
                LoggerUtil.debug(OmsAuthenticationService.class, "Cookie-based authentication found for user: " + systemUsername);
                // For cookie-based auth, we assume it's still valid unless we get an error
                return Optional.of(jwtToken);
            }

            // Check if token is still valid (for JWT tokens)
            if (credentials.isTokenValid()) {
                LoggerUtil.debug(OmsAuthenticationService.class, "Valid token found for user: " + systemUsername);
                return Optional.of(credentials.getJwtToken());
            }

            // Token expired - for manual token paste, user needs to provide new token
            LoggerUtil.info(OmsAuthenticationService.class, "Token expired for user: " + systemUsername + ", user needs to provide new token");
            return Optional.empty();

        } catch (Exception e) {
            LoggerUtil.error(OmsAuthenticationService.class, "Error getting valid token for user: " + systemUsername, e);
            return Optional.empty();
        }
    }

    public boolean isConnected(String systemUsername) {
        Optional<OmsCredentials> credentialsOpt = credentialFileManager.loadCredentials(systemUsername);
        return credentialsOpt.map(OmsCredentials::isConnected).orElse(false);
    }

    public void disconnect(String systemUsername) {
        credentialFileManager.deleteCredentials(systemUsername);
        LoggerUtil.info(OmsAuthenticationService.class, "OMS disconnected for user: " + systemUsername);
    }

    public boolean importTokenData(String systemUsername, Map<String, Object> tokenData) {
        try {
            LoggerUtil.info(OmsAuthenticationService.class, "Importing OMS token data for user: " + systemUsername);

            // Extract token from the imported data
            String jwtToken = extractTokenFromImportedData(tokenData);

            if (jwtToken == null || jwtToken.trim().isEmpty()) {
                LoggerUtil.warn(OmsAuthenticationService.class, "No valid token found in imported data for user: " + systemUsername);
                return false;
            }

            // Extract OMS username from the request data
            String omsUsername = extractUsernameFromImportedData(tokenData);
            if (omsUsername == null) {
                LoggerUtil.warn(OmsAuthenticationService.class, "No OMS username provided for user: " + systemUsername);
                return false;
            }

            // Create credentials object without password (not needed for token-based auth)
            OmsCredentials credentials = new OmsCredentials(omsUsername, ""); // No password needed
            credentials.setJwtToken(jwtToken);
            credentials.setTokenExpiry(LocalDateTime.now().plusHours(24)); // Assume 24-hour validity
            credentials.setLastLogin(LocalDateTime.now());
            credentials.setConnected(true);

            // Save credentials using system username as key, but store OMS username in credentials
            credentialFileManager.saveCredentials(systemUsername, credentials);

            LoggerUtil.info(OmsAuthenticationService.class, "Successfully imported OMS token for user: " + systemUsername);
            return true;

        } catch (Exception e) {
            LoggerUtil.error(OmsAuthenticationService.class, "Error importing token data for user: " + systemUsername, e);
            return false;
        }
    }

    private String extractTokenFromImportedData(Map<String, Object> tokenData) {
        // Check extracted data structure
        @SuppressWarnings("unchecked")
        Map<String, Object> extractedData = (Map<String, Object>) tokenData.get("extractedData");

        if (extractedData == null) {
            return null;
        }

        // 1. Try authentication cookies first (most reliable for persistent sessions)
        @SuppressWarnings("unchecked")
        Map<String, String> authCookies = (Map<String, String>) extractedData.get("authCookies");
        if (authCookies != null && !authCookies.isEmpty()) {
            // Look for the most valuable authentication cookie
            String[] priorityCookieNames = {"auth", "token", "session", "PHPSESSID", "JSESSIONID"};

            for (String priorityName : priorityCookieNames) {
                for (Map.Entry<String, String> entry : authCookies.entrySet()) {
                    if (entry.getKey().toLowerCase().contains(priorityName.toLowerCase())
                        && entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                        LoggerUtil.info(OmsAuthenticationService.class, "Found priority authentication cookie: " + entry.getKey());
                        return "COOKIE:" + entry.getKey() + "=" + entry.getValue();
                    }
                }
            }

            // If no priority cookies, use the first available auth cookie
            for (Map.Entry<String, String> entry : authCookies.entrySet()) {
                String cookieValue = entry.getValue();
                if (cookieValue != null && !cookieValue.trim().isEmpty()) {
                    LoggerUtil.info(OmsAuthenticationService.class, "Found authentication cookie: " + entry.getKey());
                    return "COOKIE:" + entry.getKey() + "=" + cookieValue;
                }
            }
        }

        // 2. Try raw cookies if no specific auth cookies found
        String cookies = (String) extractedData.get("cookies");
        if (cookies != null && !cookies.trim().isEmpty()) {
            LoggerUtil.info(OmsAuthenticationService.class, "Found raw cookies, processing for authentication");

            // Parse cookies and look for authentication-related ones
            String[] cookiePairs = cookies.split(";");
            for (String cookiePair : cookiePairs) {
                String trimmed = cookiePair.trim();
                if (trimmed.toLowerCase().contains("session") ||
                    trimmed.toLowerCase().contains("auth") ||
                    trimmed.toLowerCase().contains("token") ||
                    trimmed.toLowerCase().contains("phpsessid") ||
                    trimmed.toLowerCase().contains("jsessionid")) {
                    LoggerUtil.info(OmsAuthenticationService.class, "Found auth-related cookie in raw cookies");
                    return "COOKIES:" + cookies; // Return all cookies for authentication
                }
            }

            // If no obvious auth cookies, still return all cookies as they might contain session info
            LoggerUtil.debug(OmsAuthenticationService.class, "No obvious auth cookies found, returning all cookies");
            return "COOKIES:" + cookies;
        }

        // 3. Try localStorage
        @SuppressWarnings("unchecked")
        Map<String, String> localStorage = (Map<String, String>) extractedData.get("localStorage");
        if (localStorage != null) {
            for (String key : new String[]{"token", "authToken", "jwt", "access_token"}) {
                String token = localStorage.get(key);
                if (token != null && !token.trim().isEmpty()) {
                    LoggerUtil.debug(OmsAuthenticationService.class, "Found token in localStorage with key: " + key);
                    return token;
                }
            }
        }

        // 4. Try sessionStorage
        @SuppressWarnings("unchecked")
        Map<String, String> sessionStorage = (Map<String, String>) extractedData.get("sessionStorage");
        if (sessionStorage != null) {
            for (String key : new String[]{"token", "authToken", "jwt", "access_token"}) {
                String token = sessionStorage.get(key);
                if (token != null && !token.trim().isEmpty()) {
                    LoggerUtil.debug(OmsAuthenticationService.class, "Found token in sessionStorage with key: " + key);
                    return token;
                }
            }
        }

        // 5. If we detected login via page indicators but no explicit tokens, create a session token
        Boolean isLoggedIn = (Boolean) extractedData.get("isLoggedIn");
        String authMethod = (String) extractedData.get("authMethod");

        if (Boolean.TRUE.equals(isLoggedIn) && "page_indicators".equals(authMethod)) {
            String url = (String) extractedData.get("url");
            String timestamp = (String) extractedData.get("timestamp");

            // Create a synthetic token from session info
            String sessionToken = "session_" + url.hashCode() + "_" + timestamp;
            LoggerUtil.debug(OmsAuthenticationService.class, "Created synthetic session token from page indicators");
            return sessionToken;
        }

        return null;
    }

    private String extractUsernameFromImportedData(Map<String, Object> tokenData) {
        @SuppressWarnings("unchecked")
        Map<String, Object> extractedData = (Map<String, Object>) tokenData.get("extractedData");

        if (extractedData == null) {
            return null;
        }

        // First, check if OMS username was provided directly in the request
        Object omsUsername = extractedData.get("omsUsername");
        if (omsUsername != null && !omsUsername.toString().trim().isEmpty()) {
            return omsUsername.toString().trim();
        }

        // Fallback: Try to get username from user info
        Object userInfo = extractedData.get("userInfo");
        if (userInfo instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> userMap = (Map<String, Object>) userInfo;

            // Try common username fields
            for (String key : new String[]{"username", "user", "email", "login", "name"}) {
                Object value = userMap.get(key);
                if (value != null) {
                    return value.toString();
                }
            }
        }

        return null;
    }



    /**
     * Creates HTTP headers with authentication using stored credentials.
     * Handles both JWT tokens and cookie-based authentication.
     */
    public HttpHeaders createAuthenticatedHeaders(String systemUsername) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json, text/plain, */*");
        headers.set("Origin", "https://oms.cottontex.ro");
        headers.set("Referer", "https://oms.cottontex.ro/");
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");

        Optional<String> tokenOpt = getValidToken(systemUsername);
        if (tokenOpt.isPresent()) {
            String token = tokenOpt.get();

            if (token.startsWith("COOKIE:")) {
                // Single cookie: "COOKIE:cookieName=cookieValue"
                String cookieData = token.substring(7); // Remove "COOKIE:" prefix
                headers.set("Cookie", cookieData);
                LoggerUtil.debug(OmsAuthenticationService.class, "Added single cookie authentication for user: " + systemUsername);

            } else if (token.startsWith("COOKIES:")) {
                // Multiple cookies: "COOKIES:cookie1=value1; cookie2=value2"
                String cookieData = token.substring(8); // Remove "COOKIES:" prefix
                headers.set("Cookie", cookieData);
                LoggerUtil.debug(OmsAuthenticationService.class, "Added multiple cookies authentication for user: " + systemUsername);

            } else {
                // JWT token
                headers.set("Authorization", "Bearer " + token);
                LoggerUtil.debug(OmsAuthenticationService.class, "Added JWT authentication for user: " + systemUsername);
            }
        } else {
            LoggerUtil.warn(OmsAuthenticationService.class, "No valid authentication found for user: " + systemUsername);
        }

        return headers;
    }

}