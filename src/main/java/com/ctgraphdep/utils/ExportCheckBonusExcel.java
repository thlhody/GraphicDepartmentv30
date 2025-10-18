package com.ctgraphdep.utils;

import com.ctgraphdep.model.CheckBonusEntry;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.Month;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExportCheckBonusExcel {
    private Workbook workbook;

    public byte[] exportToExcel(List<CheckBonusEntry> bonusData, int year, int month) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            this.workbook = workbook;
            Sheet sheet = workbook.createSheet("Check Bonus Report");
            Map<String, CellStyle> styles = createStyles(workbook);

            int currentRow = 0;

            // Create title section
            currentRow = createTitleSection(sheet, styles, year, month, currentRow);
            currentRow += 2;

            // Create summary section
            currentRow = createSummarySection(sheet, styles, bonusData, currentRow);
            currentRow += 2;

            // Create data table
            currentRow = createDataTable(sheet, styles, bonusData, currentRow);

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
        titleCell.setCellValue(String.format("Check Bonus Report - %s %d", Month.of(month), year));
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(startRow-1, startRow-1, 0, 6));

        return startRow;
    }

    private int createSummarySection(Sheet sheet, Map<String, CellStyle> styles,
                                     List<CheckBonusEntry> bonusData, int startRow) {

        // Calculate summary statistics
        double totalBonus = bonusData.stream()
                .mapToDouble(entry -> entry.getBonusAmount() != null ? entry.getBonusAmount() : 0.0)
                .sum();
        double avgBonus = bonusData.stream()
                .mapToDouble(entry -> entry.getBonusAmount() != null ? entry.getBonusAmount() : 0.0)
                .average()
                .orElse(0.0);
        double maxBonus = bonusData.stream()
                .mapToDouble(entry -> entry.getBonusAmount() != null ? entry.getBonusAmount() : 0.0)
                .max()
                .orElse(0.0);
        double minBonus = bonusData.stream()
                .mapToDouble(entry -> entry.getBonusAmount() != null ? entry.getBonusAmount() : 0.0)
                .min()
                .orElse(0.0);
        double avgEfficiency = bonusData.stream()
                .mapToDouble(entry -> entry.getEfficiencyPercent() != null ? entry.getEfficiencyPercent() : 0.0)
                .average()
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
        addSummaryRow(sheet, styles, startRow++, "Average Efficiency %", avgEfficiency);
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
                                List<CheckBonusEntry> bonusData, int startRow) {
        // Create table title
        Row tableTitleRow = sheet.createRow(startRow++);
        Cell tableTitle = tableTitleRow.createCell(0);
        tableTitle.setCellValue("Check Bonus Details");
        tableTitle.setCellStyle(styles.get("subHeader"));
        sheet.addMergedRegion(new CellRangeAddress(startRow-1, startRow-1, 0, 6));

        // Create header row
        Row headerRow = sheet.createRow(startRow++);
        String[] headers = {
                "Name", "Total WU/M", "Working Hours", "Target WU/HR",
                "Total WU/HR/M", "Efficiency %", "Bonus Amount"
        };

        // Create header cells
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("columnHeader"));
        }

        // Fill data rows
        for (CheckBonusEntry entry : bonusData) {
            Row row = sheet.createRow(startRow++);

            // Determine the base style based on efficiency
            CellStyle baseStyle;
            Integer efficiency = entry.getEfficiencyPercent() != null ? entry.getEfficiencyPercent() : 0;

            if (efficiency >= 100) {
                baseStyle = styles.get("highEfficiency");
            } else if (efficiency >= 70) {
                baseStyle = styles.get("mediumEfficiency");
            } else {
                baseStyle = styles.get("lowEfficiency");
            }

            CellStyle numberStyle = createNumberStyleWithBackground(baseStyle);
            CellStyle currencyStyle = createCurrencyStyleWithBackground(baseStyle);
            CellStyle percentStyle = createPercentStyleWithBackground(baseStyle);

            int colNum = 0;

            // Name
            Cell nameCell = row.createCell(colNum++);
            nameCell.setCellValue(entry.getName() != null ? entry.getName() : "");
            nameCell.setCellStyle(baseStyle);

            // Total WU/M
            Cell wumCell = row.createCell(colNum++);
            wumCell.setCellValue(entry.getTotalWUM() != null ? entry.getTotalWUM() : 0.0);
            wumCell.setCellStyle(numberStyle);

            // Working Hours
            Cell hoursCell = row.createCell(colNum++);
            hoursCell.setCellValue(entry.getWorkingHours() != null ? entry.getWorkingHours() : 0.0);
            hoursCell.setCellStyle(numberStyle);

            // Target WU/HR
            Cell targetCell = row.createCell(colNum++);
            targetCell.setCellValue(entry.getTargetWUHR() != null ? entry.getTargetWUHR() : 0.0);
            targetCell.setCellStyle(numberStyle);

            // Total WU/HR/M
            Cell totalWUHRMCell = row.createCell(colNum++);
            totalWUHRMCell.setCellValue(entry.getTotalWUHRM() != null ? entry.getTotalWUHRM() : 0.0);
            totalWUHRMCell.setCellStyle(numberStyle);

            // Efficiency %
            Cell efficiencyCell = row.createCell(colNum++);
            efficiencyCell.setCellValue(efficiency);
            efficiencyCell.setCellStyle(percentStyle);

            // Bonus Amount
            Cell bonusCell = row.createCell(colNum++);
            bonusCell.setCellValue(entry.getBonusAmount() != null ? entry.getBonusAmount() : 0.0);
            bonusCell.setCellStyle(currencyStyle);
        }

        return startRow;
    }

    private void adjustColumnWidths(Sheet sheet) {
        for (int i = 0; i < 7; i++) {
            sheet.autoSizeColumn(i);
            // Add a bit of padding
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
        }
    }

    private Map<String, CellStyle> createStyles(XSSFWorkbook workbook) {
        Map<String, CellStyle> styles = new HashMap<>();

        // Title style
        XSSFCellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        titleStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)79, (byte)129, (byte)189}, null));
        titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        titleFont.setColor(IndexedColors.WHITE.getIndex());
        styles.put("title", titleStyle);

        // Sub-header style
        XSSFCellStyle subHeaderStyle = workbook.createCellStyle();
        Font subHeaderFont = workbook.createFont();
        subHeaderFont.setBold(true);
        subHeaderFont.setFontHeightInPoints((short) 12);
        subHeaderStyle.setFont(subHeaderFont);
        subHeaderStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)189, (byte)215, (byte)238}, null));
        subHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styles.put("subHeader", subHeaderStyle);

        // Column header style
        XSSFCellStyle columnHeaderStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        columnHeaderStyle.setFont(headerFont);
        columnHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        columnHeaderStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)189, (byte)215, (byte)238}, null));
        columnHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        columnHeaderStyle.setBorderBottom(BorderStyle.THIN);
        columnHeaderStyle.setBorderTop(BorderStyle.THIN);
        columnHeaderStyle.setBorderLeft(BorderStyle.THIN);
        columnHeaderStyle.setBorderRight(BorderStyle.THIN);
        styles.put("columnHeader", columnHeaderStyle);

        // Label style
        CellStyle labelStyle = workbook.createCellStyle();
        Font labelFont = workbook.createFont();
        labelFont.setBold(true);
        labelStyle.setFont(labelFont);
        styles.put("label", labelStyle);

        // Number style
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        styles.put("number", numberStyle);

        // Currency style (RON - no symbol, just formatted number)
        CellStyle currencyStyle = workbook.createCellStyle();
        currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        styles.put("currency", currencyStyle);

        // High efficiency style (green)
        XSSFCellStyle highEfficiencyStyle = workbook.createCellStyle();
        highEfficiencyStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)198, (byte)239, (byte)206}, null));
        highEfficiencyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        highEfficiencyStyle.setBorderBottom(BorderStyle.THIN);
        highEfficiencyStyle.setBorderTop(BorderStyle.THIN);
        highEfficiencyStyle.setBorderLeft(BorderStyle.THIN);
        highEfficiencyStyle.setBorderRight(BorderStyle.THIN);
        styles.put("highEfficiency", highEfficiencyStyle);

        // Medium efficiency style (yellow)
        XSSFCellStyle mediumEfficiencyStyle = workbook.createCellStyle();
        mediumEfficiencyStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)255, (byte)235, (byte)156}, null));
        mediumEfficiencyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        mediumEfficiencyStyle.setBorderBottom(BorderStyle.THIN);
        mediumEfficiencyStyle.setBorderTop(BorderStyle.THIN);
        mediumEfficiencyStyle.setBorderLeft(BorderStyle.THIN);
        mediumEfficiencyStyle.setBorderRight(BorderStyle.THIN);
        styles.put("mediumEfficiency", mediumEfficiencyStyle);

        // Low efficiency style (red)
        XSSFCellStyle lowEfficiencyStyle = workbook.createCellStyle();
        lowEfficiencyStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)255, (byte)199, (byte)206}, null));
        lowEfficiencyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        lowEfficiencyStyle.setBorderBottom(BorderStyle.THIN);
        lowEfficiencyStyle.setBorderTop(BorderStyle.THIN);
        lowEfficiencyStyle.setBorderLeft(BorderStyle.THIN);
        lowEfficiencyStyle.setBorderRight(BorderStyle.THIN);
        styles.put("lowEfficiency", lowEfficiencyStyle);

        return styles;
    }

    private CellStyle createNumberStyleWithBackground(CellStyle baseStyle) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createCurrencyStyleWithBackground(CellStyle baseStyle) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createPercentStyleWithBackground(CellStyle baseStyle) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        style.setDataFormat(workbook.createDataFormat().getFormat("0\"%\""));
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
}