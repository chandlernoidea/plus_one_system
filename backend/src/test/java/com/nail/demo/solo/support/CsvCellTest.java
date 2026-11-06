package com.nail.demo.solo.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CSV 转义：既要能进 Excel，也不能让单元格变公式。
 */
class CsvCellTest {

    @Test
    @DisplayName("普通文本原样输出")
    void plainText() {
        assertEquals("阿岚", CsvCell.escape("阿岚"));
    }

    @Test
    @DisplayName("含逗号、引号、换行的单元格用双引号包住")
    void quoting() {
        assertEquals("\"阿岚,小满\"", CsvCell.escape("阿岚,小满"));
        assertEquals("\"说\"\"好\"\"\"", CsvCell.escape("说\"好\""));
        assertEquals("\"第一行\n第二行\"", CsvCell.escape("第一行\n第二行"));
    }

    @Test
    @DisplayName("公式注入被中和：= + - @ 开头加前导单引号")
    void formulaInjection() {
        assertEquals("'=1+1", CsvCell.escape("=1+1"));
        // 含引号 → 中和后再按 CSV 规则转义（引号翻倍、整体包引号）
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\",\"\"点我\"\")\"",
                CsvCell.escape("=HYPERLINK(\"http://x\",\"点我\")"));
        assertEquals("'+86 138", CsvCell.escape("+86 138"));
        assertEquals("'@SUM(A1:A9)", CsvCell.escape("@SUM(A1:A9)"));
        assertEquals("'-阿岚", CsvCell.escape("-阿岚"));
    }

    @Test
    @DisplayName("负数与金额保持数字，不被当成公式")
    void numbersStayNumeric() {
        assertEquals("-58.5", CsvCell.escape("-58.5"));
        assertEquals("168", CsvCell.escape("168"));
        assertEquals("0.00", CsvCell.escape("0.00"));
    }

    @Test
    @DisplayName("null 与空串输出为空单元格")
    void nullSafe() {
        assertEquals("", CsvCell.escape(null));
        assertEquals("", CsvCell.escape(""));
    }

    @Test
    @DisplayName("制表符开头的单元格也要中和")
    void tabPrefixed() {
        assertEquals("'\t=cmd", CsvCell.escape("\t=cmd"));
    }
}
