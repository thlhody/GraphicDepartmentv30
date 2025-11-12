package com.ctgraphdep.register.service;

import com.ctgraphdep.model.CheckValuesEntry;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.UsersCheckValueEntry;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.utils.LoggerUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service for processing Excel files containing check register entries
 * Parses Excel data and converts to RegisterCheckEntry objects with calculated orderValue
 */
@Service
public class ExcelCheckRegisterProcessingService {

    @Autowired
    private CheckValuesService checkValuesService;

    // Article-based types (use articleNumbers for calculation)
    private static final List<String> ARTICLE_BASED_TYPES = Arrays.asList(
            "LAYOUT", "KIPSTA LAYOUT", "LAYOUT CHANGES", "GPT"
    );

    // File-based types (use filesNumbers for calculation)
    private static final List<String> FILE_BASED_TYPES = Arrays.asList(
            "PRODUCTION", "REORDER", "SAMPLE", "OMS PRODUCTION", "KIPSTA PRODUCTION", "GPT"
    );

    // Date formatter for DD/MM/YYYY format
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ExcelCheckRegisterProcessingService() {
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Process Excel file and extract check register entries
     *
     * @param file Excel file uploaded by user
     * @param username Username of the designer entries belong to
     * @param userId User ID
     * @param controllerUsername Username of the logged-in controller/team leader
     * @param adminSync Admin sync status to apply to entries
     * @return ServiceResult containing list of RegisterCheckEntry or error
     */
    public ServiceResult<List<RegisterCheckEntry>> processExcelFile(
            MultipartFile file,
            String username,
            Integer userId,
            String controllerUsername,
            String adminSync) {

        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Processing Excel file for user %s (ID: %d), controller: %s",
                    username, userId, controllerUsername));

            // Validate file
            if (file.isEmpty()) {
                return ServiceResult.validationError("Excel file is empty", "empty_file");
            }

            if (!isExcelFile(file)) {
                return ServiceResult.validationError(
                        "Invalid file format. Only .xlsx files are supported",
                        "invalid_file_format");
            }

            // Get check values for the user
            UsersCheckValueEntry userCheckValues = checkValuesService.getUserCheckValues(username, userId);
            if (userCheckValues == null || userCheckValues.getCheckValuesEntry() == null) {
                return ServiceResult.systemError(
                        "Check values not found for user: " + username,
                        "check_values_not_found");
            }

            CheckValuesEntry checkValues = userCheckValues.getCheckValuesEntry();

            // Parse Excel file
            List<RegisterCheckEntry> entries = parseExcelFile(file.getInputStream(), username, controllerUsername, adminSync, checkValues);

