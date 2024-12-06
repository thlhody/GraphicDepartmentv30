package com.ctgraphdep.service;

import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.statistics.ChartData;
import com.ctgraphdep.model.statistics.RegisterStatistics;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminStatisticsService {
    private final DataAccessService dataAccess;
    private final UserService userService;

    @Autowired
    public AdminStatisticsService(DataAccessService dataAccess, UserService userService) {
        this.dataAccess = dataAccess;
        this.userService = userService;
    }

    public RegisterStatistics calculateStatistics(int year, int month) {
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

    private List<RegisterEntry> getAllEntriesForMonth(int year, int month) {
        List<RegisterEntry> allEntries = new ArrayList<>();
        List<User> users = userService.getAllUsers().stream()
                .filter(user -> !user.isAdmin())
                .toList();

        for (User user : users) {
            Path registerPath = dataAccess.getUserRegisterPath(
                    user.getUsername(),
                    user.getUserId(),
                    year,
                    month
            );

            try {
                List<RegisterEntry> userEntries = dataAccess.readFile(
                        registerPath,
                        new TypeReference<>() {},
                        true
                );
                allEntries.addAll(userEntries);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(),
                        "Error reading register for user " + user.getUsername());
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
}