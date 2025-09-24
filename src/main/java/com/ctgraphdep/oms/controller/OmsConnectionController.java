package com.ctgraphdep.oms.controller;

import com.ctgraphdep.oms.service.OmsAuthenticationService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/oms")
public class OmsConnectionController {

    @Autowired
    private OmsAuthenticationService omsAuthenticationService;


    @PostMapping("/disconnect")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> disconnectFromOms(Principal principal) {
        Map<String, Object> response = new HashMap<>();

        try {
            String systemUsername = principal.getName();
            omsAuthenticationService.disconnect(systemUsername);

            response.put("success", true);
            response.put("message", "Successfully disconnected from OMS");
            LoggerUtil.info(OmsConnectionController.class, "OMS disconnected for user: " + systemUsername);

        } catch (Exception e) {
            LoggerUtil.error(OmsConnectionController.class, "Error during OMS disconnection", e);
            response.put("success", false);
            response.put("message", "An error occurred while disconnecting from OMS");
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getConnectionStatus(Principal principal) {
        Map<String, Object> response = new HashMap<>();

        try {
            String systemUsername = principal.getName();
            boolean isConnected = omsAuthenticationService.isConnected(systemUsername);

            response.put("connected", isConnected);

            if (isConnected) {
                response.put("message", "Connected to OMS");
            } else {
                response.put("message", "Not connected to OMS");
            }

        } catch (Exception e) {
            LoggerUtil.error(OmsConnectionController.class, "Error checking OMS connection status", e);
            response.put("connected", false);
            response.put("message", "Error checking connection status");
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/test-connection")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testConnection(Principal principal) {
        Map<String, Object> response = new HashMap<>();

        try {
            String systemUsername = principal.getName();
            boolean hasValidToken = omsAuthenticationService.getValidToken(systemUsername).isPresent();

            if (hasValidToken) {
                response.put("success", true);
                response.put("message", "OMS connection is active");
            } else {
                response.put("success", false);
                response.put("message", "OMS connection is not active or token expired");
            }

        } catch (Exception e) {
            LoggerUtil.error(OmsConnectionController.class, "Error testing OMS connection", e);
            response.put("success", false);
            response.put("message", "Error testing connection");
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/test-api")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testApiConnection() {
        LoggerUtil.info(OmsConnectionController.class, "Testing OMS API connectivity...");
        Map<String, Object> response = new HashMap<>();

        try {
            // Simple connectivity test to OMS API
            String testUrl = "https://backend.tboxlabs.com/user-auth/login";

            // Try to make an OPTIONS request or a simple POST to see if the endpoint exists
            org.springframework.web.client.RestTemplate testTemplate = new org.springframework.web.client.RestTemplate();

            Map<String, String> testData = new HashMap<>();
            testData.put("username", "test");
            testData.put("password", "test");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Add headers from the working HAR file
            headers.set("Accept", "application/json, text/plain, */*");
            headers.set("Origin", "https://oms.cottontex.ro");
            headers.set("Referer", "https://oms.cottontex.ro/");
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");

            HttpEntity<Map<String, String>> request = new HttpEntity<>(testData, headers);

            try {
                ResponseEntity<String> testResponse = testTemplate.postForEntity(testUrl, request, String.class);
                response.put("success", true);
                response.put("message", "OMS API is reachable");
                response.put("httpStatus", testResponse.getStatusCode().value());
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                // Even 404 or 401 means the API is reachable
                response.put("success", true);
                response.put("message", "OMS API is reachable (got expected error response)");
                response.put("httpStatus", e.getStatusCode().value());
                response.put("errorMessage", e.getResponseBodyAsString());
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Cannot reach OMS API: " + e.getMessage());
            LoggerUtil.error(OmsConnectionController.class, "Error testing OMS API connection", e);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/import-token")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> importOmsToken(
            @RequestBody Map<String, Object> tokenData,
            Principal principal) {

        Map<String, Object> response = new HashMap<>();

        try {
            String systemUsername = principal.getName();
            LoggerUtil.info(OmsConnectionController.class, "OMS token import attempt for user: " + systemUsername);

            boolean success = omsAuthenticationService.importTokenData(systemUsername, tokenData);

            if (success) {
                response.put("success", true);
                response.put("message", "Successfully imported OMS token data");
                LoggerUtil.info(OmsConnectionController.class, "OMS token import successful for user: " + systemUsername);
            } else {
                response.put("success", false);
                response.put("message", "Failed to import token data. Please ensure you are logged into OMS.");
                LoggerUtil.warn(OmsConnectionController.class, "OMS token import failed for user: " + systemUsername);
            }

        } catch (Exception e) {
            LoggerUtil.error(OmsConnectionController.class, "Error during OMS token import", e);
            response.put("success", false);
            response.put("message", "An error occurred while importing OMS token");
        }

        return ResponseEntity.ok(response);
    }

}