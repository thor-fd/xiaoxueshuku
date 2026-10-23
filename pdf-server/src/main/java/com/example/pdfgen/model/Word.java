package com.example.pdfgen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 字帖生字。JSON 里字段名用 char（Java 关键字，故映射到 character）：
 * <pre>{"char": "天", "pinyin": "tiān"}</pre>
 */
public record Word(
        @JsonProperty("char") String character,
        String pinyin) {
}
