package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.BonusEntryDTO;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.AdminBonusService;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.MonthFormatter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Month;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/bonus")
public class AdminBonusController extends BaseController {
    private final AdminBonusService adminBonusService;

    public AdminBonusController(
            UserService userService,
            FolderStatusService folderStatusService,
            AdminBonusService adminBonusService) {
        super(userService, folderStatusService);
        this.adminBonusService = adminBonusService;
        LoggerUtil.initialize(this.getClass(), "Initializing Admin Bonus Controller");
    }

    @GetMapping
    public String showBonusPage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {

        try {
            User currentUser = getUser(userDetails);

            // Verify admin access
            if (!currentUser.hasRole("ADMIN")) {
                return "redirect:/user";
            }

            // Set default year and month if not provided
            LocalDate now = LocalDate.now();
            year = year != null ? year : now.getYear();
            month = month != null ? month : now.getMonthValue();

            // Create months map
            Map<Integer, String> months = new LinkedHashMap<>();
            for (int m = 1; m <= 12; m++) {
                months.put(m, Month.of(m).toString());
            }

            // Add data to model
            model.addAttribute("currentYear", year);
            model.addAttribute("currentMonth", month);
            model.addAttribute("months", months);

            return "admin/bonus";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading bonus page: " + e.getMessage());
            model.addAttribute("error", "Error loading bonus data");
            return "admin/bonus";
        }
    }

    @GetMapping("/data")
    @ResponseBody
    public ResponseEntity<Map<Integer, BonusEntryDTO>> getBonusData(
            @RequestParam int year,
            @RequestParam int month) {
        try {
            String[] previousMonths = MonthFormatter.getPreviousMonthNames(year, month);
            Map<Integer, BonusEntryDTO> bonusData = adminBonusService.loadBonusData(year, month);

            return ResponseEntity.ok()
                    .header("X-Total-Count", String.valueOf(bonusData.size()))
                    .header("X-Previous-Month-1", previousMonths[0])
                    .header("X-Previous-Month-2", previousMonths[1])
                    .header("X-Previous-Month-3", previousMonths[2])
                    .body(bonusData);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading bonus data: " + e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportBonusData(
            @RequestParam int year,
            @RequestParam int month) {
        try {
            byte[] excelData = adminBonusService.exportBonusData(year, month);
            String filename = String.format("bonus_data_%d_%02d.xlsx", year, month);

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(excelData);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting bonus data: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/export/user")
    public ResponseEntity<byte[]> exportUserBonusData(
            @RequestParam int year,
            @RequestParam int month) {
        try {
            byte[] excelData = adminBonusService.exportUserBonusData(year, month);
            String filename = String.format("user_bonus_data_%d_%02d.xlsx", year, month);

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(excelData);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting user bonus data: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
