package com.reminder.server.payment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RecurrenceType {
    ONE_TIME,
    MONTHLY,
    BI_MONTHLY,
    QUARTERLY,
    HALF_YEARLY,
    YEARLY,
    CUSTOM_DAYS,
    UNKNOWN;

    public boolean isSupportedByBackend() {
        return this != UNKNOWN;
    }

    public boolean isLocalOnly() {
        return false;
    }

    @JsonCreator
    public static RecurrenceType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return MONTHLY;
        }
        String normalized = value.trim().toUpperCase();
        for (RecurrenceType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
