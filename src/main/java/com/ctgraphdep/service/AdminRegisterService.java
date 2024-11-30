package com.ctgraphdep.service;

import com.ctgraphdep.model.*;
import com.ctgraphdep.utils.BonusCalculatorUtil;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.PrintPrepType;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminRegisterService {
    private final DataAccessService dataAccessService;
    private final BonusCalculatorUtil bonusCalculator;
    private final UserService userService;
    private final WorkTimeManagementService workTimeManagementService;

    @Autowired
    public AdminRegisterService(DataAccessService dataAccessService,
                                BonusCalculatorUtil bonusCalculator,
                                UserService userService,
                                WorkTimeManagementService workTimeManagementService) {
        this.dataAccessService = dataAccessService;
        this.bonusCalculator = bonusCalculator;
        this.userService = userService;
        this.workTimeManagementService = workTimeManagementService;
    }

    // Load register entries for a specific user and period
    public List<RegisterEntry> loadUserRegisterEntries(String username, Integer userId, int year, int month) {
        LoggerUtil.info(this.getClass(), "Loading register entries for user: " + username + ", userId: " + userId + ", year: " + year + ", month: " + month);
        return dataAccessService.loadUserRegisterEntries(username, userId, year, month);
    }

    // Save admin modified register entries
    public void saveAdminRegisterEntries(String username, Integer userId, int year, int month,
                                         List<RegisterEntry> entries) {
        entries.forEach(entry -> entry.setAdminSync(SyncStatus.ADMIN_EDITED.name()));
        dataAccessService.saveAdminRegisterEntries(username, userId, year, month, entries);
    }

    // Filter entries by action type and/or print prep type
    public List<RegisterEntry> filterEntries(List<RegisterEntry> entries,
                                             ActionType actionType,
                                             PrintPrepType printPrepType) {
        return entries.stream()
                .filter(entry -> (actionType == null || entry.getActionType().equals(actionType.getValue())) &&
                        (printPrepType == null || entry.getPrintPrepType().equals(printPrepType.getValue())))
                .collect(Collectors.toList());
    }

    // Search entries by any field
    public List<RegisterEntry> searchEntries(List<RegisterEntry> entries, String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return entries;
        }

        String term = searchTerm.toLowerCase();
        return entries.stream()
                .filter(entry -> matchesSearchTerm(entry, term))
                .collect(Collectors.toList());
    }

    // Bulk update selected entries
    public void bulkUpdateEntries(List<RegisterEntry> entries,
                                  List<Integer> selectedEntryIds,
                                  String fieldName,
                                  String newValue) {
        entries.stream()
                .filter(entry -> selectedEntryIds.contains(entry.getEntryId()))
                .forEach(entry -> updateEntryField(entry, fieldName, newValue));
    }

    public BonusCalculationResult calculateBonusFromRequest(Map<String, Object> request) {
        try {
            // Convert and validate entries
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entriesData = (List<Map<String, Object>>) request.get("entries");
            List<RegisterEntry> entries = convertToRegisterEntries(entriesData); // Use the plural version

            Integer userId = (Integer) request.get("userId");
            Integer year = (Integer) request.get("year");
            Integer month = (Integer) request.get("month");

            // Convert and validate bonus configuration
            @SuppressWarnings("unchecked")
            Map<String, Object> configValues = (Map<String, Object>) request.get("bonusConfig");
            BonusConfiguration config = convertToBonusConfiguration(configValues);

            if (!config.isValid()) {
                throw new IllegalArgumentException("Invalid bonus configuration");
            }

            // Call the original calculateBonus method
            return calculateBonus(entries, userId, year, month, config);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in bonus calculation: " + e.getMessage());
            throw new RuntimeException("Failed to calculate bonus", e);
        }
    }

    private List<RegisterEntry> convertToRegisterEntries(List<Map<String, Object>> entriesData) {
        if (entriesData == null) return new ArrayList<>();

        return entriesData.stream()
                .map(this::convertToRegisterEntry)
                .collect(Collectors.toList());
    }

    private RegisterEntry convertToRegisterEntry(Map<String, Object> data) {
        return RegisterEntry.builder()
                .entryId(convertToInteger(data.get("entryId")))
                .userId(convertToInteger(data.get("userId")))
                .date(parseLocalDate(data.get("date")))
                .orderId(String.valueOf(data.get("orderId")))
                .productionId(String.valueOf(data.get("productionId")))
                .omsId(String.valueOf(data.get("omsId")))
                .clientName(String.valueOf(data.get("clientName")))
                .actionType(String.valueOf(data.get("actionType")))
                .printPrepType(String.valueOf(data.get("printPrepType")))
                .colorsProfile(String.valueOf(data.get("colorsProfile")))
                .articleNumbers(convertToInteger(data.get("articleNumbers")))
                .graphicComplexity(convertToDouble(data.get("graphicComplexity")))
                .observations(String.valueOf(data.get("observations")))
                .adminSync(String.valueOf(data.get("adminSync")))
                .build();
    }

    private BonusConfiguration convertToBonusConfiguration(Map<String, Object> configValues) {
        return BonusConfiguration.builder()
                .entriesPercentage(convertToDouble(configValues.get("entriesPercentage")))
                .articlesPercentage(convertToDouble(configValues.get("articlesPercentage")))
                .complexityPercentage(convertToDouble(configValues.get("complexityPercentage")))
                .miscPercentage(convertToDouble(configValues.get("miscPercentage")))
                .normValue(convertToDouble(configValues.get("normValue")))
                .sumValue(convertToDouble(configValues.get("sumValue")))
                .miscValue(convertToDouble(configValues.get("miscValue")))
                .build();
    }

    private Integer convertToInteger(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Double convertToDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private LocalDate parseLocalDate(Object value) {
        if (value == null) return LocalDate.now();
        if (value instanceof LocalDate) return (LocalDate) value;
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }
    //Calculate bonus for filtered entries
    public BonusCalculationResult calculateBonus(List<RegisterEntry> entries,
                                                 Integer userId,
                                                 int year,
                                                 int month,
                                                 BonusConfiguration config) {
        // Filter valid entries for bonus calculation
        List<RegisterEntry> validEntries = filterValidEntriesForBonus(entries);

        // Get worked days from worktime service
        int workedDays = workTimeManagementService.getWorkedDays(userId, year, month);

        // Calculate sums
        int numberOfEntries = validEntries.size();
        double sumArticleNumbers = validEntries.stream()
                .mapToDouble(RegisterEntry::getArticleNumbers)
                .sum();
        double sumComplexity = validEntries.stream()
                .mapToDouble(RegisterEntry::getGraphicComplexity)
                .sum();

        // Load previous months' data
        PreviousMonthsBonuses previousMonths = loadPreviousMonthsBonuses(userId, year, month);

        // Calculate bonus
        BonusCalculationResult result = bonusCalculator.calculateBonus(
                numberOfEntries,
                workedDays,
                sumArticleNumbers,
                sumComplexity,
                config
        );

        // Create a new result with previous months included
        return BonusCalculationResult.builder()
                .entries(result.getEntries())
                .articleNumbers(result.getArticleNumbers())
                .graphicComplexity(result.getGraphicComplexity())
                .misc(result.getMisc())
                .workedDays(result.getWorkedDays())
                .workedPercentage(result.getWorkedPercentage())
                .bonusPercentage(result.getBonusPercentage())
                .bonusAmount(result.getBonusAmount())
                .previousMonths(previousMonths)
                .build();
    }

    // Save bonus calculation result
    public void saveBonusResult(Integer userId, int year, int month, BonusCalculationResult result, String username) {
        // Create bonus entry from calculation result
        BonusEntry bonusEntry = BonusEntry.fromBonusCalculationResult(username, userId, result);
        LoggerUtil.info(this.getClass(),
                String.format("Created bonus entry for user %d with amount %f",
                        userId, bonusEntry.getBonusAmount()));

        // Load and set previous months' bonuses
        PreviousMonthsBonuses previousMonths = loadPreviousMonthsBonuses(userId, year, month);
        bonusEntry.setPreviousMonths(previousMonths);
        LoggerUtil.info(this.getClass(),
                String.format("Set previous months bonuses: month1=%f, month2=%f, month3=%f",
                        previousMonths.getMonth1(), previousMonths.getMonth2(), previousMonths.getMonth3()));

        try {
            Path bonusPath = dataAccessService.getAdminBonusPath(year, month);
            LoggerUtil.info(this.getClass(),
                    String.format("Saving to path: %s", bonusPath));

            // Load existing entries if any
            Map<Integer, BonusEntry> existingEntries = new HashMap<>();
            try {
                existingEntries = dataAccessService.readFile(bonusPath,
                        new TypeReference<Map<Integer, BonusEntry>>() {}, false);
                LoggerUtil.info(this.getClass(),
                        String.format("Loaded %d existing entries", existingEntries.size()));
            } catch (Exception e) {
                LoggerUtil.info(this.getClass(),
                        "No existing bonus entries found for " + year + "/" + month);
            }

            // Add or update the entry for this user
            existingEntries.put(userId, bonusEntry);

            // Save all entries back to file
            dataAccessService.writeFile(bonusPath, existingEntries);
            LoggerUtil.info(this.getClass(),
                    String.format("Successfully saved bonus calculation for user %s (ID: %d) for %d/%d",
                            username, userId, year, month));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error saving bonus calculation: " + e.getMessage(), e);
            throw new RuntimeException("Failed to save bonus calculation", e);
        }
    }

    // Helper methods
    private boolean matchesSearchTerm(RegisterEntry entry, String term) {
        return String.valueOf(entry.getEntryId()).contains(term) ||
                String.valueOf(entry.getUserId()).contains(term) ||
                entry.getDate().toString().contains(term) ||
                entry.getOrderId().toLowerCase().contains(term) ||
                entry.getProductionId().toLowerCase().contains(term) ||
                entry.getOmsId().toLowerCase().contains(term) ||
                entry.getClientName().toLowerCase().contains(term) ||
                entry.getActionType().toLowerCase().contains(term) ||
                entry.getPrintPrepType().toLowerCase().contains(term) ||
                entry.getColorsProfile().toLowerCase().contains(term) ||
                String.valueOf(entry.getArticleNumbers()).contains(term) ||
                String.valueOf(entry.getGraphicComplexity()).contains(term) ||
                (entry.getObservations() != null &&
                        entry.getObservations().toLowerCase().contains(term));
    }

    private void updateEntryField(RegisterEntry entry, String fieldName, String value) {
        switch (fieldName) {
            case "graphicComplexity" -> entry.setGraphicComplexity(Double.parseDouble(value));
            case "articleNumbers" -> entry.setArticleNumbers(Integer.parseInt(value));
            case "colorsProfile" -> entry.setColorsProfile(value);
            case "observations" -> entry.setObservations(value);
            default -> throw new IllegalArgumentException("Invalid field name: " + fieldName);
        }
        entry.setAdminSync(SyncStatus.ADMIN_EDITED.name());
    }

    private List<RegisterEntry> filterValidEntriesForBonus(List<RegisterEntry> entries) {
        return entries.stream()
                .filter(entry -> Arrays.stream(ActionType.values())
                        .map(ActionType::getValue)
                        .anyMatch(type -> type.equals(entry.getActionType())))
                .collect(Collectors.toList());
    }

    private BonusEntry createBonusEntry(Integer userId, BonusCalculationResult result) {
        User user = userService.getUserById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return BonusEntry.builder()
                .name(user.getName())
                .userId(userId)
                .entries(result.getEntries())
                .articleNumbers(result.getArticleNumbers())
                .graphicComplexity(result.getGraphicComplexity())
                .misc(result.getMisc())
                .workedDays(result.getWorkedDays())
                .workedPercentage(result.getWorkedPercentage())
                .bonusPercentage(result.getBonusPercentage())
                .bonusAmount(result.getBonusAmount())
                .build();
    }

    private PreviousMonthsBonuses loadPreviousMonthsBonuses(Integer userId, int year, int month) {
        YearMonth currentMonth = YearMonth.of(year, month);
        LoggerUtil.info(this.getClass(),
                String.format("Loading previous months bonuses for user %d, current month: %s",
                        userId, currentMonth));

        // Calculate previous months
        YearMonth month1 = currentMonth.minusMonths(1);
        YearMonth month2 = currentMonth.minusMonths(2);
        YearMonth month3 = currentMonth.minusMonths(3);

        Double bonus1 = loadMonthBonus(userId, month1);
        Double bonus2 = loadMonthBonus(userId, month2);
        Double bonus3 = loadMonthBonus(userId, month3);

        LoggerUtil.info(this.getClass(),
                String.format("Previous months bonuses for user %d: %f, %f, %f",
                        userId, bonus1, bonus2, bonus3));

        return PreviousMonthsBonuses.builder()
                .month1(bonus1)
                .month2(bonus2)
                .month3(bonus3)
                .build();
    }

    private Double loadMonthBonus(Integer userId, YearMonth month) {
        try {
            Path bonusPath = dataAccessService.getAdminBonusPath(month.getYear(), month.getMonthValue());
            LoggerUtil.info(this.getClass(),
                    String.format("Loading bonus for user %d from path: %s", userId, bonusPath));

            Map<Integer, BonusEntry> entries = dataAccessService.readFile(bonusPath,
                    new TypeReference<Map<Integer, BonusEntry>>() {}, false);

            Double bonus = Optional.ofNullable(entries.get(userId))
                    .map(BonusEntry::getBonusAmount)
                    .orElse(0.0);

            LoggerUtil.info(this.getClass(),
                    String.format("Loaded bonus amount: %f for user %d in %s", bonus, userId, month));

            return bonus;
        } catch (Exception e) {
            LoggerUtil.info(this.getClass(),
                    String.format("No bonus entry found for user %d in %s: %s",
                            userId, month, e.getMessage()));
            return 0.0;
        }
    }

    public RegisterSummary calculateRegisterSummary(List<RegisterEntry> entries) {
        // Filter valid entries
        List<RegisterEntry> validEntries = filterValidEntriesForBonus(entries);

        return RegisterSummary.builder()
                .totalEntries(validEntries.size())
                .averageArticleNumbers(validEntries.stream()
                        .mapToDouble(RegisterEntry::getArticleNumbers)
                        .average()
                        .orElse(0.0))
                .averageGraphicComplexity(validEntries.stream()
                        .mapToDouble(RegisterEntry::getGraphicComplexity)
                        .average()
                        .orElse(0.0))
                .build();
    }
}