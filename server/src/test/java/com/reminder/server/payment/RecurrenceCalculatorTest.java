package com.reminder.server.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;

public class RecurrenceCalculatorTest {

    private long createDate(int year, int monthZeroIndexed, int day, int hour, int minute, int second, int millis) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, monthZeroIndexed);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, second);
        cal.set(Calendar.MILLISECOND, millis);
        return cal.getTimeInMillis();
    }

    private Calendar toCalendar(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        return cal;
    }

    @Test
    @DisplayName("Month-end progression: Jan 31 -> Feb 28 -> Mar 31 in a non-leap year")
    void testMonthEndNonLeapYear() {
        // 2025 is not a leap year. Feb has 28 days.
        long jan31 = createDate(2025, Calendar.JANUARY, 31, 14, 30, 0, 0);
        int anchorDay = 31;

        // Step 1: Jan 31 -> Feb 28
        long feb = RecurrenceCalculator.calculateImmediateNextDueDate(jan31, RecurrenceType.MONTHLY, anchorDay);
        Calendar calFeb = toCalendar(feb);
        assertEquals(2025, calFeb.get(Calendar.YEAR));
        assertEquals(Calendar.FEBRUARY, calFeb.get(Calendar.MONTH));
        assertEquals(28, calFeb.get(Calendar.DAY_OF_MONTH));
        assertEquals(14, calFeb.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, calFeb.get(Calendar.MINUTE));

        // Step 2: Feb 28 -> Mar 31 (anchor day 31 is preserved)
        long mar = RecurrenceCalculator.calculateImmediateNextDueDate(feb, RecurrenceType.MONTHLY, anchorDay);
        Calendar calMar = toCalendar(mar);
        assertEquals(2025, calMar.get(Calendar.YEAR));
        assertEquals(Calendar.MARCH, calMar.get(Calendar.MONTH));
        assertEquals(31, calMar.get(Calendar.DAY_OF_MONTH));
        assertEquals(14, calMar.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, calMar.get(Calendar.MINUTE));
    }

    @Test
    @DisplayName("Leap-year February: Jan 31, 2024 -> Feb 29, 2024 -> Mar 31, 2024")
    void testLeapYearFebruary() {
        // 2024 is a leap year. Feb has 29 days.
        long jan31 = createDate(2024, Calendar.JANUARY, 31, 9, 15, 30, 500);
        int anchorDay = 31;

        // Step 1: Jan 31 -> Feb 29
        long feb = RecurrenceCalculator.calculateImmediateNextDueDate(jan31, RecurrenceType.MONTHLY, anchorDay);
        Calendar calFeb = toCalendar(feb);
        assertEquals(2024, calFeb.get(Calendar.YEAR));
        assertEquals(Calendar.FEBRUARY, calFeb.get(Calendar.MONTH));
        assertEquals(29, calFeb.get(Calendar.DAY_OF_MONTH));
        assertEquals(9, calFeb.get(Calendar.HOUR_OF_DAY));
        assertEquals(15, calFeb.get(Calendar.MINUTE));
        assertEquals(30, calFeb.get(Calendar.SECOND));
        assertEquals(500, calFeb.get(Calendar.MILLISECOND));

        // Step 2: Feb 29 -> Mar 31
        long mar = RecurrenceCalculator.calculateImmediateNextDueDate(feb, RecurrenceType.MONTHLY, anchorDay);
        Calendar calMar = toCalendar(mar);
        assertEquals(2024, calMar.get(Calendar.YEAR));
        assertEquals(Calendar.MARCH, calMar.get(Calendar.MONTH));
        assertEquals(31, calMar.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    @DisplayName("Monthly recurrence advances by exactly 1 month")
    void testMonthlyRecurrence() {
        long april15 = createDate(2026, Calendar.APRIL, 15, 10, 0, 0, 0);
        long may15 = RecurrenceCalculator.calculateImmediateNextDueDate(april15, RecurrenceType.MONTHLY, 15);
        Calendar cal = toCalendar(may15);
        assertEquals(2026, cal.get(Calendar.YEAR));
        assertEquals(Calendar.MAY, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    @DisplayName("Bi-monthly recurrence advances by exactly 2 months")
    void testBiMonthlyRecurrence() {
        long jan15 = createDate(2026, Calendar.JANUARY, 15, 11, 20, 0, 0);
        long mar15 = RecurrenceCalculator.calculateImmediateNextDueDate(jan15, RecurrenceType.BI_MONTHLY, 15);
        Calendar cal = toCalendar(mar15);
        assertEquals(2026, cal.get(Calendar.YEAR));
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));

        // Jan 31 -> Mar 31
        long jan31 = createDate(2026, Calendar.JANUARY, 31, 11, 20, 0, 0);
        long mar31 = RecurrenceCalculator.calculateImmediateNextDueDate(jan31, RecurrenceType.BI_MONTHLY, 31);
        Calendar cal31 = toCalendar(mar31);
        assertEquals(Calendar.MARCH, cal31.get(Calendar.MONTH));
        assertEquals(31, cal31.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    @DisplayName("Quarterly recurrence advances by exactly 3 months")
    void testQuarterlyRecurrence() {
        long jan31 = createDate(2026, Calendar.JANUARY, 31, 8, 0, 0, 0);
        long apr30 = RecurrenceCalculator.calculateImmediateNextDueDate(jan31, RecurrenceType.QUARTERLY, 31);
        Calendar cal = toCalendar(apr30);
        assertEquals(2026, cal.get(Calendar.YEAR));
        assertEquals(Calendar.APRIL, cal.get(Calendar.MONTH));
        assertEquals(30, cal.get(Calendar.DAY_OF_MONTH)); // April has 30 days, clamped

        // Next quarter: Apr 30 -> Jul 31 (recovers anchor day 31)
        long jul31 = RecurrenceCalculator.calculateImmediateNextDueDate(apr30, RecurrenceType.QUARTERLY, 31);
        Calendar calJul = toCalendar(jul31);
        assertEquals(2026, calJul.get(Calendar.YEAR));
        assertEquals(Calendar.JULY, calJul.get(Calendar.MONTH));
        assertEquals(31, calJul.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    @DisplayName("Half-yearly recurrence advances by exactly 6 months")
    void testHalfYearlyRecurrence() {
        long jan31 = createDate(2026, Calendar.JANUARY, 31, 16, 45, 0, 0);
        long jul31 = RecurrenceCalculator.calculateImmediateNextDueDate(jan31, RecurrenceType.HALF_YEARLY, 31);
        Calendar cal = toCalendar(jul31);
        assertEquals(2026, cal.get(Calendar.YEAR));
        assertEquals(Calendar.JULY, cal.get(Calendar.MONTH));
        assertEquals(31, cal.get(Calendar.DAY_OF_MONTH));

        // Next half-year: Jul 31 -> Jan 31 of next year
        long nextJan31 = RecurrenceCalculator.calculateImmediateNextDueDate(jul31, RecurrenceType.HALF_YEARLY, 31);
        Calendar calNext = toCalendar(nextJan31);
        assertEquals(2027, calNext.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, calNext.get(Calendar.MONTH));
        assertEquals(31, calNext.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    @DisplayName("Yearly recurrence advances by exactly 12 months")
    void testYearlyRecurrence() {
        long date = createDate(2026, Calendar.OCTOBER, 31, 12, 0, 0, 0);
        long nextYear = RecurrenceCalculator.calculateImmediateNextDueDate(date, RecurrenceType.YEARLY, 31);
        Calendar cal = toCalendar(nextYear);
        assertEquals(2027, cal.get(Calendar.YEAR));
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH));
        assertEquals(31, cal.get(Calendar.DAY_OF_MONTH));

        // Leap day yearly: Feb 29, 2024 -> Feb 28, 2025
        long leapDay = createDate(2024, Calendar.FEBRUARY, 29, 12, 0, 0, 0);
        long nonLeapNextYear = RecurrenceCalculator.calculateImmediateNextDueDate(leapDay, RecurrenceType.YEARLY, 29);
        Calendar calLeap = toCalendar(nonLeapNextYear);
        assertEquals(2025, calLeap.get(Calendar.YEAR));
        assertEquals(Calendar.FEBRUARY, calLeap.get(Calendar.MONTH));
        assertEquals(28, calLeap.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    @DisplayName("CUSTOM_DAYS recurrence advances by exact interval in days")
    void testCustomDaysRecurrence() {
        long start = createDate(2026, Calendar.MARCH, 1, 15, 30, 0, 0);

        // 28 days
        long after28 = RecurrenceCalculator.calculateImmediateNextDueDate(start, RecurrenceType.CUSTOM_DAYS, null, 28);
        Calendar cal28 = toCalendar(after28);
        assertEquals(Calendar.MARCH, cal28.get(Calendar.MONTH));
        assertEquals(29, cal28.get(Calendar.DAY_OF_MONTH));
        assertEquals(15, cal28.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, cal28.get(Calendar.MINUTE));

        // 45 days: March 1 + 45 days = April 15
        long after45 = RecurrenceCalculator.calculateImmediateNextDueDate(start, RecurrenceType.CUSTOM_DAYS, null, 45);
        Calendar cal45 = toCalendar(after45);
        assertEquals(Calendar.APRIL, cal45.get(Calendar.MONTH));
        assertEquals(15, cal45.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    @DisplayName("Preservation of exact time of day across leap years, month changes, and custom days")
    void testTimeOfDayPreservation() {
        long start = createDate(2026, Calendar.JANUARY, 31, 23, 59, 58, 999);
        long next = RecurrenceCalculator.calculateImmediateNextDueDate(start, RecurrenceType.MONTHLY, 31);
        Calendar cal = toCalendar(next);
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(59, cal.get(Calendar.MINUTE));
        assertEquals(58, cal.get(Calendar.SECOND));
        assertEquals(999, cal.get(Calendar.MILLISECOND));
    }

    @Test
    @DisplayName("Overdue payment progression advances past reference time")
    void testOverduePaymentProgression() {
        // Due 3 months ago (Jan 15, 2026), current time is April 20, 2026
        long jan15 = createDate(2026, Calendar.JANUARY, 15, 10, 0, 0, 0);
        long referenceApril20 = createDate(2026, Calendar.APRIL, 20, 12, 0, 0, 0);

        long nextFutureDue = RecurrenceCalculator.calculateNextDueDate(jan15, RecurrenceType.MONTHLY, 15, 28, referenceApril20);
        Calendar cal = toCalendar(nextFutureDue);
        // Next due date strictly in the future relative to April 20 must be May 15, 2026
        assertEquals(2026, cal.get(Calendar.YEAR));
        assertEquals(Calendar.MAY, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
        assertTrue(nextFutureDue > referenceApril20);
    }

    @Test
    @DisplayName("ONE_TIME recurrence does not advance due date")
    void testOneTimeDoesNotAdvance() {
        long date = createDate(2026, Calendar.MAY, 1, 10, 0, 0, 0);
        long result = RecurrenceCalculator.calculateImmediateNextDueDate(date, RecurrenceType.ONE_TIME, 1);
        assertEquals(date, result);
    }

    @Test
    @DisplayName("Idempotence: repeated immediate calls with same state produce identical results")
    void testIdempotence() {
        long date = createDate(2026, Calendar.JUNE, 10, 10, 0, 0, 0);
        long r1 = RecurrenceCalculator.calculateImmediateNextDueDate(date, RecurrenceType.MONTHLY, 10);
        long r2 = RecurrenceCalculator.calculateImmediateNextDueDate(date, RecurrenceType.MONTHLY, 10);
        assertEquals(r1, r2);
    }
}

