package com.ctgraphdep.utils;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

public class MonthFormatter {
    public static String[] getPreviousMonthNames(int year, int month) {
        YearMonth current = YearMonth.of(year, month);
        String[] monthNames = new String[3];

        for (int i = 0; i < 3; i++) {
            YearMonth previousMonth = current.minusMonths(i + 1);
            // Format as just the month name (e.g., "Nov")
            monthNames[i] = previousMonth.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        }

        return monthNames;
    }
}