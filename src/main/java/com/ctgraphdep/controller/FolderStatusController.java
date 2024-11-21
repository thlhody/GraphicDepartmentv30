package com.ctgraphdep.controller;

import com.ctgraphdep.model.SyncFolderStatus;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class FolderStatusController {
    private final FolderStatusService folderStatusService;

    public FolderStatusController(FolderStatusService folderStatusService) {
        this.folderStatusService = folderStatusService;
        LoggerUtil.initialize(this.getClass(), "Initializing Status Controller");
    }

    @GetMapping("/fragments/status")
    public String getStatus(Model model) {
        LoggerUtil.info(this.getClass(), "Getting sync status");
        SyncFolderStatus status = folderStatusService.getStatus();
        model.addAttribute("syncStatus", status);
        return "fragments/status :: statusIndicator";
    }

    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        SyncFolderStatus status = folderStatusService.getStatus();
        model.addAttribute("syncStatus", status);
    }
}