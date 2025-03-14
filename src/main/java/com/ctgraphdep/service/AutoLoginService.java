package com.ctgraphdep.service;

import com.ctgraphdep.model.User;
import com.ctgraphdep.security.SecretManager;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class AutoLoginService {

    @Value("${app.autologin.secret:${random.uuid}}")
    private String secretKey;

    @Value("${app.autologin.expiration:300}") // 5 minutes in seconds
    private int tokenExpirationSeconds;

    private final UserDetailsService userDetailsService;
    private final SecretManager secretManager;

    // Token cache for validating tokens and preventing replay attacks
    private final Map<String, LocalDateTime> tokenCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 100;

    public AutoLoginService(UserDetailsService userDetailsService, SecretManager secretManager) {
        this.userDetailsService = userDetailsService;
        this.secretManager = secretManager;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Generates a secure, time-limited token for auto-login
     * Format: base64(username:timestamp:h mac)
     */
    public String generateAutoLoginToken(User user) {
        try {
            LocalDateTime now = LocalDateTime.now();
            long timestamp = now.toEpochSecond(ZoneOffset.UTC);
            String username = user.getUsername();

            // Payload: username:timestamp
            String payload = username + ":" + timestamp;

            // Generate H MAC
            String hmac = calculateHmac(payload);

            // Final token: payload:h mac
            String token = payload + ":" + hmac;

            // Add to cache for validation
            String encodedToken = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
            tokenCache.put(encodedToken, now.plusSeconds(tokenExpirationSeconds));
            cleanupExpiredTokens();

            LoggerUtil.info(this.getClass(),
                    String.format("Generated auto-login token for user: %s, expires in %d seconds",
                            username, tokenExpirationSeconds));

            return encodedToken;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error generating auto-login token: " + e.getMessage());
            throw new RuntimeException("Failed to generate auto-login token", e);
        }
    }

    /**
     * Validates an auto-login token and returns Authentication if valid
     */
    public Authentication validateToken(String encodedToken) {
        try {
            // Check if token exists in cache
            if (!tokenCache.containsKey(encodedToken)) {
                LoggerUtil.warn(this.getClass(), "Token not found in cache or already used");
                return null;
            }

            // Remove token from cache to prevent reuse
            LocalDateTime expiry = tokenCache.remove(encodedToken);

            // Check if token has expired
            if (expiry.isBefore(LocalDateTime.now())) {
                LoggerUtil.warn(this.getClass(), "Auto-login token has expired");
                return null;
            }

            // Decode token
            String decodedToken = new String(Base64.getDecoder().decode(encodedToken), StandardCharsets.UTF_8);
            String[] parts = decodedToken.split(":");

            if (parts.length != 3) {
                LoggerUtil.warn(this.getClass(), "Invalid token format");
                return null;
            }

            String username = parts[0];
            long timestamp = Long.parseLong(parts[1]);
            String receivedHmac = parts[2];

            // Check token expiration
            LocalDateTime tokenTime = LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.UTC);
            if (tokenTime.plusSeconds(tokenExpirationSeconds).isBefore(LocalDateTime.now())) {
                LoggerUtil.warn(this.getClass(), "Token has expired");
                return null;
            }

            // Verify HMAC
            String payload = username + ":" + timestamp;
            String expectedHmac = calculateHmac(payload);

            if (!receivedHmac.equals(expectedHmac)) {
                LoggerUtil.warn(this.getClass(), "Invalid token signature");
                return null;
            }

            // Token is valid, load user details
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            LoggerUtil.info(this.getClass(),
                    String.format("Successfully validated auto-login for user: %s", username));

            return new UsernamePasswordAuthenticationToken(
                    userDetails.getUsername(),
                    null, // Credentials are null for security
                    userDetails.getAuthorities());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error validating auto-login token: " + e.getMessage());
            return null;
        }
    }

    /**
     * Calculate HMAC for token signature
     */
    private String calculateHmac(String data) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        String secret = secretManager.getAutoLoginSecret();
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmac.init(secretKeySpec);
        byte[] hmacBytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    /**
     * Clean up expired tokens from cache
     */
    private void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();

        // Remove expired tokens
        tokenCache.entrySet().removeIf(entry -> entry.getValue().isBefore(now));

        // If cache is still too large, remove the oldest entries
        if (tokenCache.size() > MAX_CACHE_SIZE) {
            int tokensToRemove = tokenCache.size() - MAX_CACHE_SIZE;
            tokenCache.keySet().stream()
                    .sorted((k1, k2) -> tokenCache.get(k1).compareTo(tokenCache.get(k2)))
                    .limit(tokensToRemove)
                    .toList()
                    .forEach(tokenCache::remove);
        }
    }
}