package com.example.pdfgen.service;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import jakarta.annotation.PostConstruct;
import com.example.pdfgen.model.CopybookFont;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板渲染管线：Freemarker(.ftl) → HTML → OpenHTMLtoPDF → PDF。
 * <p>
 * 字体全部为免费可商用字体（见 {@link CopybookFont}），启动时统一注册，
 * 模板通过模型里的 fontFamily（= CopybookFont.family 别名）选择；
 * 不要引入微软/方正等商业字体，有版权风险。
 */
@Service
public class PdfRenderService {

    private Configuration freemarker;
    private final Map<String, File> fontFiles = new HashMap<>();

    @PostConstruct
    void init() throws Exception {
        freemarker = new Configuration(Configuration.VERSION_2_3_32);
        freemarker.setClassForTemplateLoading(PdfRenderService.class, "/templates");
        freemarker.setDefaultEncoding(StandardCharsets.UTF_8.name());
        freemarker.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        // 所有可选字体注册进渲染器（CSS font-family 用 CopybookFont.family 同名别名）
        for (CopybookFont font : CopybookFont.values()) {
            registerFont(font.family, font.classpathFile);
        }
    }

    private void registerFont(String family, String classpathFile) throws Exception {
        // classpath 里的字体拷贝到临时文件（jar 内资源无法直接以 File 打开，OpenHTMLtoPDF 需要 File）
        try (InputStream in = PdfRenderService.class.getResourceAsStream(classpathFile)) {
            if (in == null) {
                throw new IllegalStateException("缺少字体文件 " + classpathFile
                        + "，请下载后放入 src/main/resources/fonts/");
            }
            Path tmp = Files.createTempFile("font-", ".ttf");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            fontFiles.put(family, tmp.toFile());
        }
    }

    public byte[] render(String templateName, Map<String, Object> model) {
        try {
            Template template = freemarker.getTemplate(templateName);
            StringWriter html = new StringWriter();
            template.process(model, html);

            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            for (CopybookFont font : CopybookFont.values()) {
                builder.useFont(fontFiles.get(font.family), font.family);
            }
            builder.withHtmlContent(html.toString(), null);
            builder.toStream(pdf);
            builder.run();
            return pdf.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF 生成失败: " + templateName, e);
        }
    }

    /**
     * 逐页渲染再合并：模板数据为「每页一个模型」的列表。
     * 绕开 openhtmltopdf 的分页器缺陷（跨页长表格裁剪、分页符失效、
     * 行内相对定位跨页崩溃），每页内容控制在单页容量内，渲染后用 PDFBox 合并。
     */
    public byte[] renderMerged(String templateName, List<Map<String, Object>> pageModels) {
        try (PDDocument dest = new PDDocument()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            for (Map<String, Object> pageModel : pageModels) {
                byte[] part = render(templateName, pageModel);
                try (PDDocument doc = PDDocument.load(part)) {
                    merger.appendDocument(dest, doc);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            dest.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF 合并失败: " + templateName, e);
        }
    }
}
