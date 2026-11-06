package com.nail.demo.solo.support;

import java.util.regex.Pattern;

/**
 * CSV 单元格转义。
 *
 * <p>除了逗号 / 引号 / 换行要包引号之外，还要防「公式注入」：
 * 以 = + - @ 或控制字符开头的单元格，Excel / WPS 打开时会被当成公式执行，
 * 客户昵称里写一个 {@code =HYPERLINK("http://x","点我")} 就能在老板的表格里变成可点的链接。
 * 处理方式是加前导单引号，Excel 会按纯文本展示。
 *
 * <p>纯数字（含负数）不加引号，否则金额列会变成文本、排不了序。
 */
public final class CsvCell {

    private static final Pattern NUMBER = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    private CsvCell() {
    }

    /** 转义一个单元格；null 视为空串。 */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        String text = neutralize(value).replace("\"", "\"\"");
        boolean needQuote = text.contains(",") || text.contains("\"")
                || text.contains("\n") || text.contains("\r");
        return needQuote ? "\"" + text + "\"" : text;
    }

    /** 公式注入中和：危险开头加单引号，纯数字原样返回。 */
    public static String neutralize(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        if (NUMBER.matcher(value).matches()) {
            return value;
        }
        char first = value.charAt(0);
        boolean dangerous = first == '=' || first == '+' || first == '-'
                || first == '@' || first == '\t' || first == '\r';
        return dangerous ? "'" + value : value;
    }
}
