package com.ctgraphdep.utils;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.User;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for exporting check register entries to Excel from the status view
 * This implementation aligns with the check-register-status.html UI
 */
@Component
public class CheckRegisterStatusExcelExporter {

    /**
     * Export check register entries to Excel based on check-register-status.html view
     *
     * @param user The user whose check register is being exported
     * @param entries The check register entries to export
     * @param year The year of the report
     * @param month The month of the report
     * @return Byte array containing the Excel file
     */
    public byte[] exportToExcel(User user, List<RegisterCheckEntry> entries, int year, int month) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            LoggerUtil.info(this.getClass(), "Creating status check register Excel export for " + entries.size() + " entries");

            Sheet sheet = workbook.createSheet("Check Register View");
            Map<String, CellStyle> styles = createStyles(workbook);

            int rowNum = 0;

            // Create title section
            rowNum = createTitleSection(sheet, styles, user, year, month, rowNum);

            // Add some space
            rowNum += 1;

            // Create summary section that matches the status view
            rowNum = createSummarySection(sheet, styles, entries, rowNum);

            // Add some space
            rowNum += 1;

            // Create entries table that matches the status view columns
            createEntriesTable(sheet, styles, entries, rowNum);

            // Auto-size columns for better readability
            for (int i = 0; i < 13; i++) { // We have 13 columns in the status view
                sheet.autoSizeColumn(i);
            }

