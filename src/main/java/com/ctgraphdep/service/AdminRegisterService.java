package com.ctgraphdep.service;

import com.ctgraphdep.enums.RegisterMergeRule;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.model.*;
import com.ctgraphdep.utils.BonusCalculatorUtil;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminRegisterService {
    private final DataAccessService dataAccessService;
    private final BonusCalculatorUtil bonusCalculator;
    private final WorkTimeManagementService workTimeManagementService;
    private final UserService userService;

    @Autowired
    public AdminRegisterService(DataAccessService dataAccessService,
                                BonusCalculatorUtil bonusCalculator,
                                WorkTimeManagementService workTimeManagementService, UserService userService) {
        this.dataAccessService = dataAccessService;
        this.bonusCalculator = bonusCalculator;
        this.workTimeManagementService = workTimeManagementService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public Optional<BonusEntry> loadBonusEntry(Integer userId, Integer year, Integer month) {
        try {
            List<BonusEntry> bonusEntries = dataAccessService.readAdminBonus(year, month);
            return bonusEntries.stream()
                    .filter(entry -> entry.getEmployeeId().equals(userId))
                    .findFirst();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading bonus entry for user %d: %s", userId, e.getMessage()));
            return Optional.empty();
        }
    }

    public List<RegisterEntry> loadUserRegisterEntries(String username, Integer userId, Integer year, Integer month) {
        // 1. Load user entries from network location
        List<RegisterEntry> userEntries = dataAccessService.readNetworkUserRegister(username, userId, year, month);
        if (userEntries == null) {
            userEntries = new ArrayList<>();
        }

        // 2. Check local admin file
        List<RegisterEntry> adminEntries;
        try {
            adminEntries = dataAccessService.readLocalAdminRegister(username, userId, year, month);
            if (adminEntries == null) {
                adminEntries = new ArrayList<>();
            }
        } catch (Exception e) {
            // If no local admin file exists, create it with user entries
            adminEntries = userEntries.stream()
                    .map(this::copyEntry)
                    .collect(Collectors.toList());
        }

        // 3. Merge entries based on status
        List<RegisterEntry> mergedEntries = mergeEntries(userEntries, adminEntries);

        // 4. Save merged entries to local admin register file
        dataAccessService.writeLocalAdminRegister(username, userId, mergedEntries, year, month);

        // 5. Sync local admin register to network
        dataAccessService.syncAdminRegisterToNetwork(username, userId, year, month);

        return mergedEntries;
    }

    private List<RegisterEntry> mergeEntries(List<RegisterEntry> userEntries, List<RegisterEntry> adminEntries) {
        Map<Integer, RegisterEntry> adminEntriesMap = adminEntries.stream()
                .collect(Collectors.toMap(RegisterEntry::getEntryId, entry -> entry));

        return userEntries.stream()
                .map(userEntry -> RegisterMergeRule.apply(userEntry, adminEntriesMap.get(userEntry.getEntryId())))
                .collect(Collectors.toList());
    }

    public void saveAdminRegisterEntries(String username, Integer userId, Integer year, Integer month,
                                         List<RegisterEntry> entries) {
        try {
            // First, get any existing entries - this is critical for preserving all previous edits
            List<RegisterEntry> existingEntries;
            try {
                existingEntries = dataAccessService.readLocalAdminRegister(username, userId, year, month);
                LoggerUtil.info(this.getClass(),
                        String.format("Found %d existing entries in admin register", existingEntries.size()));
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("No existing admin register found, creating new one: %s", e.getMessage()));
                existingEntries = new ArrayList<>();
            }

            // Create a map of existing entries by ID for quick lookup
            Map<Integer, RegisterEntry> existingEntriesMap = existingEntries.stream()
                    .collect(Collectors.toMap(RegisterEntry::getEntryId, entry -> entry, (e1, e2) -> e1));

            // Create the final list of entries to save
            List<RegisterEntry> updatedEntries = new ArrayList<>();

            // Process each entry from the incoming request
            for (RegisterEntry entry : entries) {
                RegisterEntry entryToSave = copyEntry(entry); // Create a copy to avoid reference issues

                // Check if there's an existing entry with this ID
                RegisterEntry existingEntry = existingEntriesMap.get(entry.getEntryId());

                if (existingEntry != null) {
                    // If existing entry is already ADMIN_EDITED and new entry is not, preserve the ADMIN_EDITED status
                    if (existingEntry.getAdminSync().equals(SyncStatus.ADMIN_EDITED.name()) &&
                            !entry.getAdminSync().equals(SyncStatus.ADMIN_EDITED.name())) {
                        entryToSave.setAdminSync(SyncStatus.ADMIN_EDITED.name());
                        LoggerUtil.debug(this.getClass(),
                                String.format("Preserving ADMIN_EDITED status for entry %d", entry.getEntryId()));
                    }
                }

                // Apply entry status updates based on sync state
                if (entryToSave.getAdminSync().equals(SyncStatus.USER_INPUT.name())) {
                    entryToSave.setAdminSync(SyncStatus.USER_DONE.name());
                    LoggerUtil.debug(this.getClass(),
                            String.format("Updated entry %d status from USER_INPUT to USER_DONE", entry.getEntryId()));
                }

                updatedEntries.add(entryToSave);
            }

            // Save to admin file with the complete updated list
            dataAccessService.writeLocalAdminRegister(username, userId, updatedEntries, year, month);
            LoggerUtil.info(this.getClass(),
                    String.format("Saved %d entries to admin register for user %s", updatedEntries.size(), username));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error saving admin register entries: %s", e.getMessage()), e);
            throw new RuntimeException("Failed to save admin register entries", e);
        }
    }

    private RegisterEntry copyEntry(RegisterEntry source) {
        return RegisterEntry.builder()
                .entryId(source.getEntryId())
                .userId(source.getUserId())
                .date(source.getDate())
                .orderId(source.getOrderId())
                .productionId(source.getProductionId())
                .omsId(source.getOmsId())
                .clientName(source.getClientName())
                .actionType(source.getActionType())
                .printPrepTypes(new ArrayList<>(source.getPrintPrepTypes()))
                .colorsProfile(source.getColorsProfile())
                .articleNumbers(source.getArticleNumbers())
                .graphicComplexity(source.getGraphicComplexity())
                .observations(source.getObservations())
                .adminSync(source.getAdminSync())
                .build();
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

            if (config.notValid()) {
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

    @SuppressWarnings("unchecked")
    private RegisterEntry convertToRegisterEntry(Map<String, Object> data) {
        // Get printPrepTypes as a list from the data
        List<String> printPrepTypes = new ArrayList<>();
        if (data.get("printPrepTypes") instanceof List) {
            printPrepTypes = new ArrayList<>(new LinkedHashSet<>((List<String>) data.get("printPrepTypes")));
        } else if (data.get("printPrepTypes") instanceof String) {
            printPrepTypes = new ArrayList<>(new LinkedHashSet<>(
                    Arrays.asList(((String) data.get("printPrepTypes")).split("\\s*,\\s*"))
            ));
        }

        return RegisterEntry.builder()
                .entryId(convertToInteger(data.get("entryId")))
                .userId(convertToInteger(data.get("userId")))
                .date(parseLocalDate(data.get("date")))
                .orderId(String.valueOf(data.get("orderId")))
                .productionId(String.valueOf(data.get("productionId")))
                .omsId(String.valueOf(data.get("omsId")))
                .clientName(String.valueOf(data.get("clientName")))
                .actionType(String.valueOf(data.get("actionType")))
                .printPrepTypes(printPrepTypes)
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
    public BonusCalculationResult calculateBonus(List<RegisterEntry> entries, Integer userId, Integer year, Integer month, BonusConfiguration config) {
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
    public void saveBonusResult(Integer userId, Integer year, Integer month,
                                BonusCalculationResult result, String username) {
        try {
            // Get user's employeeId
            Integer employeeId = userService.getUserById(userId)
                    .map(User::getEmployeeId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            // Create bonus entry from calculation result
            BonusEntry bonusEntry = BonusEntry.fromBonusCalculationResult(username, employeeId, result);
            LoggerUtil.info(this.getClass(),
                    String.format("Created bonus entry for employee %d with amount %f",
                            employeeId, bonusEntry.getBonusAmount()));

            // Load and set previous months' bonuses
            PreviousMonthsBonuses previousMonths = loadPreviousMonthsBonuses(employeeId, year, month);
            bonusEntry.setPreviousMonths(previousMonths);

            // Read existing bonus entries
            List<BonusEntry> existingEntries;
            try {
                existingEntries = dataAccessService.readAdminBonus(year, month);
            } catch (Exception e) {
                existingEntries = new ArrayList<>();
            }

            // Find and replace or add the entry for this employee
            existingEntries.removeIf(entry -> entry.getEmployeeId().equals(employeeId));
            existingEntries.add(bonusEntry);

            // Save all entries
            dataAccessService.writeAdminBonus(existingEntries, year, month);

            LoggerUtil.info(this.getClass(),
                    String.format("Successfully saved bonus calculation for user %s (Employee ID: %d) for %d/%d",
                            username, employeeId, year, month));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error processing bonus calculation for user %d: %s",
                            userId, e.getMessage()));
            throw new RuntimeException("Failed to process bonus calculation", e);
        }
    }

    private Double loadMonthBonus(Integer employeeId, YearMonth month) {
        try {
            List<BonusEntry> entries = dataAccessService.readAdminBonus(month.getYear(), month.getMonthValue());

            return entries.stream()
                    .filter(entry -> entry.getEmployeeId().equals(employeeId))
                    .map(BonusEntry::getBonusAmount)
                    .findFirst()
                    .orElse(0.0);

        } catch (Exception e) {
            LoggerUtil.info(this.getClass(),
                    String.format("No bonus entry found for employee %d in %s: %s",
                            employeeId, month, e.getMessage()));
            return 0.0;
        }
    }

    private PreviousMonthsBonuses loadPreviousMonthsBonuses(Integer userId, Integer year, Integer month) {
        try {
            // Get employee ID first
            Integer employeeId = userService.getUserById(userId)
                    .map(User::getEmployeeId)
                    .orElse(userId); // Fallback to userId if employeeId not found

            YearMonth currentMonth = YearMonth.of(year, month);
            LoggerUtil.info(this.getClass(),
                    String.format("Loading previous months bonuses for employee %d, current month: %s",
                            employeeId, currentMonth));

            // Calculate previous months
            YearMonth month1 = currentMonth.minusMonths(1);
            YearMonth month2 = currentMonth.minusMonths(2);
            YearMonth month3 = currentMonth.minusMonths(3);

            Double bonus1 = loadMonthBonus(employeeId, month1);
            Double bonus2 = loadMonthBonus(employeeId, month2);
            Double bonus3 = loadMonthBonus(employeeId, month3);

            LoggerUtil.info(this.getClass(),
                    String.format("Previous months bonuses for employee %d: %f, %f, %f",
                            employeeId, bonus1, bonus2, bonus3));

            return PreviousMonthsBonuses.builder()
                    .month1(bonus1)
                    .month2(bonus2)
                    .month3(bonus3)
                    .build();

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(),
                    String.format("Error loading previous bonuses for user %d, returning zeros: %s",
                            userId, e.getMessage()));
            return PreviousMonthsBonuses.builder()
                    .month1(0.0)
                    .month2(0.0)
                    .month3(0.0)
                    .build();
        }
    }

    public BonusCalculationResult loadSavedBonusResult(Integer userId, Integer year, Integer month) {
        try {
            // Get user's employeeId
            Integer employeeId = userService.getUserById(userId)
                    .map(User::getEmployeeId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            Optional<BonusEntry> bonusEntryOpt = loadBonusEntry(employeeId, year, month);

            if (bonusEntryOpt.isEmpty()) {
                return null;
            }

            BonusEntry entry = bonusEntryOpt.get();

            return BonusCalculationResult.builder()
                    .entries(entry.getEntries())
                    .articleNumbers(entry.getArticleNumbers())
                    .graphicComplexity(entry.getGraphicComplexity())
                    .misc(entry.getMisc())
                    .workedDays(entry.getWorkedDays())
                    .workedPercentage(entry.getWorkedPercentage())
                    .bonusPercentage(entry.getBonusPercentage())
                    .bonusAmount(entry.getBonusAmount())
                    .previousMonths(entry.getPreviousMonths())
                    .build();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading bonus result for user %d: %s",
                            userId, e.getMessage()));
            throw new RuntimeException("Failed to load bonus result", e);
        }
    }

    private List<RegisterEntry> filterValidEntriesForBonus(List<RegisterEntry> entries) {
        List<String> bonusEligibleTypes = ActionType.getBonusEligibleValues();
        return entries.stream()
                .filter(entry -> bonusEligibleTypes.contains(entry.getActionType()))
                .collect(Collectors.toList());
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