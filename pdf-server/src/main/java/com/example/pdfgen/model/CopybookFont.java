package com.example.pdfgen.model;

/**
 * 字帖字体选项（全部为免费可商用字体，文件在 resources/fonts/）。
 * <p>
 * family 是注册进 OpenHTMLtoPDF 的别名（CSS font-family 用同名即可，
 * 与字体内部命名无关）。接收参数时大小写不敏感，也接受中文名。
 */
public enum CopybookFont {
    /** 霞鹜文楷（OFL），基于 Klee One，屏显友好 */
    WENKAI("LXGW WenKai", "霞鹜文楷", "/fonts/LXGWWenKai-Regular.ttf"),
    /** 汇迹正楷（免费商用），正楷风格更接近教材楷体 */
    HUIJI("HuiJi ZhengKai", "汇迹正楷", "/fonts/CooperZhengKai-1.1.ttf");

    public final String family;
    public final String label;
    public final String classpathFile;

    CopybookFont(String family, String label, String classpathFile) {
        this.family = family;
        this.label = label;
        this.classpathFile = classpathFile;
    }

    /** 解析参数：null/空 → 默认霞鹜文楷；大小写不敏感，接受中文名 */
    public static CopybookFont of(String value) {
        if (value == null || value.isBlank()) {
            return WENKAI;
        }
        String v = value.trim();
        for (CopybookFont f : values()) {
            if (f.name().equalsIgnoreCase(v) || f.label.equals(v) || f.family.equalsIgnoreCase(v)) {
                return f;
            }
        }
        return WENKAI;
    }
}