            if (entries.isEmpty()) {
                return ServiceResult.successWithWarning(entries, "No valid entries found in Excel file");
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully processed %d entries from Excel file for user %s",
                    entries.size(), username));

            return ServiceResult.success(entries);

        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error reading Excel file: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to read Excel file: " + e.getMessage(), "file_read_error");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error processing Excel file: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to process Excel file: " + e.getMessage(), "processing_error");
        }
    }

    /**
     * Parse Excel file and extract entries
     *
     * @param inputStream Excel file input stream
     * @param designerUsername Designer username
     * @param controllerUsername Controller username
     * @param adminSync Admin sync status
     * @param checkValues Check values for calculation
     * @return List of RegisterCheckEntry
     * @throws IOException if file cannot be read
     */
    private List<RegisterCheckEntry> parseExcelFile(
            InputStream inputStream,
            String designerUsername,
            String controllerUsername,
            String adminSync,
            CheckValuesEntry checkValues) throws IOException {

        List<RegisterCheckEntry> entries = new ArrayList<>();
        int nextEntryId = 1;

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            // Log all sheets
            int sheetCount = workbook.getNumberOfSheets();
            LoggerUtil.info(this.getClass(), String.format("Excel file has %d sheets", sheetCount));
            for (int i = 0; i < sheetCount; i++) {
                Sheet s = workbook.getSheetAt(i);
                LoggerUtil.info(this.getClass(), String.format("  Sheet %d: '%s' has %d rows",
                        i, s.getSheetName(), s.getLastRowNum() + 1));
            }

            Sheet sheet = workbook.getSheetAt(0); // Get first sheet

            int totalRows = sheet.getLastRowNum() + 1;
            LoggerUtil.info(this.getClass(), String.format(
                    "Using first sheet '%s' with %d total rows (including header)",
                    sheet.getSheetName(), totalRows));

            // Skip header row (row 0) and empty row (row 1)
            // Data starts at row 2 (Excel row 3)
            int rowNum = 1;
            int skippedEmptyRows = 0;

            for (Row row : sheet) {
                // Skip first 2 rows (header at row 0, empty at row 1)
                if (row.getRowNum() <= 1) {
                    LoggerUtil.debug(this.getClass(), String.format("Skipping row %d (header/formatting)", row.getRowNum()));
                    continue;
                }

                // Skip empty rows
                if (isEmptyRow(row)) {
                    skippedEmptyRows++;
                    // Log first few empty rows with cell details
                    if (skippedEmptyRows <= 3) {
                        StringBuilder cellInfo = new StringBuilder();
                        for (int cellNum = 0; cellNum < 11; cellNum++) {
                            Cell cell = row.getCell(cellNum);
                            if (cell != null) {
                                cellInfo.append(String.format(" [%d:%s='%s']", cellNum,
                                        cell.getCellType(), getCellValueAsString(cell)));
                            } else {
                                cellInfo.append(String.format(" [%d:null]", cellNum));
                            }
                        }
                        LoggerUtil.info(this.getClass(), String.format("Row %d is empty, cells:%s",
                                row.getRowNum() + 1, cellInfo.toString()));
                    } else if (skippedEmptyRows == 4) {
                        LoggerUtil.debug(this.getClass(), "Skipping detailed logging for remaining empty rows...");
                    }
                    continue;
                }

                try {
                    RegisterCheckEntry entry = parseRow(row, designerUsername, controllerUsername, adminSync, checkValues, nextEntryId);
                    if (entry != null) {
                        entries.add(entry);
                        nextEntryId++;
                        LoggerUtil.debug(this.getClass(), String.format(
                                "Parsed entry %d: Date=%s, OMS=%s, CheckType=%s, OrderValue=%.2f",
                                entry.getEntryId(), entry.getDate(), entry.getOmsId(),
                                entry.getCheckType(), entry.getOrderValue()));
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Failed to parse row %d: %s", row.getRowNum() + 1, e.getMessage()));
                    // Continue processing other rows
                }

                rowNum++;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Excel processing complete: %d entries parsed, %d rows skipped as empty",
                    entries.size(), skippedEmptyRows));
        }

        return entries;
    }

    /**
     * Parse a single Excel row into RegisterCheckEntry
     *
     * Column mapping:
     * A: Designer (designerName) - can be different from file owner
     * B: Date (date) - format DD/MM/YYYY
     * C: Order ID (omsId)
     * D: CV No. (productionId)
     * E: Work Type (checkType)
     * F: Nr of articles (articleNumbers)
     * G: Nr of pieces (filesNumbers)
     * H: Name & Number - SKIP
     * I: Error Description (errorDescription)
     * J: Approval Status (approvalStatus)
     * K: Controller - use logged in user (controllerUsername)
     */
    private RegisterCheckEntry parseRow(
            Row row,
            String designerUsername,
            String controllerUsername,
            String adminSync,
            CheckValuesEntry checkValues,
            int entryId) {

        try {
            // Column A: Designer Name (from Excel, might be different from file owner)
            String designerName = getCellValueAsString(row.getCell(0));
            if (designerName == null || designerName.trim().isEmpty()) {
                LoggerUtil.warn(this.getClass(), "Row " + (row.getRowNum() + 1) + ": Designer name is missing");
                return null;
            }

            // Column B: Date (DD/MM/YYYY format)
            LocalDate date = parseDateFromCell(row.getCell(1));
            if (date == null) {
                LoggerUtil.warn(this.getClass(), "Row " + (row.getRowNum() + 1) + ": Invalid or missing date");
                return null;
            }

            // Column C: Order ID (OMS ID)
            String omsId = getCellValueAsString(row.getCell(2));
            if (omsId == null || omsId.trim().isEmpty()) {
                LoggerUtil.warn(this.getClass(), "Row " + (row.getRowNum() + 1) + ": OMS ID is missing");
                return null;
            }

            // Column D: CV No. (Production ID)
            String productionId = getCellValueAsString(row.getCell(3));

            // Column E: Work Type (Check Type)
            String checkType = getCellValueAsString(row.getCell(4));
            if (checkType == null || checkType.trim().isEmpty()) {
                LoggerUtil.warn(this.getClass(), "Row " + (row.getRowNum() + 1) + ": Check type is missing");
                return null;
            }

            // Column F: Nr of articles (must be number)
            Integer articleNumbers = getCellValueAsInteger(row.getCell(5));
            if (articleNumbers == null || articleNumbers < 0) {
                LoggerUtil.warn(this.getClass(), "Row " + (row.getRowNum() + 1) + ": Invalid article numbers");
                return null;
            }

            // Column G: Nr of pieces (must be number)
            Integer filesNumbers = getCellValueAsInteger(row.getCell(6));
            if (filesNumbers == null || filesNumbers < 0) {
                LoggerUtil.warn(this.getClass(), "Row " + (row.getRowNum() + 1) + ": Invalid file numbers");
                return null;
            }

            // Column H: Name & Number - SKIP

            // Column I: Error Description
            String errorDescription = getCellValueAsString(row.getCell(8));

            // Column J: Approval Status
            String approvalStatus = getCellValueAsString(row.getCell(9));
            if (approvalStatus == null || approvalStatus.trim().isEmpty()) {
                LoggerUtil.warn(this.getClass(), "Row " + (row.getRowNum() + 1) + ": Approval status is missing");
                return null;
            }

            // Calculate orderValue based on check type and check values
            Double orderValue = calculateOrderValue(checkType, articleNumbers, filesNumbers, checkValues);

            // Build entry
            return RegisterCheckEntry.builder()
                    .entryId(entryId)
                    .designerName(designerName.trim())
                    .date(date)
                    .omsId(omsId.trim())
                    .productionId(productionId != null ? productionId.trim() : null)
                    .checkType(checkType.trim())
                    .articleNumbers(articleNumbers)
                    .filesNumbers(filesNumbers)
                    .errorDescription(errorDescription != null ? errorDescription.trim() : null)
                    .approvalStatus(approvalStatus.trim())
                    .orderValue(orderValue)
                    .adminSync(adminSync)
                    .build();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error parsing row " + (row.getRowNum() + 1) + ": " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Calculate order value based on check type and numbers
     * Matches JavaScript logic from CheckRegisterForm.js
     *
     * Rules:
     * - GPT: (articleNumbers * typeValue) + (filesNumbers * typeValue)
     * - Article-based types: articleNumbers * typeValue
     * - File-based types: filesNumbers * typeValue
     */
    private Double calculateOrderValue(String checkType, Integer articleNumbers, Integer filesNumbers, CheckValuesEntry checkValues) {
        double typeValue = getCheckTypeValue(checkType, checkValues);
        double orderValue = 0.0;

        if ("GPT".equals(checkType)) {
            // GPT uses both articles and files
            orderValue = (articleNumbers * typeValue) + (filesNumbers * typeValue);
        } else if (ARTICLE_BASED_TYPES.contains(checkType)) {
            // Article-based types
            orderValue = articleNumbers * typeValue;
        } else if (FILE_BASED_TYPES.contains(checkType)) {
            // File-based types
            orderValue = filesNumbers * typeValue;
        }

        return orderValue;
    }

    /**
     * Get check type value from CheckValuesEntry
     */
    private double getCheckTypeValue(String checkType, CheckValuesEntry checkValues) {
        return switch (checkType) {
            case "LAYOUT" -> checkValues.getLayoutValue();
            case "KIPSTA LAYOUT" -> checkValues.getKipstaLayoutValue();
            case "LAYOUT CHANGES" -> checkValues.getLayoutChangesValue();
            case "GPT" -> checkValues.getGptArticlesValue();
            case "PRODUCTION" -> checkValues.getProductionValue();
            case "REORDER" -> checkValues.getReorderValue();
            case "SAMPLE" -> checkValues.getSampleValue();
            case "OMS PRODUCTION" -> checkValues.getOmsProductionValue();
            case "KIPSTA PRODUCTION" -> checkValues.getKipstaProductionValue();
            default -> 0.1; // Default value
        };
    }

    /**
     * Parse date from Excel cell
     * Supports both DD/MM/YYYY string format and Excel date format
     */
    private LocalDate parseDateFromCell(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            // Try to get as date if cell is formatted as date
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }

            // Try to parse as string in DD/MM/YYYY format
            String dateStr = getCellValueAsString(cell);
            if (dateStr != null && !dateStr.trim().isEmpty()) {
                return LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
            }
        } catch (DateTimeParseException e) {
            LoggerUtil.warn(this.getClass(), "Failed to parse date: " + getCellValueAsString(cell));
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error reading date cell: " + e.getMessage());
        }

        return null;
    }

    /**
     * Get cell value as string
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> cell.getCachedFormulaResultType() == CellType.STRING
                        ? cell.getStringCellValue()
                        : String.valueOf(cell.getNumericCellValue());
                default -> null;
            };
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error reading cell as string: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get cell value as integer
     */
    private Integer getCellValueAsInteger(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (int) cell.getNumericCellValue();
                case STRING -> {
                    String str = cell.getStringCellValue().trim();
                    yield str.isEmpty() ? null : Integer.parseInt(str);
                }
                case FORMULA -> cell.getCachedFormulaResultType() == CellType.NUMERIC
                        ? (int) cell.getNumericCellValue()
                        : null;
                default -> null;
            };
        } catch (NumberFormatException e) {
            LoggerUtil.warn(this.getClass(), "Invalid number format: " + getCellValueAsString(cell));
            return null;
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error reading cell as integer: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if row is empty
     */
    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }

        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Validate if file is Excel format
     */
    private boolean isExcelFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return filename != null && (filename.endsWith(".xlsx") || filename.endsWith(".xls"));
    }
}
