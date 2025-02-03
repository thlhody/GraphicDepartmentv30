package com.ctgraphdep.service;

import com.ctgraphdep.model.BonusEntry;
import com.ctgraphdep.model.BonusEntryDTO;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.AdminBonusExcelExporter;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.util.*;

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
            // Get bonus data with null check
            List<BonusEntry> bonusData = dataAccessService.readAdminBonus(year, month);
            if (bonusData == null) {
                bonusData = new ArrayList<>(); // Initialize empty list if null
            }

            Map<Integer, BonusEntryDTO> enrichedData = new HashMap<>();

            // Process each bonus entry safely
            for (BonusEntry entry : bonusData) {
                try {
                    // Find user by employeeId instead of userId
                    Optional<User> userOpt = userService.findByEmployeeId(entry.getEmployeeId());

                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        BonusEntryDTO dto = new BonusEntryDTO(entry, user.getName());
                        enrichedData.put(entry.getEmployeeId(), dto);
                        LoggerUtil.info(this.getClass(),
                                String.format("Successfully enriched data for employee %d: %s",
                                        entry.getEmployeeId(), user.getName()));
                    } else {
                        // Fallback to using username if user not found
                        BonusEntryDTO dto = new BonusEntryDTO(entry, entry.getUsername());
                        enrichedData.put(entry.getEmployeeId(), dto);
                        LoggerUtil.warn(this.getClass(),
                                String.format("User not found for employee ID %d, using username",
                                        entry.getEmployeeId()));
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(),
                            String.format("Error processing employee ID %d: %s",
                                    entry.getEmployeeId(), e.getMessage()));
                    // Continue processing other entries
                }
            }

            return enrichedData;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading bonus data for %d/%d: %s",
                            year, month, e.getMessage()));
            return new HashMap<>(); // Return empty map instead of null
        }
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