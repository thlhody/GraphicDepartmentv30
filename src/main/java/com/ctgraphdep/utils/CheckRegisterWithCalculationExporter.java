package com.ctgraphdep.utils;

import com.ctgraphdep.model.CheckValuesEntry;
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

/**
 * Utility class for exporting check register entries to Excel with two sheets:
 * 1. Registry - All entry data
 * 2. Calculation - Summary with formulas and check values
 */
@Component
public class CheckRegisterWithCalculationExporter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Export check register entries to Excel with Registry and Calculation sheets
     *
     * @param user The user whose check register is being exported
     * @param entries The check register entries to export
     * @param checkValues The check values for this user (contains points/values)
     * @param year The year of the report
     * @param month The month of the report
     * @return Byte array containing the Excel file
     */
    public byte[] exportToExcel(User user, List<RegisterCheckEntry> entries,
                                CheckValuesEntry checkValues, int year, int month) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            LoggerUtil.info(this.getClass(), "Creating check register Excel export with calculation sheet for " +
                    entries.size() + " entries");

            Map<String, CellStyle> styles = createStyles(workbook);

            // Create Sheet 1: Registry
            createRegistrySheet(workbook, styles, user, entries, year, month);

            // Create Sheet 2: Calculation
            createCalculationSheet(workbook, styles, user, entries, checkValues, year, month);

            // Write to byte array
            return writeToByteArray(workbook);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating Excel export: " + e.getMessage(), e);
            return new byte[0];
        }
    }

    /**
     * Create the Registry sheet with all entry data
     */
    private void createRegistrySheet(XSSFWorkbook workbook, Map<String, CellStyle> styles,
                                     User user, List<RegisterCheckEntry> entries, int year, int month) {
        Sheet sheet = workbook.createSheet("Registry");
        int rowNum = 0;

        // Title section
        rowNum = createRegistryTitle(sheet, styles, user, year, month, rowNum);
        rowNum += 1; // Space

        // Line 2: Header row with light green background, double height, triple width
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.setHeightInPoints(40); // Double height (20 * 2)
        String[] headers = {"Designer", "Date", "Order ID", "CV No.", "Work Type",
                           "Nr of articles", "Nr of pieces", "Error Description",
                           "Approval Status", "Controller", "Name & Number"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("header-green"));
            // Set column width to triple (default ~10 units, so 30)
            sheet.setColumnWidth(i, 30 * 256);
        }

        // Line 3: Info row with detailed descriptions
        Row infoRow = sheet.createRow(rowNum++);
        infoRow.setHeightInPoints(60); // Taller for wrapped text

        String[] infoTexts = {
            "", // Designer - no info
            "DD/MM/YYYY", // Date format info
            "", // Order ID - no info
            "", // CV No. - no info
            "", // Work Type - no info
            "Only the ones checked in the moment (not the one already approved)", // Nr of articles
            "The exact number of .tff / .pdf / .eps files checked", // Nr of pieces
            "", // Error Description - no info
            "APPROVED / PARTIALLY APPROVED / CORRECTION", // Approval Status
            "", // Controller - no info
            "Just for reference" // Name & Number
        };

        for (int i = 0; i < infoTexts.length; i++) {
            Cell cell = infoRow.createCell(i);
            cell.setCellValue(infoTexts[i]);
            cell.setCellStyle(styles.get("info-row"));
        }

        // Sort entries by date (newest first, matching UI)
        List<RegisterCheckEntry> sortedEntries = new ArrayList<>(entries);
        sortedEntries.sort(Comparator.comparing(RegisterCheckEntry::getDate).reversed());

        // Data rows
        for (RegisterCheckEntry entry : sortedEntries) {
            Row dataRow = sheet.createRow(rowNum++);

            // Designer
            Cell designerCell = dataRow.createCell(0);
            designerCell.setCellValue(entry.getDesignerName() != null ? entry.getDesignerName() : "");
            designerCell.setCellStyle(styles.get("cell-text"));

            // Date
            Cell dateCell = dataRow.createCell(1);
            dateCell.setCellValue(entry.getDate().format(DATE_FORMATTER));
            dateCell.setCellStyle(styles.get("cell-center"));

            // Order ID (OMS ID)
            Cell orderIdCell = dataRow.createCell(2);
            orderIdCell.setCellValue(entry.getOmsId() != null ? entry.getOmsId() : "");
            orderIdCell.setCellStyle(styles.get("cell-text"));

            // CV No. (Production ID)
            Cell cvNoCell = dataRow.createCell(3);
            cvNoCell.setCellValue(entry.getProductionId() != null ? entry.getProductionId() : "");
            cvNoCell.setCellStyle(styles.get("cell-text"));

            // Work Type (Check Type) - centered, no colors
            Cell workTypeCell = dataRow.createCell(4);
            workTypeCell.setCellValue(entry.getCheckType() != null ? entry.getCheckType() : "");
            workTypeCell.setCellStyle(styles.get("cell-center"));

            // Nr of articles - centered
            Cell articlesCell = dataRow.createCell(5);
            articlesCell.setCellValue(entry.getArticleNumbers() != null ? entry.getArticleNumbers() : 0);
            articlesCell.setCellStyle(styles.get("cell-center"));

            // Nr of pieces (Files) - centered
            Cell piecesCell = dataRow.createCell(6);
            piecesCell.setCellValue(entry.getFilesNumbers() != null ? entry.getFilesNumbers() : 0);
            piecesCell.setCellStyle(styles.get("cell-center"));

            // Error Description - centered
            Cell errorCell = dataRow.createCell(7);
            errorCell.setCellValue(entry.getErrorDescription() != null ? entry.getErrorDescription() : "");
            errorCell.setCellStyle(styles.get("cell-center"));

            // Approval Status - centered, no colors
            Cell statusCell = dataRow.createCell(8);
            statusCell.setCellValue(entry.getApprovalStatus() != null ? entry.getApprovalStatus() : "");
            statusCell.setCellStyle(styles.get("cell-center"));

            // Controller (username) - capitalize first letter, centered
            Cell controllerCell = dataRow.createCell(9);
            String controller = user.getUsername() != null ? user.getUsername() : "";
            if (!controller.isEmpty()) {
                controller = controller.substring(0, 1).toUpperCase() + controller.substring(1);
            }
            controllerCell.setCellValue(controller);
            controllerCell.setCellStyle(styles.get("cell-center"));

            // Name & Number (empty for now) - centered
            Cell nameNumberCell = dataRow.createCell(10);
            nameNumberCell.setCellValue("");
            nameNumberCell.setCellStyle(styles.get("cell-center"));
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Create the Calculation sheet with formulas and summaries
     */
    private void createCalculationSheet(XSSFWorkbook workbook, Map<String, CellStyle> styles,
                                       User user, List<RegisterCheckEntry> entries,
                                       CheckValuesEntry checkValues, int year, int month) {
        Sheet sheet = workbook.createSheet("Calculation");
        int rowNum = 0;

        // Title section
        rowNum = createCalculationTitle(sheet, styles, user, year, month, rowNum);
        rowNum += 2; // Extra space

        // Main calculation table
        rowNum = createCalculationTable(sheet, styles, checkValues, rowNum);

        // Space before productivity settings
        rowNum += 2;

        // Productivity settings section
        rowNum = createProductivitySection(sheet, styles, checkValues, rowNum);

        // Auto-size columns
        for (int i = 0; i < 6; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Create the main calculation table with work types and formulas
     */
    private int createCalculationTable(Sheet sheet, Map<String, CellStyle> styles,
                                       CheckValuesEntry checkValues, int startRow) {
        int rowNum = startRow;

        // Table header
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"WORK TYPE", "NUMBER", "POINTS", "RESULT"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("calc-header"));
        }

        // Data rows with formulas
        int firstDataRow = rowNum;

        // NO OF ARTICLES (NORMAL ORDER) - LAYOUT
        rowNum = addCalculationRow(sheet, styles, rowNum,
                "NO OF ARTICLES (NORMAL ORDER)",
                "SUMIF(Registry!$E:$E,\"LAYOUT\",Registry!$F:$F)",
                checkValues.getLayoutValue());

        // NO OF ARTICLES (KIPSTA LAYOUT)
        rowNum = addCalculationRow(sheet, styles, rowNum,
                "NO OF ARTICLES (KIPSTA LAYOUT)",
                "SUMIF(Registry!$E:$E,\"KIPSTA LAYOUT\",Registry!$F:$F)",
                checkValues.getKipstaLayoutValue());

        // NO OF ARTICLES (LAYOUT CHANGES)
        rowNum = addCalculationRow(sheet, styles, rowNum,
                "NO OF ARTICLES (LAYOUT CHANGES)",
                "SUMIF(Registry!$E:$E,\"LAYOUT CHANGES\",Registry!$F:$F)",
                checkValues.getLayoutChangesValue());

        // NO OF ARTICLES (GPT)
        rowNum = addCalculationRow(sheet, styles, rowNum,
                "NO OF ARTICLES (GPT)",
                "SUMIF(Registry!$E:$E,\"GPT\",Registry!$F:$F)",
                checkValues.getGptArticlesValue());

        // ORDER (GPT) - uses files column
        rowNum = addCalculationRow(sheet, styles, rowNum,
                "ORDER (GPT)",
                "SUMIF(Registry!$E:$E,\"GPT\",Registry!$G:$G)",
                checkValues.getGptFilesValue());

        // ORDER (NORMAL) - PRODUCTION + REORDER
        rowNum = addCalculationRow(sheet, styles, rowNum,
                "ORDER (NORMAL)",
                "SUMIF(Registry!$E:$E,\"PRODUCTION\",Registry!$G:$G)+SUMIF(Registry!$E:$E,\"REORDER\",Registry!$G:$G)",
                checkValues.getProductionValue());

        // SAMPLE
        rowNum = addCalculationRow(sheet, styles, rowNum,
                "SAMPLE",
                "SUMIF(Registry!$E:$E,\"SAMPLE\",Registry!$G:$G)",
                checkValues.getSampleValue());

        // OMS PRODUCTION
        rowNum = addCalculationRow(sheet, styles, rowNum,
                "OMS PRODUCTION",
                "SUMIF(Registry!$E:$E,\"OMS PRODUCTION\",Registry!$G:$G)",
                checkValues.getOmsProductionValue());

        // KIPSTA PRODUCTION
        rowNum = addCalculationRow(sheet, styles, rowNum,
                "KIPSTA PRODUCTION",
                "SUMIF(Registry!$E:$E,\"KIPSTA PRODUCTION\",Registry!$G:$G)",
                checkValues.getKipstaProductionValue());

        int lastDataRow = rowNum - 1;

        // Empty row before total
        rowNum++;

        // Total row
        Row totalRow = sheet.createRow(rowNum++);
        int totalRowIndex = rowNum;  // Save for efficiency calculation
        Cell totalLabelCell = totalRow.createCell(0);
        totalLabelCell.setCellValue("TOTAL");
        totalLabelCell.setCellStyle(styles.get("total-label"));

        // Total result formula
        Cell totalResultCell = totalRow.createCell(3);
        totalResultCell.setCellFormula("SUM(D" + (firstDataRow + 1) + ":D" + (lastDataRow + 1) + ")");
        totalResultCell.setCellStyle(styles.get("total-value"));

        // Efficiency row (TOTAL / Target Work Units/Hour)
        Row efficiencyRow = sheet.createRow(rowNum++);
        Cell efficiencyLabelCell = efficiencyRow.createCell(0);
        efficiencyLabelCell.setCellValue("EFFICIENCY");
        efficiencyLabelCell.setCellStyle(styles.get("total-label"));

        // Efficiency formula: Total / Target Work Units/Hour
        Cell efficiencyResultCell = efficiencyRow.createCell(3);
        efficiencyResultCell.setCellFormula("D" + totalRowIndex + "/" + checkValues.getWorkUnitsPerHour());
        efficiencyResultCell.setCellStyle(styles.get("total-value"));

        return rowNum;
    }

    /**
     * Add a calculation row with formula
     */
    private int addCalculationRow(Sheet sheet, Map<String, CellStyle> styles, int rowNum,
                                  String workType, String numberFormula, Double points) {
        Row row = sheet.createRow(rowNum);

        // Work Type
        Cell workTypeCell = row.createCell(0);
        workTypeCell.setCellValue(workType);
        workTypeCell.setCellStyle(styles.get("calc-text"));

        // Number (formula)
        Cell numberCell = row.createCell(1);
        numberCell.setCellFormula(numberFormula);
        numberCell.setCellStyle(styles.get("calc-number"));

        // Points (value from checkValues)
        Cell pointsCell = row.createCell(2);
        pointsCell.setCellValue(points);
        pointsCell.setCellStyle(styles.get("calc-decimal"));

        // Result (formula: NUMBER * POINTS)
        Cell resultCell = row.createCell(3);
        resultCell.setCellFormula("B" + (rowNum + 1) + "*C" + (rowNum + 1));
        resultCell.setCellStyle(styles.get("calc-decimal"));

        return rowNum + 1;
    }

    /**
     * Create productivity settings section
     */
    private int createProductivitySection(Sheet sheet, Map<String, CellStyle> styles,
                                         CheckValuesEntry checkValues, int startRow) {
        int rowNum = startRow;

        // Section header
        Row headerRow = sheet.createRow(rowNum++);
        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("Productivity Settings");
        headerCell.setCellStyle(styles.get("section-header"));
        sheet.addMergedRegion(new CellRangeAddress(headerRow.getRowNum(), headerRow.getRowNum(), 0, 3));

        rowNum++; // Space

        // Work Units Per Hour
        rowNum = addProductivityRow(sheet, styles, rowNum,
                "Target Work Units/Hour", checkValues.getWorkUnitsPerHour());

        // Additional check values that weren't in main table
        rowNum++;
        Row additionalHeaderRow = sheet.createRow(rowNum++);
        Cell additionalHeaderCell = additionalHeaderRow.createCell(0);
        additionalHeaderCell.setCellValue("Check Values Reference");
        additionalHeaderCell.setCellStyle(styles.get("section-header"));
        sheet.addMergedRegion(new CellRangeAddress(additionalHeaderRow.getRowNum(),
                additionalHeaderRow.getRowNum(), 0, 3));

        rowNum = addProductivityRow(sheet, styles, rowNum, "Layout Value", checkValues.getLayoutValue());
        rowNum = addProductivityRow(sheet, styles, rowNum, "Kipsta Layout Value", checkValues.getKipstaLayoutValue());
        rowNum = addProductivityRow(sheet, styles, rowNum, "Layout Changes Value", checkValues.getLayoutChangesValue());
        rowNum = addProductivityRow(sheet, styles, rowNum, "GPT Articles Value", checkValues.getGptArticlesValue());
        rowNum = addProductivityRow(sheet, styles, rowNum, "GPT Files Value", checkValues.getGptFilesValue());
        rowNum = addProductivityRow(sheet, styles, rowNum, "Production Value", checkValues.getProductionValue());
        rowNum = addProductivityRow(sheet, styles, rowNum, "Reorder Value", checkValues.getReorderValue());
        rowNum = addProductivityRow(sheet, styles, rowNum, "Sample Value", checkValues.getSampleValue());
        rowNum = addProductivityRow(sheet, styles, rowNum, "OMS Production Value", checkValues.getOmsProductionValue());
        rowNum = addProductivityRow(sheet, styles, rowNum, "Kipsta Production Value", checkValues.getKipstaProductionValue());

        return rowNum;
    }

    /**
     * Add a productivity settings row
     */
    private int addProductivityRow(Sheet sheet, Map<String, CellStyle> styles, int rowNum,
                                   String label, Double value) {
        Row row = sheet.createRow(rowNum);

        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.get("prod-label"));

        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(styles.get("prod-value"));

        return rowNum + 1;
    }

    /**
     * Create title section for Registry sheet
     */
    private int createRegistryTitle(Sheet sheet, Map<String, CellStyle> styles,
                                    User user, int year, int month, int startRow) {
        int rowNum = startRow;

        // Line 1: Title - all in one line, no ID
        Row titleRow = sheet.createRow(rowNum++);
        titleRow.setHeightInPoints(20); // Standard height
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Check Register - " + user.getName() + " - " +
                                Month.of(month).toString() + " " + year +
                                " - Export Date: " + java.time.LocalDate.now().format(DATE_FORMATTER));
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(titleRow.getRowNum(), titleRow.getRowNum(), 0, 10));

        return rowNum;
    }

    /**
     * Create title section for Calculation sheet
     */
    private int createCalculationTitle(Sheet sheet, Map<String, CellStyle> styles,
                                       User user, int year, int month, int startRow) {
        int rowNum = startRow;

        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Calculation Summary - " + Month.of(month).toString() + " " + year);
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(titleRow.getRowNum(), titleRow.getRowNum(), 0, 3));

        Row userRow = sheet.createRow(rowNum++);
        Cell userCell = userRow.createCell(0);
        userCell.setCellValue("User: " + user.getName());
        userCell.setCellStyle(styles.get("subtitle"));
        sheet.addMergedRegion(new CellRangeAddress(userRow.getRowNum(), userRow.getRowNum(), 0, 3));

        return rowNum;
    }

    /**
     * Get appropriate style for check type
     */
    private CellStyle getCheckTypeStyle(String checkType, Map<String, CellStyle> styles) {
        if (checkType == null) return styles.get("cell-text");

        return switch (checkType) {
            case "LAYOUT" -> styles.get("badge-blue");
            case "KIPSTA LAYOUT" -> styles.get("badge-indigo");
            case "LAYOUT CHANGES" -> styles.get("badge-lightblue");
            case "GPT" -> styles.get("badge-cyan");
            case "PRODUCTION", "REORDER" -> styles.get("badge-gray");
            case "SAMPLE" -> styles.get("badge-green");
            case "OMS PRODUCTION" -> styles.get("badge-red");
            case "KIPSTA PRODUCTION" -> styles.get("badge-orange");
            default -> styles.get("cell-text");
        };
    }

    /**
     * Get appropriate style for approval status
     */
    private CellStyle getApprovalStatusStyle(String status, Map<String, CellStyle> styles) {
        if (status == null) return styles.get("cell-text");

        return switch (status) {
            case "APPROVED" -> styles.get("badge-green");
            case "PARTIALLY APPROVED" -> styles.get("badge-yellow");
            case "CORRECTION" -> styles.get("badge-red");
            default -> styles.get("cell-text");
        };
    }

    /**
     * Create all cell styles
     */
    private Map<String, CellStyle> createStyles(Workbook workbook) {
        Map<String, CellStyle> styles = new HashMap<>();

        // Fonts
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
        sectionHeaderStyle.setBorderBottom(BorderStyle.THIN);
        styles.put("section-header", sectionHeaderStyle);

        // Table header style
        CellStyle tableHeaderStyle = workbook.createCellStyle();
        tableHeaderStyle.setFont(boldFont);
        tableHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        tableHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        tableHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        tableHeaderStyle.setBorderBottom(BorderStyle.THIN);
        tableHeaderStyle.setBorderTop(BorderStyle.THIN);
        tableHeaderStyle.setBorderLeft(BorderStyle.THIN);
        tableHeaderStyle.setBorderRight(BorderStyle.THIN);
        styles.put("table-header", tableHeaderStyle);

        // Header green style (light green background, double height, centered, wrapped)
        CellStyle headerGreenStyle = workbook.createCellStyle();
        headerGreenStyle.setFont(boldFont);
        headerGreenStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        headerGreenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerGreenStyle.setAlignment(HorizontalAlignment.CENTER);
        headerGreenStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerGreenStyle.setWrapText(true);
        headerGreenStyle.setBorderBottom(BorderStyle.THIN);
        headerGreenStyle.setBorderTop(BorderStyle.THIN);
        headerGreenStyle.setBorderLeft(BorderStyle.THIN);
        headerGreenStyle.setBorderRight(BorderStyle.THIN);
        styles.put("header-green", headerGreenStyle);

        // Info row style (for detailed descriptions)
        CellStyle infoRowStyle = workbook.createCellStyle();
        infoRowStyle.setAlignment(HorizontalAlignment.CENTER);
        infoRowStyle.setVerticalAlignment(VerticalAlignment.TOP);
        infoRowStyle.setWrapText(true);
        infoRowStyle.setFont(workbook.createFont()); // Normal font
        infoRowStyle.setBorderBottom(BorderStyle.THIN);
        infoRowStyle.setBorderTop(BorderStyle.THIN);
        infoRowStyle.setBorderLeft(BorderStyle.THIN);
        infoRowStyle.setBorderRight(BorderStyle.THIN);
        styles.put("info-row", infoRowStyle);

        // Cell styles
        CellStyle cellTextStyle = createBorderedStyle(workbook, HorizontalAlignment.LEFT);
        cellTextStyle.setWrapText(true);
        styles.put("cell-text", cellTextStyle);

        CellStyle cellCenterStyle = createBorderedStyle(workbook, HorizontalAlignment.CENTER);
        styles.put("cell-center", cellCenterStyle);

        CellStyle cellNumberStyle = createBorderedStyle(workbook, HorizontalAlignment.RIGHT);
        styles.put("cell-number", cellNumberStyle);

        CellStyle cellDecimalStyle = createBorderedStyle(workbook, HorizontalAlignment.RIGHT);
        cellDecimalStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        styles.put("cell-decimal", cellDecimalStyle);

        // Calculation table styles
        CellStyle calcHeaderStyle = workbook.createCellStyle();
        calcHeaderStyle.setFont(boldFont);
        calcHeaderStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        calcHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        calcHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        calcHeaderStyle.setBorderBottom(BorderStyle.MEDIUM);
        calcHeaderStyle.setBorderTop(BorderStyle.THIN);
        calcHeaderStyle.setBorderLeft(BorderStyle.THIN);
        calcHeaderStyle.setBorderRight(BorderStyle.THIN);
        styles.put("calc-header", calcHeaderStyle);

        CellStyle calcTextStyle = createBorderedStyle(workbook, HorizontalAlignment.LEFT);
        styles.put("calc-text", calcTextStyle);

        CellStyle calcNumberStyle = createBorderedStyle(workbook, HorizontalAlignment.RIGHT);
        styles.put("calc-number", calcNumberStyle);

        CellStyle calcDecimalStyle = createBorderedStyle(workbook, HorizontalAlignment.RIGHT);
        calcDecimalStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        styles.put("calc-decimal", calcDecimalStyle);

        // Total row styles
        CellStyle totalLabelStyle = workbook.createCellStyle();
        totalLabelStyle.setFont(boldFont);
        totalLabelStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        totalLabelStyle.setAlignment(HorizontalAlignment.LEFT);
        totalLabelStyle.setBorderTop(BorderStyle.DOUBLE);
        totalLabelStyle.setBorderBottom(BorderStyle.DOUBLE);
        totalLabelStyle.setBorderLeft(BorderStyle.THIN);
        totalLabelStyle.setBorderRight(BorderStyle.THIN);
        styles.put("total-label", totalLabelStyle);

        CellStyle totalValueStyle = workbook.createCellStyle();
        totalValueStyle.setFont(boldFont);
        totalValueStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        totalValueStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        totalValueStyle.setAlignment(HorizontalAlignment.RIGHT);
        totalValueStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        totalValueStyle.setBorderTop(BorderStyle.DOUBLE);
        totalValueStyle.setBorderBottom(BorderStyle.DOUBLE);
        totalValueStyle.setBorderLeft(BorderStyle.THIN);
        totalValueStyle.setBorderRight(BorderStyle.THIN);
        styles.put("total-value", totalValueStyle);

        // Productivity section styles
        CellStyle prodLabelStyle = createBorderedStyle(workbook, HorizontalAlignment.LEFT);
        prodLabelStyle.setFont(boldFont);
        styles.put("prod-label", prodLabelStyle);

        CellStyle prodValueStyle = createBorderedStyle(workbook, HorizontalAlignment.RIGHT);
        prodValueStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        styles.put("prod-value", prodValueStyle);

        // Badge styles for check types and statuses
        createBadgeStyle(workbook, styles, "badge-blue", IndexedColors.CORNFLOWER_BLUE, whiteFont);
        createBadgeStyle(workbook, styles, "badge-indigo", IndexedColors.INDIGO, whiteFont);
        createBadgeStyle(workbook, styles, "badge-lightblue", IndexedColors.LIGHT_BLUE, boldFont);
        createBadgeStyle(workbook, styles, "badge-cyan", IndexedColors.AQUA, boldFont);
        createBadgeStyle(workbook, styles, "badge-gray", IndexedColors.GREY_50_PERCENT, whiteFont);
        createBadgeStyle(workbook, styles, "badge-green", IndexedColors.GREEN, whiteFont);
        createBadgeStyle(workbook, styles, "badge-red", IndexedColors.RED, whiteFont);
        createBadgeStyle(workbook, styles, "badge-orange", IndexedColors.LIGHT_ORANGE, boldFont);
        createBadgeStyle(workbook, styles, "badge-yellow", IndexedColors.YELLOW, boldFont);

        return styles;
    }

    /**
     * Create a bordered cell style
     */
    private CellStyle createBorderedStyle(Workbook workbook, HorizontalAlignment alignment) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(alignment);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * Create a badge style
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