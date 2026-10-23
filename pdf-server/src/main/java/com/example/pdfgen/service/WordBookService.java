package com.example.pdfgen.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 生字表数据源：data/ 下每个学科一个文件（文件名 = 学科名），一个文件含所有年级。
 * <pre>
 * data/
 * ├── 语文.xlsx
 * ├── 数学.xlsx
 * └── 英语.xlsx
 * </pre>
 * 列约定（第 1 个 sheet，首行表头）：
 * <pre>
 * 第 1 列 = 年级（如「一年级上」「一年级下」，同一文件内多个年级）
 * 第 2 列 = 单元名称（如「第一单元·识字」，可为空；拼进 PDF 标题）
 * 第 3 列 = 课时信息（原样出现在 PDF 标题）
 * 第 4 列 = 一类字（会写）
 * 第 5 列 = 二类字（会认）
 * </pre>
 * 也支持同名 .csv（UTF-8、英文逗号）。拼音自动标注（pinyin4j，带声调）。
 */
@Service
public class WordBookService {

    private static final Path DATA_DIR = Path.of("data");

    /** subject -> volume(年级，如「一年级上」) -> (lesson -> data)，保持文件行序 */
    private final Map<String, Map<String, Map<String, LessonData>>> book = new LinkedHashMap<>();

    private final HanyuPinyinOutputFormat pinyinFormat = new HanyuPinyinOutputFormat();

    public record LessonData(String unit, String lesson, List<Word> type1, List<Word> type2) {
        /**
         * @param type hanzi=汉字（田字格+自动注音） | pinyin=拼音串（四线三格描红）
         */
        public record Word(String type, String character, String pinyin) {
        }
    }

    @PostConstruct
    void init() {
        pinyinFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        pinyinFormat.setToneType(HanyuPinyinToneType.WITH_TONE_MARK);
        pinyinFormat.setVCharType(HanyuPinyinVCharType.WITH_U_UNICODE);
        reload();
    }

    /**
     * 重新扫描 data/ 目录并替换内存数据（管理接口调用，无需重启服务）。
     * 先加载到临时 Map，成功才替换——加载失败时保留旧数据继续服务。
     */
    public synchronized void reload() {
        Map<String, Map<String, Map<String, LessonData>>> fresh = new LinkedHashMap<>();
        try {
            scanSubjectFiles(fresh);
        } catch (Exception e) {
            throw new IllegalStateException("生字表数据加载失败（保留原数据）: " + e.getMessage(), e);
        }
        book.clear();
        book.putAll(fresh);
    }

    public boolean isEmpty() {
        return book.isEmpty();
    }

    /** 学科目录：subject -> 年级列表（H5 首屏下拉用） */
    public Map<String, List<String>> catalog() {
        Map<String, List<String>> catalog = new LinkedHashMap<>();
        book.forEach((subject, volumes) -> catalog.put(subject, new ArrayList<>(volumes.keySet())));
        return catalog;
    }

    public List<String> volumes(String subject) {
        return new ArrayList<>(book.getOrDefault(subject, Map.of()).keySet());
    }

    public List<String> lessons(String subject, String volume) {
        return new ArrayList<>(book.getOrDefault(subject, Map.of())
                .getOrDefault(volume, Map.of()).keySet());
    }

    /** 课时行（含单元），H5/小程序下拉展示用 */
    public List<LessonData> lessonRows(String subject, String volume) {
        return new ArrayList<>(book.getOrDefault(subject, Map.of())
                .getOrDefault(volume, Map.of()).values());
    }

    /** 按课时模糊匹配（如「口耳目」命中「识字3 口耳目」）；keyword 为空取第一课 */
    public LessonData lesson(String subject, String volume, String keyword) {
        Map<String, LessonData> lessons = book.getOrDefault(subject, Map.of())
                .getOrDefault(volume, Map.of());
        if (lessons.isEmpty()) {
            throw new IllegalArgumentException("生字表中不存在: " + subject + " / " + volume);
        }
        if (keyword == null || keyword.isBlank()) {
            return lessons.values().iterator().next();
        }
        return lessons.values().stream()
                .filter(l -> l.lesson().contains(keyword.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "在 " + volume + " 中找不到课时: " + keyword));
    }

