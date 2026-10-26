package in.uk.ulb.ptax.common;

import java.time.LocalDate;

/**
 * Indian financial year, April to March. Stored as "2026-27".
 */
public final class FinancialYear {

    private FinancialYear() {
    }

    public static String of(LocalDate date) {
        int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return format(start);
    }

    public static String current() {
        return of(LocalDate.now());
    }

    public static String previous(String fy) {
        return format(startYear(fy) - 1);
    }

    public static int startYear(String fy) {
        if (fy == null || !fy.matches("\\d{4}-\\d{2}")) {
            throw ApiException.badRequest("Financial year must look like 2026-27");
        }
        return Integer.parseInt(fy.substring(0, 4));
    }

    private static String format(int startYear) {
        return startYear + "-" + String.format("%02d", (startYear + 1) % 100);
    }
}
