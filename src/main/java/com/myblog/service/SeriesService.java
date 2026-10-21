package com.myblog.service;

import org.springframework.stereotype.Service;
import com.myblog.config.BlogProperties;
import com.myblog.model.SeriesMeta;
import com.myblog.repository.JsonStorageRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// 系列业务层：笔记系列与文章的查询、解析、CRUD 操作。
//
// 三层架构中的位置：Controller → Service → Repository/文件系统。
@Service
public class SeriesService {

    // 运行时解析出的系列信息（src/dst 为绝对路径）
    public record SeriesInfo(String key, String title, String desc, Path src, Path dst, int order) { }

    // 文章条目，subDir 为 null 表示位于系列根目录
    public record ArticleEntry(String slug, String title, String filename, String subDir) { }

    // \w 需 Unicode 感知（含中文），故显式开启 UNICODE_CHARACTER_CLASS
    private static final Pattern NON_WORD = Pattern.compile("[^\\w]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern LEADING_DIGITS = Pattern.compile("^\\d+_", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ILLEGAL_FILENAME_CHARS = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1f]");
    private static final Pattern H1 = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern IMAGE_REF = Pattern.compile("(?:!\\[.*?\\]\\(|<img[^>]+src=[\"'])([^\"')\\s]+)");
    private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]+\\)");

    private final BlogProperties props;
    private final JsonStorageRepository storage;

    public SeriesService(BlogProperties props, JsonStorageRepository storage) {
        this.props = props;
        this.storage = storage;
    }

    // 汇总所有笔记系列：仅收录 meta 中 dynamic 为 true 且 note/ 目录存在的条目，
    // 同一目录被多个系列引用时后出现的覆盖先出现的，最终按 order 升序。
    public LinkedHashMap<String, SeriesInfo> getAllSeries() {
        LinkedHashMap<String, SeriesInfo> result = new LinkedHashMap<>();
        for (Map.Entry<String, SeriesMeta> entry : storage.loadMeta().entrySet()) {
            String key = entry.getKey();
            SeriesMeta metaEntry = entry.getValue();
            if (!Boolean.TRUE.equals(metaEntry.getDynamic())) {
                continue;
            }
            String srcDir = metaEntry.getSrcDir() != null ? metaEntry.getSrcDir() : key;
            Path src = props.noteDir().resolve(srcDir);
            if (!Files.isDirectory(src)) {
                continue;
            }
            Path srcNorm = src.normalize();
            result.entrySet().removeIf(e -> e.getValue().src().normalize().equals(srcNorm));
            result.put(key, new SeriesInfo(
                    key,
                    metaEntry.getTitle() != null ? metaEntry.getTitle() : key,
                    metaEntry.getDesc() != null ? metaEntry.getDesc() : "",
                    src,
                    props.articlesDir().resolve(key),
                    metaEntry.getOrder() != null ? metaEntry.getOrder() : 999
            ));
        }
        return result.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().order()))
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);
    }

    // 按标识查找系列，不存在时返回 null。
    public SeriesInfo getSeriesByKey(String key) {
        return getAllSeries().get(key);
    }

    // 由 Markdown 文件名生成 URL 友好的 slug：非单词字符转下划线、转小写、去掉数字前缀。
    public static String makeSlug(String filename) {
        String slug = NON_WORD.matcher(filename).replaceAll("_").strip();
        while (slug.startsWith("_")) slug = slug.substring(1);
        while (slug.endsWith("_")) slug = slug.substring(0, slug.length() - 1);
        return LEADING_DIGITS.matcher(slug.toLowerCase(Locale.ROOT)).replaceFirst("");
    }

    // 由系列名生成系列标识（与 slug 规则一致）。
    public static String makeSeriesKey(String name) {
        return makeSlug(name);
    }

    // 从 Markdown 文本中提取一级标题，不存在时返回 null。
    public static String h1FromText(String content) {
        Matcher m = H1.matcher(content);
        return m.find() ? m.group(1).trim() : null;
    }

    // 读取 Markdown 文件的一级标题作为文章标题，无标题时回退为文件名（不含扩展名）。
    public String readTitle(Path mdFile) {
        String content = readString(mdFile);
        String h1 = h1FromText(content);
        return h1 != null ? h1 : stripExtension(mdFile.getFileName().toString());
    }

    // 由标题生成合法文件名：替换非法字符与控制符，超长截断，为空时回退 fallback。
    public static String sanitizeFilename(String title, String fallback) {
        String name = ILLEGAL_FILENAME_CHARS.matcher(title).replaceAll("_").trim();
        name = stripDots(name);
        if (name.codePointCount(0, name.length()) > 80) {
            name = trimCodePoints(name, 80).stripTrailing();
        }
        return name.isEmpty() ? fallback : name;
    }

    // 笔记排序键：按文件修改时间（即上传先后）升序，时间相同再按文件名。
    private static final Comparator<Path> UPLOAD_ORDER =
            Comparator.comparing(SeriesService::mtime).thenComparing(p -> p.getFileName().toString());

    private static java.nio.file.attribute.FileTime mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Comparator<Path> uploadOrder() {
        return UPLOAD_ORDER;
    }

    // 为上传的笔记挑选最终文件名：同时避免文件名重复与 slug（文章 URL）重复，
    // 冲突时依次追加 _1、_2（makeSlug 会剥掉序号前缀，只判断文件名会漏掉
    // "01_标题" 与 "05_标题" 这类同标题笔记互相覆盖的情况）。
    public String resolveUploadName(Path dir, String base, String ext, Set<String> usedSlugs) {
        int index = 0;
        while (true) {
            String stem = index == 0 ? base : base + "_" + index;
            String slug = makeSlug(stem);
            if (!usedSlugs.contains(slug) && !Files.exists(dir.resolve(stem + ext))) {
                usedSlugs.add(slug);
                return stem + ext;
            }
            index++;
        }
    }

    // 系列目录下已有笔记的 slug 集合。
    public Set<String> usedSlugs(Path src) {
        Set<String> slugs = new LinkedHashSet<>();
        for (Path p : listMarkdown(src)) {
            slugs.add(makeSlug(stripExtension(p.getFileName().toString())));
        }
        return slugs;
    }

    // 列出目录下一层的 .md 文件（按上传顺序）。
    public List<Path> listMarkdown(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            List<Path> files = new ArrayList<>();
            stream.forEach(files::add);
            files.sort(UPLOAD_ORDER);
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 列出系列目录下的一级子目录（跳过 . 开头的隐藏目录）。
    public List<Path> listSubDirs(Path src) {
        if (!Files.isDirectory(src)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(src)) {
            return stream.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 列出所有系列下的文章（含系列根目录与一级子目录中的 .md 文件）。
    public LinkedHashMap<String, Map<String, Object>> getArticleList() {
        LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, SeriesInfo> e : getAllSeries().entrySet()) {
            SeriesInfo info = e.getValue();
            if (!Files.isDirectory(info.src())) {
                continue;
            }
            List<Map<String, Object>> articles = new ArrayList<>();
            for (Path mdFile : listMarkdown(info.src())) {
                String filename = stripExtension(mdFile.getFileName().toString());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("slug", makeSlug(filename));
                item.put("title", readTitle(mdFile));
                item.put("filename", filename);
                item.put("sub_dir", null);
                articles.add(item);
            }
            for (Path sub : listSubDirs(info.src())) {
                String subName = sub.getFileName().toString();
                for (Path mdFile : listMarkdown(sub)) {
                    String filename = stripExtension(mdFile.getFileName().toString());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("slug", makeSlug(filename));
                    item.put("title", readTitle(mdFile));
                    item.put("filename", filename);
                    item.put("sub_dir", subName);
                    articles.add(item);
                }
            }
            Map<String, Object> series = new LinkedHashMap<>();
            series.put("title", info.title());
            series.put("desc", info.desc());
            series.put("order", info.order());
            series.put("articles", articles);
            series.put("count", articles.size());
            series.put("dynamic", true);
            result.put(e.getKey(), series);
        }
        return result;
    }

    // 提取 Markdown 正文中引用的图片文件名集合（支持 ![]() 语法与 <img> 标签）。
    public Set<String> getImageRefs(Path mdFile) {
        Matcher m = IMAGE_REF.matcher(readString(mdFile));
        Set<String> refs = new LinkedHashSet<>();
        while (m.find()) {
            String ref = m.group(1);
            if (ref != null && !ref.isEmpty()) {
                refs.add(Path.of(ref).getFileName().toString());
            }
        }
        return refs;
    }

    public String readString(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String stripDots(String s) {
        int start = 0, end = s.length();
        while (start < end && s.charAt(start) == '.') start++;
        while (end > start && s.charAt(end - 1) == '.') end--;
        return s.substring(start, end);
    }

    private static String trimCodePoints(String s, int maxCodePoints) {
        int end = s.offsetByCodePoints(0, Math.min(maxCodePoints, s.codePointCount(0, s.length())));
        return s.substring(0, end);
    }

    // ==================== 系列 CRUD ====================

    // 系列操作结果：success 为 false 时 status 为对应的 HTTP 状态码。
    public record SeriesOpResult(boolean success, int status, String message, String key) {
        static SeriesOpResult ok(String message, String key) {
            return new SeriesOpResult(true, 200, message, key);
        }
        static SeriesOpResult fail(int status, String message) {
            return new SeriesOpResult(false, status, message, null);
        }
    }

    // 创建新笔记系列：校验名称与目录冲突，在 note/ 下建目录并写入 meta。
    //
    // 业务规则：名称不能为空、slug 不能为空、key 不能已存在、目录不能被其他系列占用。
    public SeriesOpResult createSeries(String name, String desc) {
        if (name.isEmpty()) {
            return SeriesOpResult.fail(400, "系列名称不能为空");
        }
        String key = makeSeriesKey(name);
        if (key.isEmpty()) {
            return SeriesOpResult.fail(400, "无法生成系列标识");
        }

        Map<String, SeriesInfo> all = getAllSeries();
        if (all.containsKey(key)) {
            return SeriesOpResult.fail(400, "系列已存在");
        }
        // 检查目录是否已被其他系列引用
        Path src = props.noteDir().resolve(name);
        Path srcNorm = src.normalize();
        for (SeriesInfo info : all.values()) {
            if (info.src().normalize().equals(srcNorm)) {
                return SeriesOpResult.fail(400, "目录'" + name + "'已被系列'" + info.title() + "'使用");
            }
        }
        createDirectories(src);

        LinkedHashMap<String, SeriesMeta> meta = storage.loadMeta();
        SeriesMeta metaEntry = new SeriesMeta();
        metaEntry.setTitle(name);
        metaEntry.setDesc(desc);
        metaEntry.setSrcDir(name);
        metaEntry.setDynamic(true);
        meta.put(key, metaEntry);
        storage.saveMeta(meta);

        return SeriesOpResult.ok("系列'" + name + "'创建成功", key);
    }

    // 修改系列名称与描述：同步重命名 note/ 目录与系列标识。
    //
    // 标识冲突时回滚目录重命名，保证文件系统与 meta 的一致性。
    public SeriesOpResult editSeries(String catKey, String newName, String newDesc) {
        if (newName.isEmpty()) {
            return SeriesOpResult.fail(400, "系列名称不能为空");
        }
        SeriesInfo info = getSeriesByKey(catKey);
        if (info == null) {
            return SeriesOpResult.fail(404, "系列未找到");
        }
        Path oldSrc = info.src();
        Path newSrc = props.noteDir().resolve(newName);
        boolean renamed = !oldSrc.normalize().equals(newSrc.normalize());
        if (renamed) {
            if (Files.exists(newSrc)) {
                return SeriesOpResult.fail(400, "目录'" + newName + "'已存在，无法重命名");
            }
            move(oldSrc, newSrc);
        }

        String newKey = makeSeriesKey(newName);
        LinkedHashMap<String, SeriesMeta> meta = storage.loadMeta();
        if (!newKey.equals(catKey)) {
            // 检查新标识是否与其他系列冲突，冲突则回滚目录重命名
            Map<String, SeriesInfo> others = getAllSeries();
            others.remove(catKey);
            if (others.containsKey(newKey)) {
                if (renamed) {
                    move(newSrc, oldSrc);
                }
                return SeriesOpResult.fail(400, "系列标识'" + newKey + "'已被其他系列使用");
            }
            SeriesMeta metaEntry = meta.remove(catKey);
            if (metaEntry == null) {
                metaEntry = new SeriesMeta();
            }
            metaEntry.setTitle(newName);
            metaEntry.setDesc(newDesc);
            metaEntry.setSrcDir(newName);
            meta.put(newKey, metaEntry);
        } else {
            SeriesMeta metaEntry = meta.computeIfAbsent(catKey, k -> new SeriesMeta());
            metaEntry.setTitle(newName);
            metaEntry.setDesc(newDesc);
            metaEntry.setSrcDir(newName);
        }
        storage.saveMeta(meta);
        return SeriesOpResult.ok("系列更新成功", newKey);
    }

    // 删除笔记系列：删除 note/ 下的整个目录并从 meta 中移除。
    public SeriesOpResult deleteSeries(String catKey) {
        SeriesInfo info = getSeriesByKey(catKey);
        if (info == null) {
            return SeriesOpResult.fail(404, "系列未找到");
        }
        deleteRecursively(info.src());
        LinkedHashMap<String, SeriesMeta> meta = storage.loadMeta();
        meta.remove(catKey);
        storage.saveMeta(meta);
        return SeriesOpResult.ok("系列已删除", null);
    }

    // 按传入的 keys 顺序重排系列，写入 order 字段。
    public void reorderSeries(List<String> keys) {
        LinkedHashMap<String, SeriesMeta> meta = storage.loadMeta();
        for (int i = 0; i < keys.size(); i++) {
            meta.computeIfAbsent(keys.get(i), k -> new SeriesMeta()).setOrder(i + 1);
        }
        storage.saveMeta(meta);
    }

    // ==================== 文件系统操作 ====================

    private static void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void move(Path from, Path to) {
        try {
            Files.move(from, to);
        } catch (IOException e) {
            throw new UncheckedIOException("重命名目录失败: " + from, e);
        }
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("删除目录失败: " + dir, e);
        }
    }
}
