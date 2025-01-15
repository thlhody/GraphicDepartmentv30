package com.ctgraphdep.service;

import com.ctgraphdep.model.BonusEntry;
import com.ctgraphdep.model.BonusEntryDTO;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.AdminBonusExcelExporter;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AdminBonusService {
    private final DataAccessService dataAccessService;
    private final UserService userService;
    private final AdminBonusExcelExporter adminBonusExcelExporter;

    public AdminBonusService(
            DataAccessService dataAccessService,
            UserService userService, AdminBonusExcelExporter adminBonusExcelExporter) {
        this.dataAccessService = dataAccessService;
        this.userService = userService;
        this.adminBonusExcelExporter = adminBonusExcelExporter;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public Map<Integer, BonusEntryDTO> loadBonusData(Integer year, Integer month) {
        try {
            Path bonusPath = dataAccessService.getAdminBonusPath(year, month);
            Map<Integer, BonusEntry> bonusData = dataAccessService.readFile(
                    bonusPath,
                    new TypeReference<>() {},
                    false
            );

            Map<Integer, BonusEntryDTO> enrichedData = new HashMap<>();

            bonusData.forEach((employeeId, entry) -> {
                try {
                    // Find user by employeeId instead of userId
                    Optional<User> userOpt = userService.findByEmployeeId(employeeId);
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        BonusEntryDTO dto = new BonusEntryDTO(entry, user.getName());
                        enrichedData.put(employeeId, dto);
                        LoggerUtil.info(this.getClass(), String.format("Successfully enriched data for employee %d: %s", employeeId, user.getName()));
                    } else {
                        BonusEntryDTO dto = new BonusEntryDTO(entry, entry.getUsername());
                        enrichedData.put(employeeId, dto);
                        LoggerUtil.warn(this.getClass(), String.format("User not found for employee ID %d, using username", employeeId));
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), String.format("Error processing employee ID %d: %s", employeeId, e.getMessage()));
                }
            });

            return enrichedData;

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error loading bonus data for %d/%d: %s", year, month, e.getMessage()), e);
        }
        return null;
    }

    public byte[] exportBonusData(Integer year, Integer month) {
        try {
            Map<Integer, BonusEntryDTO> bonusData = loadBonusData(year, month);
            return adminBonusExcelExporter.exportToExcel(bonusData, year, month);
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(),
                    String.format("Error exporting bonus data for %d/%d: %s", year, month, e.getMessage()),
                    e);
        }
        return null;
    }

    public byte[] exportUserBonusData(Integer year, Integer month) {
        try {
            Map<Integer, BonusEntryDTO> bonusData = loadBonusData(year, month);
            return adminBonusExcelExporter.exportUserToExcel(bonusData, year, month);
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(),
                    String.format("Error exporting user bonus data for %d/%d: %s", year, month, e.getMessage()),
                    e);
        }
        return null;
    }
}