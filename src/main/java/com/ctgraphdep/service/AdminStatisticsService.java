package com.ctgraphdep.service;

import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.statistics.ChartData;
import com.ctgraphdep.model.statistics.RegisterStatistics;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminStatisticsService {
    private final DataAccessService dataAccess;
    private final UserService userService;

    @Autowired
    public AdminStatisticsService(DataAccessService dataAccess, UserService userService) {
        this.dataAccess = dataAccess;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public RegisterStatistics calculateStatistics(Integer year, Integer month) {
        List<RegisterEntry> allEntries = getAllEntriesForMonth(year, month);

        return RegisterStatistics.builder()
                .clientDistribution(calculateClientDistribution(allEntries))
                .actionTypeDistribution(calculateActionTypeDistribution(allEntries))
                .printPrepTypeDistribution(calculatePrintPrepTypeDistribution(allEntries))
                .totalEntries(allEntries.size())
                .averageArticles(calculateAverageArticles(allEntries))
                .averageComplexity(calculateAverageComplexity(allEntries))
                .build();
    }

    private List<RegisterEntry> getAllEntriesForMonth(Integer year, Integer month) {
        List<RegisterEntry> allEntries = new ArrayList<>();
        List<User> users = userService.getAllUsers().stream()
                .filter(user -> !user.isAdmin())
                .toList();

        for (User user : users) {
            try {
                // Use readUserRegister method with isAdmin set to true to read from network path
                List<RegisterEntry> userEntries = dataAccess.readNetworkUserRegister(
                        user.getUsername(),
                        user.getUserId(),
                        year,
                        month
                );

                if (userEntries != null) {
                    allEntries.addAll(userEntries);
                }
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Error reading register for user %s: %s", user.getUsername(), e.getMessage()));
            }
        }
        return allEntries;
    }

    private ChartData calculateClientDistribution(List<RegisterEntry> entries) {
        Map<String, Integer> distribution = entries.stream()
                .collect(Collectors.groupingBy(
                        RegisterEntry::getClientName,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));

        return ChartData.builder()
                .labels(new ArrayList<>(distribution.keySet()))
                .data(new ArrayList<>(distribution.values()))
                .build();
    }

    private ChartData calculateActionTypeDistribution(List<RegisterEntry> entries) {
        Map<String, Integer> distribution = entries.stream()
                .collect(Collectors.groupingBy(
                        RegisterEntry::getActionType,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));

        return ChartData.builder()
                .labels(new ArrayList<>(distribution.keySet()))
                .data(new ArrayList<>(distribution.values()))
                .build();
    }

    private ChartData calculatePrintPrepTypeDistribution(List<RegisterEntry> entries) {
        Map<String, Integer> distribution = entries.stream()
                .flatMap(entry -> entry.getPrintPrepTypes().stream())
                .collect(Collectors.groupingBy(
                        type -> type,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));

        return ChartData.builder()
                .labels(new ArrayList<>(distribution.keySet()))
                .data(new ArrayList<>(distribution.values()))
                .build();
    }

    private double calculateAverageArticles(List<RegisterEntry> entries) {
        return entries.stream()
                .mapToInt(RegisterEntry::getArticleNumbers)
                .average()
                .orElse(0.0);
    }

    private double calculateAverageComplexity(List<RegisterEntry> entries) {
        return entries.stream()
                .mapToDouble(RegisterEntry::getGraphicComplexity)
                .average()
                .orElse(0.0);
    }

    public Map<String, Map<String, Integer>> getMonthlyEntriesForYear(Integer year) {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        Map<String, Integer> regularEntries = new LinkedHashMap<>();
        Map<String, Integer> spizedEntries = new LinkedHashMap<>();

        // Initialize all months with 0
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        Arrays.asList(months).forEach(month -> {
            regularEntries.put(month, 0);
            spizedEntries.put(month, 0);
        });

        // For each month
        for (int month = 1; month <= 12; month++) {
            List<RegisterEntry> entries = getAllEntriesForMonth(year, month);

            // Count regular entries (excluding IMPOSTARE and ORDIN SPIZED)
            int regularCount = (int) entries.stream()
                    .filter(entry -> !"IMPOSTARE".equals(entry.getActionType())
                            && !"ORDIN SPIZED".equals(entry.getActionType()))
                    .count();

            // Count ORDIN SPIZED entries
            int spizedCount = (int) entries.stream().filter(entry -> "ORDIN SPIZED".equals(entry.getActionType())).count();

            regularEntries.put(months[month - 1], regularCount);
            spizedEntries.put(months[month - 1], spizedCount);
        }

        result.put("regular", regularEntries);
        result.put("spized", spizedEntries);

        return result;
    }

    public Map<Integer, Integer> getDailyEntriesForMonth(Integer year, Integer month) {
        Map<Integer, Integer> dailyEntries = new TreeMap<>(); // Using TreeMap to maintain order by day

        // Get all entries for the month
        List<RegisterEntry> entries = getAllEntriesForMonth(year, month);

        // Initialize all days with 0
        YearMonth yearMonth = YearMonth.of(year, month);
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            dailyEntries.put(day, 0);
        }

        // Count entries per day
        entries.stream()
                .filter(entry -> !"IMPOSTARE".equals(entry.getActionType()))
                .forEach(entry -> {
                    Integer day = entry.getDate().getDayOfMonth();
                    dailyEntries.merge(day, 1, Integer::sum);
                });

        return dailyEntries;
    }
}