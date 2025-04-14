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
import java.util.*;

/**
 * Utility class for exporting check register entries to Excel
 * This implementation aligns with the check-register.html UI display
 */
@Component
public class CheckRegisterExcelExporter {

    // Constants for value calculations - matching the JavaScript constants in check-register.js
    private static final Map<String, Double> CHECK_TYPE_VALUES = Map.of(
            "LAYOUT", 1.0,
            "KIPSTA LAYOUT", 0.25,
            "LAYOUT CHANGES", 0.25,
            "GPT", 0.1,
            "PRODUCTION", 0.1,
            "REORDER", 0.1,
            "SAMPLE", 0.3,
            "OMS PRODUCTION", 0.1,
            "KIPSTA PRODUCTION", 0.1
    );

    // Types that use articlesNumbers for calculation
    private static final List<String> ARTICLE_BASED_TYPES = List.of(
            "LAYOUT",
            "KIPSTA LAYOUT",
            "LAYOUT CHANGES",
            "GPT"
    );

    // Types that use filesNumbers for calculation
    private static final List<String> FILE_BASED_TYPES = List.of(
            "PRODUCTION",
            "REORDER",
            "SAMPLE",
            "OMS PRODUCTION",
            "KIPSTA PRODUCTION",
            "GPT"  // GPT uses both articles and files
    );

    /**
     * Export check register entries to Excel
     *
     * @param user The user whose check register is being exported
     * @param entries The check register entries to export
     * @param year The year of the report
     * @param month The month of the report
     * @return Byte array containing the Excel file
     */
    public byte[] exportToExcel(User user, List<RegisterCheckEntry> entries, int year, int month) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            LoggerUtil.info(this.getClass(), "Creating check register Excel export for " + entries.size() + " entries");

            Sheet sheet = workbook.createSheet("Check Register");
            Map<String, CellStyle> styles = createStyles(workbook);

            int rowNum = 0;

            // Create title section
            rowNum = createTitleSection(sheet, styles, user, year, month, rowNum);

            // Add some space
            rowNum += 1;

            // Create monthly summary section with metrics matching the UI
            rowNum = createSummarySection(sheet, styles, entries, rowNum);

            // Add some space
            rowNum += 1;

            // Create entries table
            createEntriesTable(sheet, styles, entries, rowNum);

            // Auto-size columns for better readability
            for (int i = 0; i < 12; i++) {
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
        sheet.addMergedRegion(new CellRangeAddress(titleRow.getRowNum(), titleRow.getRowNum(), 0, 11));

        // User information row
        Row userRow = sheet.createRow(rowNum++);
        Cell userCell = userRow.createCell(0);
        String userInfo = "User: " + user.getName();
        if (user.getEmployeeId() != null) {
            userInfo += " (ID: " + user.getEmployeeId() + ")";
        }
        userCell.setCellValue(userInfo);
        userCell.setCellStyle(styles.get("subtitle"));
        sheet.addMergedRegion(new CellRangeAddress(userRow.getRowNum(), userRow.getRowNum(), 0, 11));

        // Current date row
        Row dateRow = sheet.createRow(rowNum++);
        Cell dateCell = dateRow.createCell(0);
        dateCell.setCellValue("Export Date: " + java.time.LocalDate.now().format(WorkCode.DATE_FORMATTER));
        dateCell.setCellStyle(styles.get("date-header"));
        sheet.addMergedRegion(new CellRangeAddress(dateRow.getRowNum(), dateRow.getRowNum(), 0, 11));

        return rowNum;
    }

