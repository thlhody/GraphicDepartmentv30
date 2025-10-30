package com.ctgraphdep.worktime.display.preparation;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.worktime.WorkTimeDisplayDTO;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.worktime.util.WorkTimeEntryUtil;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Service for preparing display data for Thymeleaf views.
 * This class handles data preparation logic for admin and user views,
 * including:
 * - Day headers for calendar display
 * - Display DTO creation for worktime entries
 * - View-specific data formatting
 * Extracted from WorktimeDisplayService to separate view concerns
 * from business logic.
 */
@Component
public class DisplayDataPreparationService {

    /**
     * Prepare day headers for admin calendar display.
     * Creates header information for each day in a month including:
     * - Day number
     * - Day initial (Romanian: L, M, M, J, V, S, D)
     * - Weekend flag
     * @param yearMonth Year and month for which to prepare headers
     * @return List of header maps, one per day
     */
    public List<Map<String, String>> prepareDayHeaders(YearMonth yearMonth) {
        WorkTimeEntryUtil.validateYearMonth(yearMonth);

        List<Map<String, String>> dayHeaders = new ArrayList<>();

        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            Map<String, String> headerInfo = new HashMap<>();

            headerInfo.put("day", String.valueOf(day));
            headerInfo.put("initial", WorkCode.ROMANIAN_DAY_INITIALS.get(date.getDayOfWeek()));
            headerInfo.put("isWeekend", WorkTimeEntryUtil.isDateWeekend(date) ? "true" : "false");
            dayHeaders.add(headerInfo);
        }

        LoggerUtil.debug(this.getClass(), String.format(
                "Prepared %d day headers for %s", dayHeaders.size(), yearMonth));

        return dayHeaders;
    }

    /**
     * Prepare worktime display DTOs for multiple users.
     * Creates a complete calendar view for each user, with one DTO per day.
     * Each DTO represents either:
     * - An existing worktime entry (work, time-off, etc.)
     * - An empty day (no entry)
     * @param users List of users to process
     * @param userEntriesMap Map of userId → (date → entry)
     * @param year Year
     * @param month Month
     * @param createDisplayDTO Function to create DTO from entry (injected from calling service)
     * @return Map of userId → (date → display DTO)
     */
    public Map<Integer, Map<LocalDate, WorkTimeDisplayDTO>> prepareDisplayDTOs(
            List<User> users,
            Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap,
            int year,
            int month,
            DisplayDTOCreator createDisplayDTO) {

        LoggerUtil.info(this.getClass(), String.format("Preparing display DTOs for %d users in %d/%d", users.size(), month, year));

        Map<Integer, Map<LocalDate, WorkTimeDisplayDTO>> displayDTOs = new HashMap<>();
        YearMonth yearMonth = YearMonth.of(year, month);

        for (User user : users) {
            Map<LocalDate, WorkTimeDisplayDTO> userDisplayMap = new TreeMap<>();
            Map<LocalDate, WorkTimeTable> userEntries = userEntriesMap.getOrDefault(user.getUserId(), new HashMap<>());

            // Process each day of the month
            for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
                LocalDate date = yearMonth.atDay(day);
                WorkTimeTable entry = userEntries.get(date);
                boolean isWeekend = WorkTimeEntryUtil.isDateWeekend(date);

                // Delegate DTO creation to calling service (which has access to status converter)
                WorkTimeDisplayDTO displayDTO = createDisplayDTO.create(entry, user, date, isWeekend);
                userDisplayMap.put(date, displayDTO);
            }

            displayDTOs.put(user.getUserId(), userDisplayMap);

            LoggerUtil.debug(this.getClass(), String.format("Created %d display DTOs for user %s", userDisplayMap.size(), user.getName()));
        }

        LoggerUtil.info(this.getClass(), String.format("Successfully prepared display DTOs for %d users", users.size()));

        return displayDTOs;
    }

    /**
     * Functional interface for creating display DTOs.
     * Allows the calling service to inject its DTO creation logic
     * while this service handles the iteration and structure.
     */
    @FunctionalInterface
    public interface DisplayDTOCreator {
        WorkTimeDisplayDTO create(WorkTimeTable entry, User user, LocalDate date, boolean isWeekend);
    }
}
