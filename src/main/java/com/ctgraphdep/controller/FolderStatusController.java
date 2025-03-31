package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.dto.SyncFolderStatusDTO;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class FolderStatusController extends BaseController {
    private final FolderStatus folderStatus;

    protected FolderStatusController(UserService userService, FolderStatus folderStatus, TimeValidationService timeValidationService, FolderStatus folderStatus1) {
        super(userService, folderStatus, timeValidationService);
        this.folderStatus = folderStatus1;
    }


    @GetMapping("/fragments/status")
    public String getStatus(Model model) {
        LoggerUtil.info(this.getClass(), "Getting sync status");
        SyncFolderStatusDTO status = folderStatus.getStatus();
        model.addAttribute("syncStatus", status);
        return "fragments/status :: statusIndicator";
    }

    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        SyncFolderStatusDTO status = folderStatus.getStatus();
        model.addAttribute("syncStatus", status);
    }
}