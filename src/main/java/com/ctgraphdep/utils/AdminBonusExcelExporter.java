package com.ctgraphdep.utils;

import com.ctgraphdep.model.BonusEntry;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;

@Component
public class AdminBonusExcelExporter {

    public byte[] exportToExcel(Map<Integer, BonusEntry> bonusData, int year, int month) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bonus Report");
            Map<String, CellStyle> styles = createStyles(workbook);

            int currentRow = 0;

            // Create title section
            currentRow = createTitleSection(sheet, styles, year, month, currentRow);

            // Add spacing
            currentRow += 2;

            // Create summary section
            currentRow = createSummarySection(sheet, styles, bonusData, currentRow);

            // Add spacing
            currentRow += 2;

            // Create data table
            currentRow = createDataTable(sheet, styles, bonusData, currentRow);

            // Adjust column widths
            adjustColumnWidths(sheet);

            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error creating Excel export for %d/%d: %s",
                            year, month, e.getMessage()));
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
                                     Map<Integer, BonusEntry> bonusData, int startRow) {

        // Calculate summary statistics
        double totalBonus = bonusData.values().stream()
                .mapToDouble(BonusEntry::getBonusAmount)
                .sum();
        double avgBonus = bonusData.values().stream()
                .mapToDouble(BonusEntry::getBonusAmount)
                .average()
                .orElse(0.0);
        double maxBonus = bonusData.values().stream()
                .mapToDouble(BonusEntry::getBonusAmount)
                .max()
                .orElse(0.0);
        double minBonus = bonusData.values().stream()
                .mapToDouble(BonusEntry::getBonusAmount)
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
                                Map<Integer, BonusEntry> bonusData, int startRow) {
        // Create table title
        Row tableTitleRow = sheet.createRow(startRow++);
        Cell tableTitle = tableTitleRow.createCell(0);
        tableTitle.setCellValue("Bonus Details");
        tableTitle.setCellStyle(styles.get("subHeader"));
        sheet.addMergedRegion(new CellRangeAddress(startRow-1, startRow-1, 0, 2));

        // Create header row
        Row headerRow = sheet.createRow(startRow++);
        String[] headers = {
                "Name", "User ID", "Entries", "Articles", "Complexity", "Misc",
                "Worked Days", "Worked %", "Bonus %", "Bonus Amount",
                "Previous M1", "Previous M2", "Previous M3", "Calc Date"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("columnHeader"));
        }

        // Fill data rows
        for (BonusEntry entry : bonusData.values()) {
            Row row = sheet.createRow(startRow++);

            createCell(row, 0, entry.getName(), styles.get("text"));
            createCell(row, 1, entry.getEmployeeId(), styles.get("number"));
            createCell(row, 2, entry.getEntries(), styles.get("number"));
            createCell(row, 3, entry.getArticleNumbers(), styles.get("number"));
            createCell(row, 4, entry.getGraphicComplexity(), styles.get("number"));
            createCell(row, 5, entry.getMisc(), styles.get("number"));
            createCell(row, 6, entry.getWorkedDays(), styles.get("number"));
            createCell(row, 7, entry.getWorkedPercentage() / 100, styles.get("percentage"));
            createCell(row, 8, entry.getBonusPercentage() / 100, styles.get("percentage"));
            createCell(row, 9, entry.getBonusAmount(), styles.get("currency"));
            createCell(row, 10, entry.getPreviousMonths().getMonth1(), styles.get("currency"));
            createCell(row, 11, entry.getPreviousMonths().getMonth2(), styles.get("currency"));
            createCell(row, 12, entry.getPreviousMonths().getMonth3(), styles.get("currency"));
            createCell(row, 13, entry.getCalculationDate(), styles.get("date"));
        }

        return startRow;
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

        return styles;
    }
}