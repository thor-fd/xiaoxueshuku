package com.example.pdfgen.controller;

import com.example.pdfgen.model.CopybookFont;
import com.example.pdfgen.model.CopybookRequest;
import com.example.pdfgen.model.GridType;
import com.example.pdfgen.model.KnowledgePoint;
import com.example.pdfgen.model.OralMathRequest;
import com.example.pdfgen.model.Word;
import com.example.pdfgen.service.CopybookService;
import com.example.pdfgen.service.OralMathService;
import com.example.pdfgen.service.PdfRenderService;
import com.example.pdfgen.service.WordBookService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class GenerateController {

    private static final String DEFAULT_SUBJECT = "语文";
    private static final String DEFAULT_VOLUME = "一年级上";

    /** 数学史册 -> 知识点（程序即时出题，无需数据文件；按课标进度映射年级） */
    private static final Map<String, List<String>> MATH_CATALOG = new LinkedHashMap<>();
    static {
        MATH_CATALOG.put("一年级上", List.of("10以内加法", "10以内减法"));
        MATH_CATALOG.put("一年级下", List.of("20以内进位加法", "20以内退位减法"));
        MATH_CATALOG.put("二年级上", List.of("表内乘法"));
    }

    private final PdfRenderService renderer;
    private final CopybookService copybookService;
    private final OralMathService oralMathService;
    private final WordBookService wordBookService;

    public GenerateController(PdfRenderService renderer,
                              CopybookService copybookService,
                              OralMathService oralMathService,
                              WordBookService wordBookService) {
        this.renderer = renderer;
        this.copybookService = copybookService;
        this.oralMathService = oralMathService;
        this.wordBookService = wordBookService;
    }

    // ---------- H5 页面接口 ----------

    /** 学科 -> 年级册 目录（H5 首屏下拉）；数学为程序生成的口算题库 */
    @GetMapping("/h5/meta")
    public Map<String, Object> h5Meta() {
        Map<String, List<String>> catalog = new LinkedHashMap<>(wordBookService.catalog());
        catalog.merge("数学", new ArrayList<>(MATH_CATALOG.keySet()), (fileVolumes, mathVolumes) -> {
            LinkedHashSet<String> merged = new LinkedHashSet<>(fileVolumes);
            merged.addAll(mathVolumes);
            return new ArrayList<>(merged);
        });
        return Map.of("subjects", catalog);
    }

    /** 某年级册下的课时/知识点列表（含单元，前端按单元分组展示） */
    @GetMapping("/h5/lessons")
    public List<Map<String, String>> h5Lessons(
            @RequestParam(defaultValue = DEFAULT_SUBJECT) String subject,
            @RequestParam(defaultValue = DEFAULT_VOLUME) String volume) {
        // 数学的知识点来自程序生成器（若 data/数学.xlsx 有数据则优先）
        if ("数学".equals(subject) && wordBookService.lessons(subject, volume).isEmpty()
                && MATH_CATALOG.containsKey(volume)) {
            return MATH_CATALOG.get(volume).stream()
                    .map(label -> Map.of("unit", "口算", "lesson", label))
                    .toList();
        }
        return wordBookService.lessonRows(subject, volume).stream()
                .map(ld -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("unit", ld.unit() == null ? "" : ld.unit());
                    row.put("lesson", ld.lesson());
                    return row;
                })
                .toList();
    }

    // ---------- 统一生成入口（H5/小程序）：语文=字帖，数学=口算 ----------

    @GetMapping(value = "/generate/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generatePdf(
            @RequestParam(defaultValue = DEFAULT_SUBJECT) String subject,
            @RequestParam String volume,
            @RequestParam String lesson,
            @RequestParam(defaultValue = "WENKAI") String font,
            @RequestParam(defaultValue = "32") int count,
            @RequestParam(defaultValue = "TIAN") GridType gridType,
            @RequestParam(defaultValue = "true") boolean withPinyin,
            @RequestParam(defaultValue = "false") boolean traceDark) {
        if ("数学".equals(subject)) {
            KnowledgePoint kp = KnowledgePoint.byLabel(lesson);
            byte[] body = renderer.render("oral_math.ftl", oralMathService.buildModel(
                    new OralMathRequest(kp, Math.max(4, Math.min(count, 100)), 4,
                            null, true, null, font)));
            return pdf(volume + "-" + lesson + "-口算.pdf", body, true);
        }
        // 语文 → 字帖
        if (wordBookService.isEmpty()) {
            return plainText(400, "未找到生字表数据：请把 Excel 放到 data/语文.xlsx"
                    + "（第1列年级、第2列单元、第3列课时、第4列一类字、第5列二类字）");
        }
        List<WordBookService.LessonData> lessons =
                List.of(wordBookService.lesson(subject, volume, lesson));
        byte[] body = renderer.renderMerged("copybook.ftl",
                copybookService.buildLessonsModel(volume, lessons, gridType, withPinyin,
                        traceDark, CopybookFont.of(font)));
        return pdf(lesson + "-字帖.pdf", body, true);
    }

    // ---------- 字帖 ----------
    /** H5 生成下载入口：读 data/ 生字表，一类字/二类字分节，标题带课时 */
    @GetMapping(value = "/generate/copybook/excel", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> fromExcel(
            @RequestParam(defaultValue = DEFAULT_SUBJECT) String subject,
            @RequestParam(defaultValue = DEFAULT_VOLUME) String volume,
            @RequestParam(required = false) String lesson,
            @RequestParam(required = false) String titlePrefix,
            @RequestParam(defaultValue = "TIAN") GridType gridType,
            @RequestParam(defaultValue = "true") boolean withPinyin,
            @RequestParam(defaultValue = "false") boolean traceDark,
            @RequestParam(defaultValue = "WENKAI") String font) {
        if (wordBookService.isEmpty()) {
            return plainText(400, "未找到生字表数据：请把 Excel 放到 data/学科/年级册.xlsx"
                    + "（第1列课时、第2列一类字、第3列二类字）");
        }
        List<WordBookService.LessonData> lessons = lesson == null || lesson.isBlank()
                ? wordBookService.lessons(subject, volume).stream()
                        .map(name -> wordBookService.lesson(subject, volume, name))
                        .toList()
                : List.of(wordBookService.lesson(subject, volume, lesson));
        byte[] body = renderer.renderMerged("copybook.ftl",
                copybookService.buildLessonsModel(titlePrefix, lessons, gridType, withPinyin,
                        traceDark, CopybookFont.of(font)));
        String name = (lesson == null || lesson.isBlank() ? volume : lesson) + "-字帖.pdf";
        return pdf(name, body, true);
    }

    /** 字帖生成（JSON 请求体，见 CopybookRequest） */
    @PostMapping(value = "/generate/copybook", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> copybook(@RequestBody CopybookRequest req) {
        return pdf("字帖.pdf", renderer.renderMerged("copybook.ftl", copybookService.buildModel(req)), false);
    }

    // ---------- 口算 ----------

    /** 口算题卡生成（JSON 请求体，见 OralMathRequest） */
    @PostMapping(value = "/generate/oral-math", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> oralMath(@RequestBody OralMathRequest req) {
        return pdf("口算练习.pdf", renderer.render("oral_math.ftl", oralMathService.buildModel(req)), false);
    }

    // ---------- 演示 ----------

    /** 演示：浏览器直接打开看效果（有生字表数据时取第一个课时） */
    @GetMapping(value = "/demo/copybook", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> demoCopybook() {
        if (!wordBookService.isEmpty()) {
            return fromExcel(DEFAULT_SUBJECT, firstVolume(), null, null,
                    GridType.TIAN, true, false, "WENKAI");
        }
        CopybookRequest req = new CopybookRequest(
                "一年级上册 · 识字表（一）", GridType.TIAN, true, false, 3, 7, "WENKAI",
                List.of(new Word("天", "tiān"), new Word("地", "dì"), new Word("人", "rén"),
                        new Word("你", "nǐ"), new Word("我", "wǒ"), new Word("他", "tā"),
                        new Word("一", "yī"), new Word("二", "èr"), new Word("三", "sān"),
                        new Word("四", "sì")));
        return copybook(req);
    }

    /** 演示：浏览器直接打开看效果 */
    @GetMapping(value = "/demo/oral-math", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> demoOralMath(
            @RequestParam(defaultValue = "ADD20CARRY") KnowledgePoint kp,
            @RequestParam(defaultValue = "32") int count,
            @RequestParam(defaultValue = "42") long seed) {
        return oralMath(new OralMathRequest(kp, count, 4, seed, true, null, "WENKAI"));
    }

    // ---------- 内部 ----------

    private String firstVolume() {
        List<String> volumes = wordBookService.volumes(DEFAULT_SUBJECT);
        return volumes.isEmpty() ? DEFAULT_VOLUME : volumes.get(0);
    }

    private ResponseEntity<byte[]> pdf(String filename, byte[] body, boolean attachment) {
        ContentDisposition disposition = (attachment ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    private ResponseEntity<byte[]> plainText(int status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(message.getBytes(StandardCharsets.UTF_8));
    }
}
