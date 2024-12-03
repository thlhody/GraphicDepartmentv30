package com.ctgraphdep.utils;

import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.User;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class UserRegisterExcelExporter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] exportToExcel(User user, List<RegisterEntry> entries, int year, int month) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            LoggerUtil.info(this.getClass(), "Creating Excel export for " + entries.size() + " entries");
            Sheet sheet = workbook.createSheet("Register Report");
            Map<String, CellStyle> styles = createStyles(workbook);

            int currentRow = 0;

            // Title row
            Row titleRow = sheet.createRow(currentRow++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(String.format("Register Report - %s %d",
                    java.time.Month.of(month).toString(), year));
            titleCell.setCellStyle(styles.get("title"));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));

            // User info row
            Row userRow = sheet.createRow(currentRow++);
            Cell userCell = userRow.createCell(0);
            userCell.setCellValue("Employee: " + user.getName() + " (ID: " + user.getEmployeeId() + ")");
            userCell.setCellStyle(styles.get("header"));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 11));

            // Spacing row
            currentRow++;

            // Headers row
            Row headerRow = sheet.createRow(currentRow++);
            String[] headers = {"Date", "Order ID", "Production ID", "OMS ID", "Client", "Action Type",
                    "Print Prep Type", "Colors Profile", "Articles", "Complexity", "Observations", "Status"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(styles.get("columnHeader"));
            }

            // Populate register data
            currentRow = populateRegisterData(sheet, styles, entries, currentRow);

            // Add spacing before summary
            currentRow += 2;

            // Summary section title
            Row summaryTitleRow = sheet.createRow(currentRow++);
            Cell summaryTitleCell = summaryTitleRow.createCell(0);
            summaryTitleCell.setCellValue("Month Summary");
            summaryTitleCell.setCellStyle(styles.get("subHeader"));
            sheet.addMergedRegion(new CellRangeAddress(currentRow - 1, currentRow - 1, 0, 2));

            // Populate summary data
            populateSummaryData(sheet, styles, entries, currentRow);

            // Autosize columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            return writeToByteArray(workbook);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating Excel file", e);
            throw new RuntimeException("Failed to export Excel file", e);
        }
    }

    private int populateRegisterData(Sheet sheet, Map<String, CellStyle> styles, List<RegisterEntry> entries, int startRow) {
        CellStyle dateStyle = styles.get("date");
        CellStyle textStyle = styles.get("text");
        CellStyle numberStyle = styles.get("number");

        int currentRow = startRow;

        // Sort entries by date and ID
        List<RegisterEntry> sortedEntries = new ArrayList<>(entries);
        sortedEntries.sort(Comparator.comparing(RegisterEntry::getDate).reversed()
                .thenComparing(RegisterEntry::getEntryId, Comparator.reverseOrder()));

        for (RegisterEntry entry : sortedEntries) {
            Row row = sheet.createRow(currentRow++);

            // Date
            createCell(row, 0, entry.getDate().format(DATE_FORMATTER), dateStyle);
            createCell(row, 1, entry.getOrderId(), textStyle);
            createCell(row, 2, entry.getProductionId(), textStyle);
            createCell(row, 3, entry.getOmsId(), textStyle);
            createCell(row, 4, entry.getClientName(), textStyle);
            createCell(row, 5, entry.getActionType(), textStyle);
            createCell(row, 6, entry.getPrintPrepType(), textStyle);
            createCell(row, 7, entry.getColorsProfile(), textStyle);
            createNumericCell(row, 8, entry.getArticleNumbers(), numberStyle);
            createNumericCell(row, 9, entry.getGraphicComplexity(), numberStyle);
            createCell(row, 10, entry.getObservations(), textStyle);
            createCell(row, 11, entry.getAdminSync(), textStyle);
        }

        return currentRow;
    }

    private void populateSummaryData(Sheet sheet, Map<String, CellStyle> styles, List<RegisterEntry> entries, int startRow) {
        AtomicInteger currentRow = new AtomicInteger(startRow);

        // Calculate summary statistics
        int totalEntries = entries.size();
        double avgArticles = entries.stream()
                .mapToInt(RegisterEntry::getArticleNumbers)
                .average()
                .orElse(0.0);
        double avgComplexity = entries.stream()
                .mapToDouble(RegisterEntry::getGraphicComplexity)
                .average()
                .orElse(0.0);

        // Add summary rows
        addSummaryRow(sheet, styles, currentRow.getAndIncrement(), "Total Entries", String.valueOf(totalEntries));
        addSummaryRow(sheet, styles, currentRow.getAndIncrement(), "Average Articles", String.format("%.2f", avgArticles));
        addSummaryRow(sheet, styles, currentRow.getAndIncrement(), "Average Complexity", String.format("%.2f", avgComplexity));

        // Add status counts
        Map<String, Long> statusCounts = entries.stream()
                .collect(Collectors.groupingBy(RegisterEntry::getAdminSync, Collectors.counting()));

        currentRow.getAndIncrement(); // Add spacing
        addSummaryRow(sheet, styles, currentRow.getAndIncrement(), "Status Distribution", "");
        statusCounts.forEach((status, count) ->
                addSummaryRow(sheet, styles, currentRow.getAndIncrement(), status, count.toString())
        );
    }

    private void addSummaryRow(Sheet sheet, Map<String, CellStyle> styles, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);

        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.get("summaryLabel"));

        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(styles.get("summaryValue"));

        // Merge the remaining cells
        if (!value.isEmpty()) {
            sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 2, 11));
        }
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

        // Column header style
        CellStyle columnHeaderStyle = workbook.createCellStyle();
        Font columnHeaderFont = workbook.createFont();
        columnHeaderFont.setBold(true);
        columnHeaderStyle.setFont(columnHeaderFont);
        columnHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        columnHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        columnHeaderStyle.setBorderBottom(BorderStyle.THIN);
        columnHeaderStyle.setBorderTop(BorderStyle.THIN);
        columnHeaderStyle.setBorderRight(BorderStyle.THIN);
        columnHeaderStyle.setBorderLeft(BorderStyle.THIN);
        columnHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.put("columnHeader", columnHeaderStyle);

        // Date style
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setBorderBottom(BorderStyle.THIN);
        dateStyle.setBorderTop(BorderStyle.THIN);
        dateStyle.setBorderRight(BorderStyle.THIN);
        dateStyle.setBorderLeft(BorderStyle.THIN);
        dateStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.put("date", dateStyle);

        // Text style
        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setBorderBottom(BorderStyle.THIN);
        textStyle.setBorderTop(BorderStyle.THIN);
        textStyle.setBorderRight(BorderStyle.THIN);
        textStyle.setBorderLeft(BorderStyle.THIN);
        textStyle.setAlignment(HorizontalAlignment.LEFT);
        styles.put("text", textStyle);

        // Number style
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setBorderBottom(BorderStyle.THIN);
        numberStyle.setBorderTop(BorderStyle.THIN);
        numberStyle.setBorderRight(BorderStyle.THIN);
        numberStyle.setBorderLeft(BorderStyle.THIN);
        numberStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.put("number", numberStyle);

        // Summary styles
        CellStyle subHeaderStyle = workbook.createCellStyle();
        Font subHeaderFont = workbook.createFont();
        subHeaderFont.setBold(true);
        subHeaderStyle.setFont(subHeaderFont);
        subHeaderStyle.setAlignment(HorizontalAlignment.LEFT);
        subHeaderStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        subHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styles.put("subHeader", subHeaderStyle);

        CellStyle summaryLabelStyle = workbook.createCellStyle();
        summaryLabelStyle.setAlignment(HorizontalAlignment.LEFT);
        styles.put("summaryLabel", summaryLabelStyle);

        CellStyle summaryValueStyle = workbook.createCellStyle();
        summaryValueStyle.setAlignment(HorizontalAlignment.CENTER);
        summaryValueStyle.setBorderBottom(BorderStyle.THIN);
        summaryValueStyle.setBorderTop(BorderStyle.THIN);
        summaryValueStyle.setBorderRight(BorderStyle.THIN);
        summaryValueStyle.setBorderLeft(BorderStyle.THIN);
        styles.put("summaryValue", summaryValueStyle);

        return styles;
    }

    private byte[] writeToByteArray(XSSFWorkbook workbook) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        return outputStream.toByteArray();
    }
}