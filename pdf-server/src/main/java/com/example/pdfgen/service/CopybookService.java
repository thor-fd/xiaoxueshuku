package com.example.pdfgen.service;

import com.example.pdfgen.model.CopybookFont;
import com.example.pdfgen.model.CopybookRequest;
import com.example.pdfgen.model.GridType;
import com.example.pdfgen.model.Word;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组装字帖模板数据（模板只接收 Map，规避 FTL 对 record 访问器的兼容问题）。
 * <p>
 * 返回「每页一个数据模型」的列表：openhtmltopdf 的分页器对跨页长表格、
 * 页间分页符、行内相对定位均有渲染缺陷，因此每一页单独渲染成单页 PDF
 * 再用 PDFBox 合并（见 {@link PdfRenderService#renderMerged}）。
 * 手动分页规则：首页 9 个字、后续页 10 个字，超出自动顺延并标注（续）。
 */
@Service
public class CopybookService {

    /** 首页（含标题/姓名栏）容量，单位=生字个数 */
    private static final int FIRST_PAGE_CAPACITY = 9;
    /** 后续页容量 */
    private static final int PAGE_CAPACITY = 10;

    private static final int TYPE1_TRACE = 3;
    private static final int TYPE1_BLANK = 7;
    private static final int TYPE2_TRACE = 2;
    private static final int TYPE2_BLANK = 8;

    private final GridImageService gridImageService;

    public CopybookService(GridImageService gridImageService) {
        this.gridImageService = gridImageService;
    }

    /** 直传生字列表的旧入口：包装成单个课时 */
    public List<Map<String, Object>> buildModel(CopybookRequest req) {
        GridType grid = req.gridType() == null ? GridType.TIAN : req.gridType();
        boolean withPinyin = req.withPinyin() == null || req.withPinyin();
        boolean traceDark = Boolean.TRUE.equals(req.traceDark());
        int traceCount = clamp(req.traceCount(), 3, 1, 9);
        int blankCount = clamp(req.blankCount(), 7, 1, 9);

        List<WordBookService.LessonData.Word> words = new ArrayList<>();
        if (req.words() != null) {
            for (Word w : req.words()) {
                // 直传数据也自动识别：纯字母 → 拼音格，否则按汉字处理
                boolean pinyin = WordBookService.isPinyin(w.character());
                words.add(new WordBookService.LessonData.Word(
                        pinyin ? "pinyin" : "hanzi",
                        w.character(),
                        pinyin ? "" : (w.pinyin() == null ? "" : w.pinyin())));
            }
        }
        String title = req.title() == null || req.title().isBlank() ? defaultTitle(grid) : req.title();

        List<SectionData> sections = List.of(new SectionData(
                "练字 · 描 " + traceCount + " 写 " + blankCount,
                words, traceCount, blankCount));
        return buildPages(List.of(new Lesson(title, sections)), grid, withPinyin, traceDark,
                CopybookFont.of(req.font()));
    }

    /**
     * 按课时（一类字/二类字分节）组装模板数据。
     * 一类字（会写）默认 描3写7，二类字（会认）默认 描2写8；课时标题只出现在该课第一页。
     */
    public List<Map<String, Object>> buildLessonsModel(String titlePrefix,
                                                       List<WordBookService.LessonData> lessons,
                                                       GridType grid,
                                                       boolean withPinyin,
                                                       boolean traceDark,
                                                       CopybookFont font) {
        List<Lesson> lessonsBlocks = new ArrayList<>();
        for (WordBookService.LessonData ld : lessons) {
            // 标题：前缀 · 单元 · 课时（前缀或单元为空则跳过对应段）
            StringBuilder titleBuilder = new StringBuilder();
            if (titlePrefix != null && !titlePrefix.isBlank()) {
                titleBuilder.append(titlePrefix).append(" · ");
            }
            if (ld.unit() != null && !ld.unit().isBlank()) {
                titleBuilder.append(ld.unit()).append(" · ");
            }
            titleBuilder.append(ld.lesson());
            String title = titleBuilder.toString();

            List<SectionData> sections = new ArrayList<>();
            if (!ld.type1().isEmpty()) {
                sections.add(new SectionData(
                        "一类字（会写）· 描 " + TYPE1_TRACE + " 写 " + TYPE1_BLANK,
                        ld.type1(), TYPE1_TRACE, TYPE1_BLANK));
            }
            if (!ld.type2().isEmpty()) {
                sections.add(new SectionData(
                        "二类字（会认）· 描 " + TYPE2_TRACE + " 写 " + TYPE2_BLANK,
                        ld.type2(), TYPE2_TRACE, TYPE2_BLANK));
            }
            lessonsBlocks.add(new Lesson(title, sections));
        }
        return buildPages(lessonsBlocks, grid, withPinyin, traceDark, font);
    }

    // ---------- 手动分页 + 每页模型 ----------

    private record Lesson(String title, List<SectionData> sections) {
    }

    private record SectionData(String label, List<WordBookService.LessonData.Word> words,
                               int trace, int blank) {
    }

    private List<Map<String, Object>> buildPages(List<Lesson> lessons, GridType grid,
                                                 boolean withPinyin, boolean traceDark,
                                                 CopybookFont font) {
        List<Map<String, Object>> pageModels = new ArrayList<>();
        for (Lesson lesson : lessons) {
            List<Map<String, Object>> pageSections = new ArrayList<>();
            int used = 0;
            int capacity = FIRST_PAGE_CAPACITY;
            String pendingTitle = lesson.title();

            for (SectionData sec : lesson.sections()) {
                boolean started = false;
                int idx = 0;
                while (idx < sec.words().size()) {
                    if (used >= capacity) {
                        pageModels.add(pageModel(pendingTitle, pageSections,
                                grid, withPinyin, traceDark, font));
                        pageSections = new ArrayList<>();
                        pendingTitle = null;
                        used = 0;
                        capacity = PAGE_CAPACITY;
                    }
                    int take = Math.min(capacity - used, sec.words().size() - idx);
                    String label = sec.label() + (started ? "（续）" : "");
                    pageSections.add(section(label,
                            sec.words().subList(idx, idx + take), sec.trace(), sec.blank(), grid));
                    used += take;
                    idx += take;
                    started = true;
                }
            }
            if (!pageSections.isEmpty()) {
                pageModels.add(pageModel(pendingTitle, pageSections, grid, withPinyin, traceDark, font));
            }
        }
        return pageModels;
    }

    private Map<String, Object> pageModel(String title, List<Map<String, Object>> sections,
                                          GridType grid, boolean withPinyin, boolean traceDark,
                                          CopybookFont font) {
        Map<String, Object> model = new HashMap<>();
        model.put("title", title);
        model.put("sections", sections);
        model.put("gridBg", gridImageService.dataUri(grid));
        model.put("pinyinGridBg", gridImageService.pinyinDataUri());
        model.put("traceColor", traceDark ? "#C9C9C9" : "#DCDCDC");
        model.put("withPinyin", withPinyin);
        model.put("fontFamily", font.family);
        return model;
    }

    private Map<String, Object> section(String label, List<WordBookService.LessonData.Word> words,
                                        int trace, int blank, GridType grid) {
        List<Map<String, Object>> ws = new ArrayList<>();
        for (WordBookService.LessonData.Word w : words) {
            Map<String, Object> m = new HashMap<>();
            m.put("type", w.type());
            m.put("char", w.character());
            m.put("pinyin", w.pinyin() == null ? "" : w.pinyin());
            // 汉字 → 田/米/方格，拼音 → 四线三格
            m.put("bg", "pinyin".equals(w.type())
                    ? gridImageService.pinyinDataUri()
                    : gridImageService.dataUri(grid));
            ws.add(m);
        }
        Map<String, Object> sec = new HashMap<>();
        sec.put("label", label);
        sec.put("words", ws);
        sec.put("trace", trace);
        sec.put("blank", blank);
        sec.put("total", trace + blank);
        return sec;
    }

    private int clamp(Integer value, int defaultValue, int min, int max) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(min, Math.min(value, max));
    }

    private String defaultTitle(GridType grid) {
        return switch (grid) {
            case TIAN -> "田字格练字帖";
            case MI -> "米字格练字帖";
            case FANG -> "方格练字帖";
        };
    }
}
