package com.nicholasburczyk.packupdater.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionUtil {

    private static final Pattern NUMERIC = Pattern.compile("\\d+");

    private VersionUtil() {
    }

    public static int compare(String left, String right) {
        int[] a = parse(left);
        int[] b = parse(right);
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int ai = i < a.length ? a[i] : 0;
            int bi = i < b.length ? b[i] : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    public static boolean isGreater(String candidate, String baseline) {
        return compare(candidate, baseline) > 0;
    }

    public static String normalize(String version) {
        if (version == null) {
            return "0";
        }
        String trimmed = version.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.isEmpty() ? "0" : trimmed;
    }

    private static int[] parse(String version) {
        String normalized = normalize(version);
        String[] rawParts = normalized.split("[.\\-+_]");
        int[] parts = new int[rawParts.length];
        for (int i = 0; i < rawParts.length; i++) {
            parts[i] = firstNumber(rawParts[i]);
        }
        return parts;
    }

    private static int firstNumber(String token) {
        Matcher matcher = NUMERIC.matcher(token);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
