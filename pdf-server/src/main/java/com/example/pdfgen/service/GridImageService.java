package com.example.pdfgen.service;

import com.example.pdfgen.model.GridType;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * 生成格线底图（田字格/米字格/方格），以 PNG data URI 嵌入模板。
 * <p>
 * 为什么用背景图而不是嵌套表格画虚线：OpenHTMLtoPDF 只支持 CSS 2.1，
 * 没有让文字叠加在表格线上方的手段；背景图方案下生字就是普通文字，天然居中叠加，
 * 且背景会作为页面内容完整印进 PDF（不存在浏览器"不打印背景"的问题）。
 */
@Service
public class GridImageService {

    private static final int SIZE = 512;
    private static final Color FRAME = new Color(0x33, 0x33, 0x33);
    private static final Color GUIDE = new Color(0xB8, 0xB8, 0xB8);

    private final Map<GridType, String> cache = new EnumMap<>(GridType.class);
    private String pinyinCache;

    /** 返回单元格背景图 data URI（结果缓存，进程内每类只画一次） */
    public synchronized String dataUri(GridType type) {
        return cache.computeIfAbsent(type, this::render);
    }

    /**
     * 四线三格（拼音书写格）：4 条通长横线、无竖线，相邻单元格拼成通栏。
     * 拼音主体写在中间格，上格容 ascender（t/l），下格容 descender（g/p/q/y）。
     */
    public synchronized String pinyinDataUri() {
        if (pinyinCache == null) {
            BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, SIZE, SIZE);
                g.setColor(FRAME);
                g.setStroke(new BasicStroke(5f));
                int inset = 5;
                int step = (SIZE - inset * 2) / 3;
                for (int i = 0; i <= 3; i++) {
                    int y = inset + step * i;
                    g.drawLine(0, y, SIZE, y);
                }
            } finally {
                g.dispose();
            }
            pinyinCache = toDataUri(img);
        }
        return pinyinCache;
    }

    private String render(GridType type) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, SIZE, SIZE);

            if (type == GridType.TIAN || type == GridType.MI) {
                g.setStroke(guideStroke());
                g.setColor(GUIDE);
                line(g, SIZE / 2f, 6, SIZE / 2f, SIZE - 6);
                line(g, 6, SIZE / 2f, SIZE - 6, SIZE / 2f);
            }
            if (type == GridType.MI) {
                g.setStroke(guideStroke());
                g.setColor(GUIDE);
                line(g, 10, 10, SIZE - 10, SIZE - 10);
                line(g, SIZE - 10, 10, 10, SIZE - 10);
            }

            g.setStroke(new BasicStroke(6f));
            g.setColor(FRAME);
            g.drawRect(4, 4, SIZE - 8, SIZE - 8);
        } finally {
            g.dispose();
        }
        return toDataUri(img);
    }

    /** 虚线：段长与间隔按 512px 画布调，缩印到 17mm 时每段约 1.5mm */
    private BasicStroke guideStroke() {
        return new BasicStroke(3.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{16f, 12f}, 0f);
    }

    private void line(Graphics2D g, float x1, float y1, float x2, float y2) {
        g.drawLine(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2));
    }

    private String toDataUri(BufferedImage img) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", os);
            return "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(os.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("生成格线底图失败", e);
        }
    }
}
