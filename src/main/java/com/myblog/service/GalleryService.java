package com.myblog.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.myblog.config.BlogProperties;
import com.myblog.model.GalleryImage;
import com.myblog.repository.JsonStorageRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 相册业务层：处理图片上传、删除与排序。
//
// 三层架构中的位置：Controller → Service → Repository/文件系统。
@Service
public class GalleryService {

    // 允许上传的相册图片扩展名
    private static final List<String> ALLOWED_IMAGE_EXTS = List.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final BlogProperties props;
    private final JsonStorageRepository storage;

    public GalleryService(BlogProperties props, JsonStorageRepository storage) {
        this.props = props;
        this.storage = storage;
    }

    // 上传结果：已保存的文件名列表。
    public record UploadResult(List<String> uploaded) {}

    // 上传相册图片（支持多选）：以上传时间命名（仅保留原扩展名），同一秒内冲突时追加计数。
    //
    // 先统一校验格式，避免部分保存后失败；图片格式不在允许列表中时抛 IllegalArgumentException。
    public UploadResult uploadImages(List<MultipartFile> images, String alt) {
        // 统一校验格式
        List<String> exts = new ArrayList<>();
        for (MultipartFile f : images) {
            String ext = extensionOf(f.getOriginalFilename()).toLowerCase(Locale.ROOT);
            if (!ALLOWED_IMAGE_EXTS.contains(ext)) {
                throw new IllegalArgumentException("不支持的图片格式: " + f.getOriginalFilename());
            }
            exts.add(ext);
        }

        Path galleryDir = props.galleryImageDir();
        createDirectories(galleryDir);
        List<GalleryImage> gallery = new ArrayList<>(storage.loadGallery());
        String trimmedAlt = alt.trim();
        List<String> uploaded = new ArrayList<>();

        for (int idx = 0; idx < images.size(); idx++) {
            // 以上传时间命名：YYYYMMDD_HHMMSS，同一秒内冲突时追加计数
            String base = LocalDateTime.now().format(FILE_STAMP);
            String finalName = base + exts.get(idx);
            int counter = 1;
            while (Files.exists(galleryDir.resolve(finalName))) {
                finalName = base + "_" + counter + exts.get(idx);
                counter++;
            }
            write(galleryDir.resolve(finalName), read(images.get(idx)));
            gallery.add(new GalleryImage(System.currentTimeMillis() + "_" + idx, finalName,
                    trimmedAlt.isEmpty() ? finalName : trimmedAlt));
            uploaded.add(finalName);
        }
        storage.saveGallery(gallery);
        return new UploadResult(uploaded);
    }

    // 按 id 删除相册图片：同时删除磁盘文件与元数据。
    //
    // 找到并删除返回 true，图片不存在返回 false。
    public boolean deleteImage(String imgId) {
        List<GalleryImage> images = storage.loadGallery();
        GalleryImage target = images.stream()
                .filter(img -> imgId.equals(img.getId()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return false;
        }
        deleteIfExists(props.galleryImageDir().resolve(target.getFilename()));
        storage.saveGallery(images.stream().filter(img -> !imgId.equals(img.getId())).toList());
        return true;
    }

    // 按传入的 ids 顺序重排相册图片。
    public void reorderImages(List<String> ids) {
        Map<String, GalleryImage> byId = new LinkedHashMap<>();
        for (GalleryImage img : storage.loadGallery()) {
            byId.put(img.getId(), img);
        }
        List<GalleryImage> reordered = new ArrayList<>();
        for (String id : ids) {
            GalleryImage img = byId.get(id);
            if (img != null) {
                reordered.add(img);
            }
        }
        storage.saveGallery(reordered);
    }

    // 取扩展名（含点），无扩展名时返回空串。
    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传文件失败", e);
        }
    }

    private static void write(Path path, byte[] data) {
        try {
            Files.write(path, data);
        } catch (IOException e) {
            throw new UncheckedIOException("写入文件失败: " + path, e);
        }
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException("删除失败: " + path, e);
        }
    }

    private static void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
