package com.ctgraphdep.oms.service;

import com.ctgraphdep.oms.model.OmsCredentials;
import com.ctgraphdep.utils.LoggerUtil;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class OmsBrowserAuthenticationService {

    @Value("${app.oms.frontend-url:https://oms.cottontex.ro}")
    private String omsFrontendUrl;

    @Value("${app.oms.browser.headless:true}")
    private boolean headless;

    @Value("${app.oms.browser.timeout:30}")
    private int timeoutSeconds;

    @Autowired
    private OmsCredentialFileManager credentialFileManager;

    public boolean authenticateAndStore(String systemUsername, String omsUsername, String omsPassword) {
        WebDriver driver = null;

        try {
            LoggerUtil.info(OmsBrowserAuthenticationService.class, "Starting browser-based OMS authentication for user: " + systemUsername);

            // Setup Chrome with headless mode
            ChromeOptions options = new ChromeOptions();
            if (headless) {
                options.addArguments("--headless");
            }
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");

            driver = new ChromeDriver(options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

            // Navigate to OMS login page
            LoggerUtil.debug(OmsBrowserAuthenticationService.class, "Navigating to OMS login page: " + omsFrontendUrl);
            driver.get(omsFrontendUrl);

            // Wait for and find login form elements with more specific selectors
            LoggerUtil.debug(OmsBrowserAuthenticationService.class, "Waiting for login form elements...");

            // Find username field using exact ID from the OMS page
            WebElement usernameField = null;
            try {
                usernameField = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("username")));
                LoggerUtil.debug(OmsBrowserAuthenticationService.class, "Found username field with ID 'username'");
            } catch (Exception e) {
                LoggerUtil.error(OmsBrowserAuthenticationService.class, "Could not find username field with ID 'username'", e);
                return false;
            }

            // Find password field using exact ID from the OMS page
            WebElement passwordField = null;
            try {
                passwordField = driver.findElement(By.id("pass"));
                LoggerUtil.debug(OmsBrowserAuthenticationService.class, "Found password field with ID 'pass'");
            } catch (Exception e) {
                LoggerUtil.error(OmsBrowserAuthenticationService.class, "Could not find password field with ID 'pass'", e);
                return false;
            }

            // Find login button using exact ID from the OMS page
            WebElement loginButton = null;
            try {
                loginButton = driver.findElement(By.id("loginBtn"));
                LoggerUtil.debug(OmsBrowserAuthenticationService.class, "Found login button with ID 'loginBtn'");
            } catch (Exception e) {
                LoggerUtil.error(OmsBrowserAuthenticationService.class, "Could not find login button with ID 'loginBtn'", e);
                return false;
            }

            // Clear and enter credentials
            LoggerUtil.debug(OmsBrowserAuthenticationService.class, "Entering credentials for username: " + omsUsername);
            usernameField.clear();
            usernameField.sendKeys(omsUsername);

            passwordField.clear();
            passwordField.sendKeys(omsPassword);

            // Click login button
            loginButton.click();

            // Wait for login to complete - look for dashboard or successful login indicators
            Thread.sleep(2000); // Give it a moment for the request to complete

            // Check if login was successful by looking for error messages or dashboard elements
            boolean loginSuccessful = isLoginSuccessful(driver, wait);

            if (loginSuccessful) {
                // Extract the JWT token from local storage or network requests
                Optional<String> token = extractJwtToken(driver);

                if (token.isPresent()) {
                    // Create and save credentials
                    String encryptedPassword = encryptPassword(omsPassword);
                    OmsCredentials credentials = new OmsCredentials(omsUsername, encryptedPassword);
                    credentials.setJwtToken(token.get());
                    credentials.setTokenExpiry(LocalDateTime.now().plusHours(24));
                    credentials.setLastLogin(LocalDateTime.now());
                    credentials.setConnected(true);

                    credentialFileManager.saveCredentials(systemUsername, credentials);

                    LoggerUtil.info(OmsBrowserAuthenticationService.class, "Browser-based OMS authentication successful for user: " + systemUsername);
                    return true;
                } else {
                    LoggerUtil.warn(OmsBrowserAuthenticationService.class, "Login appeared successful but could not extract JWT token");
                    return false;
                }
            } else {
                LoggerUtil.warn(OmsBrowserAuthenticationService.class, "Browser-based OMS authentication failed - login unsuccessful");
                return false;
            }

        } catch (Exception e) {
            LoggerUtil.error(OmsBrowserAuthenticationService.class, "Error during browser-based OMS authentication for user: " + systemUsername, e);
            return false;
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    LoggerUtil.warn(OmsBrowserAuthenticationService.class, "Error closing browser driver: " + e.getMessage());
                }
            }
        }
    }

    private boolean isLoginSuccessful(WebDriver driver, WebDriverWait wait) {
        try {
            // Wait for either error message or successful dashboard
            // Look for common error indicators
            try {
                WebElement errorElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(".error, .alert-danger, [class*='error'], [class*='invalid']")));
                if (errorElement.isDisplayed()) {
                    LoggerUtil.debug(OmsBrowserAuthenticationService.class, "Login error detected: " + errorElement.getText());
                    return false;
                }
            } catch (Exception e) {
                // No error found, continue checking for success
            }

            // Check if URL changed (successful login usually redirects)
            String currentUrl = driver.getCurrentUrl();
            if (!currentUrl.equals(omsFrontendUrl) && !currentUrl.contains("login")) {
                LoggerUtil.debug(OmsBrowserAuthenticationService.class, "URL changed to: " + currentUrl + ", assuming login successful");
                return true;
            }

            // Look for dashboard elements
            try {
                WebElement dashboardElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[class*='dashboard'], [class*='main'], [class*='content'], [class*='home']")));
                return dashboardElement.isDisplayed();
            } catch (Exception e) {
                // No dashboard found
            }

            return false;

        } catch (Exception e) {
            LoggerUtil.debug(OmsBrowserAuthenticationService.class, "Error checking login status: " + e.getMessage());
            return false;
        }
    }

    private Optional<String> extractJwtToken(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Try to extract token from localStorage
            Object tokenFromStorage = js.executeScript("return localStorage.getItem('token') || localStorage.getItem('authToken') || localStorage.getItem('jwt')");
            if (tokenFromStorage != null) {
                LoggerUtil.debug(OmsBrowserAuthenticationService.class, "Token extracted from localStorage");
                return Optional.of(tokenFromStorage.toString());
            }

            // Try to extract from sessionStorage
            Object tokenFromSession = js.executeScript("return sessionStorage.getItem('token') || sessionStorage.getItem('authToken') || sessionStorage.getItem('jwt')");
            if (tokenFromSession != null) {
                LoggerUtil.debug(OmsBrowserAuthenticationService.class, "Token extracted from sessionStorage");
                return Optional.of(tokenFromSession.toString());
            }

            // If no token found in storage, we'll need to make a request to get it
            // This is a fallback - the token might be set after a subsequent request
            LoggerUtil.debug(OmsBrowserAuthenticationService.class, "No token found in browser storage");
            return Optional.empty();

        } catch (Exception e) {
            LoggerUtil.error(OmsBrowserAuthenticationService.class, "Error extracting JWT token from browser", e);
            return Optional.empty();
        }
    }

    private String encryptPassword(String password) throws Exception {
        // Use the same encryption method as the file manager
        // For now, return base64 encoded - this should be improved to use proper encryption
        return java.util.Base64.getEncoder().encodeToString(password.getBytes());
    }
}