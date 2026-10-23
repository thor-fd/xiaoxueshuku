package com.example.pdfgen.controller;

import com.example.pdfgen.service.WordBookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 管理接口。鉴权很简单：请求头 X-Admin-Token 需与配置 admin.token 一致。
 * reload 只重新读取服务器本地 data/ 目录，外部无法借此注入数据，token 泄露影响有限，
 * 但部署后仍建议改成随机值。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final WordBookService wordBookService;
    private final String adminToken;

    public AdminController(WordBookService wordBookService,
                           @Value("${admin.token:}") String adminToken) {
        this.wordBookService = wordBookService;
        this.adminToken = adminToken;
    }

    /** 热更新生字表：改完 Excel → rsync 到 data/ → 调用本接口，无需重启 */
    @PostMapping(value = "/reload", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reload(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (adminToken.isBlank() || !adminToken.equals(token)) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("X-Admin-Token 缺失或不匹配".getBytes(StandardCharsets.UTF_8));
        }
        try {
            wordBookService.reload();
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("重新加载失败，服务仍使用旧数据: " + e.getMessage())
                            .getBytes(StandardCharsets.UTF_8));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("status", "ok", "catalog", wordBookService.catalog()));
    }
}
