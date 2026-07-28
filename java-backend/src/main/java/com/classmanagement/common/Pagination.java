package com.classmanagement.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// page >= 1, page_size 1..100 (mac dinh 20) — NFR-6.
// parseOr mo phong parseInt cua JS + `|| default` ben Node (utils/validate.js):
// lay tien to so ('50abc' -> 50), khong co tien to -> default, parse ra 0 -> default.
public record Pagination(int page, int pageSize) {

    private static final Pattern LEADING_INT = Pattern.compile("^[+-]?\\d+");

    public static Pagination of(String pageParam, String pageSizeParam) {
        return new Pagination(
                Math.max(1, parseOr(pageParam, 1)),
                Math.min(100, Math.max(1, parseOr(pageSizeParam, 20))));
    }

    private static int parseOr(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        Matcher m = LEADING_INT.matcher(value.trim());
        if (!m.find()) {
            return fallback;
        }
        long n;
        try {
            n = Long.parseLong(m.group());
        } catch (NumberFormatException e) {
            // Chuoi so qua dai: JS van parse ra so cuc lon -> de clamp min/max xu ly
            n = m.group().startsWith("-") ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        if (n == 0) {
            return fallback;
        }
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, n));
    }
}
