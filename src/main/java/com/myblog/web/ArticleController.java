package com.myblog.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.myblog.service.ArticleService;
import com.myblog.service.RebuildService;
import com.myblog.service.SeriesService;
import com.myblog.service.SeriesService.SeriesInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 文章上传、删除与手动重建接口（表现层）。
//
// 职责：HTTP 请求映射、参数校验、响应封装、重建编排；
// 文件操作与命名规则委托给 ArticleService。
@RestController
@LoginRequired
public class ArticleController {

    private final SeriesService series;
    private final ArticleService articles;
    private final RebuildService rebuild;

    public ArticleController(SeriesService series, ArticleService articles, RebuildService rebuild) {
        this.series = series;
        this.articles = articles;
        this.rebuild = rebuild;
    }

    // 删除单篇文章（按 slug 匹配），可选同时删除其引用的图片，随后重建站点。
    @DeleteMapping("/api/articles/{catKey}/{slug}")
    public ResponseEntity<Map<String, Object>> deleteArticle(
            @PathVariable String catKey,
            @PathVariable String slug,
            @RequestParam(name = "delete_images", defaultValue = "false") String deleteImagesParam) {

        SeriesInfo info = series.getSeriesByKey(catKey);
        if (info == null) {
            return Api.error(400, "无效分类");
        }
        boolean deleteImages = "true".equals(deleteImagesParam);
        if (!articles.deleteArticle(info, slug, deleteImages)) {
            return Api.error(404, "文章未找到");
        }
        return ResponseEntity.ok(Api.result(rebuild.rebuild()));
    }

    // 上传笔记与配图，随后重建站点。
    @PostMapping("/api/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam(name = "category", defaultValue = "") String catKey,
            @RequestParam(name = "md_files", required = false) List<MultipartFile> mdFiles,
            @RequestParam(name = "img_files", required = false) List<MultipartFile> imgFiles) {

        SeriesInfo info = series.getSeriesByKey(catKey);
        if (info == null) {
            return Api.error(400, "无效分类");
        }
        List<MultipartFile> mds = mdFiles != null ? mdFiles : List.of();
        List<MultipartFile> imgs = imgFiles != null ? imgFiles : List.of();
        if (mds.isEmpty()) {
            return Api.error(400, "未选择 Markdown 文件");
        }

        ArticleService.UploadResult uploaded = articles.uploadArticles(info, mds, imgs);
        RebuildService.Result result = rebuild.rebuild();

        Map<String, Object> body = new LinkedHashMap<>(Api.result(result));
        body.put("uploaded_md", uploaded.uploadedMd());
        body.put("uploaded_images", uploaded.uploadedImages());
        return ResponseEntity.ok(body);
    }

    // 手动触发站点重建。
    @PostMapping("/api/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildSite() {
        return ResponseEntity.ok(Api.result(rebuild.rebuild()));
    }
}
