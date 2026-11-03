package com.reminder.server.payment;

import java.util.Calendar;

/**
 * Deterministic recurrence calculation engine matching Android and Windows clients.
 */
public class RecurrenceCalculator {

    public static long calculateImmediateNextDueDate(long currentDueDateMillis, RecurrenceType recurrence, Integer originalDueDay) {
        return calculateNextDueDate(currentDueDateMillis, recurrence, originalDueDay, 28, Long.MIN_VALUE);
    }

    public static long calculateImmediateNextDueDate(long currentDueDateMillis, RecurrenceType recurrence, Integer originalDueDay, Integer customIntervalDays) {
        return calculateNextDueDate(currentDueDateMillis, recurrence, originalDueDay, customIntervalDays, Long.MIN_VALUE);
    }

    public static long calculateNextDueDate(long currentDueDateMillis, RecurrenceType recurrence, Integer originalDueDay) {
        return calculateNextDueDate(currentDueDateMillis, recurrence, originalDueDay, 28, System.currentTimeMillis());
    }

    public static long calculateNextDueDate(long currentDueDateMillis, RecurrenceType recurrence, Integer originalDueDay, Integer customIntervalDays) {
        return calculateNextDueDate(currentDueDateMillis, recurrence, originalDueDay, customIntervalDays, System.currentTimeMillis());
    }

    public static long calculateNextDueDate(long currentDueDateMillis, RecurrenceType recurrence, Integer originalDueDay, Integer customIntervalDays, long referenceTimeMillis) {
        if (recurrence == null || recurrence == RecurrenceType.ONE_TIME || recurrence == RecurrenceType.UNKNOWN) {
            return currentDueDateMillis;
        }

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(currentDueDateMillis);

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);
        int millis = cal.get(Calendar.MILLISECOND);

        if (recurrence == RecurrenceType.CUSTOM_DAYS) {
            int interval = (customIntervalDays != null && customIntervalDays > 0 && customIntervalDays <= 365)
                    ? customIntervalDays : 28;

            int iterations = 0;
            do {
                cal.add(Calendar.DATE, interval);
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                cal.set(Calendar.SECOND, second);
                cal.set(Calendar.MILLISECOND, millis);
                iterations++;
            } while (cal.getTimeInMillis() <= referenceTimeMillis && iterations < 1000);

            return cal.getTimeInMillis();
        }

        int stepMonths;
        switch (recurrence) {
            case BI_MONTHLY:
                stepMonths = 2;
                break;
            case QUARTERLY:
                stepMonths = 3;
                break;
            case HALF_YEARLY:
                stepMonths = 6;
                break;
            case YEARLY:
                stepMonths = 12;
                break;
            case MONTHLY:
            default:
                stepMonths = 1;
                break;
        }

        int targetDay = (originalDueDay != null && originalDueDay >= 1 && originalDueDay <= 31)
                ? originalDueDay : cal.get(Calendar.DAY_OF_MONTH);

        int iterations = 0;
        do {
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.add(Calendar.MONTH, stepMonths);
            int maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            int clampedDay = Math.min(targetDay, maxDays);
            cal.set(Calendar.DAY_OF_MONTH, clampedDay);
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, second);
            cal.set(Calendar.MILLISECOND, millis);
            iterations++;
        } while (cal.getTimeInMillis() <= referenceTimeMillis && iterations < 120);

        return cal.getTimeInMillis();
    }
}