    /**
     * Create the summary section with metrics matching the UI display
     */
    private int createSummarySection(Sheet sheet, Map<String, CellStyle> styles,
                                     List<RegisterCheckEntry> entries, int startRow) {
        int rowNum = startRow;

        // Section header
        Row headerRow = sheet.createRow(rowNum++);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("Monthly Summary");
        headerCell.setCellStyle(styles.get("section-header"));
        sheet.addMergedRegion(new CellRangeAddress(headerRow.getRowNum(), headerRow.getRowNum(), 0, 11));

        // Calculate metrics - matching what's done in check-register.js
        Map<String, Object> metrics = calculateMetrics(entries);

        // Check Type Metrics
        Row checkTypeHeaderRow = sheet.createRow(rowNum++);
        Cell checkTypeHeaderCell = checkTypeHeaderRow.createCell(0);
        checkTypeHeaderCell.setCellValue("Check Types:");
        checkTypeHeaderCell.setCellStyle(styles.get("metric-header"));

        // Create check type metrics in two rows to match UI layout
        @SuppressWarnings("unchecked")
        Map<String, Long> typeCounts = (Map<String, Long>) metrics.get("typeCounts");
        int cellIndex = 1;
        Row typeRow1 = sheet.createRow(rowNum++);

        // Add LAYOUT counts
        addMetricBadge(typeRow1, cellIndex++, "LAYOUT", typeCounts.getOrDefault("layout", 0L), styles);
        addMetricBadge(typeRow1, cellIndex++, "KIPSTA LAYOUT", typeCounts.getOrDefault("kipstaLayout", 0L), styles);
        addMetricBadge(typeRow1, cellIndex++, "LAYOUT CHANGES", typeCounts.getOrDefault("layoutChanges", 0L), styles);
        addMetricBadge(typeRow1, cellIndex++, "GPT", typeCounts.getOrDefault("gpt", 0L), styles);
        addMetricBadge(typeRow1, cellIndex++, "PRODUCTION", typeCounts.getOrDefault("production", 0L) +
                typeCounts.getOrDefault("reorder", 0L), styles);

        // Second row for remaining check types
        Row typeRow2 = sheet.createRow(rowNum++);
        cellIndex = 1;
        addMetricBadge(typeRow2, cellIndex++, "SAMPLE", typeCounts.getOrDefault("sample", 0L), styles);
        addMetricBadge(typeRow2, cellIndex++, "OMS PRODUCTION", typeCounts.getOrDefault("omsProduction", 0L), styles);
        addMetricBadge(typeRow2, cellIndex++, "KIPSTA PRODUCTION", typeCounts.getOrDefault("kipstaProduction", 0L), styles);

        // Approval Status Metrics
        Row approvalHeaderRow = sheet.createRow(rowNum++);
        Cell approvalHeaderCell = approvalHeaderRow.createCell(0);
        approvalHeaderCell.setCellValue("Approval Status:");
        approvalHeaderCell.setCellStyle(styles.get("metric-header"));

        // Create approval status row
        @SuppressWarnings("unchecked")
        Map<String, Long> approvalCounts = (Map<String, Long>) metrics.get("approvalCounts");
        cellIndex = 1;
        Row approvalRow = sheet.createRow(rowNum++);

        addMetricBadge(approvalRow, cellIndex++, "APPROVED", approvalCounts.getOrDefault("approved", 0L), styles);
        addMetricBadge(approvalRow, cellIndex++, "PARTIALLY APPROVED", approvalCounts.getOrDefault("partiallyApproved", 0L), styles);
        addMetricBadge(approvalRow, cellIndex++, "CORRECTION", approvalCounts.getOrDefault("correction", 0L), styles);

        // Key Metrics
        Row keyMetricsHeaderRow = sheet.createRow(rowNum++);
        Cell keyMetricsHeaderCell = keyMetricsHeaderRow.createCell(0);
        keyMetricsHeaderCell.setCellValue("Key Metrics:");
        keyMetricsHeaderCell.setCellStyle(styles.get("metric-header"));

        // Create key metrics row
        Row keyMetricsRow = sheet.createRow(rowNum++);
        cellIndex = 0;

        // Add all metrics in a single row
        addKeyMetric(keyMetricsRow, cellIndex++, "Total Entries", metrics.get("totalEntries"), styles);
        addKeyMetric(keyMetricsRow, cellIndex++, "Total Art", metrics.get("totalArticles"), styles);
        addKeyMetric(keyMetricsRow, cellIndex++, "Total Files", metrics.get("totalFiles"), styles);
        addKeyMetric(keyMetricsRow, cellIndex++, "Total Value", String.format("%.2f", (Double)metrics.get("totalOrderValue")), styles);

        // Include efficiency metrics if available
        if (metrics.containsKey("standardHours")) {
            addKeyMetric(keyMetricsRow, cellIndex++, "Hours", metrics.get("standardHours"), styles);
            addKeyMetric(keyMetricsRow, cellIndex++, "WU/Hr", metrics.get("targetUnitsHour"), styles);
            addKeyMetric(keyMetricsRow, cellIndex++, "Eff.", metrics.get("efficiency") + "%", styles);
        }

        return rowNum;
    }

