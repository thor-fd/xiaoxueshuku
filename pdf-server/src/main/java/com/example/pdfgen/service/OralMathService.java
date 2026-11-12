package com.example.pdfgen.service;

import com.example.pdfgen.model.CopybookFont;
import com.example.pdfgen.model.KnowledgePoint;
import com.example.pdfgen.model.OralMathRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 口算题生成：规则引擎随机出题（不存题库）。
 * 固定 seed → 同参数结果可复现，是后续按参数哈希缓存 PDF 的基础。
 */
@Service
public class OralMathService {

    public Map<String, Object> buildModel(OralMathRequest req) {
        KnowledgePoint kp = req.knowledgePoint() == null ? KnowledgePoint.ADD10 : req.knowledgePoint();
        int count = clamp(req.count(), 32, 4, 100);
        int cols = clamp(req.cols(), 4, 2, 5);
        boolean withAnswer = req.withAnswer() == null || req.withAnswer();
        Random random = req.seed() == null ? new Random() : new Random(req.seed());

        List<Map<String, Object>> equations = generate(kp, count, random);

        List<List<Map<String, Object>>> rows = partition(equations, cols);
        List<List<Map<String, Object>>> answerRows = partition(equations.stream()
                .map(e -> {
                    Map<String, Object> copy = new LinkedHashMap<>(e);
                    copy.put("text", e.get("question") + " " + e.get("answer"));
                    return copy;
                })
                .toList(), cols);

        String title = "口算练习 · " + kp.label
                + (req.day() == null ? "" : "（第 " + req.day() + " 天）");

        Map<String, Object> model = new HashMap<>();
        model.put("title", title);
        model.put("rows", rows);
        model.put("withAnswer", withAnswer);
        model.put("answerRows", answerRows);
        model.put("fontFamily", CopybookFont.of(req.font()).family);
        return model;
    }

    private List<Map<String, Object>> generate(KnowledgePoint kp, int count, Random r) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int attempts = 0;
        while (result.size() < count && attempts < count * 50) {
            attempts++;
            int a;
            int b;
            int ans;
            String op;
            switch (kp) {
                // 10以内加法：和不超过 10，排除 0+0
                case ADD10 -> {
                    a = r.nextInt(11);
                    b = r.nextInt(11);
                    op = "+";
                    ans = a + b;
                    if (ans == 0 || ans > 10) {
                        continue;
                    }
                }
                // 10以内减法：差不为负
                case SUB10 -> {
                    a = 1 + r.nextInt(10);
                    b = r.nextInt(a + 1);
                    op = "-";
                    ans = a - b;
                }
                // 20以内进位加法：和≤20 且个位相加必须进位
                case ADD20CARRY -> {
                    a = 1 + r.nextInt(19);
                    b = 1 + r.nextInt(19);
                    op = "+";
                    ans = a + b;
                    if (ans > 20 || (a % 10) + (b % 10) < 10) {
                        continue;
                    }
                }
                // 20以内退位减法：个位不够减（a 个位 < b 个位）
                case SUB20BORROW -> {
                    a = 2 + r.nextInt(19);
                    b = 1 + r.nextInt(19);
                    op = "-";
                    ans = a - b;
                    if (b > a || (a % 10) >= (b % 10)) {
                        continue;
                    }
                }
                // 表内乘法：九九乘法表范围
                case MUL99 -> {
                    a = 1 + r.nextInt(9);
                    b = 1 + r.nextInt(9);
                    op = "×";
                    ans = a * b;
                }
                // 「运算 × 位数」自由组合（H5/小程序选数学时的 直接选择模式）
                case ADD1 -> {          // 一位数加法：a,b ∈ [1,9]
                    a = 1 + r.nextInt(9);
                    b = 1 + r.nextInt(9);
                    op = "+";
                    ans = a + b;
                }
                case ADD2 -> {          // 两位数加法（列竖式范围）
                    a = 10 + r.nextInt(90);
                    b = 10 + r.nextInt(90);
                    op = "+";
                    ans = a + b;
                }
                case SUB1 -> {          // 一位数减法：差 ≥ 0
                    a = 1 + r.nextInt(9);
                    b = 1 + r.nextInt(a);
                    op = "-";
                    ans = a - b;
                }
                case SUB2 -> {          // 两位数减法：差 ≥ 1
                    a = 11 + r.nextInt(89);
                    b = 1 + r.nextInt(a - 1);
                    op = "-";
                    ans = a - b;
                }
                case MUL1 -> {          // 一位数乘法：表内口诀
                    a = 1 + r.nextInt(9);
                    b = 1 + r.nextInt(9);
                    op = "×";
                    ans = a * b;
                }
                case MUL2 -> {          // 两位数乘法：两位数 × 一位数
                    a = 11 + r.nextInt(89);
                    b = 2 + r.nextInt(8);
                    op = "×";
                    ans = a * b;
                }
                case DIV1 -> {          // 一位数除法：被除数一位数、整除
                    ans = 1 + r.nextInt(9);
                    b = 2 + r.nextInt(8);
                    if (b * ans > 9) {
                        continue;
                    }
                    a = b * ans;
                    op = "÷";
                }
                case DIV2 -> {          // 两位数除法：两位数 ÷ 一位数、整除
                    ans = 1 + r.nextInt(49);
                    b = 2 + r.nextInt(8);
                    a = b * ans;
                    if (a < 10 || a > 99) {
                        continue;
                    }
                    op = "÷";
                }
                default -> throw new IllegalStateException("未知知识点: " + kp);
            }
            String q = a + " " + op + " " + b;
            if (!seen.add(q)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("question", q + " =");
            m.put("answer", ans);
            result.add(m);
        }
        return result;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> rows = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            rows.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return rows;
    }

    private int clamp(Integer value, int defaultValue, int min, int max) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(min, Math.min(value, max));
    }
}
