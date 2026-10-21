package com.myblog.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.myblog.service.RebuildService;
import com.myblog.service.SeriesService;
import com.myblog.service.SeriesService.SeriesOpResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 系列管理接口（表现层）。
//
// 职责：HTTP 请求映射、参数提取、响应封装、重建编排；业务逻辑全部委托给 SeriesService。
//
// 所有写操作在改完 conf/note 后都会触发一次站点重建。
@RestController
@LoginRequired
public class SeriesController {

    private final SeriesService series;
    private final RebuildService rebuild;

    public SeriesController(SeriesService series, RebuildService rebuild) {
        this.series = series;
        this.rebuild = rebuild;
    }

    // 返回所有系列及其文章列表（管理后台用）。
    @GetMapping("/api/articles")
    public ResponseEntity<Map<String, Map<String, Object>>> listArticles() {
        return ResponseEntity.ok(series.getArticleList());
    }

    // 返回所有系列的标识与标题，供上传时选择分类。
    @GetMapping("/api/categories")
    public ResponseEntity<List<Map<String, String>>> listCategories() {
        List<Map<String, String>> body = new ArrayList<>();
        series.getAllSeries().forEach((key, info) -> body.add(Map.of("key", key, "title", info.title())));
        return ResponseEntity.ok(body);
    }

    // 创建新笔记系列，随后重建站点。
    @PostMapping("/api/series")
    public ResponseEntity<Map<String, Object>> createSeries(@RequestBody Map<String, String> body) {
        String name = trim(body.get("name"));
        String desc = trim(body.get("desc"));

        SeriesOpResult result = series.createSeries(name, desc);
        if (!result.success()) {
            return Api.error(result.status(), result.message());
        }
        // 此处不反映重建结果，固定返回成功（前端只关心系列是否创建成功）
        rebuild.rebuild();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", result.message());
        resp.put("key", result.key());
        return ResponseEntity.ok(resp);
    }

    // 修改系列名称与描述，随后重建站点。
    @PutMapping("/api/series/{catKey}")
    public ResponseEntity<Map<String, Object>> editSeries(@PathVariable String catKey,
                                                          @RequestBody Map<String, String> body) {
        String newName = trim(body.get("name"));
        String newDesc = trim(body.get("desc"));

        SeriesOpResult result = series.editSeries(catKey, newName, newDesc);
        if (!result.success()) {
            return Api.error(result.status(), result.message());
        }
        return ResponseEntity.ok(Api.result(rebuild.rebuild()));
    }

    // 按传入的 keys 顺序重排系列，随后重建站点。
    @PutMapping("/api/series/reorder")
    public ResponseEntity<Map<String, Object>> reorderSeries(@RequestBody Map<String, Object> body) {
        Object raw = body.get("keys");
        if (!(raw instanceof List<?> keys) || keys.isEmpty()) {
            return Api.error(400, "缺少 keys 参数");
        }
        List<String> keyList = keys.stream().map(String::valueOf).toList();
        series.reorderSeries(keyList);
        return ResponseEntity.ok(Api.result(rebuild.rebuild()));
    }

    // 删除笔记系列，随后重建站点。
    @DeleteMapping("/api/series/{catKey}")
    public ResponseEntity<Map<String, Object>> deleteSeries(@PathVariable String catKey) {
        SeriesOpResult result = series.deleteSeries(catKey);
        if (!result.success()) {
            return Api.error(result.status(), result.message());
        }
        return ResponseEntity.ok(Api.result(rebuild.rebuild()));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
