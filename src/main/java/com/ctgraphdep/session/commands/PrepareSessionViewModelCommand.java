package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.session.WorkSessionDTO;
import com.ctgraphdep.session.service.SessionService;
import com.ctgraphdep.session.SessionContext;
import org.springframework.ui.Model;

// Command to prepare view model for session page using the SessionService
public class PrepareSessionViewModelCommand extends BaseSessionCommand<Void> {
    private final Model model;
    private final User user;

    // Creates a command to prepare the session view model
    public PrepareSessionViewModelCommand(Model model, User user) {
        validateCondition(model != null, "Model cannot be null");
        validateCondition(user != null, "User cannot be null");

        this.model = model;
        this.user = user;
    }

    @Override
    public Void execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            debug("Preparing session view model for user: " + user.getUsername());

            // Get SessionService from context
            SessionService sessionService = ctx.getSessionService();

            // Use SessionService to get a fully calculated WorkSessionDTO
            WorkSessionDTO sessionDTO = sessionService.getCurrentSession(user.getUsername(), user.getUserId());

            // Add the complete DTO to the model
            model.addAttribute("sessionDTO", sessionDTO);

            // Also add individual attributes for backward compatibility with existing templates
            mapDtoToModelAttributes(model, sessionDTO);

            // Add user information
            model.addAttribute("username", user.getUsername());
            model.addAttribute("userFullName", user.getName());

            info("Session view model prepared successfully for user: " + user.getUsername());
            return null;
        });
    }

    // Maps the WorkSessionDTO fields to individual model attributes for backward compatibility
    private void mapDtoToModelAttributes(Model model, WorkSessionDTO dto) {
        // Basic session information
        model.addAttribute("sessionStatus", dto.getFormattedStatus());

        // Time information
        model.addAttribute("currentDateTime", dto.getFormattedCurrentTime());
        model.addAttribute("dayStartTime", dto.getFormattedDayStartTime());

        // Work time calculations
        model.addAttribute("totalWorkRaw", dto.getFormattedRawWorkTime());
        model.addAttribute("actualWorkTime", dto.getFormattedActualWorkTime());
        model.addAttribute("overtime", dto.getFormattedOvertimeMinutes());

        // Breaks information
        model.addAttribute("temporaryStopCount", dto.getTemporaryStopCount());
        model.addAttribute("totalTemporaryStopTime", dto.getFormattedTotalTemporaryStopTime());
        model.addAttribute("lastTemporaryStopTime", dto.getFormattedLastTemporaryStopTime());

        // Lunch break information
        model.addAttribute("lunchBreakStatus", dto.getLunchBreakDeducted());

        // Calculation details
        model.addAttribute("discardedMinutes", dto.getDiscardedMinutes());

        // Scheduled end time
        if (dto.getScheduledEndTime() != null) {
            model.addAttribute("scheduledEndTime", dto.getFormattedScheduledEndTime());
        } else {
            model.addAttribute("scheduledEndTime", null);
        }
    }
}