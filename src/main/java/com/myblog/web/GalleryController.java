package com.myblog.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.myblog.model.GalleryImage;
import com.myblog.repository.JsonStorageRepository;
import com.myblog.service.GalleryService;
import com.myblog.service.RebuildService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 相册管理接口（表现层）。
//
// 职责：HTTP 请求映射、参数校验、响应封装、重建编排；
// 图片存储与元数据管理委托给 GalleryService。
@RestController
@LoginRequired
public class GalleryController {

    private final GalleryService galleryService;
    private final JsonStorageRepository storage;
    private final RebuildService rebuild;

    public GalleryController(GalleryService galleryService, JsonStorageRepository storage, RebuildService rebuild) {
        this.galleryService = galleryService;
        this.storage = storage;
        this.rebuild = rebuild;
    }

    // 返回相册图片元数据列表。
    @GetMapping("/api/gallery")
    public ResponseEntity<List<GalleryImage>> getGallery() {
        return ResponseEntity.ok(storage.loadGallery());
    }

    // 上传相册图片（支持多选），随后重建站点。
    @PostMapping("/api/gallery/upload")
    public ResponseEntity<Map<String, Object>> uploadGallery(
            @RequestParam(name = "images", required = false) List<MultipartFile> images,
            @RequestParam(name = "alt", defaultValue = "") String altParam) {

        // 过滤空文件（HTTP 层关注点）
        List<MultipartFile> files = new ArrayList<>();
        if (images != null) {
            for (MultipartFile f : images) {
                if (f != null && f.getOriginalFilename() != null && !f.getOriginalFilename().isEmpty()) {
                    files.add(f);
                }
            }
        }
        if (files.isEmpty()) {
            return Api.error(400, "未选择图片");
        }

        GalleryService.UploadResult uploaded;
        try {
            uploaded = galleryService.uploadImages(files, altParam);
        } catch (IllegalArgumentException e) {
            return Api.error(400, e.getMessage());
        }

        RebuildService.Result result = rebuild.rebuild();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", result.success());
        body.put("message", result.success() ? "成功上传 " + uploaded.uploaded().size() + " 张图片" : result.message());
        body.put("uploaded", uploaded.uploaded());
        return ResponseEntity.ok(body);
    }

    // 按 id 删除相册图片，随后重建站点。
    @DeleteMapping("/api/gallery/{imgId}")
    public ResponseEntity<Map<String, Object>> deleteGalleryImage(@PathVariable String imgId) {
        if (!galleryService.deleteImage(imgId)) {
            return Api.error(404, "图片未找到");
        }
        return ResponseEntity.ok(Api.result(rebuild.rebuild()));
    }

    // 按传入的 ids 顺序重排相册图片，随后重建站点。
    @PutMapping("/api/gallery/reorder")
    public ResponseEntity<Map<String, Object>> reorderGallery(@RequestBody Map<String, Object> body) {
        if (!(body.get("ids") instanceof List<?> ids) || ids.isEmpty()) {
            return Api.error(400, "缺少 ids 参数");
        }
        List<String> idList = ids.stream().map(String::valueOf).toList();
        galleryService.reorderImages(idList);
        return ResponseEntity.ok(Api.result(rebuild.rebuild()));
    }
}