    // ---------- 加载 ----------

    private void scanSubjectFiles(Map<String, Map<String, Map<String, LessonData>>> target) throws Exception {
        if (!Files.isDirectory(DATA_DIR)) {
            return;
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(DATA_DIR)) {
            files = list.filter(this::isDataFile).sorted().toList();
        }
        for (Path file : files) {
            String subject = stripExtension(file.getFileName().toString());
            Map<String, Map<String, LessonData>> volumes = new LinkedHashMap<>();
            if (file.toString().endsWith(".csv")) {
                loadCsv(file, volumes);
            } else {
                loadXlsx(file.toFile(), volumes);
            }
            if (!volumes.isEmpty()) {
                target.put(subject, volumes);
            }
        }
    }

    private boolean isDataFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".xlsx") || name.endsWith(".csv");
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private void loadXlsx(File file, Map<String, Map<String, LessonData>> volumes) throws Exception {
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            for (Row row : sheet) {
                addRow(cellText(fmt, row, 0), cellText(fmt, row, 1), cellText(fmt, row, 2),
                        cellText(fmt, row, 3), cellText(fmt, row, 4), volumes);
            }
        }
    }

    private void loadCsv(Path path, Map<String, Map<String, LessonData>> volumes) throws Exception {
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.startsWith("﻿")) {
                line = line.substring(1);
            }
            if (line.isBlank()) {
                continue;
            }
            String[] cols = line.split(",", -1);
            addRow(col(cols, 0), col(cols, 1), col(cols, 2), col(cols, 3), col(cols, 4), volumes);
        }
    }

    private String col(String[] cols, int idx) {
        return idx < cols.length ? cols[idx].trim() : "";
    }

    private void addRow(String grade, String unit, String lesson, String type1, String type2,
                        Map<String, Map<String, LessonData>> volumes) {
        if (grade.isBlank() || grade.equals("年级") || lesson.isBlank()) {
            return; // 空行或表头
        }
        volumes.computeIfAbsent(grade, k -> new LinkedHashMap<>())
                .put(lesson, new LessonData(unit, lesson, toWords(type1), toWords(type2)));
    }

    private String cellText(DataFormatter fmt, Row row, int idx) {
        Cell cell = row.getCell(idx);
        return cell == null ? "" : fmt.formatCellValue(cell).trim();
    }

    /** 拼音字母（含带调字符与 ü） */
    private static final String PINYIN_CHARS = "a-zA-Zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü";

    /** 判断一段文本是否为纯拼音（用于自动选择四线三格） */
    public static boolean isPinyin(String text) {
        return text != null && !text.isBlank()
                && text.matches("[" + PINYIN_CHARS + "]+");
    }

    /**
     * 单元格解析，自动识别汉字/拼音：
     * 「天、地、人」→ 3 个汉字；「tiān dì」→ 2 个拼音串；一个格子里混写则分别解析。
     */
    private List<LessonData.Word> toWords(String text) {
        List<LessonData.Word> words = new ArrayList<>();
        if (text == null) {
            return words;
        }
        for (String token : text.split("[\\s、，,;；/·.]+")) {
            if (token.isBlank()) {
                continue;
            }
            if (isPinyin(token)) {
                words.add(new LessonData.Word("pinyin", token.toLowerCase(), ""));
                continue;
            }
            int i = 0;
            while (i < token.length()) {
                int cp = token.codePointAt(i);
                if (cp >= 0x4E00 && cp <= 0x9FFF) {
                    String ch = new String(Character.toChars(cp));
                    words.add(new LessonData.Word("hanzi", ch, pinyinOf(ch.charAt(0))));
                }
                i += Character.charCount(cp);
            }
        }
        return words;
    }

    private String pinyinOf(char ch) {
        try {
            String[] result = PinyinHelper.toHanyuPinyinStringArray(ch, pinyinFormat);
            return result == null ? "" : result[0];
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            return "";
        }
    }
}
