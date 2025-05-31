package com.ctgraphdep.service.cache;

import com.ctgraphdep.model.CheckValuesEntry;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CheckValuesCacheManager {
    private final Map<String, CheckValuesEntry> checkValuesCache = new ConcurrentHashMap<>();

    public CheckValuesCacheManager() {
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Caches a user's check values in memory
     */
    public void cacheCheckValues(String username, CheckValuesEntry values) {
        if (username != null && values != null) {
            checkValuesCache.put(username, values);
            // Add detailed logging
            LoggerUtil.info(this.getClass(), String.format("CACHE UPDATE: Cached check values for user %s: " +
                            "WU/H=%f, Values=LV:%f, KPLV:%f, LCHV:%f, GPTFV:%f, PV:%f, ReV:%f, SV:%f, OMSPV:%f, KPV:%f",
                    username, values.getWorkUnitsPerHour(), values.getLayoutValue(), values.getKipstaLayoutValue(), values.getLayoutChangesValue(),
                    values.getGptFilesValue(), values.getProductionValue(), values.getReorderValue(), values.getSampleValue(), values.getOmsProductionValue(),
                    values.getKipstaProductionValue()));
        }
    }

    /**
     * Gets a user's check values from cache
     * @return the cached check values or null if not in cache
     */
    public CheckValuesEntry getCachedCheckValues(String username) {
        return checkValuesCache.get(username);
    }

    /**
     * Checks if a user has cached check values
     */
    public boolean hasCachedCheckValues(String username) {
        return checkValuesCache.containsKey(username);
    }

    /**
     * Clears all cached check values
     */
    public void clearAllCachedCheckValues() {
        checkValuesCache.clear();
        LoggerUtil.info(this.getClass(), "Cleared all cached check values");
    }

    /**
     * Gets the value for a specific check type from cache
     * @param username The username
     * @param checkType The check type
     * @return The value for the check type, or default if not found
     */
    public double getCheckTypeValue(String username, String checkType) {
        CheckValuesEntry entry = getCachedCheckValues(username);

        if (entry == null) {
            return getDefaultCheckTypeValue(checkType);
        }

        return switch (checkType) {
            case "LAYOUT" -> entry.getLayoutValue();
            case "KIPSTA LAYOUT" -> entry.getKipstaLayoutValue();
            case "LAYOUT CHANGES" -> entry.getLayoutChangesValue();
            case "GPT" -> entry.getGptArticlesValue(); // For articles
            case "PRODUCTION" -> entry.getProductionValue();
            case "REORDER" -> entry.getReorderValue();
            case "SAMPLE" -> entry.getSampleValue();
            case "OMS PRODUCTION" -> entry.getOmsProductionValue();
            case "KIPSTA PRODUCTION" -> entry.getKipstaProductionValue();
            default -> getDefaultCheckTypeValue(checkType);
        };
    }

    /**
     * Gets the target work units per hour from cache
     * @param username The username
     * @return The target work units per hour
     */
    public double getTargetWorkUnitsPerHour(String username) {
        CheckValuesEntry entry = getCachedCheckValues(username);

        if (entry == null) {
            LoggerUtil.warn(this.getClass(), "CACHE MISS: No cached value found for " + username + ", using default");
            return 4.5; // Default value
        }

        // Add detailed logging
        LoggerUtil.info(this.getClass(), String.format(
                "CACHE HIT: Retrieved target units for %s: %f",
                username, entry.getWorkUnitsPerHour()));

        return entry.getWorkUnitsPerHour();
    }

    /**
     * Gets the default value for a specific check type
     * @param checkType The check type
     * @return The default value for the check type
     */
    private double getDefaultCheckTypeValue(String checkType) {
        return switch (checkType) {
            case "LAYOUT" -> 1.0;
            case "KIPSTA LAYOUT", "LAYOUT CHANGES" -> 0.25;
            case "SAMPLE" -> 0.3;
            case "GPT", "PRODUCTION", "REORDER", "OMS PRODUCTION", "KIPSTA PRODUCTION" -> 0.1;
            default -> 0.11;
        };
    }
}