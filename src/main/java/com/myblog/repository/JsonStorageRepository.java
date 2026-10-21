package com.myblog.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.myblog.config.BlogProperties;
import com.myblog.model.About;
import com.myblog.model.GalleryImage;
import com.myblog.model.Message;
import com.myblog.model.SeriesMeta;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;

// 数据访问层：负责 conf/ 目录下各 JSON 配置文件的读写。
//
// 序列化风格：两空格缩进、保留中文（不转义）、null 字段省略、键顺序与写入顺序一致，
// 使 conf/ 下的 JSON 稳定可读、diff 友好。
//
// 三层架构中的位置：Controller → Service → Repository → 文件系统。
@Repository
public class JsonStorageRepository {

    // "关于我"页面默认数据
    private static final About DEFAULT_ABOUT = new About("云山蓝", List.of(
            "24级计算机科学与技术专业的学生，喜欢探索新鲜事物，热衷于分享自己的学习心得和生活感悟。",
            "擅长计算机技术，喜欢制作美观、交互友好的网页，享受代码带来的乐趣。",
            "生活中喜欢摄影、旅行、阅读，用文字和图片记录生活中的每一个美好瞬间。"
    ));

    private final BlogProperties props;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    public JsonStorageRepository(BlogProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.writer = mapper.copy().writer(pythonStylePrinter());
    }

    // JSON 美化输出器：两空格缩进、空容器输出 {} / []、冒号后空格。
    private static DefaultPrettyPrinter pythonStylePrinter() {
        Separators separators = Separators.createDefaultInstance()
                .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                .withObjectEmptySeparator("")
                .withArrayEmptySeparator("");
        DefaultIndenter indenter = new DefaultIndenter("  ", DefaultIndenter.SYS_LF);
        return new DefaultPrettyPrinter(separators)
                .withObjectIndenter(indenter)
                .withArrayIndenter(indenter);
    }

    // 启动时确保 conf/ 目录存在。
    @PostConstruct
    public void ensureDirs() {
        try {
            Files.createDirectories(props.confDir());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ==================== 系列元数据 ====================

    public LinkedHashMap<String, SeriesMeta> loadMeta() {
        return read(props.metaFile(), new TypeReference<LinkedHashMap<String, SeriesMeta>>() { }, LinkedHashMap::new);
    }

    public void saveMeta(LinkedHashMap<String, SeriesMeta> meta) {
        write(props.metaFile(), meta);
    }

    // ==================== 留言 ====================

    public List<Message> loadMessages() {
        return read(props.messagesFile(), new TypeReference<List<Message>>() { }, ArrayList::new);
    }

    public void saveMessages(List<Message> messages) {
        write(props.messagesFile(), messages);
    }

    // ==================== 关于我 ====================

    // 文件不存在时写入默认数据并返回。
    public About loadAbout() {
        if (Files.exists(props.aboutFile())) {
            return read(props.aboutFile(), new TypeReference<About>() { }, () -> DEFAULT_ABOUT);
        }
        saveAbout(DEFAULT_ABOUT);
        return DEFAULT_ABOUT;
    }

    public void saveAbout(About about) {
        write(props.aboutFile(), about);
    }

    // 构建时读取"关于我"数据：文件不存在时返回 null 且不写盘。
    public About readAboutOrNull() {
        return Files.exists(props.aboutFile())
                ? read(props.aboutFile(), new TypeReference<About>() { }, () -> null)
                : null;
    }

    // ==================== 相册 ====================

    public List<GalleryImage> loadGallery() {
        return read(props.galleryFile(), new TypeReference<List<GalleryImage>>() { }, ArrayList::new);
    }

    public void saveGallery(List<GalleryImage> images) {
        write(props.galleryFile(), images);
    }

    // ==================== 内部读写 ====================

    // 读取 JSON 文件：不存在或读取失败时按 fallback 兜底/报错。
    private <T> T read(Path file, TypeReference<T> type, Supplier<T> fallback) {
        if (!Files.exists(file)) {
            return fallback.get();
        }
        try {
            return mapper.readValue(Files.readString(file, StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new UncheckedIOException("读取配置失败: " + file, e);
        }
    }

    // 序列化为 JSON 并写盘。
    private void write(Path file, Object value) {
        try {
            Files.writeString(file, writer.writeValueAsString(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("写入配置失败: " + file, e);
        }
    }
}
