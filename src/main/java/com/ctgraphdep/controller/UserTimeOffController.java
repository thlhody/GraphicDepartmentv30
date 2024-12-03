package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.TimeOffSummary;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.service.*;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/user/timeoff")
public class UserTimeOffController extends BaseController {
    private final UserTimeOffService timeOffService;
    private final AdminPaidHolidayService holidayService;
    private final UserWorkTimeService userWorkTimeService;

    public UserTimeOffController(
            UserService userService,
            FolderStatusService folderStatusService,
            UserTimeOffService timeOffService,
            AdminPaidHolidayService holidayService,
            UserWorkTimeService userWorkTimeService) {
        super(userService, folderStatusService);
        this.timeOffService = timeOffService;
        this.holidayService = holidayService;
        this.userWorkTimeService = userWorkTimeService;
    }

    @GetMapping
    public String showTimeOffPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        try {
            User user = getUser(userDetails);
            prepareTimeOffPageModel(model, user);
            return "user/timeoff";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading time off page: " + e.getMessage());
            model.addAttribute("errorMessage", "Error loading time off data");
            return "user/timeoff";
        }
    }

    @PostMapping
    public String processTimeOffRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String timeOffType,
            @RequestParam(defaultValue = "false") boolean isSingleDayRequest,
            RedirectAttributes redirectAttributes) {

        // Validate required fields
        if (startDate == null || (endDate == null && !isSingleDayRequest)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select date(s) for your time off request");
            return "redirect:/user/timeoff?error=date_required";
        }

        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a time off type");
            return "redirect:/user/timeoff?error=type_required";
        }

        // Handle single day request
        if (isSingleDayRequest) {
            endDate = startDate;
        }

        // Validate date range for multi-day requests
        if (!isSingleDayRequest && endDate.isBefore(startDate)) {
            redirectAttributes.addFlashAttribute("errorMessage", "End date must be after start date");
            return "redirect:/user/timeoff?error=invalid_range";
        }

        // Validate future dates
        LocalDate today = LocalDate.now();
        if (startDate.isBefore(today)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot request time off for past dates");
            return "redirect:/user/timeoff?error=past_date";
        }

        try {
            User user = getUser(userDetails);

            // Calculate workdays between dates
            int daysNeeded = CalculateWorkHoursUtil.calculateWorkDays(startDate, endDate, userWorkTimeService);

            // Validate paid leave availability if CO type
            if ("CO".equals(timeOffType)) {
                int availableDays = holidayService.getRemainingHolidayDays(user.getUsername(), user.getUserId());
                if (availableDays < daysNeeded) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            String.format("Insufficient paid holiday days. Needed: %d, Available: %d",
                                    daysNeeded, availableDays));
                    return "redirect:/user/timeoff?error=insufficient_days";
                }
            }

            timeOffService.processTimeOffRequest(user, startDate, endDate, timeOffType);
            String successMessage = createSuccessMessage(timeOffType, startDate, endDate, daysNeeded);
            redirectAttributes.addFlashAttribute("successMessage", successMessage);

            return "redirect:/user/timeoff";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error processing time off request: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to process time off request");
            return "redirect:/user/timeoff?error=submit_failed";
        }
    }

    private String createSuccessMessage(String timeOffType, LocalDate startDate, LocalDate endDate, int daysCount) {
        String dateInfo = startDate.equals(endDate) ?
                startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) :
                String.format("%s to %s",
                        startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        String typeLabel = "CO".equals(timeOffType) ? "vacation" : "medical leave";

        return String.format("Successfully requested %s for %s (%d working day%s)",
                typeLabel, dateInfo, daysCount, daysCount > 1 ? "s" : "");
    }

    private void prepareTimeOffPageModel(Model model, User user) {
        LocalDate twoMonthsAgo = LocalDate.now().minusMonths(2).withDayOfMonth(1);
        LocalDate maxDate = LocalDate.now().plusMonths(6);

        int availablePaidDays = holidayService.getRemainingHolidayDays(user.getUsername(), user.getUserId());
        List<WorkTimeTable> yearWorktime = userWorkTimeService.loadMonthWorktime(
                user.getUsername(), Year.now().getValue(), YearMonth.now().getMonthValue());

        TimeOffSummary summary = calculateTimeOffSummary(yearWorktime, availablePaidDays);

        // Get the user's current time off request
        List<WorkTimeTable> currentRequest = timeOffService.getUpcomingTimeOff(user);

        // Calculate the remaining days based on the current request
        int remainingDays = availablePaidDays;
        for (WorkTimeTable entry : currentRequest) {
            if ("CO".equals(entry.getTimeOffType())) {
                remainingDays -= 1;
            }
        }

        summary.setRemainingPaidDays(remainingDays);

        model.addAttribute("user", user);
        model.addAttribute("summary", summary);
        model.addAttribute("maxDate", maxDate);
        model.addAttribute("minDate", twoMonthsAgo.format(DateTimeFormatter.ISO_DATE));
        model.addAttribute("today", LocalDate.now().format(DateTimeFormatter.ISO_DATE));
        model.addAttribute("upcomingTimeOff", currentRequest);
    }

    private TimeOffSummary calculateTimeOffSummary(List<WorkTimeTable> worktime, int availablePaidDays) {
        int snDays = 0, coDays = 0, cmDays = 0;

        for (WorkTimeTable entry : worktime) {
            if (entry.getTimeOffType() != null) {
                switch (entry.getTimeOffType()) {
                    case "SN" -> snDays++;
                    case "CO" -> coDays++;
                    case "CM" -> cmDays++;
                }
            }
        }

        return TimeOffSummary.builder()
                .snDays(snDays)
                .coDays(coDays)
                .cmDays(cmDays)
                .availablePaidDays(availablePaidDays)
                .totalRequestedDays(coDays + cmDays)
                .totalApprovedDays(coDays + cmDays)
                .remainingPaidDays(availablePaidDays - coDays)
                .build();
    }
}