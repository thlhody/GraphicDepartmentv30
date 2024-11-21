package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController extends BaseController {

    public HomeController(UserService userService, FolderStatusService folderStatusService) {
        super(userService, folderStatusService);
        LoggerUtil.initialize(this.getClass(), "Initializing Home Controller");
    }

    @GetMapping("/")
    public String home() {
        LoggerUtil.info(this.getClass(), "Accessing home page");
        return "index";
    }
}