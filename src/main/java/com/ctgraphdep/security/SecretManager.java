package com.ctgraphdep.security;

import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

@Component
public class SecretManager {

    @Value("${app.data.directory:${user.home}/.ctgraphdep}")
    private String dataDirectory;

    private static final String SECRET_FILE = "security.properties";
    private static final String SECRET_PROPERTY = "autologin.secret";
    private static final int SECRET_LENGTH = 32; // 256 bits

    private String cachedSecret;

    @PostConstruct
    public void initialize() {
        try {
            ensureSecretExists();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to initialize secret: " + e.getMessage());
        }
    }

    /**
     * Gets the installation-specific auto-login secret
     */
    public String getAutoLoginSecret() {
        if (cachedSecret != null) {
            return cachedSecret;
        }

        try {
            Properties props = loadSecurityProperties();
            cachedSecret = props.getProperty(SECRET_PROPERTY);
            return cachedSecret;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to read auto-login secret: " + e.getMessage());
            // Return a temporary secret that will be valid for this session only
            return generateTempSecret();
        }
    }

    /**
     * Ensures that a secret exists for this installation
     */
    private void ensureSecretExists() throws Exception {
        File securityFile = getSecurityPropertiesFile();

        if (!securityFile.exists()) {
            // Create data directory if it doesn't exist
            Files.createDirectories(Paths.get(dataDirectory));

            // Generate and save new secret
            Properties props = new Properties();
            props.setProperty(SECRET_PROPERTY, generateSecret());

            try (FileOutputStream fos = new FileOutputStream(securityFile)) {
                props.store(fos, "Security properties - DO NOT SHARE OR MODIFY");
                LoggerUtil.info(this.getClass(), "Generated new installation-specific secret");
            }
        }

        // Load the secret
        cachedSecret = loadSecurityProperties().getProperty(SECRET_PROPERTY);
    }

    /**
     * Loads the security properties file
     */
    private Properties loadSecurityProperties() throws Exception {
        Properties props = new Properties();
        File securityFile = getSecurityPropertiesFile();

        if (securityFile.exists()) {
            try (FileInputStream fis = new FileInputStream(securityFile)) {
                props.load(fis);
            }
        }

        return props;
    }

    /**
     * Gets the security properties file
     */
    private File getSecurityPropertiesFile() {
        return Paths.get(dataDirectory, SECRET_FILE).toFile();
    }

    /**
     * Generates a cryptographically secure random string
     */
    private String generateSecret() {
        byte[] bytes = new byte[SECRET_LENGTH];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Generates a temporary secret for emergency use
     */
    private String generateTempSecret() {
        String tempSecret = generateSecret();
        LoggerUtil.warn(this.getClass(), "Using temporary secret for this session only");
        return tempSecret;
    }
}