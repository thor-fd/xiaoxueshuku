package com.example.pdfgen.model;

/**
 * 口算题卡生成请求。
 * 固定 seed 可复现同一份题（用于 PDF 缓存）。
 */
public record OralMathRequest(
        KnowledgePoint knowledgePoint,
        Integer count,
        Integer cols,
        Long seed,
        Boolean withAnswer,
        Integer day,
        String font) {
}
