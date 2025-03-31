package com.ctgraphdep.utils;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.bonus.BonusCalculationResultDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.*;

@Component
public class AdminRegisterExcelExporter {

    public byte[] exportToExcel(User user, List<RegisterEntry> entries, BonusConfiguration bonusConfig,
                                BonusCalculationResultDTO bonusResult, int year, int month) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            LoggerUtil.info(this.getClass(), "Creating Excel export for " + entries.size() + " entries");
            Sheet sheet = workbook.createSheet("Register Report");
            Map<String, CellStyle> styles = createStyles(workbook);

            int currentRow = 0;

            // Title row
            currentRow = createTitleSection(sheet, styles, user, year, month, currentRow);

            // Spacing row
            currentRow += 2;

            // Bonus Configuration Section
            currentRow = createBonusConfigSection(sheet, styles, bonusConfig, currentRow);

            // Spacing row
            currentRow += 2;

            // Bonus Calculation Results Section (if exists)
            if (bonusResult != null) {
                currentRow = createBonusResultSection(sheet, styles, bonusResult, currentRow);
                currentRow += 2;
            }

            // Register Entries Table Section
            currentRow = createRegisterEntriesSection(sheet, styles, entries, currentRow);

            // Autosize columns
            for (int i = 0; i < 12; i++) {
                sheet.autoSizeColumn(i);
            }

