package com.example.pdfgen.model;

import java.util.List;

/**
 * 口算题卡生成请求。
 * 固定 seed 可复现同一份题（用于 PDF 缓存）。
 * knowledgePoints 支持多个知识点混合出题（如 加法+减法 混合练习）。
 */
public record OralMathRequest(
        List<KnowledgePoint> knowledgePoints,
        Integer count,
        Integer cols,
        Long seed,
        Boolean withAnswer,
        Integer day,
        String font) {
}
