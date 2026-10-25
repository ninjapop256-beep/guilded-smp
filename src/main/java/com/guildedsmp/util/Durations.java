package com.guildedsmp.util;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and formats short duration strings such as {@code 30s}, {@code 10m}, {@code 1d12h}. */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d{1,9})([smhdw])");

    private Durations() {
    }

    /**
     * Parses durations like "30s", "10m", "2h", "7d", "1w" or combinations ("1d12h30m").
     * Returns empty for invalid, zero, or overflowing input.
     */
    public static Optional<Duration> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.length() > 32) {
            return Optional.empty();
        }
        Matcher m = PART.matcher(s);
        int pos = 0;
        long seconds = 0;
        while (m.find()) {
            if (m.start() != pos) {
                return Optional.empty();
            }
            long n = Long.parseLong(m.group(1));
            long unit = switch (m.group(2)) {
                case "s" -> 1L;
                case "m" -> 60L;
                case "h" -> 3_600L;
                case "d" -> 86_400L;
                case "w" -> 604_800L;
                default -> 0L;
            };
            try {
                seconds = Math.addExact(seconds, Math.multiplyExact(n, unit));
            } catch (ArithmeticException overflow) {
                return Optional.empty();
            }
            pos = m.end();
        }
        if (pos != s.length() || seconds <= 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(seconds));
    }

    /** Formats a duration as e.g. "1d 2h 5m"; at most three units, never empty. */
    public static String format(Duration duration) {
        long total = Math.max(0, duration.getSeconds());
        if (total == 0) {
            return "0s";
        }
        long[] sizes = {604_800L, 86_400L, 3_600L, 60L, 1L};
        String[] names = {"w", "d", "h", "m", "s"};
        StringBuilder sb = new StringBuilder();
        int parts = 0;
        for (int i = 0; i < sizes.length && parts < 3; i++) {
            long v = total / sizes[i];
            if (v > 0) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(v).append(names[i]);
                total %= sizes[i];
                parts++;
            }
        }
        return sb.toString();
    }
}
