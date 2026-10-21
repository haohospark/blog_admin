package com.myblog.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.myblog.service.SeriesService.SeriesInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// 文章业务层：处理笔记上传与删除的文件系统操作。
//
// Controller 负责 HTTP 映射与重建编排，本类只关心文件读写与命名规则。
//
// 三层架构中的位置：Controller → Service → Repository/文件系统。
@Service
public class ArticleService {

    // Windows/Unix 路径分隔符与非法文件名字符
    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[<>:\"/\\\\|?*]");

    private final SeriesService series;

    public ArticleService(SeriesService series) {
        this.series = series;
    }

    // 上传结果：已保存的 Markdown 文件名列表与图片数量。
    public record UploadResult(List<String> uploadedMd, int uploadedImages) {}

    // 保存上传的 Markdown 笔记与配图到系列目录。
    //
    // Markdown 以正文一级标题命名（无标题时沿用原文件名，重名追加 _1、_2），
    // 并按上传先后写入递增的修改时间，供列表与构建按上传顺序排列。
    public UploadResult uploadArticles(SeriesInfo info, List<MultipartFile> mdFiles, List<MultipartFile> imgFiles) {
        Path src = info.src();
        Path imgDir = src.resolve(".image");
        createDirectories(src);
        createDirectories(imgDir);

        Set<String> usedSlugs = series.usedSlugs(src);
        List<String> savedMd = new ArrayList<>();
        // 同一批次内文件系统时间戳精度不足会出现相同值，按序号递增写入以保证上传顺序稳定
        long baseMillis = System.currentTimeMillis();

        for (int index = 0; index < mdFiles.size(); index++) {
            MultipartFile file = mdFiles.get(index);
            String original = file.getOriginalFilename();
            if (original == null || !original.endsWith(".md")) {
                continue;
            }
            // 浏览器提交的原始文件名去路径后，把非法字符替换为下划线
            String sanitized = ILLEGAL_CHARS.matcher(baseName(original)).replaceAll("_");
            byte[] data = read(file);
            String fallback = SeriesService.stripExtension(sanitized);
            String h1 = SeriesService.h1FromText(new String(data, StandardCharsets.UTF_8));
            String base = SeriesService.sanitizeFilename(h1 != null ? h1 : fallback, fallback);
            String finalName = series.resolveUploadName(src, base, ".md", usedSlugs);

            Path path = src.resolve(finalName);
            write(path, data);
            setLastModified(path, FileTime.from(Instant.ofEpochMilli(baseMillis + index)));
            savedMd.add(finalName);
        }

        for (MultipartFile file : imgFiles) {
            String original = file.getOriginalFilename();
            if (original == null) {
                continue;
            }
            String fname = baseName(original);
            if (fname.isEmpty() || fname.startsWith(".")) {
                continue;
            }
            write(imgDir.resolve(ILLEGAL_CHARS.matcher(fname).replaceAll("_")), read(file));
        }

        return new UploadResult(savedMd, imgFiles.size());
    }

    // 删除指定文章，可选同时删除其引用的图片。
    //
    // 先在系列根目录找，再找一级子目录；找到并删除返回 true，文章不存在返回 false。
    public boolean deleteArticle(SeriesInfo info, String slug, boolean deleteImages) {
        Path mdFile = findBySlug(info.src(), slug);
        if (mdFile == null) {
            return false;
        }

        if (deleteImages) {
            Set<String> refs = series.getImageRefs(mdFile);
            Path imgDir = mdFile.getParent().resolve(".image");
            delete(mdFile);
            if (!refs.isEmpty() && Files.isDirectory(imgDir)) {
                for (String name : refs) {
                    delete(imgDir.resolve(name));
                }
            }
        } else {
            delete(mdFile);
        }
        return true;
    }

    // 按 slug 查找文章：先搜索系列根目录，再搜索一级子目录。
    private Path findBySlug(Path src, String slug) {
        for (Path md : series.listMarkdown(src)) {
            if (SeriesService.makeSlug(SeriesService.stripExtension(md.getFileName().toString())).equals(slug)) {
                return md;
            }
        }
        for (Path sub : series.listSubDirs(src)) {
            for (Path md : series.listMarkdown(sub)) {
                if (SeriesService.makeSlug(SeriesService.stripExtension(md.getFileName().toString())).equals(slug)) {
                    return md;
                }
            }
        }
        return null;
    }

    // 浏览器提交的可能是完整路径，取最后一段文件名。
    private static String baseName(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return slash >= 0 ? filename.substring(slash + 1) : filename;
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

    private static void setLastModified(Path path, FileTime stamp) {
        try {
            Files.setLastModifiedTime(path, stamp);
        } catch (IOException e) {
            throw new UncheckedIOException("设置修改时间失败: " + path, e);
        }
    }

    private static void delete(Path path) {
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