            return writeToByteArray(workbook);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating Excel file", e);
            throw new RuntimeException("Failed to export Excel file", e);
        }
    }

    private int createTitleSection(Sheet sheet, Map<String, CellStyle> styles, User user, int year, int month, int startRow) {
        int currentRow = startRow;

        // Title row
        Row titleRow = sheet.createRow(currentRow++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(String.format("Register Report - %s %d",
                java.time.Month.of(month).toString(), year));
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(startRow, startRow, 0, 11));

        // User info row
        Row userRow = sheet.createRow(currentRow++);
        Cell userCell = userRow.createCell(0);
        userCell.setCellValue("Employee: " + user.getName() + " (ID: " + user.getEmployeeId() + ")");
        userCell.setCellStyle(styles.get("header"));
        sheet.addMergedRegion(new CellRangeAddress(currentRow - 1, currentRow - 1, 0, 11));

        return currentRow;
    }

    private int createBonusConfigSection(Sheet sheet, Map<String, CellStyle> styles, BonusConfiguration config, int startRow) {
        // Section title
        Row titleRow = sheet.createRow(startRow++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Bonus Configuration");
        titleCell.setCellStyle(styles.get("subHeader"));
        sheet.addMergedRegion(new CellRangeAddress(startRow - 1, startRow - 1, 0, 2));

        // Add configuration rows
        addConfigRow(sheet, styles, startRow++, "Bonus Sum", String.format("%.2f", config.getSumValue()));
        addConfigRow(sheet, styles, startRow++, "Entries Percentage", String.format("%.2f%%", config.getEntriesPercentage() * 100));
        addConfigRow(sheet, styles, startRow++, "Articles Percentage", String.format("%.2f%%", config.getArticlesPercentage() * 100));
        addConfigRow(sheet, styles, startRow++, "Complexity Percentage", String.format("%.2f%%", config.getComplexityPercentage() * 100));
        addConfigRow(sheet, styles, startRow++, "Misc Percentage", String.format("%.2f%%", config.getMiscPercentage() * 100));
        addConfigRow(sheet, styles, startRow++, "Norm Value", String.format("%.2f", config.getNormValue()));
        addConfigRow(sheet, styles, startRow++, "Misc Value", String.format("%.2f", config.getMiscValue()));

        return startRow;
    }

    private int createBonusResultSection(Sheet sheet, Map<String, CellStyle> styles, BonusCalculationResultDTO result, int startRow) {
        // Section title
        Row titleRow = sheet.createRow(startRow++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Bonus Calculation Results");
        titleCell.setCellStyle(styles.get("subHeader"));
        sheet.addMergedRegion(new CellRangeAddress(startRow - 1, startRow - 1, 0, 2));

        // Headers row
        Row headerRow = sheet.createRow(startRow++);
        String[] headers = {"Entries", "Art Nr.", "CG", "Misc", "Worked D", "Worked%", "Bonus%", "Bonus$", "1M Ago", "2M Ago", "3M Ago"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("columnHeader"));
        }

        // Data row
        Row dataRow = sheet.createRow(startRow++);
        createNumericCell(dataRow, 0, result.getEntries(), styles.get("number"));
        createNumericCell(dataRow, 1, result.getArticleNumbers(), styles.get("number"));
        createNumericCell(dataRow, 2, result.getGraphicComplexity(), styles.get("number"));
        createNumericCell(dataRow, 3, result.getMisc(), styles.get("number"));
        createNumericCell(dataRow, 4, result.getWorkedDays(), styles.get("number"));
        createNumericCell(dataRow, 5, result.getWorkedPercentage(), styles.get("percentage"));
        createNumericCell(dataRow, 6, result.getBonusPercentage(), styles.get("percentage"));
        createNumericCell(dataRow, 7, result.getBonusAmount(), styles.get("currency"));

        if (result.getPreviousMonths() != null) {
            createNumericCell(dataRow, 8, result.getPreviousMonths().getMonth1(), styles.get("currency"));
            createNumericCell(dataRow, 9, result.getPreviousMonths().getMonth2(), styles.get("currency"));
            createNumericCell(dataRow, 10, result.getPreviousMonths().getMonth3(), styles.get("currency"));
        }

        return startRow;
    }

    private int createRegisterEntriesSection(Sheet sheet, Map<String, CellStyle> styles, List<RegisterEntry> entries, int startRow) {
        // Table title
        Row tableTitleRow = sheet.createRow(startRow++);
        Cell tableTitleCell = tableTitleRow.createCell(0);
        tableTitleCell.setCellValue("Register Entries");
        tableTitleCell.setCellStyle(styles.get("subHeader"));
        sheet.addMergedRegion(new CellRangeAddress(startRow - 1, startRow - 1, 0, 2));

        // Headers row
        Row headerRow = sheet.createRow(startRow++);
        String[] headers = {"Date", "Order ID", "Production ID", "OMS ID", "Client", "Action Type",
                "Print Prep Type", "Colors Profile", "Articles", "Complexity", "Observations", "Status"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.get("columnHeader"));
        }

        // Sort entries by date
        List<RegisterEntry> sortedEntries = new ArrayList<>(entries);
        sortedEntries.sort(Comparator.comparing(RegisterEntry::getDate).reversed());

        // Populate data rows
        for (RegisterEntry entry : sortedEntries) {
            Row row = sheet.createRow(startRow++);
            createCell(row, 0, entry.getDate().format(WorkCode.DATE_FORMATTER), styles.get("date"));
            createCell(row, 1, entry.getOrderId(), styles.get("text"));
            createCell(row, 2, entry.getProductionId(), styles.get("text"));
            createCell(row, 3, entry.getOmsId(), styles.get("text"));
            createCell(row, 4, entry.getClientName(), styles.get("text"));
            createCell(row, 5, entry.getActionType(), styles.get("text"));
            createCell(row, 6, String.join(", ", entry.getPrintPrepTypes()), styles.get("text"));
            createCell(row, 7, entry.getColorsProfile(), styles.get("text"));
            createNumericCell(row, 8, entry.getArticleNumbers(), styles.get("number"));
            createNumericCell(row, 9, entry.getGraphicComplexity(), styles.get("number"));
            createCell(row, 10, entry.getObservations(), styles.get("text"));
            createCell(row, 11, entry.getAdminSync(), styles.get("status"));
        }

        return startRow;
    }

    private void addConfigRow(Sheet sheet, Map<String, CellStyle> styles, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.get("label"));

        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(styles.get("value"));
    }

    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "-");
        cell.setCellStyle(style);
    }

    private void createNumericCell(Row row, int column, Number value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue("-");
        }
        cell.setCellStyle(style);
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

        // Header style
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.LEFT);
        styles.put("header", headerStyle);

        // Sub-header style
        CellStyle subHeaderStyle = workbook.createCellStyle();
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
        labelStyle.setAlignment(HorizontalAlignment.LEFT);
        styles.put("label", labelStyle);

        // Value style
        CellStyle valueStyle = workbook.createCellStyle();
        valueStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.put("value", valueStyle);

        // Date style
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setBorderBottom(BorderStyle.THIN);
        dateStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.put("date", dateStyle);

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
        currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));
        styles.put("currency", currencyStyle);

        // Status style
        CellStyle statusStyle = workbook.createCellStyle();
        statusStyle.setBorderBottom(BorderStyle.THIN);
        statusStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.put("status", statusStyle);

        return styles;
    }

    private byte[] writeToByteArray(XSSFWorkbook workbook) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        return outputStream.toByteArray();
    }
}