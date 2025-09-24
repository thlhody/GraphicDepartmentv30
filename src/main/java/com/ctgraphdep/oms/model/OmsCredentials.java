package com.ctgraphdep.oms.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OmsCredentials {
    private String username;
    private String encryptedPassword;
    private String jwtToken;
    private LocalDateTime tokenExpiry;
    private LocalDateTime lastLogin;
    private boolean isConnected;

    public OmsCredentials() {}

    public OmsCredentials(String username, String encryptedPassword) {
        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.isConnected = false;
    }

    public boolean isTokenValid() {
        return jwtToken != null && tokenExpiry != null && LocalDateTime.now().isBefore(tokenExpiry);
    }

    public boolean needsReauthentication() {
        return jwtToken == null || tokenExpiry == null || LocalDateTime.now().isAfter(tokenExpiry);
    }
}