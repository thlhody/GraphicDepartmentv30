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
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
        LoggerUtil.initialize(this.getClass(), "Initializing Time Off Controller");
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
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam String timeOffType,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        LoggerUtil.info(this.getClass(),
                String.format("Received time off request - User: %s, Start: %s, End: %s, Type: %s",
                        userDetails.getUsername(), startDate, endDate, timeOffType));

        try {
            User user = getUser(userDetails);

            LoggerUtil.info(this.getClass(),
                    String.format("Processing time off request for user: %s (ID: %d)",
                            user.getUsername(), user.getUserId()));

            // Check for validation errors
            if (bindingResult.hasErrors()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Please check the form and try again.");
                return "redirect:/user/timeoff";
            }

            // Check if this is a CO request and if user has enough days
            if ("CO".equals(timeOffType)) {
                int daysNeeded = CalculateWorkHoursUtil.calculateWorkDays(
                        startDate, endDate, userWorkTimeService);
                int availableDays = holidayService.getRemainingHolidayDays(
                        user.getUsername(), user.getUserId());

                LoggerUtil.info(this.getClass(),
                        String.format("Checking CO availability - Needed: %d, Available: %d",
                                daysNeeded, availableDays));

                if (availableDays < daysNeeded) {
                    String message = String.format(
                            "Insufficient paid holiday days. Needed: %d, Available: %d",
                            daysNeeded, availableDays);
                    LoggerUtil.warn(this.getClass(), message);
                    redirectAttributes.addFlashAttribute("errorMessage", message);
                    return "redirect:/user/timeoff";
                }
            }

            timeOffService.processTimeOffRequest(user, startDate, endDate, timeOffType);
            LoggerUtil.info(this.getClass(),
                    String.format("Successfully processed time off request for %s",
                            user.getUsername()));

            redirectAttributes.addFlashAttribute("successMessage", "Time off request processed successfully.");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error processing time off request: %s", e.getMessage()), e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/user/timeoff";
    }

    private void prepareTimeOffPageModel(Model model, User user) {
        LocalDate twoMonthsAgo = LocalDate.now().minusMonths(2).withDayOfMonth(1);

        // Get user's holiday data
        int availablePaidDays = holidayService.getRemainingHolidayDays(user.getUsername(), user.getUserId());

        // Get yearly worktime data
        List<WorkTimeTable> yearWorktime = userWorkTimeService.loadMonthWorktime(
                user.getUsername(), Year.now().getValue(), YearMonth.now().getMonthValue());

        // Calculate summary
        TimeOffSummary summary = calculateTimeOffSummary(yearWorktime, availablePaidDays);

        // Prepare model attributes
        model.addAttribute("user", user);
        model.addAttribute("summary", summary);
        model.addAttribute("maxDate", LocalDate.now().plusMonths(6));
        model.addAttribute("minDate", twoMonthsAgo.format(DateTimeFormatter.ISO_DATE));
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
                .totalRequestedDays(snDays + coDays + cmDays)
                .totalApprovedDays(snDays + coDays + cmDays)
                .remainingPaidDays(availablePaidDays)
                .build();
    }
}