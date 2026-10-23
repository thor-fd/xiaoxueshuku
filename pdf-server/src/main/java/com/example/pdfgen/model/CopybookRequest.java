package com.example.pdfgen.model;

import java.util.List;

/**
 * 字帖生成请求，所有字段都有默认值（除 words）。
 *
 * @param title      页面标题，默认按格线类型生成
 * @param gridType   格线类型 TIAN/MI/FANG，默认 TIAN
 * @param withPinyin 是否带拼音行，默认 true
 * @param traceDark  描红加深（喷墨机浅灰打不出来时用），默认 false
 * @param traceCount 每行描红格数，默认 3
 * @param blankCount 每行空白格数，默认 7
 * @param font       字体 WENKAI 霞鹜文楷 | HUIJI 汇迹正楷，默认 WENKAI
 * @param words      生字列表
 */
public record CopybookRequest(
        String title,
        GridType gridType,
        Boolean withPinyin,
        Boolean traceDark,
        Integer traceCount,
        Integer blankCount,
        String font,
        List<Word> words) {
}
