package com.myblog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.myblog.config.BlogProperties;
import com.myblog.model.SeriesMeta;
import com.myblog.repository.JsonStorageRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesServiceTest {

    @TempDir
    Path tmp;

    BlogProperties props;
    JsonStorageRepository storage;
    SeriesService series;

    // 笔记按修改时间排序，写入时递增序号模拟上传先后
    private int uploadCounter = 1;

    @BeforeEach
    void setUp() throws IOException {
        props = new BlogProperties(tmp, null, null, null, "admin", "123456");
        storage = new JsonStorageRepository(props, new ObjectMapper());
        storage.ensureDirs();
        Files.createDirectories(props.noteDir());
        series = new SeriesService(props, storage);
    }

    @Test
    void slugKeepsChineseAndStripsNumberPrefix() {
        assertEquals("课程笔记", SeriesService.makeSlug("01_课程笔记"));
        assertEquals("hello_world", SeriesService.makeSlug("Hello World!"));
        assertEquals("a_b_c", SeriesService.makeSlug("a-b  c"));
        assertEquals("笔记", SeriesService.makeSlug("__笔记__"));
    }

    @Test
    void sanitizeFilenameReplacesIllegalCharsAndTruncates() {
        assertEquals("标题_内容_", SeriesService.sanitizeFilename("标题:内容?", "fallback"));
        assertEquals("fallback", SeriesService.sanitizeFilename("   ", "fallback"));
        assertEquals("fallback", SeriesService.sanitizeFilename("...", "fallback"));
        String longTitle = "a".repeat(100);
        assertEquals(80, SeriesService.sanitizeFilename(longTitle, "fallback").length());
    }

    @Test
    void h1ExtractedFromMarkdown() {
        assertEquals("第一篇文章", SeriesService.h1FromText("# 第一篇文章\n\n正文"));
        assertNull(SeriesService.h1FromText("## 只有二级标题"));
    }

    @Test
    void imageRefsFromBothSyntaxes() throws IOException {
        Path file = props.noteDir().resolve("t.md");
        Files.writeString(file, """
                ![图片](.image/a.png)
                <img src=".image/b.jpg" alt="x">
                普通文本 https://example.com/c.png
                """, StandardCharsets.UTF_8);
        assertTrue(series.getImageRefs(file).containsAll(List.of("a.png", "b.jpg")));
        assertEquals(2, series.getImageRefs(file).size());
    }

    @Test
    void resolveUploadNameAvoidsSlugCollision() {
        Path dir = props.noteDir();
        var used = new LinkedHashSet<>(List.of("课程笔记"));
        assertEquals("课程笔记_1.md", series.resolveUploadName(dir, "课程笔记", ".md", used));
        assertEquals("01_课程笔记_2.md", series.resolveUploadName(dir, "01_课程笔记", ".md", used));
    }

    @Test
    void articleListOrderedByUploadTime() throws IOException {
        Path src = Files.createDirectories(props.noteDir().resolve("课程"));
        writeNote(src.resolve("01_第一节.md"), "# 第一节");
        writeNote(src.resolve("02_第二节.md"), "# 第二节");
        writeNote(src.resolve("子目录.md"), "# 子目录");
        Path sub = Files.createDirectories(src.resolve("进阶"));
        writeNote(sub.resolve("03_第三节.md"), "# 第三节");

        LinkedHashMap<String, SeriesMeta> meta = new LinkedHashMap<>();
        SeriesMeta m = new SeriesMeta();
        m.setTitle("课程");
        m.setSrcDir("课程");
        m.setDynamic(true);
        m.setOrder(1);
        meta.put("课程", m);
        storage.saveMeta(meta);

        Map<String, Object> result = series.getArticleList().get("课程");
        assertEquals(4, result.get("count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> articles = (List<Map<String, Object>>) result.get("articles");
        assertEquals(List.of("第一节", "第二节", "子目录", "第三节"),
                articles.stream().map(a -> a.get("title")).toList());
        assertNull(articles.get(0).get("sub_dir"));
        assertEquals("进阶", articles.get(3).get("sub_dir"));
    }

    @Test
    void hiddenDirsSkippedAndSubDirsSorted() throws IOException {
        Path src = Files.createDirectories(props.noteDir().resolve("课程"));
        Files.createDirectories(src.resolve(".image"));
        Files.createDirectories(src.resolve("b"));
        Files.createDirectories(src.resolve("a"));
        assertEquals(List.of("a", "b"),
                series.listSubDirs(src).stream().map(p -> p.getFileName().toString()).toList());
    }

    @Test
    void seriesSortedByOrderAndDedupedBySrcDir() throws IOException {
        Files.createDirectories(props.noteDir().resolve("共用目录"));
        LinkedHashMap<String, SeriesMeta> meta = new LinkedHashMap<>();
        meta.put("late", metaEntry("后系列", "共用目录", 2, true));
        meta.put("early", metaEntry("先系列", "共用目录", 1, true));
        meta.put("hidden", metaEntry("非动态", "不存在", 3, false));
        storage.saveMeta(meta);

        List<String> keys = new ArrayList<>(series.getAllSeries().keySet());
        // 同一目录被两个系列引用：后遍历到的 early 顶掉 late
        assertEquals(List.of("early"), keys);
        assertEquals(1, series.getSeriesByKey("early").order());
        assertNull(series.getSeriesByKey("late"));
        assertNull(series.getSeriesByKey("hidden"));
    }

    private SeriesMeta metaEntry(String title, String srcDir, int order, boolean dynamic) {
        SeriesMeta m = new SeriesMeta();
        m.setTitle(title);
        m.setSrcDir(srcDir);
        m.setOrder(order);
        m.setDynamic(dynamic);
        return m;
    }

    // 笔记按修改时间排序，写入时递增 mtime 模拟上传先后
    private void writeNote(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
        long base = 1_700_000_000_000L;
        Files.setLastModifiedTime(file, FileTime.from(Instant.ofEpochMilli(base + uploadCounter++ * 1000)));
    }
}
