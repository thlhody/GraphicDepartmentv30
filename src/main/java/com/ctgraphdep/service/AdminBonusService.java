
package com.ctgraphdep.service;

import com.ctgraphdep.model.BonusEntry;
import com.ctgraphdep.utils.AdminBonusExcelExporter;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.poi.ss.usermodel.*;
        import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Map;

@Service
public class AdminBonusService {
    private final DataAccessService dataAccessService;
    private final UserService userService;
    private final AdminBonusExcelExporter adminBonusExcelExporter;

    public AdminBonusService(
            DataAccessService dataAccessService,
            UserService userService, AdminBonusExcelExporter adminBonusExcelExporterexcelExporter) {
        this.dataAccessService = dataAccessService;
        this.userService = userService;
        this.adminBonusExcelExporter = adminBonusExcelExporterexcelExporter;
        LoggerUtil.initialize(this.getClass(), "Initializing Admin Bonus Service");
    }

    public Map<Integer, BonusEntry> loadBonusData(int year, int month) {
        try {
            Path bonusPath = dataAccessService.getAdminBonusPath(year, month);
            Map<Integer, BonusEntry> bonusData = dataAccessService.readFile(
                    bonusPath,
                    new TypeReference<>() {},
                    false
            );

            // Enhance bonus data with user full names
            bonusData.forEach((userId, entry) -> {
                userService.getUserById(userId).ifPresent(user -> {
                    entry.setName(user.getName()); // This will use the full name instead of username
                });
            });

            LoggerUtil.info(this.getClass(),
                    String.format("Loaded bonus data for %d/%d with %d entries",
                            year, month, bonusData.size()));

            return bonusData;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading bonus data for %d/%d: %s",
                            year, month, e.getMessage()));
            throw new RuntimeException("Failed to load bonus data", e);
        }
    }

    public byte[] exportBonusData(int year, int month) {
        try {
            Map<Integer, BonusEntry> bonusData = loadBonusData(year, month);
            return adminBonusExcelExporter.exportToExcel(bonusData, year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error exporting bonus data for %d/%d: %s",
                            year, month, e.getMessage()));
            throw new RuntimeException("Failed to export bonus data", e);
        }
    }
}