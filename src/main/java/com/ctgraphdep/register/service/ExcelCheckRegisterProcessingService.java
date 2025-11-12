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

    // Date formatters for multiple formats (try day-first, then month-first)
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),  // Day first with /
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),  // Day first with .
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),  // Day first with -
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),  // Month first with /
            DateTimeFormatter.ofPattern("MM.dd.yyyy"),  // Month first with .
            DateTimeFormatter.ofPattern("MM-dd-yyyy")   // Month first with -
    );

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

            // Parse Excel file with validation
            List<String> validationErrors = new ArrayList<>();
            List<RegisterCheckEntry> entries = parseExcelFile(file.getInputStream(), username, controllerUsername, adminSync, checkValues, validationErrors);

            // If there are validation errors, return them to user with downloadable error file
            if (!validationErrors.isEmpty()) {
                String errorMessage = String.format("Validation failed for %d rows. Please correct the following errors and upload again:\n%s",
                        validationErrors.size(),
                        String.join("\n", validationErrors));
                LoggerUtil.warn(this.getClass(), String.format("Validation failed with %d errors", validationErrors.size()));
                return ServiceResult.validationError(errorMessage, "validation_failed");
            }

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
     * @param validationErrors List to collect all validation errors
     * @return List of RegisterCheckEntry
     * @throws IOException if file cannot be read
     */
    private List<RegisterCheckEntry> parseExcelFile(
            InputStream inputStream,
            String designerUsername,
            String controllerUsername,
            String adminSync,
            CheckValuesEntry checkValues,
            List<String> validationErrors) throws IOException {

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

            // Try to find the "Registry" sheet, otherwise use first sheet
            Sheet sheet = null;
            for (int i = 0; i < sheetCount; i++) {
                Sheet s = workbook.getSheetAt(i);
                if ("Registry".equalsIgnoreCase(s.getSheetName())) {
                    sheet = s;
                    LoggerUtil.info(this.getClass(), "Found 'Registry' sheet, using it for data");
                    break;
                }
            }

            // Fallback to first sheet if Registry not found
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
                LoggerUtil.info(this.getClass(), "Registry sheet not found, using first sheet as fallback");
            }

            int totalRows = sheet.getLastRowNum() + 1;
            LoggerUtil.info(this.getClass(), String.format(
                    "Using sheet '%s' with %d total rows (including header)",
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
                    RegisterCheckEntry entry = parseRow(row, designerUsername, controllerUsername, adminSync, checkValues, nextEntryId, validationErrors);
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
                    validationErrors.add(String.format("Row %d: Unexpected error - %s", row.getRowNum() + 1, e.getMessage()));
                    // Continue processing other rows to collect all errors
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
     * A: Designer (designerName) - defaults to CTTT_Name if empty
     * B: Date (date) - multiple formats supported (DD/MM/YYYY, MM/DD/YYYY with /, ., -)
     * C: Order ID (omsId)
     * D: CV No. (productionId) - optional
     * E: Work Type (checkType)
     * F: Nr of articles (articleNumbers) - defaults to 1 if null/empty/0
     * G: Nr of pieces (filesNumbers) - defaults to 1 if null/empty/0
     * H: Name & Number - SKIP
     * I: Error Description (errorDescription) - optional
     * J: Approval Status (approvalStatus)
     * K: Controller - use logged in user (controllerUsername)
     */
    private RegisterCheckEntry parseRow(
            Row row,
            String designerUsername,
            String controllerUsername,
            String adminSync,
            CheckValuesEntry checkValues,
            int entryId,
            List<String> validationErrors) {

        try {
            int excelRowNum = row.getRowNum() + 1; // Excel row number (1-based)
            boolean hasErrors = false;

            // Column A: Designer Name (default to CTTT_Name if empty)
            String designerName = getCellValueAsString(row.getCell(0));
            if (designerName == null || designerName.trim().isEmpty()) {
                designerName = "CTTT_Name";
                LoggerUtil.debug(this.getClass(), "Row " + excelRowNum + ": Using default designer name: CTTT_Name");
            }

            // Column B: Date (multiple formats supported)
            String dateStr = getCellValueAsString(row.getCell(1));
            LocalDate date = parseDateFromCell(row.getCell(1), excelRowNum, dateStr, validationErrors);
            if (date == null) {
                hasErrors = true;
            }

            // Column C: Order ID (OMS ID)
            String omsId = getCellValueAsString(row.getCell(2));
            if (omsId == null || omsId.trim().isEmpty()) {
                validationErrors.add(String.format("Row %d: OMS ID is missing", excelRowNum));
                hasErrors = true;
            }

            // Column D: CV No. (Production ID) - optional, can be null/empty
            String productionId = getCellValueAsString(row.getCell(3));

            // Column E: Work Type (Check Type)
            String checkType = getCellValueAsString(row.getCell(4));
            if (checkType == null || checkType.trim().isEmpty()) {
                validationErrors.add(String.format("Row %d: Check type is missing", excelRowNum));
                hasErrors = true;
            }

            // Column F: Nr of articles (defaults to 1 if null/empty/0)
            Integer articleNumbers = getCellValueAsInteger(row.getCell(5));
            if (articleNumbers == null || articleNumbers <= 0) {
                articleNumbers = 1;
                LoggerUtil.debug(this.getClass(), "Row " + excelRowNum + ": Nr of articles is null/empty/0, defaulting to 1");
            }

            // Column G: Nr of pieces (defaults to 1 if null/empty/0)
            Integer filesNumbers = getCellValueAsInteger(row.getCell(6));
            if (filesNumbers == null || filesNumbers <= 0) {
                filesNumbers = 1;
                LoggerUtil.debug(this.getClass(), "Row " + excelRowNum + ": Nr of pieces is null/empty/0, defaulting to 1");
            }

            // Column H: Name & Number - SKIP

            // Column I: Error Description (optional - can be null/empty)
            String errorDescription = getCellValueAsString(row.getCell(8));

            // Column J: Approval Status
            String approvalStatus = getCellValueAsString(row.getCell(9));
            if (approvalStatus == null || approvalStatus.trim().isEmpty()) {
                validationErrors.add(String.format("Row %d: Approval status is missing", excelRowNum));
                hasErrors = true;
            }

            // If this row has validation errors, skip it (errors already collected)
            if (hasErrors) {
                LoggerUtil.warn(this.getClass(), "Row " + excelRowNum + ": Skipping row due to validation errors");
                return null;
            }

            // Calculate orderValue based on check type and check values
            // Round to 2 decimal places to avoid floating point precision issues (e.g., 0.30000000000000004)
            Double orderValue = calculateOrderValue(checkType, articleNumbers, filesNumbers, checkValues);
            orderValue = Math.round(orderValue * 100.0) / 100.0;

            // Build entry
            return RegisterCheckEntry.builder()
                    .entryId(entryId)
                    .designerName(designerName.trim())
                    .date(date)
                    .omsId(omsId != null ? omsId.trim() : null)
                    .productionId(productionId != null ? productionId.trim() : null)
                    .checkType(checkType != null ? checkType.trim() : null)
                    .articleNumbers(articleNumbers)
                    .filesNumbers(filesNumbers)
                    .errorDescription(errorDescription != null ? errorDescription.trim() : null)
                    .approvalStatus(approvalStatus != null ? approvalStatus.trim() : null)
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
     * Supports multiple date formats and Excel native date format
     * Collects validation errors instead of failing immediately
     *
     * @param cell Excel cell containing date
     * @param rowNum Excel row number (1-based) for error reporting
     * @param dateStr Raw date string value for error reporting
     * @param dateErrors List to collect validation errors
     * @return Parsed LocalDate or null if parsing failed (error added to dateErrors)
     */
    private LocalDate parseDateFromCell(Cell cell, int rowNum, String dateStr, List<String> dateErrors) {
        if (cell == null) {
            dateErrors.add(String.format("Row %d: Date is missing", rowNum));
            return null;
        }

        try {
            // Try to get as date if cell is formatted as native Excel date
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }

            // Try to parse as string with multiple formatters
            if (dateStr != null && !dateStr.trim().isEmpty()) {
                String trimmedDate = dateStr.trim();

                // Try each date formatter
                for (DateTimeFormatter formatter : DATE_FORMATTERS) {
                    try {
                        return LocalDate.parse(trimmedDate, formatter);
                    } catch (DateTimeParseException e) {
                        // Continue to next formatter
                    }
                }

                // If all formatters failed, add detailed error
                dateErrors.add(String.format("Row %d: Invalid date format '%s' - Expected formats: DD/MM/YYYY, MM/DD/YYYY, DD.MM.YYYY, MM.DD.YYYY, DD-MM-YYYY, or MM-DD-YYYY",
                        rowNum, trimmedDate));
                LoggerUtil.warn(this.getClass(), String.format("Failed to parse date at row %d: '%s'", rowNum, trimmedDate));
                return null;
            } else {
                dateErrors.add(String.format("Row %d: Date is empty", rowNum));
                return null;
            }
        } catch (Exception e) {
            dateErrors.add(String.format("Row %d: Error reading date cell - %s", rowNum, e.getMessage()));
            LoggerUtil.warn(this.getClass(), String.format("Error reading date cell at row %d: %s", rowNum, e.getMessage()));
            return null;
        }
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
