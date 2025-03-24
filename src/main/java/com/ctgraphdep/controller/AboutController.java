package com.ctgraphdep.controller;

import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AboutController {

    public AboutController() {
    }

    @GetMapping("/about")
    public String about() {

        LoggerUtil.info(this.getClass(), "Accessing about page");
        return "about";
    }
}