    /**
     * Add a metric badge cell (similar to the badges in the UI)
     */
    private void addMetricBadge(Row row, int cellIndex, String label, Object value, Map<String, CellStyle> styles) {
        Cell cell = row.createCell(cellIndex);
        cell.setCellValue(label + ": " + value);

        // Choose style based on label type
        if (label.contains("LAYOUT") || label.equals("GPT")) {
            cell.setCellStyle(styles.get("badge-blue"));
        } else if (label.equals("PRODUCTION") || label.equals("REORDER") || label.equals("SAMPLE")) {
            cell.setCellStyle(styles.get("badge-gray"));
        } else if (label.equals("OMS PRODUCTION") || label.equals("KIPSTA PRODUCTION")) {
            cell.setCellStyle(styles.get("badge-orange"));
        } else if (label.equals("APPROVED")) {
            cell.setCellStyle(styles.get("badge-green"));
        } else if (label.equals("PARTIALLY APPROVED")) {
            cell.setCellStyle(styles.get("badge-yellow"));
        } else if (label.equals("CORRECTION")) {
            cell.setCellStyle(styles.get("badge-red"));
        } else {
            cell.setCellStyle(styles.get("badge-default"));
        }
    }

    /**
     * Add a key metric cell
     */
    private void addKeyMetric(Row row, int cellIndex, String label, Object value, Map<String, CellStyle> styles) {
        Cell cell = row.createCell(cellIndex);
        cell.setCellValue(label + ": " + value);
        cell.setCellStyle(styles.get("key-metric"));
    }

