package com.groomdesk.logging;

import java.util.regex.Pattern;

/** Helpers that keep personal data and log-forging characters out of log lines. */
public final class LogText {

    /** Indian mobile numbers (10 digits starting 6-9) anywhere in a string. */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)([6-9]\\d)\\d{6}(\\d{2})(?!\\d)");
    private static final Pattern CONTROL = Pattern.compile("[\\r\\n\\t\\x00-\\x1F\\x7F]+");

    private LogText() {
    }

    /** "9910551967" becomes "99XXXXXX67": enough to recognise a customer, not enough to call them. */
    public static String maskPhones(String text) {
        return text == null ? null : PHONE.matcher(text).replaceAll("$1XXXXXX$2");
    }

    /**
     * Makes untrusted text (usernames, browser error messages) safe for one log line: control
     * characters such as newlines become a single space, so nobody can forge extra log lines, and
     * the result is cut to {@code max} characters.
     */
    public static String clean(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = CONTROL.matcher(text).replaceAll(" ").strip();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    /** {@link #clean} plus {@link #maskPhones}: the default for anything a user typed. */
    public static String safe(String text, int max) {
        return maskPhones(clean(text, max));
    }
}
