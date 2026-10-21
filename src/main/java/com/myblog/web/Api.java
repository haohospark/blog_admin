package com.myblog.web;

import org.springframework.http.ResponseEntity;
import com.myblog.service.RebuildService;

import java.util.LinkedHashMap;
import java.util.Map;

// 接口响应的统一构造：错误用 {"error": ...}，操作用 {"success": ..., "message": ...}
// （前端 admin.html 按这些字段判断）。
final class Api {

    private Api() { }

    static ResponseEntity<Map<String, Object>> error(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }

    static Map<String, Object> result(boolean success, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", success);
        body.put("message", message);
        return body;
    }

    static Map<String, Object> result(RebuildService.Result rebuild) {
        return result(rebuild.success(), rebuild.message());
    }
}
