package com.reminder.server.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralized utility for privacy-safe logging across the Reminder Server ecosystem.
 * Ensures email addresses, usernames, and WebSocket destination paths containing user
 * identifiers are consistently masked according to security and privacy standards.
 */
public final class LoggingUtils {

    private static final Pattern EMAIL_IN_PATH_PATTERN = Pattern.compile("([^/@\\s]+@[^/@\\s]+)");

    private LoggingUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Masks an email or username identifier for safe diagnostic logging.
     * Examples:
     * <ul>
     *   <li>"dev_tester@localdev.internal" -> "de***@localdev.internal"</li>
     *   <li>"john.doe@example.com" -> "jo***@example.com"</li>
     *   <li>"a@example.com" -> "a***@example.com"</li>
     *   <li>"@example.com" -> "***@example.com"</li>
     *   <li>"admin" -> "ad***"</li>
     *   <li>"ab" -> "***"</li>
     *   <li>null or empty -> "[EMPTY]"</li>
     *   <li>"anonymous" or "anonymousUser" -> "anonymous"</li>
     * </ul>
     *
     * @param identifier the raw email or username string
     * @return the privacy-masked string
     */
    public static String maskEmail(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return "[EMPTY]";
        }
        String trimmed = identifier.trim();
        if ("anonymous".equalsIgnoreCase(trimmed) || "anonymousUser".equalsIgnoreCase(trimmed)) {
            return "anonymous";
        }

        int atIndex = trimmed.indexOf('@');
        if (atIndex > 1) {
            return trimmed.substring(0, 2) + "***" + trimmed.substring(atIndex);
        } else if (atIndex == 1) {
            return trimmed.charAt(0) + "***" + trimmed.substring(atIndex);
        } else if (atIndex == 0) {
            return "***" + trimmed;
        } else {
            // Identifier has no '@' sign (plain username)
            return trimmed.length() > 2 ? trimmed.substring(0, 2) + "***" : "***";
        }
    }

    /**
     * Masks any embedded email address within a destination path (e.g. WebSocket STOMP destinations).
     * Examples:
     * <ul>
     *   <li>"/user/dev_tester@localdev.internal/topic/sync" -> "/user/de***@localdev.internal/topic/sync"</li>
     *   <li>"/user/topic/sync" -> "/user/topic/sync"</li>
     *   <li>"/topic/sync" -> "/topic/sync"</li>
     * </ul>
     *
     * @param destination the raw destination string
     * @return the masked destination string safe for logging
     */
    public static String maskDestination(String destination) {
        if (destination == null || destination.trim().isEmpty()) {
            return "";
        }
        Matcher matcher = EMAIL_IN_PATH_PATTERN.matcher(destination);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(maskEmail(matcher.group(1))));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}