            // Write to byte array
            return writeToByteArray(workbook);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating Excel export: " + e.getMessage(), e);
            return new byte[0]; // Return empty array on error
        }
    }

    /**
     * Create the title section of the Excel report
     */
    private int createTitleSection(Sheet sheet, Map<String, CellStyle> styles, User user, int year, int month, int startRow) {
        int rowNum = startRow;

        // Title row with month and year
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Check Register Report - " + Month.of(month).toString() + " " + year);
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(titleRow.getRowNum(), titleRow.getRowNum(), 0, 12));

        // User information row
        Row userRow = sheet.createRow(rowNum++);
        Cell userCell = userRow.createCell(0);
        String userInfo = "User: " + user.getName();
        if (user.getEmployeeId() != null) {
            userInfo += " (ID: " + user.getEmployeeId() + ")";
        }
        userCell.setCellValue(userInfo);
        userCell.setCellStyle(styles.get("subtitle"));
        sheet.addMergedRegion(new CellRangeAddress(userRow.getRowNum(), userRow.getRowNum(), 0, 12));

        // Current date row
        Row dateRow = sheet.createRow(rowNum++);
        Cell dateCell = dateRow.createCell(0);
        dateCell.setCellValue("Export Date: " + java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        dateCell.setCellStyle(styles.get("date-header"));
        sheet.addMergedRegion(new CellRangeAddress(dateRow.getRowNum(), dateRow.getRowNum(), 0, 12));

        return rowNum;
    }

    /**
     * Create summary section like the one in check-register-status.html
     */
    private int createSummarySection(Sheet sheet, Map<String, CellStyle> styles, List<RegisterCheckEntry> entries, int startRow) {
        int rowNum = startRow;

        // Summary header
        Row headerRow = sheet.createRow(rowNum++);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("Summary Statistics");
        headerCell.setCellStyle(styles.get("section-header"));
        sheet.addMergedRegion(new CellRangeAddress(headerRow.getRowNum(), headerRow.getRowNum(), 0, 12));

        // Calculate summary metrics like in the StatusService
        Map<String, Object> summary = calculateSummary(entries);

        // Check Types row - using the same types shown in the status page
        Row checkTypeHeaderRow = sheet.createRow(rowNum++);
        Cell checkTypeHeaderCell = checkTypeHeaderRow.createCell(0);
        checkTypeHeaderCell.setCellValue("Check Types:");
        checkTypeHeaderCell.setCellStyle(styles.get("summary-label"));

        // Create badges for check types
        @SuppressWarnings("unchecked")
        Map<String, Long> checkTypeCounts = (Map<String, Long>) summary.get("checkTypeCounts");
        Row checkTypeRow = sheet.createRow(rowNum++);
        int cellIndex = 1;

        // Create badges for each check type in the order shown in the status page
        addBadgeCell(checkTypeRow, cellIndex++, "GPT", checkTypeCounts.getOrDefault("GPT", 0L), styles.get("badge-primary"));
        addBadgeCell(checkTypeRow, cellIndex++, "LAYOUT", checkTypeCounts.getOrDefault("LAYOUT", 0L), styles.get("badge-success"));
        addBadgeCell(checkTypeRow, cellIndex++, "PRODUCTION", checkTypeCounts.getOrDefault("PRODUCTION", 0L), styles.get("badge-info"));
        addBadgeCell(checkTypeRow, cellIndex++, "SAMPLE", checkTypeCounts.getOrDefault("SAMPLE", 0L), styles.get("badge-warning"));
        addBadgeCell(checkTypeRow, cellIndex++, "OMS PRODUCTION", checkTypeCounts.getOrDefault("OMS_PRODUCTION", 0L), styles.get("badge-danger"));

        long otherCount = getTotalForOtherCheckTypes(checkTypeCounts);
        addBadgeCell(checkTypeRow, cellIndex++, "Others", otherCount, styles.get("badge-secondary"));

        // Key Metrics row
        Row metricsHeaderRow = sheet.createRow(rowNum++);
        Cell metricsHeaderCell = metricsHeaderRow.createCell(0);
        metricsHeaderCell.setCellValue("Key Metrics:");
        metricsHeaderCell.setCellStyle(styles.get("summary-label"));

        // Create key metrics cells
        Row metricsRow = sheet.createRow(rowNum++);
        int metricsIndex = 1;

        // Add metrics matching the UI
        addKeyMetricCell(metricsRow, metricsIndex++, "Total Entries", summary.get("totalEntries"), styles.get("metric-value"));
        addKeyMetricCell(metricsRow, metricsIndex++, "Avg Files", String.format("%.2f", summary.get("avgFiles")), styles.get("metric-value"));
        addKeyMetricCell(metricsRow, metricsIndex++, "Avg Articles", String.format("%.2f", summary.get("avgArticles")), styles.get("metric-value"));
        addKeyMetricCell(metricsRow, metricsIndex++, "Total Order Value", String.format("%.2f", summary.get("totalOrderValue")), styles.get("metric-value"));

        return rowNum;
    }

    /**
     * Create the entries table with columns matching the status page
     */
    private void createEntriesTable(Sheet sheet, Map<String, CellStyle> styles, List<RegisterCheckEntry> entries, int startRow) {
        int rowNum = startRow;

        // Table header
        Row tableHeaderRow = sheet.createRow(rowNum++);
        Cell tableHeaderCell = tableHeaderRow.createCell(0);
        tableHeaderCell.setCellValue("Check Register Entries");
        tableHeaderCell.setCellStyle(styles.get("section-header"));
        sheet.addMergedRegion(new CellRangeAddress(tableHeaderRow.getRowNum(), tableHeaderRow.getRowNum(), 0, 11));

        // Column headers - matching the status view
        String[] headers = {"ID", "Date", "OMS ID", "Prod. ID", "Designer", "Check Type", "Art", "Files", "Approval", "Value", "Error Desc", "Status"};

        Row headerRow = sheet.createRow(rowNum++);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("table-header"));
        }

        // Sort entries by date (newest first) like the status view
        List<RegisterCheckEntry> sortedEntries = new ArrayList<>(entries);
        sortedEntries.sort(Comparator.comparing(RegisterCheckEntry::getDate).reversed());

        // Add entries
        for (RegisterCheckEntry entry : sortedEntries) {
            Row entryRow = sheet.createRow(rowNum++);

            // Entry ID
            Cell idCell = entryRow.createCell(0);
            idCell.setCellValue(entry.getEntryId() != null ? entry.getEntryId() : 0);
            idCell.setCellStyle(styles.get("cell-center"));

            // Date
            Cell dateCell = entryRow.createCell(1);
            dateCell.setCellValue(entry.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            dateCell.setCellStyle(styles.get("cell-center"));

            // OMS ID
            Cell omsIdCell = entryRow.createCell(2);
            omsIdCell.setCellValue(entry.getOmsId() != null ? entry.getOmsId() : "");
            omsIdCell.setCellStyle(styles.get("cell-text"));


            // Production ID
            Cell productionIdCell = entryRow.createCell(3);
            productionIdCell.setCellValue(entry.getProductionId() != null ? entry.getProductionId() : "");
            productionIdCell.setCellStyle(styles.get("cell-text"));

            // Designer Name
            Cell designerCell = entryRow.createCell(4);
            designerCell.setCellValue(entry.getDesignerName() != null ? entry.getDesignerName() : "");
            designerCell.setCellStyle(styles.get("cell-text"));

            // Check Type with badge styling
            Cell checkTypeCell = entryRow.createCell(5);
            checkTypeCell.setCellValue(entry.getCheckType() != null ? entry.getCheckType() : "");
            checkTypeCell.setCellStyle(getCheckTypeCellStyle(entry.getCheckType(), styles));

            // Articles
            Cell articlesCell = entryRow.createCell(6);
            articlesCell.setCellValue(entry.getArticleNumbers() != null ? entry.getArticleNumbers() : 0);
            articlesCell.setCellStyle(styles.get("cell-number"));

            // Files
            Cell filesCell = entryRow.createCell(7);
            filesCell.setCellValue(entry.getFilesNumbers() != null ? entry.getFilesNumbers() : 0);
            filesCell.setCellStyle(styles.get("cell-number"));

            // Approval Status with badge styling
            Cell approvalCell = entryRow.createCell(8);
            approvalCell.setCellValue(entry.getApprovalStatus() != null ? entry.getApprovalStatus() : "");
            approvalCell.setCellStyle(getApprovalStatusCellStyle(entry.getApprovalStatus(), styles));

            // Order Value
            Cell valueCell = entryRow.createCell(9);
            valueCell.setCellValue(entry.getOrderValue() != null ? entry.getOrderValue() : 0.0);
            valueCell.setCellStyle(styles.get("cell-decimal"));

            // Error Description
            Cell errorDescCell = entryRow.createCell(10);
            errorDescCell.setCellValue(entry.getErrorDescription() != null ? entry.getErrorDescription() : "");
            errorDescCell.setCellStyle(styles.get("cell-text"));

            // Admin Sync Status with badge styling
            Cell statusCell = entryRow.createCell(1);
            String statusText = getReadableStatus(entry.getAdminSync());
            statusCell.setCellValue(statusText);
            statusCell.setCellStyle(getAdminStatusCellStyle(entry.getAdminSync(), styles));
        }
    }

    /**
     * Helper method to get a readable status text from adminSync code
     */
    private String getReadableStatus(String adminSync) {
        if (adminSync == null) return "Unknown";

        return switch (adminSync) {
            case "CHECKING_INPUT" -> "In Process";
            case "CHECKING_DONE" -> "Complete";
            case "TL_CHECK_DONE" -> "TL Approved";
            case "TL_EDITED" -> "TL Edited";
            case "ADMIN_EDITED" -> "Admin Edited";
            case "ADMIN_DONE" -> "Admin Approved";
            default -> adminSync;
        };
    }

    /**
     * Helper method to add a badge-style cell
     */
    private void addBadgeCell(Row row, int cellIndex, String label, Object value, CellStyle style) {
        Cell cell = row.createCell(cellIndex);
        cell.setCellValue(label + ": " + value);
        cell.setCellStyle(style);
    }

    /**
     * Helper method to add a key metric cell
     */
    private void addKeyMetricCell(Row row, int cellIndex, String label, Object value, CellStyle style) {
        Cell cell = row.createCell(cellIndex);
        cell.setCellValue(label + ": " + value);
        cell.setCellStyle(style);
    }

    /**
     * Helper method to get total count for "Others" category
     */
    private long getTotalForOtherCheckTypes(Map<String, Long> checkTypeCounts) {
        long otherCount = 0;

        // The main check types shown in the UI
        Set<String> mainTypes = Set.of("GPT", "LAYOUT", "PRODUCTION", "SAMPLE", "OMS_PRODUCTION");

        // Sum counts for all other types
        for (Map.Entry<String, Long> entry : checkTypeCounts.entrySet()) {
            if (!mainTypes.contains(entry.getKey())) {
                otherCount += entry.getValue();
            }
        }

        return otherCount;
    }

    /**
     * Calculate summary metrics similar to those shown in check-register-status.html
     */
    private Map<String, Object> calculateSummary(List<RegisterCheckEntry> entries) {
        Map<String, Object> summary = new HashMap<>();

        // Count check types
        Map<String, Long> checkTypeCounts = entries.stream()
                .filter(entry -> entry.getCheckType() != null)
                .collect(Collectors.groupingBy(
                        RegisterCheckEntry::getCheckType,
                        Collectors.counting()
                ));

        // Calculate total entries
        int totalEntries = entries.size();

        // Calculate average articles
        double avgArticles = entries.stream()
                .filter(entry -> entry.getArticleNumbers() != null)
                .mapToInt(RegisterCheckEntry::getArticleNumbers)
                .average()
                .orElse(0.0);

        // Calculate average files
        double avgFiles = entries.stream()
                .filter(entry -> entry.getFilesNumbers() != null)
                .mapToInt(RegisterCheckEntry::getFilesNumbers)
                .average()
                .orElse(0.0);

        // Calculate total order value
        double totalOrderValue = entries.stream()
                .filter(entry -> entry.getOrderValue() != null)
                .mapToDouble(RegisterCheckEntry::getOrderValue)
                .sum();

        // Store summary values
        summary.put("checkTypeCounts", checkTypeCounts);
        summary.put("totalEntries", totalEntries);
        summary.put("avgArticles", avgArticles);
        summary.put("avgFiles", avgFiles);
        summary.put("totalOrderValue", totalOrderValue);

        return summary;
    }

    /**
     * Get the appropriate cell style for a check type
     */
    private CellStyle getCheckTypeCellStyle(String checkType, Map<String, CellStyle> styles) {
        if (checkType == null) return styles.get("cell-text");

        return switch (checkType) {
            case "GPT" -> styles.get("badge-primary");
            case "LAYOUT" -> styles.get("badge-success");
            case "PRODUCTION" -> styles.get("badge-info");
            case "SAMPLE" -> styles.get("badge-warning");
            case "OMS_PRODUCTION" -> styles.get("badge-danger");
            default -> styles.get("badge-secondary");
        };
    }

    /**
     * Get the appropriate cell style for an approval status
     */
    private CellStyle getApprovalStatusCellStyle(String status, Map<String, CellStyle> styles) {
        if (status == null) return styles.get("cell-text");

        return switch (status) {
            case "APPROVED" -> styles.get("badge-success");
            case "PARTIALLY APPROVED" -> styles.get("badge-warning");
            case "CORRECTION" -> styles.get("badge-danger");
            default -> styles.get("badge-secondary");
        };
    }

    /**
     * Get the appropriate cell style for an admin status
     */
    private CellStyle getAdminStatusCellStyle(String status, Map<String, CellStyle> styles) {
        if (status == null) return styles.get("cell-text");

        return switch (status) {
            case "CHECKING_INPUT" -> styles.get("badge-secondary");
            case "CHECKING_DONE" -> styles.get("badge-success");
            case "TL_CHECK_DONE" -> styles.get("badge-primary");
            case "TL_EDITED" -> styles.get("badge-info");
            case "ADMIN_EDITED" -> styles.get("badge-warning");
            case "ADMIN_DONE" -> styles.get("badge-success");
            default -> styles.get("badge-secondary");
        };
    }

    /**
     * Create all cell styles used in the workbook
     */
    private Map<String, CellStyle> createStyles(Workbook workbook) {
        Map<String, CellStyle> styles = new HashMap<>();

        // Create fonts
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);

        Font subtitleFont = workbook.createFont();
        subtitleFont.setBold(true);
        subtitleFont.setFontHeightInPoints((short) 12);

        Font boldFont = workbook.createFont();
        boldFont.setBold(true);

        Font whiteFont = workbook.createFont();
        whiteFont.setColor(IndexedColors.WHITE.getIndex());
        whiteFont.setBold(true);

        // Title style
        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.put("title", titleStyle);

        // Subtitle style
        CellStyle subtitleStyle = workbook.createCellStyle();
        subtitleStyle.setFont(subtitleFont);
        subtitleStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.put("subtitle", subtitleStyle);

        // Date header style
        CellStyle dateHeaderStyle = workbook.createCellStyle();
        dateHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        dateHeaderStyle.setFont(boldFont);
        styles.put("date-header", dateHeaderStyle);

        // Section header style
        CellStyle sectionHeaderStyle = workbook.createCellStyle();
        sectionHeaderStyle.setFont(boldFont);
        sectionHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        sectionHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        sectionHeaderStyle.setAlignment(HorizontalAlignment.LEFT);
        sectionHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        sectionHeaderStyle.setBorderBottom(BorderStyle.THIN);
        styles.put("section-header", sectionHeaderStyle);

        // Summary label style
        CellStyle summaryLabelStyle = workbook.createCellStyle();
        summaryLabelStyle.setFont(boldFont);
        summaryLabelStyle.setAlignment(HorizontalAlignment.LEFT);
        summaryLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.put("summary-label", summaryLabelStyle);

        // Metric value style
        CellStyle metricValueStyle = workbook.createCellStyle();
        metricValueStyle.setAlignment(HorizontalAlignment.LEFT);
        metricValueStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.put("metric-value", metricValueStyle);

        // Table header style
        CellStyle tableHeaderStyle = workbook.createCellStyle();
        tableHeaderStyle.setFont(boldFont);
        tableHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        tableHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tableHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        tableHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        tableHeaderStyle.setBorderBottom(BorderStyle.THIN);
        tableHeaderStyle.setBorderTop(BorderStyle.THIN);
        tableHeaderStyle.setBorderLeft(BorderStyle.THIN);
        tableHeaderStyle.setBorderRight(BorderStyle.THIN);
        styles.put("table-header", tableHeaderStyle);

        // Basic cell styles
        CellStyle cellTextStyle = workbook.createCellStyle();
        cellTextStyle.setAlignment(HorizontalAlignment.LEFT);
        cellTextStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        cellTextStyle.setBorderBottom(BorderStyle.THIN);
        cellTextStyle.setBorderTop(BorderStyle.THIN);
        cellTextStyle.setBorderLeft(BorderStyle.THIN);
        cellTextStyle.setBorderRight(BorderStyle.THIN);
        cellTextStyle.setWrapText(true);
        styles.put("cell-text", cellTextStyle);

        CellStyle cellCenterStyle = workbook.createCellStyle();
        cellCenterStyle.setAlignment(HorizontalAlignment.CENTER);
        cellCenterStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        cellCenterStyle.setBorderBottom(BorderStyle.THIN);
        cellCenterStyle.setBorderTop(BorderStyle.THIN);
        cellCenterStyle.setBorderLeft(BorderStyle.THIN);
        cellCenterStyle.setBorderRight(BorderStyle.THIN);
        styles.put("cell-center", cellCenterStyle);

        CellStyle cellNumberStyle = workbook.createCellStyle();
        cellNumberStyle.setAlignment(HorizontalAlignment.RIGHT);
        cellNumberStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        cellNumberStyle.setBorderBottom(BorderStyle.THIN);
        cellNumberStyle.setBorderTop(BorderStyle.THIN);
        cellNumberStyle.setBorderLeft(BorderStyle.THIN);
        cellNumberStyle.setBorderRight(BorderStyle.THIN);
        styles.put("cell-number", cellNumberStyle);

        CellStyle cellDecimalStyle = workbook.createCellStyle();
        cellDecimalStyle.setAlignment(HorizontalAlignment.RIGHT);
        cellDecimalStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        cellDecimalStyle.setBorderBottom(BorderStyle.THIN);
        cellDecimalStyle.setBorderTop(BorderStyle.THIN);
        cellDecimalStyle.setBorderLeft(BorderStyle.THIN);
        cellDecimalStyle.setBorderRight(BorderStyle.THIN);
        cellDecimalStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        styles.put("cell-decimal", cellDecimalStyle);

        // Badge styles for bootstrap colors
        createBadgeStyle(workbook, styles, "badge-primary", IndexedColors.CORNFLOWER_BLUE, whiteFont);
        createBadgeStyle(workbook, styles, "badge-secondary", IndexedColors.GREY_50_PERCENT, whiteFont);
        createBadgeStyle(workbook, styles, "badge-success", IndexedColors.GREEN, whiteFont);
        createBadgeStyle(workbook, styles, "badge-danger", IndexedColors.RED, whiteFont);
        createBadgeStyle(workbook, styles, "badge-warning", IndexedColors.LIGHT_YELLOW, boldFont);
        createBadgeStyle(workbook, styles, "badge-info", IndexedColors.SKY_BLUE, whiteFont);

        return styles;
    }

    /**
     * Helper method to create badge styles with consistent formatting
     */
    private void createBadgeStyle(Workbook workbook, Map<String, CellStyle> styles,
                                  String styleName, IndexedColors color, Font font) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        styles.put(styleName, style);
    }

    /**
     * Write workbook to byte array
     */
    private byte[] writeToByteArray(XSSFWorkbook workbook) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        return outputStream.toByteArray();
    }
}