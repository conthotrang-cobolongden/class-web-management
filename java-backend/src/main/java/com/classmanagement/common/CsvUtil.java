package com.classmanagement.common;

import java.nio.charset.StandardCharsets;
import java.util.List;

// Xuat CSV UTF-8 co BOM de Excel mo dung tieng Viet (FR-5.4)
public final class CsvUtil {

    private static final String BOM = "\uFEFF";

    private CsvUtil() {
    }

    // rows = danh sach dong (dong dau thuong la header). null -> o trong.
    public static byte[] build(List<String[]> rows) {
        StringBuilder sb = new StringBuilder(BOM);
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escape(row[i]));
            }
            sb.append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