    /**
     * Create the entries table
     */
    private void createEntriesTable(Sheet sheet, Map<String, CellStyle> styles, List<RegisterCheckEntry> entries, int startRow) {
        int rowNum = startRow;

        // Table header
        Row tableHeaderRow = sheet.createRow(rowNum++);
        Cell tableHeaderCell = tableHeaderRow.createCell(0);
        tableHeaderCell.setCellValue("Check Register Entries");
        tableHeaderCell.setCellStyle(styles.get("section-header"));
        sheet.addMergedRegion(new CellRangeAddress(tableHeaderRow.getRowNum(), tableHeaderRow.getRowNum(), 0, 11));

        // Column headers - matching the UI table
        String[] headers = new String[] {
                "#", "Date", "Order ID", "Production ID", "OMS ID", "Designer",
                "Check Type", "Art.", "Files", "Error Description", "Status", "Value"
        };

        Row headerRow = sheet.createRow(rowNum++);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("table-header"));
        }

        // Sort entries by date (newest first)
        List<RegisterCheckEntry> sortedEntries = new ArrayList<>(entries);
        sortedEntries.sort(Comparator.comparing(RegisterCheckEntry::getDate).reversed());

        // Add entries
        int entryCount = 1;
        for (RegisterCheckEntry entry : sortedEntries) {
            Row entryRow = sheet.createRow(rowNum++);

            // Entry number
            Cell numberCell = entryRow.createCell(0);
            numberCell.setCellValue(entryCount++);
            numberCell.setCellStyle(styles.get("cell-center"));

            // Date
            Cell dateCell = entryRow.createCell(1);
            dateCell.setCellValue(entry.getDate().format(WorkCode.DATE_FORMATTER));
            dateCell.setCellStyle(styles.get("cell-center"));

            // Order ID
            Cell orderIdCell = entryRow.createCell(2);
            orderIdCell.setCellValue(entry.getOrderId() != null ? entry.getOrderId() : "");
            orderIdCell.setCellStyle(styles.get("cell-text"));

            // Production ID
            Cell productionIdCell = entryRow.createCell(3);
            productionIdCell.setCellValue(entry.getProductionId() != null ? entry.getProductionId() : "");
            productionIdCell.setCellStyle(styles.get("cell-text"));

            // OMS ID
            Cell omsIdCell = entryRow.createCell(4);
            omsIdCell.setCellValue(entry.getOmsId() != null ? entry.getOmsId() : "");
            omsIdCell.setCellStyle(styles.get("cell-text"));

            // Designer Name
            Cell designerCell = entryRow.createCell(5);
            designerCell.setCellValue(entry.getDesignerName() != null ? entry.getDesignerName() : "");
            designerCell.setCellStyle(styles.get("cell-text"));

            // Check Type (with colored backgrounds matching UI)
            Cell checkTypeCell = entryRow.createCell(6);
            checkTypeCell.setCellValue(entry.getCheckType() != null ? entry.getCheckType() : "");
            checkTypeCell.setCellStyle(getCheckTypeStyle(entry.getCheckType(), styles));

            // Articles
            Cell articlesCell = entryRow.createCell(7);
            articlesCell.setCellValue(entry.getArticleNumbers() != null ? entry.getArticleNumbers() : 0);
            articlesCell.setCellStyle(styles.get("cell-number"));

            // Files
            Cell filesCell = entryRow.createCell(8);
            filesCell.setCellValue(entry.getFilesNumbers() != null ? entry.getFilesNumbers() : 0);
            filesCell.setCellStyle(styles.get("cell-number"));

            // Error Description
            Cell errorDescCell = entryRow.createCell(9);
            errorDescCell.setCellValue(entry.getErrorDescription() != null ? entry.getErrorDescription() : "");
            errorDescCell.setCellStyle(styles.get("cell-text"));

            // Approval Status (with colored backgrounds matching UI)
            Cell statusCell = entryRow.createCell(10);
            statusCell.setCellValue(entry.getApprovalStatus() != null ? entry.getApprovalStatus() : "");
            statusCell.setCellStyle(getApprovalStatusStyle(entry.getApprovalStatus(), styles));

            // Order Value
            Cell valueCell = entryRow.createCell(11);
            if (entry.getOrderValue() != null) {
                valueCell.setCellValue(entry.getOrderValue());
            } else {
                valueCell.setCellValue(0.0);
            }
            valueCell.setCellStyle(styles.get("cell-decimal"));
        }
    }

    /**
     * Calculate metrics that match the JavaScript calculations in check-register.js
     */
    private Map<String, Object> calculateMetrics(List<RegisterCheckEntry> entries) {
        Map<String, Object> metrics = new HashMap<>();

        // Initialize type counters
        Map<String, Long> typeCounts = new HashMap<>();
        typeCounts.put("layout", 0L);
        typeCounts.put("kipstaLayout", 0L);
        typeCounts.put("layoutChanges", 0L);
        typeCounts.put("gpt", 0L);
        typeCounts.put("production", 0L);
        typeCounts.put("reorder", 0L);
        typeCounts.put("sample", 0L);
        typeCounts.put("omsProduction", 0L);
        typeCounts.put("kipstaProduction", 0L);

        // Initialize approval status counters
        Map<String, Long> approvalCounts = new HashMap<>();
        approvalCounts.put("approved", 0L);
        approvalCounts.put("partiallyApproved", 0L);
        approvalCounts.put("correction", 0L);

        // Calculate all metrics
        int totalEntries = entries.size();
        int totalArticles = 0;
        int totalFiles = 0;
        double totalOrderValue = 0.0;

        for (RegisterCheckEntry entry : entries) {
            // Count check types
            String checkType = entry.getCheckType();
            if (checkType != null) {
                switch (checkType) {
                    case "LAYOUT":
                        typeCounts.put("layout", typeCounts.get("layout") + 1);
                        break;
                    case "KIPSTA LAYOUT":
                        typeCounts.put("kipstaLayout", typeCounts.get("kipstaLayout") + 1);
                        break;
                    case "LAYOUT CHANGES":
                        typeCounts.put("layoutChanges", typeCounts.get("layoutChanges") + 1);
                        break;
                    case "GPT":
                        typeCounts.put("gpt", typeCounts.get("gpt") + 1);
                        break;
                    case "PRODUCTION":
                        typeCounts.put("production", typeCounts.get("production") + 1);
                        break;
                    case "REORDER":
                        typeCounts.put("reorder", typeCounts.get("reorder") + 1);
                        break;
                    case "SAMPLE":
                        typeCounts.put("sample", typeCounts.get("sample") + 1);
                        break;
                    case "OMS PRODUCTION":
                        typeCounts.put("omsProduction", typeCounts.get("omsProduction") + 1);
                        break;
                    case "KIPSTA PRODUCTION":
                        typeCounts.put("kipstaProduction", typeCounts.get("kipstaProduction") + 1);
                        break;
                }
            }

            // Count approval statuses
            String approvalStatus = entry.getApprovalStatus();
            if (approvalStatus != null) {
                switch (approvalStatus) {
                    case "APPROVED":
                        approvalCounts.put("approved", approvalCounts.get("approved") + 1);
                        break;
                    case "PARTIALLY APPROVED":
                        approvalCounts.put("partiallyApproved", approvalCounts.get("partiallyApproved") + 1);
                        break;
                    case "CORRECTION":
                        approvalCounts.put("correction", approvalCounts.get("correction") + 1);
                        break;
                }
            }

            // Sum articles and files
            totalArticles += entry.getArticleNumbers() != null ? entry.getArticleNumbers() : 0;
            totalFiles += entry.getFilesNumbers() != null ? entry.getFilesNumbers() : 0;

            // Sum order values (recalculating if null to match JS behavior)
            double orderValue = entry.getOrderValue() != null ? entry.getOrderValue() :
                    calculateOrderValue(entry.getCheckType(),
                            entry.getArticleNumbers(),
                            entry.getFilesNumbers());
            totalOrderValue += orderValue;
        }

        // Store all metrics
        metrics.put("typeCounts", typeCounts);
        metrics.put("approvalCounts", approvalCounts);
        metrics.put("totalEntries", totalEntries);
        metrics.put("totalArticles", totalArticles);
        metrics.put("totalFiles", totalFiles);
        metrics.put("totalOrderValue", totalOrderValue);

        // Calculate efficiency if we have standard hours (these would be provided in practice)
        // Reasonable defaults
        int standardHours = 160;
        double targetUnitsHour = 4.5;
        double efficiency;

        if (totalOrderValue > 0) {
            double targetTotal = standardHours * targetUnitsHour;
            efficiency = totalOrderValue / targetTotal * 100;

            metrics.put("standardHours", standardHours);
            metrics.put("targetUnitsHour", targetUnitsHour);
            metrics.put("efficiency", Math.round(efficiency * 10) / 10.0); // Round to 1 decimal place
        }

        return metrics;
    }

    /**
     * Recalculate order value using the same formula as in check-register.js
     * This ensures Excel export matches what's shown in the UI
     */
    private double calculateOrderValue(String checkType, Integer articleNumbers, Integer filesNumbers) {
        if (checkType == null || !CHECK_TYPE_VALUES.containsKey(checkType)) return 0;

        int articles = articleNumbers != null ? articleNumbers : 0;
        int files = filesNumbers != null ? filesNumbers : 0;
        double typeValue = CHECK_TYPE_VALUES.get(checkType);
        double orderValue = 0;

        // Calculate based on type
        if ("GPT".equals(checkType)) {
            // GPT uses both articles and files
            double articleValue = articles * typeValue;
            double filesValue = files * typeValue;
            orderValue = articleValue + filesValue;
        } else if (ARTICLE_BASED_TYPES.contains(checkType)) {
            // Types that use article numbers
            orderValue = articles * typeValue;
        } else if (FILE_BASED_TYPES.contains(checkType)) {
            // Types that use file numbers
            orderValue = files * typeValue;
        }

        return orderValue;
    }

    /**
     * Get the appropriate cell style for a check type
     */
    private CellStyle getCheckTypeStyle(String checkType, Map<String, CellStyle> styles) {
        if (checkType == null) return styles.get("cell-text");

        return switch (checkType) {
            case "LAYOUT" -> styles.get("check-layout");
            case "KIPSTA LAYOUT" -> styles.get("check-kipsta-layout");
            case "LAYOUT CHANGES" -> styles.get("check-layout-changes");
            case "GPT" -> styles.get("check-gpt");
            case "PRODUCTION" -> styles.get("check-production");
            case "REORDER" -> styles.get("check-reorder");
            case "SAMPLE" -> styles.get("check-sample");
            case "OMS PRODUCTION" -> styles.get("check-oms-production");
            case "KIPSTA PRODUCTION" -> styles.get("check-kipsta-production");
            default -> styles.get("cell-text");
        };
    }

    /**
     * Get the appropriate cell style for an approval status
     */
    private CellStyle getApprovalStatusStyle(String status, Map<String, CellStyle> styles) {
        if (status == null) return styles.get("cell-text");

        return switch (status) {
            case "APPROVED" -> styles.get("status-approved");
            case "PARTIALLY APPROVED" -> styles.get("status-partially-approved");
            case "CORRECTION" -> styles.get("status-correction");
            default -> styles.get("cell-text");
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

        // Metric header style
        CellStyle metricHeaderStyle = workbook.createCellStyle();
        metricHeaderStyle.setFont(boldFont);
        metricHeaderStyle.setAlignment(HorizontalAlignment.LEFT);
        metricHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        styles.put("metric-header", metricHeaderStyle);

        // Key metric style
        CellStyle keyMetricStyle = workbook.createCellStyle();
        keyMetricStyle.setAlignment(HorizontalAlignment.LEFT);
        keyMetricStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        keyMetricStyle.setBorderBottom(BorderStyle.THIN);
        keyMetricStyle.setBorderTop(BorderStyle.THIN);
        keyMetricStyle.setBorderLeft(BorderStyle.THIN);
        keyMetricStyle.setBorderRight(BorderStyle.THIN);
        styles.put("key-metric", keyMetricStyle);

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

        // Badge styles (for metrics section)
        createBadgeStyle(workbook, styles, "badge-blue", IndexedColors.LIGHT_BLUE, boldFont);
        createBadgeStyle(workbook, styles, "badge-gray", IndexedColors.GREY_40_PERCENT, whiteFont);
        createBadgeStyle(workbook, styles, "badge-orange", IndexedColors.LIGHT_ORANGE, boldFont);
        createBadgeStyle(workbook, styles, "badge-green", IndexedColors.GREEN, whiteFont);
        createBadgeStyle(workbook, styles, "badge-yellow", IndexedColors.YELLOW, boldFont);
        createBadgeStyle(workbook, styles, "badge-red", IndexedColors.RED, whiteFont);
        createBadgeStyle(workbook, styles, "badge-default", IndexedColors.GREY_25_PERCENT, boldFont);

        // Check type styles (matching the UI colors)
        createCheckTypeStyle(workbook, styles, "check-layout", IndexedColors.CORNFLOWER_BLUE, whiteFont);
        createCheckTypeStyle(workbook, styles, "check-kipsta-layout", IndexedColors.INDIGO, whiteFont);
        createCheckTypeStyle(workbook, styles, "check-layout-changes", IndexedColors.LIGHT_BLUE, boldFont);
        createCheckTypeStyle(workbook, styles, "check-gpt", IndexedColors.AQUA, boldFont);
        createCheckTypeStyle(workbook, styles, "check-production", IndexedColors.GREY_50_PERCENT, whiteFont);
        createCheckTypeStyle(workbook, styles, "check-reorder", IndexedColors.GREY_80_PERCENT, whiteFont);
        createCheckTypeStyle(workbook, styles, "check-sample", IndexedColors.GREEN, whiteFont);
        createCheckTypeStyle(workbook, styles, "check-oms-production", IndexedColors.RED, whiteFont);
        createCheckTypeStyle(workbook, styles, "check-kipsta-production", IndexedColors.ORANGE, boldFont);

        // Approval status styles
        createCheckTypeStyle(workbook, styles, "status-approved", IndexedColors.GREEN, whiteFont);
        createCheckTypeStyle(workbook, styles, "status-partially-approved", IndexedColors.YELLOW, boldFont);
        createCheckTypeStyle(workbook, styles, "status-correction", IndexedColors.RED, whiteFont);

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
     * Helper method to create check type styles with consistent formatting
     */
    private void createCheckTypeStyle(Workbook workbook, Map<String, CellStyle> styles,
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