package com.ctgraphdep.oms.service;

import com.ctgraphdep.oms.model.OmsCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Optional;

@Service
public class OmsCredentialFileManager {
    private static final Logger logger = LoggerFactory.getLogger(OmsCredentialFileManager.class);
    private static final String CREDENTIALS_DIR = ".cttt";
    private static final String CREDENTIALS_FILE = "oms-credentials.enc";
    private static final String ALGORITHM = "AES";

    @Value("${app.encryption.key:defaultEncryptionKey123}")
    private String encryptionKey;

    private final ObjectMapper objectMapper;

    public OmsCredentialFileManager() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void saveCredentials(String systemUsername, OmsCredentials credentials) {
        try {
            Path credentialsPath = getCredentialsPath(systemUsername);
            ensureDirectoryExists(credentialsPath.getParent());

            String json = objectMapper.writeValueAsString(credentials);
            String encryptedJson = encrypt(json);

            Files.write(credentialsPath, encryptedJson.getBytes());
            logger.info("OMS credentials saved for user: {}", systemUsername);

        } catch (Exception e) {
            logger.error("Failed to save OMS credentials for user: {}", systemUsername, e);
            throw new RuntimeException("Failed to save OMS credentials", e);
        }
    }

    public Optional<OmsCredentials> loadCredentials(String systemUsername) {
        try {
            Path credentialsPath = getCredentialsPath(systemUsername);

            if (!Files.exists(credentialsPath)) {
                logger.debug("No OMS credentials file found for user: {}", systemUsername);
                return Optional.empty();
            }

            String encryptedJson = new String(Files.readAllBytes(credentialsPath));
            String json = decrypt(encryptedJson);

            OmsCredentials credentials = objectMapper.readValue(json, OmsCredentials.class);
            logger.debug("OMS credentials loaded for user: {}", systemUsername);

            return Optional.of(credentials);

        } catch (Exception e) {
            logger.error("Failed to load OMS credentials for user: {}", systemUsername, e);
            return Optional.empty();
        }
    }

    public void deleteCredentials(String systemUsername) {
        try {
            Path credentialsPath = getCredentialsPath(systemUsername);

            if (Files.exists(credentialsPath)) {
                Files.delete(credentialsPath);
                logger.info("OMS credentials deleted for user: {}", systemUsername);
            }

        } catch (Exception e) {
            logger.error("Failed to delete OMS credentials for user: {}", systemUsername, e);
        }
    }

    public boolean hasCredentials(String systemUsername) {
        Path credentialsPath = getCredentialsPath(systemUsername);
        return Files.exists(credentialsPath);
    }

    private Path getCredentialsPath(String systemUsername) {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, CREDENTIALS_DIR, systemUsername + "-" + CREDENTIALS_FILE);
    }

    private void ensureDirectoryExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }

    private String encrypt(String plainText) throws Exception {
        SecretKey secretKey = new SecretKeySpec(getKeyBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    private String decrypt(String encryptedText) throws Exception {
        SecretKey secretKey = new SecretKeySpec(getKeyBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
        return new String(decryptedBytes);
    }

    private byte[] getKeyBytes() {
        // Create a 16-byte key from the encryption key string
        byte[] keyBytes = new byte[16];
        byte[] sourceBytes = encryptionKey.getBytes();

        System.arraycopy(sourceBytes, 0, keyBytes, 0, Math.min(sourceBytes.length, 16));

        // Fill remaining bytes if needed
        for (int i = sourceBytes.length; i < 16; i++) {
            keyBytes[i] = (byte) (i % 256);
        }

        return keyBytes;
    }
}