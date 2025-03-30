package com.ctgraphdep.utils;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeEntryDTO;
import com.ctgraphdep.model.WorkTimeSummaryDTO;
import com.ctgraphdep.model.User;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.*;

@Component
public class UserWorktimeExcelExporter {

    public byte[] exportToExcel(User user, List<WorkTimeEntryDTO> worktimeData, WorkTimeSummaryDTO summary, int year, int month) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            LoggerUtil.info(this.getClass(), "Creating Excel export for " + worktimeData.size() + " entries");
            Sheet sheet = workbook.createSheet("Work Time Report");
            Map<String, CellStyle> styles = createStyles(workbook);

            int currentRow = 0;

            // Title row
            Row titleRow = sheet.createRow(currentRow++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(String.format("Work Time Report - %s %d",
                    java.time.Month.of(month).toString(), year));
            titleCell.setCellStyle(styles.get("title"));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

            // User info row
            Row userRow = sheet.createRow(currentRow++);
            Cell userCell = userRow.createCell(0);
            userCell.setCellValue("Employee: " + user.getName() + " (ID: " + user.getEmployeeId() + ")");
            userCell.setCellStyle(styles.get("header"));
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 6));

            // Spacing row
            currentRow++;

            // Headers row
            Row headerRow = sheet.createRow(currentRow++);
            String[] headers = {"Date", "Start Time", "End Time", "Breaks", "Time Off", "Hours", "Overtime"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(styles.get("columnHeader"));
            }

            // Populate worktime data
            currentRow = populateWorkTimeData(sheet, styles, worktimeData, currentRow);

            // Add spacing before summary
            currentRow += 2;

            // Summary section title
            Row summaryTitleRow = sheet.createRow(currentRow++);
            Cell summaryTitleCell = summaryTitleRow.createCell(0);
            summaryTitleCell.setCellValue("Month Summary");
            summaryTitleCell.setCellStyle(styles.get("subHeader"));
            sheet.addMergedRegion(new CellRangeAddress(currentRow - 1, currentRow - 1, 0, 2));

            // Populate summary data starting at the new row
            populateSummaryData(sheet, styles, summary, currentRow);

            // Auto size columns
            for (int i = 0; i < 7; i++) {
                sheet.autoSizeColumn(i);
            }

            return writeToByteArray(workbook);
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), "Error creating Excel file", e);
            return null;
        }
    }

    private int populateWorkTimeData(Sheet sheet, Map<String, CellStyle> styles, List<WorkTimeEntryDTO> worktimeData, int startRow) {
        CellStyle dateStyle = styles.get("date");
        CellStyle numberStyle = styles.get("number");
        CellStyle timeStyle = styles.get("time");

        int currentRow = startRow;

        // Create a new ArrayList and sort it explicitly
        List<WorkTimeEntryDTO> sortedData = new ArrayList<>(worktimeData);
        sortedData.sort(Comparator.comparing(WorkTimeEntryDTO::getWorkDate));

        LoggerUtil.info(this.getClass(), "Processing sorted entries:");

        for (WorkTimeEntryDTO record : sortedData) {
            LoggerUtil.debug(this.getClass(), "Processing row for date: " + record.getWorkDate());

            Row row = sheet.createRow(currentRow++);

            // Date - use formatted date from DTO
            createCell(row, 0, record.getFormattedDate(), dateStyle);

            // Start Time - use formatted time from DTO
            createCell(row, 1, record.getFormattedStartTime() != null ? record.getFormattedStartTime() : "-", timeStyle);

            // End Time - use formatted time from DTO
            createCell(row, 2, record.getFormattedEndTime() != null ? record.getFormattedEndTime() : "-", timeStyle);

            // Breaks
            if (record.getFormattedTemporaryStop() != null) {
                createCell(row, 3, record.getFormattedTemporaryStop(), numberStyle);
            } else {
                createCell(row, 3, "-", numberStyle);
            }

            // Time Off
            createCell(row, 4, record.getTimeOffType() != null ? record.getTimeOffType() : "-", numberStyle);

            // Hours - use formatted raw time from DTO
            createCell(row, 5, record.getFormattedRawTime() != null ? record.getFormattedRawTime() : "-", numberStyle);

            // Overtime - use formatted overtime time from DTO
            createCell(row, 6, record.getFormattedOvertimeTime() != null ? record.getFormattedOvertimeTime() : "-", numberStyle);
        }

        return currentRow;
    }

    private void populateSummaryData(Sheet sheet, Map<String, CellStyle> styles, WorkTimeSummaryDTO summary, int startRow) {
        int currentRow = startRow;

        // Work Days Section
        addSummaryRow(sheet, styles, currentRow++, WorkCode.WORK_DAYS, String.valueOf(summary.getTotalWorkDays()));
        addSummaryRow(sheet, styles, currentRow++, WorkCode.DAYS_WORKED, String.valueOf(summary.getDaysWorked()));
        addSummaryRow(sheet, styles, currentRow++, WorkCode.DAYS_REMAINING, String.valueOf(summary.getRemainingWorkDays()));

        currentRow++; // Spacing

        // Time Off Details
        addSummaryRow(sheet, styles, currentRow++, WorkCode.NATIONAL_HOLIDAY_CODE_LONG, String.valueOf(summary.getSnDays()));
        addSummaryRow(sheet, styles, currentRow++, WorkCode.AVAILABLE_PAID_DAYS, String.valueOf(summary.getAvailablePaidDays()));
        addSummaryRow(sheet, styles, currentRow++, WorkCode.TIME_OFF_CODE_LONG, String.valueOf(summary.getCoDays()));
        addSummaryRow(sheet, styles, currentRow++, WorkCode.MEDICAL_LEAVE_CODE_LONG, String.valueOf(summary.getCmDays()));

        currentRow++; // Spacing

        // Hours Details - use formatted hours from DTO
        addSummaryRow(sheet, styles, currentRow++, WorkCode.REGULAR_HOURS, summary.getFormattedRegularHours());
        addSummaryRow(sheet, styles, currentRow++, WorkCode.OVERTIME, summary.getFormattedOvertimeHours());
        addSummaryRow(sheet, styles, currentRow, WorkCode.TOTAL_HOURS, summary.getFormattedTotalHours());
    }

    private void addSummaryRow(Sheet sheet, Map<String, CellStyle> styles, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);

        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.get("summaryLabel"));

        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(styles.get("summaryValue"));

        // Merge the label cells
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 2, 6));
    }

    // Helper methods to ensure consistent cell creation
    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "-");
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

        // Time style
        CellStyle timeStyle = workbook.createCellStyle();
        timeStyle.setBorderBottom(BorderStyle.THIN);
        timeStyle.setBorderTop(BorderStyle.THIN);
        timeStyle.setBorderRight(BorderStyle.THIN);
        timeStyle.setBorderLeft(BorderStyle.THIN);
        timeStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.put("time", timeStyle);

        // Number style
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setBorderBottom(BorderStyle.THIN);
        numberStyle.setBorderTop(BorderStyle.THIN);
        numberStyle.setBorderRight(BorderStyle.THIN);
        numberStyle.setBorderLeft(BorderStyle.THIN);
        numberStyle.setAlignment(HorizontalAlignment.CENTER);
        styles.put("number", numberStyle);

        // Add new styles for summary section
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