package com.ctgraphdep.utils;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.worktime.WorkTimeResultDTO;
import com.ctgraphdep.model.WorkTimeTable;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Component
public class WorkTimeExcelExporter {

    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");

    public byte[] exportToExcel(List<User> users,
                                Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap,
                                int year,
                                int month) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Work Time Report");
            YearMonth yearMonth = YearMonth.of(year, month);

            // Get non-admin users
            List<User> nonAdminUsers = filterNonAdminUsers(users);

            // Create all cell styles
            Map<String, CellStyle> styles = createStyles(workbook);

            // Setup sheet structure
            int columnCount = yearMonth.lengthOfMonth() + 5; // Name, ID, days, Hours, OT, Total
            setupSheetHeader(sheet, yearMonth, styles, columnCount);

            // Add data rows
            populateSheetData(sheet, yearMonth, nonAdminUsers, userEntriesMap, styles, columnCount);

            // Final formatting
            for (int i = 0; i < columnCount; i++) {
                sheet.autoSizeColumn(i);
            }

            return writeToByteArray(workbook);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating Excel file", e);
            throw new RuntimeException("Failed to export Excel file", e);
        }
    }

    private List<User> filterNonAdminUsers(List<User> users) {
        return users.stream()
                .filter(user -> !user.getRole().contains(SecurityConstants.ROLE_ADMIN))
                .toList();
    }

    private void setupSheetHeader(Sheet sheet, YearMonth yearMonth, Map<String, CellStyle> styles, int columnCount) {
        // Title row
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Work Time Report - " + yearMonth.format(MONTH_YEAR_FORMATTER));
        titleCell.setCellStyle(styles.get("title"));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columnCount - 1));

        // Day names row
        Row dayNamesRow = sheet.createRow(1);
        Row headerRow = sheet.createRow(2);

        // Basic headers
        createBasicHeaders(dayNamesRow, headerRow, styles.get("header"));

        // Create day columns
        createDayColumns(yearMonth, dayNamesRow, headerRow, styles);

        // Create summary columns
        createSummaryColumns(dayNamesRow, headerRow, styles.get("header"), columnCount);

        // Merge header cells
        mergeHeaderCells(sheet);
    }

    private void createBasicHeaders(Row dayNamesRow, Row headerRow, CellStyle headerStyle) {
        String[] headers = {"Name", "Employee ID"};
        for (int i = 0; i < 2; i++) {
            Cell dayNameCell = dayNamesRow.createCell(i);
            Cell headerCell = headerRow.createCell(i);
            dayNameCell.setCellValue(headers[i]);
            headerCell.setCellValue(headers[i]);
            dayNameCell.setCellStyle(headerStyle);
            headerCell.setCellStyle(headerStyle);
        }
    }

    private void createDayColumns(YearMonth yearMonth, Row dayNamesRow, Row headerRow, Map<String, CellStyle> styles) {
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);

            Cell dayNameCell = dayNamesRow.createCell(day + 1);
            Cell dayNumberCell = headerRow.createCell(day + 1);

            dayNameCell.setCellValue(WorkCode.ROMANIAN_DAY_INITIALS.get(date.getDayOfWeek()));
            dayNumberCell.setCellValue(day);

            CellStyle style = isWeekend(date) ? styles.get("weekend") : styles.get("date");
            dayNameCell.setCellStyle(style);
            dayNumberCell.setCellStyle(style);
        }
    }

    private void createSummaryColumns(Row dayNamesRow, Row headerRow, CellStyle headerStyle, int columnCount) {
        String[] summaryHeaders = {"Hours", "OT", "Total"};
        for (int i = 0; i < 3; i++) {
            Cell dayNameCell = dayNamesRow.createCell(columnCount - 3 + i);
            Cell headerCell = headerRow.createCell(columnCount - 3 + i);
            headerCell.setCellValue(summaryHeaders[i]);
            dayNameCell.setCellStyle(headerStyle);
            headerCell.setCellStyle(headerStyle);
        }
    }

    private void mergeHeaderCells(Sheet sheet) {
        // Merge Name and Employee ID cells
        sheet.addMergedRegion(new CellRangeAddress(1, 2, 0, 0));
        sheet.addMergedRegion(new CellRangeAddress(1, 2, 1, 1));
    }

    private void populateSheetData(Sheet sheet, YearMonth yearMonth, List<User> users,
                                   Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap,
                                   Map<String, CellStyle> styles, int columnCount) {
        int rowNum = 3;
        for (User user : users) {
            Row row = sheet.createRow(rowNum++);
            Map<LocalDate, WorkTimeTable> userEntries = userEntriesMap.getOrDefault(user.getUserId(), new HashMap<>());

            // User info
            createUserInfoCells(row, user, styles.get("name"));

            // Process daily entries
            WorkTimeResultDTO summary = processDailyEntries(row, yearMonth, userEntries, user, styles);

            // Create summary cells
            createSummaryCells(row, summary, columnCount, styles.get("number"));
        }
    }

    private WorkTimeResultDTO processDailyEntries(Row row, YearMonth yearMonth,
                                                  Map<LocalDate, WorkTimeTable> userEntries,
                                                  User user, Map<String, CellStyle> styles) {
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;

        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            Cell cell = row.createCell(day + 1);
            LocalDate date = yearMonth.atDay(day);
            WorkTimeTable entry = userEntries.get(date);

            CellStyle cellStyle = determineCellStyle(date, entry, styles);
            cell.setCellStyle(cellStyle);

            if (shouldProcessEntry(entry)) {
                processEntry(cell, entry);

                if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                    var result = CalculateWorkHoursUtil.calculateWorkTime(
                            entry.getTotalWorkedMinutes(),
                            user.getSchedule()
                    );
                    totalRegularMinutes += result.getProcessedMinutes();
                    totalOvertimeMinutes += result.getOvertimeMinutes();
                }
            }
        }

        return new WorkTimeResultDTO(totalRegularMinutes, totalOvertimeMinutes);
    }


    private boolean shouldProcessEntry(WorkTimeTable entry) {
        return entry != null &&
                (entry.getTimeOffType() == null || !"BLANK".equals(entry.getTimeOffType()));
    }

    private CellStyle determineCellStyle(LocalDate date, WorkTimeTable entry, Map<String, CellStyle> styles) {
        if (isWeekend(date)) {
            return styles.get("weekend");
        }
        if (entry != null && entry.getTimeOffType() != null && !"BLANK".equals(entry.getTimeOffType())) {
            return styles.get("timeOff");
        }
        return styles.get("number");
    }

    private void processEntry(Cell cell, WorkTimeTable entry) {
        if (entry.getTimeOffType() != null && !"BLANK".equals(entry.getTimeOffType())) {
            cell.setCellValue(entry.getTimeOffType());
        } else if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
            cell.setCellValue(Double.parseDouble(
                    CalculateWorkHoursUtil.minutesToHH(entry.getTotalWorkedMinutes())
            ));
        }
    }

    private void createUserInfoCells(Row row, User user, CellStyle nameStyle) {
        Cell nameCell = row.createCell(0);
        Cell idCell = row.createCell(1);
        nameCell.setCellValue(user.getName());
        idCell.setCellValue(user.getEmployeeId());
        nameCell.setCellStyle(nameStyle);
        idCell.setCellStyle(nameStyle);
    }

    private void createSummaryCells(Row row, WorkTimeResultDTO summary, int columnCount, CellStyle numberStyle) {
        createSummaryCell(row, columnCount - 3, summary.getTotalRegularMinutes(), numberStyle);
        createSummaryCell(row, columnCount - 2, summary.getTotalOvertimeMinutes(), numberStyle);
        createSummaryCell(row, columnCount - 1,
                summary.getTotalRegularMinutes() + summary.getTotalOvertimeMinutes(), numberStyle);
    }

    private void createSummaryCell(Row row, int column, int minutes, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(Double.parseDouble(CalculateWorkHoursUtil.minutesToHH(minutes)));
        cell.setCellStyle(style);
    }

    private byte[] writeToByteArray(XSSFWorkbook workbook) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        return outputStream.toByteArray();
    }

    private Map<String, CellStyle> createStyles(Workbook workbook) {
        Map<String, CellStyle> styles = new HashMap<>();
        styles.put("title", createTitleStyle(workbook));
        styles.put("header", createHeaderStyle(workbook));
        styles.put("date", createDateStyle(workbook));
        styles.put("weekend", createWeekendStyle(workbook));
        styles.put("number", createNumberStyle(workbook));
        styles.put("timeOff", createTimeOffStyle(workbook));
        styles.put("name", createNameStyle(workbook));
        return styles;
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createWeekendStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setDataFormat(workbook.createDataFormat().getFormat("0"));
        return style;
    }

    private CellStyle createTimeOffStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createNameStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    public static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY ||
                date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}