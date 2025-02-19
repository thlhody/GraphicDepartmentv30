package com.ctgraphdep.utils;

import com.ctgraphdep.model.BonusEntryDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;

@Component
public class AdminBonusExcelExporter {
    private Workbook workbook;

    public byte[] exportToExcel(Map<Integer, BonusEntryDTO> bonusData, int year, int month) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            this.workbook = workbook;
            Sheet sheet = workbook.createSheet("Bonus Report");
            Map<String, CellStyle> styles = createStyles(workbook);

            int currentRow = 0;

            // Create title section
            currentRow = createTitleSection(sheet, styles, year, month, currentRow);
            currentRow += 2;

            // Create summary section
            currentRow = createSummarySection(sheet, styles, bonusData, currentRow);
            currentRow += 2;

            // Create data table - pass year and month
            currentRow = createDataTable(sheet, styles, bonusData, currentRow, year, month);

            // Adjust column widths
            adjustColumnWidths(sheet);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating Excel export: " + e.getMessage());
            throw new RuntimeException("Failed to create Excel export", e);
        }
    }

    private int createTitleSection(Sheet sheet, Map<String, CellStyle> styles, int year, int month, int startRow) {
        Row titleRow = sheet.createRow(startRow++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(String.format("Bonus Report - %s %d", Month.of(month), year));
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(startRow-1, startRow-1, 0, 13));

        return startRow;
    }

    private int createSummarySection(Sheet sheet, Map<String, CellStyle> styles,
                                     Map<Integer, BonusEntryDTO> bonusData, int startRow) {

        // Calculate summary statistics
        double totalBonus = bonusData.values().stream()
                .mapToDouble(BonusEntryDTO::getBonusAmount)
                .sum();
        double avgBonus = bonusData.values().stream()
                .mapToDouble(BonusEntryDTO::getBonusAmount)
                .average()
                .orElse(0.0);
        double maxBonus = bonusData.values().stream()
                .mapToDouble(BonusEntryDTO::getBonusAmount)
                .max()
                .orElse(0.0);
        double minBonus = bonusData.values().stream()
                .mapToDouble(BonusEntryDTO::getBonusAmount)
                .min()
                .orElse(0.0);


        // Create summary rows
        Row summaryTitleRow = sheet.createRow(startRow++);
        Cell summaryTitle = summaryTitleRow.createCell(0);
        summaryTitle.setCellValue("Summary");
        summaryTitle.setCellStyle(styles.get("subHeader"));
        sheet.addMergedRegion(new CellRangeAddress(startRow-1, startRow-1, 0, 2));

        addSummaryRow(sheet, styles, startRow++, "Total Bonus", totalBonus);
        addSummaryRow(sheet, styles, startRow++, "Average Bonus", avgBonus);
        addSummaryRow(sheet, styles, startRow++, "Maximum Bonus", maxBonus);
        addSummaryRow(sheet, styles, startRow++, "Minimum Bonus", minBonus);
        addSummaryRow(sheet, styles, startRow++, "Number of Employees", bonusData.size());

        return startRow;
    }

    private void addSummaryRow(Sheet sheet, Map<String, CellStyle> styles,
                               int rowNum, String label, Number value) {
        Row row = sheet.createRow(rowNum);

        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.get("label"));

        Cell valueCell = row.createCell(1);
        if (value instanceof Double) {
            valueCell.setCellValue((Double) value);
            valueCell.setCellStyle(styles.get("currency"));
        } else {
            valueCell.setCellValue(value.intValue());
            valueCell.setCellStyle(styles.get("number"));
        }
    }


    private int createDataTable(Sheet sheet, Map<String, CellStyle> styles,
                                Map<Integer, BonusEntryDTO> bonusData, int startRow, int year, int month) {
        // Create table title
        Row tableTitleRow = sheet.createRow(startRow++);
        Cell tableTitle = tableTitleRow.createCell(0);
        tableTitle.setCellValue("Bonus Details");
        tableTitle.setCellStyle(styles.get("subHeader"));
        sheet.addMergedRegion(new CellRangeAddress(startRow-1, startRow-1, 0, 2));

        // Get previous month names
        String[] previousMonths = MonthFormatter.getPreviousMonthNames(year, month);

        // Create header row
        Row headerRow = sheet.createRow(startRow++);
        String[] headers = {
                "Name", "User ID", "Entries", "Articles", "Complexity", "Misc",
                "Worked Days", "Worked %", "Bonus %", "Bonus Amount",
                previousMonths[0], previousMonths[1], previousMonths[2], "Calc Date"
        };

        // Create header cells
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("columnHeader"));
        }

        // Fill data rows
        for (BonusEntryDTO entry : bonusData.values()) {
            Row row = sheet.createRow(startRow++);

            // Determine the base style and row color style based on entries
            CellStyle baseStyle;
            if (entry.getEntries() > 39) {
                baseStyle = styles.get("high-entries");
            } else if (entry.getEntries() > 14) {
                baseStyle = styles.get("medium-entries");
            } else {
                baseStyle = styles.get("low-entries");
            }

            // Create cells with appropriate styles but keeping the formatting
            // Name column - text style with color
            createCell(row, 0, entry.getDisplayName(), baseStyle);

            // Number columns with color
            createCell(row, 1, entry.getEmployeeId(), cloneStyleWithFormat(baseStyle, styles.get("number"), workbook));
            createCell(row, 2, entry.getEntries(), cloneStyleWithFormat(baseStyle, styles.get("number"), workbook));
            createCell(row, 3, entry.getArticleNumbers(), cloneStyleWithFormat(baseStyle, styles.get("number"), workbook));
            createCell(row, 4, entry.getGraphicComplexity(), cloneStyleWithFormat(baseStyle, styles.get("number"), workbook));
            createCell(row, 5, entry.getMisc(), cloneStyleWithFormat(baseStyle, styles.get("number"), workbook));
            createCell(row, 6, entry.getWorkedDays(), cloneStyleWithFormat(baseStyle, styles.get("number"), workbook));

            // Percentage columns with color
            createCell(row, 7, entry.getWorkedPercentage() / 100, cloneStyleWithFormat(baseStyle, styles.get("percentage"), workbook));
            createCell(row, 8, entry.getBonusPercentage() / 100, cloneStyleWithFormat(baseStyle, styles.get("percentage"), workbook));

            // Currency columns with color
            createCell(row, 9, entry.getBonusAmount(), cloneStyleWithFormat(baseStyle, styles.get("currency"), workbook));
            createCell(row, 10, entry.getPreviousMonths().getMonth1(), cloneStyleWithFormat(baseStyle, styles.get("currency"), workbook));
            createCell(row, 11, entry.getPreviousMonths().getMonth2(), cloneStyleWithFormat(baseStyle, styles.get("currency"), workbook));
            createCell(row, 12, entry.getPreviousMonths().getMonth3(), cloneStyleWithFormat(baseStyle, styles.get("currency"), workbook));

            // Date column with color
            createCell(row, 13, entry.getCalculationDate(), cloneStyleWithFormat(baseStyle, styles.get("date"), workbook));
        }

        return startRow;
    }

    // Add this helper method to clone styles while preserving formatting
    private CellStyle cloneStyleWithFormat(CellStyle colorStyle, CellStyle formatStyle, Workbook workbook) {
        CellStyle newStyle = workbook.createCellStyle();

        // Copy color properties
        newStyle.setFillForegroundColor(colorStyle.getFillForegroundColorColor());
        newStyle.setFillPattern(colorStyle.getFillPattern());

        // Copy formatting properties
        newStyle.setDataFormat(formatStyle.getDataFormat());
        newStyle.setAlignment(formatStyle.getAlignment());
        newStyle.setBorderBottom(formatStyle.getBorderBottom());

        return newStyle;
    }

    private void createCell(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            if (value instanceof String) {
                cell.setCellValue((String) value);
            } else if (value instanceof Number) {
                cell.setCellValue(((Number) value).doubleValue());
            }
        } else {
            cell.setCellValue("-");
        }
        cell.setCellStyle(style);
    }

    private void adjustColumnWidths(Sheet sheet) {
        // Auto-size all columns
        for (int i = 0; i < 14; i++) {
            sheet.autoSizeColumn(i);
        }

        // Set minimum widths for specific columns
        sheet.setColumnWidth(0, Math.max(sheet.getColumnWidth(0), 20 * 256)); // Name
        sheet.setColumnWidth(9, Math.max(sheet.getColumnWidth(9), 15 * 256)); // Bonus Amount
        sheet.setColumnWidth(13, Math.max(sheet.getColumnWidth(13), 25 * 256)); // Calc Date
    }

    public byte[] exportUserToExcel(Map<Integer, BonusEntryDTO> bonusData, int year, int month) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            this.workbook = workbook;
            Sheet sheet = workbook.createSheet("Performance Metrics");
            Map<String, CellStyle> styles = createStyles(workbook);

            int currentRow = 0;

            // Create title section
            currentRow = createTitleSection(sheet, styles, year, month, currentRow);
            currentRow += 2;

            // Create data table
            currentRow = createUserDataTable(sheet, styles, bonusData, currentRow);

            // Adjust column widths
            for (int i = 0; i < 10; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating user Excel export: " + e.getMessage());
            throw new RuntimeException("Failed to create user Excel export", e);
        }
    }

    private int createUserDataTable(Sheet sheet, Map<String, CellStyle> styles,
                                    Map<Integer, BonusEntryDTO> bonusData, int startRow) {
        // Create table title
        Row tableTitleRow = sheet.createRow(startRow++);
        Cell tableTitle = tableTitleRow.createCell(0);
        tableTitle.setCellValue("Performance Details");
        tableTitle.setCellStyle(styles.get("subHeader"));
        sheet.addMergedRegion(new CellRangeAddress(startRow-1, startRow-1, 0, 8));

        // Create header row
        Row headerRow = sheet.createRow(startRow++);
        String[] headers = {"Name", "User ID", "Entries", "Articles", "Complexity", "Misc", "Worked Days", "Worked %", "Bonus %", "Calc Date"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("columnHeader"));
        }

        // Fill data rows
        for (BonusEntryDTO entry : bonusData.values()) {
            Row row = sheet.createRow(startRow++);

            // Determine style based on entries
            CellStyle baseStyle;
            if (entry.getEntries() > 39) {
                baseStyle = styles.get("high-entries");
            } else if (entry.getEntries() > 14) {
                baseStyle = styles.get("medium-entries");
            } else {
                baseStyle = styles.get("low-entries");
            }

            // Create cells with appropriate styles
            createCell(row, 0, entry.getDisplayName(), baseStyle);
            createCell(row, 1, entry.getEmployeeId(), cloneStyleWithFormat(baseStyle, styles.get("number"), workbook));
            createCell(row, 2, entry.getEntries(), cloneStyleWithFormat(baseStyle, styles.get("number"), workbook));
            createCell(row, 3, entry.getArticleNumbers(), cloneStyleWithFormat(baseStyle, styles.get("number"), workbook));
            createCell(row, 4, entry.getGraphicComplexity(), cloneStyleWithFormat(baseStyle, styles.get("number"), workbook));
            createCell(row, 5, entry.getMisc(), cloneStyleWithFormat(baseStyle, styles.get("number"), workbook));
            createCell(row, 6, entry.getWorkedDays(), cloneStyleWithFormat(baseStyle, styles.get("number"), workbook));
            createCell(row, 7, entry.getWorkedPercentage() / 100, cloneStyleWithFormat(baseStyle, styles.get("percentage"), workbook));
            createCell(row, 8, entry.getBonusPercentage() / 100, cloneStyleWithFormat(baseStyle, styles.get("percentage"), workbook));
            createCell(row, 9, entry.getCalculationDate(), cloneStyleWithFormat(baseStyle, styles.get("date"), workbook));
        }

        return startRow;
    }

    private Map<String, CellStyle> createStyles(Workbook workbook) {
        Map<String, CellStyle> styles = new HashMap<>();

        // Title style
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.put("title", titleStyle);

        // Sub-header style
        CellStyle subHeaderStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        subHeaderStyle.setFont(headerFont);
        subHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        subHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styles.put("subHeader", subHeaderStyle);

        // Column header style
        CellStyle columnHeaderStyle = workbook.createCellStyle();
        columnHeaderStyle.setFont(headerFont);
        columnHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        columnHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        columnHeaderStyle.setBorderBottom(BorderStyle.THIN);
        columnHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.put("columnHeader", columnHeaderStyle);

        // Label style
        CellStyle labelStyle = workbook.createCellStyle();
        Font labelFont = workbook.createFont();
        labelFont.setBold(true);
        labelStyle.setFont(labelFont);
        labelStyle.setAlignment(HorizontalAlignment.LEFT);
        styles.put("label", labelStyle);

        // Text style
        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setBorderBottom(BorderStyle.THIN);
        textStyle.setAlignment(HorizontalAlignment.LEFT);
        styles.put("text", textStyle);

        // Number style
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setBorderBottom(BorderStyle.THIN);
        numberStyle.setAlignment(HorizontalAlignment.CENTER);
        numberStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        styles.put("number", numberStyle);

        // Percentage style
        CellStyle percentageStyle = workbook.createCellStyle();
        percentageStyle.setBorderBottom(BorderStyle.THIN);
        percentageStyle.setAlignment(HorizontalAlignment.CENTER);
        percentageStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        styles.put("percentage", percentageStyle);

        // Currency style
        CellStyle currencyStyle = workbook.createCellStyle();
        currencyStyle.setBorderBottom(BorderStyle.THIN);
        currencyStyle.setAlignment(HorizontalAlignment.CENTER);
        currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00 [$RON]"));
        styles.put("currency", currencyStyle);

        // Date style
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setBorderBottom(BorderStyle.THIN);
        dateStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.put("date", dateStyle);

        XSSFWorkbook xssfWorkbook = (XSSFWorkbook) workbook;
// High entries - Light blue
        XSSFCellStyle highEntriesStyle = xssfWorkbook.createCellStyle();
        highEntriesStyle.cloneStyleFrom(styles.get("text"));
        XSSFColor lightBlue = new XSSFColor(new byte[]{(byte)208, (byte)227, (byte)255}, null);
        highEntriesStyle.setFillForegroundColor(lightBlue);
        highEntriesStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styles.put("high-entries", highEntriesStyle);

// Medium entries - Light orange
        XSSFCellStyle mediumEntriesStyle = xssfWorkbook.createCellStyle();
        mediumEntriesStyle.cloneStyleFrom(styles.get("text"));
        XSSFColor lightOrange = new XSSFColor(new byte[]{(byte)255, (byte)228, (byte)196}, null);
        mediumEntriesStyle.setFillForegroundColor(lightOrange);
        mediumEntriesStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styles.put("medium-entries", mediumEntriesStyle);

// Low entries - Light red
        XSSFCellStyle lowEntriesStyle = xssfWorkbook.createCellStyle();
        lowEntriesStyle.cloneStyleFrom(styles.get("text"));
        XSSFColor lightRed = new XSSFColor(new byte[]{(byte)255, (byte)204, (byte)204}, null);
        lowEntriesStyle.setFillForegroundColor(lightRed);
        lowEntriesStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styles.put("low-entries", lowEntriesStyle);

        return styles;
    }
}