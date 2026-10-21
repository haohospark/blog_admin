package com.myblog.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.myblog.model.About;
import com.myblog.service.AboutService;
import com.myblog.service.RebuildService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// "关于我"页面接口（表现层）。
//
// 职责：HTTP 请求映射、参数提取、响应封装、重建编排；业务逻辑委托给 AboutService。
@RestController
@LoginRequired
public class AboutController {

    private final AboutService aboutService;
    private final RebuildService rebuild;

    public AboutController(AboutService aboutService, RebuildService rebuild) {
        this.aboutService = aboutService;
        this.rebuild = rebuild;
    }

    // 返回"关于我"页面数据。
    @GetMapping("/api/about")
    public ResponseEntity<About> getAbout() {
        return ResponseEntity.ok(aboutService.getAbout());
    }

    // 更新"关于我"的姓名与介绍文本，随后重建站点。
    @PutMapping("/api/about")
    public ResponseEntity<Map<String, Object>> updateAbout(@RequestBody Map<String, Object> body) {
        String name = trim(asString(body.get("name")));
        if (name.isEmpty()) {
            return Api.error(400, "姓名不能为空");
        }
        List<String> texts = new ArrayList<>();
        if (body.get("texts") instanceof List<?> raw) {
            for (Object item : raw) {
                String text = trim(asString(item));
                if (!text.isEmpty()) {
                    texts.add(text);
                }
            }
        }
        aboutService.updateAbout(name, texts);
        return ResponseEntity.ok(Api.result(rebuild.rebuild()));
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
