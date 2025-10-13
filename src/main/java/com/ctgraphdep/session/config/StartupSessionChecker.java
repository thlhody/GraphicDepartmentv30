package com.ctgraphdep.session.config;

import com.ctgraphdep.session.service.SessionMidnightHandler;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.StartupSessionCheckCommand;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
public class StartupSessionChecker {

    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final SessionMidnightHandler sessionMidnightHandler;

    @Autowired
    public StartupSessionChecker(SessionCommandService commandService, SessionCommandFactory commandFactory, SessionMidnightHandler sessionMidnightHandler) {
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.sessionMidnightHandler = sessionMidnightHandler;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkSessionsAtStartup() {
        try {
            LoggerUtil.info(this.getClass(), "Executing startup session check...");

            StartupSessionCheckCommand command = commandFactory.createStartupSessionCheckCommand(sessionMidnightHandler);
            commandService.executeCommand(command);

            LoggerUtil.info(this.getClass(), "Startup session check completed");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during startup session check: " + e.getMessage(), e);
        }
    }
}