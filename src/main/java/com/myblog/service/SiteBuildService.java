package com.myblog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.myblog.config.BlogProperties;
import com.myblog.model.About;
import com.myblog.model.GalleryImage;
import com.myblog.repository.JsonStorageRepository;
import com.myblog.service.SeriesService.SeriesInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 静态网站生成器
//
// 流程：清空并重建 website/articles/ -> 逐系列把 Markdown 经 pandoc 转 HTML 并做 KaTeX 兼容
// 后处理 -> 复制 .image/ 图片 -> 生成 index/articles/about/gallery/message 五个页面
@Service
public class SiteBuildService {

    private static final Logger log = LoggerFactory.getLogger(SiteBuildService.class);

    // pandoc 输出的显示公式环境名（带可选 *）
    private static final String[] DISPLAY_ENVS = {
            "align\\*?", "flalign\\*?", "eqnarray\\*?", "equation\\*?", "gather\\*?", "multline\\*?", "split"
    };

    // KaTeX 的 \tag 只支持块级公式：含 \tag 的行内公式需升级为 display
    private static final Pattern TAGGED_INLINE_MATH = Pattern.compile(
            "<span\\s+class=\"math inline\">\\\\\\((?:(?!</span>).)*?\\\\tag\\{(?:(?!\\\\\\)).)*?\\\\\\)</span>",
            Pattern.DOTALL);

    // 去掉 math span 外壳，让定界符独占一行便于 auto-render 识别
    private static final Pattern MATH_SPAN = Pattern.compile(
            "<span\\s+class=\"math (?:inline|display)\">(.*?)</span>", Pattern.DOTALL);

    private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]+\\)");
    private static final Pattern MD_MARKS = Pattern.compile("[`*#]");
    private static final Pattern H1 = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);

    private static final String CSS_VERSION_TOKEN = "?v=9";
    private static final String IMAGE_DIR_NAME = ".image";

    private final BlogProperties props;
    private final JsonStorageRepository storage;
    private final SeriesService series;
    private final PandocRunner pandoc;

    private final String articleTemplate;
    private final String articlesListTemplate;
    private final String indexTemplate;
    private final String aboutTemplate;
    private final String galleryTemplate;
    private final String messageTemplate;

    public SiteBuildService(BlogProperties props, JsonStorageRepository storage, SeriesService series,
                            PandocRunner pandoc) {
        this.props = props;
        this.storage = storage;
        this.series = series;
        this.pandoc = pandoc;
        this.articleTemplate = loadTemplate("article.html");
        this.articlesListTemplate = loadTemplate("articles_list.html");
        this.indexTemplate = loadTemplate("index.html");
        this.aboutTemplate = loadTemplate("about.html");
        this.galleryTemplate = loadTemplate("gallery.html");
        this.messageTemplate = loadTemplate("message.html");
    }

    // 一次完整构建的产出摘要
    public record BuildSummary(int articleCount, List<String> pages) { }

    // 单篇文章信息（生成列表页与首页卡片时使用）
    record ArticleInfo(String title, String slug, String desc, String category, String categoryTitle) { }

    // 构建入口：清空并重建 website/articles/，逐系列转换 Markdown，最后生成五个页面。
    public BuildSummary buildAll() {
        String cssVersion = cssVersion();
        LinkedHashMap<String, SeriesInfo> notes = series.getAllSeries();

        deleteRecursively(props.articlesDir());
        createDirectories(props.articlesDir());

        LinkedHashMap<String, List<ArticleInfo>> allArticles = new LinkedHashMap<>();
        int total = 0;

        for (Map.Entry<String, SeriesInfo> entry : notes.entrySet()) {
            String catKey = entry.getKey();
            SeriesInfo info = entry.getValue();
            if (!Files.isDirectory(info.src())) {
                continue;
            }
            createDirectories(info.dst());
            copyImages(info.src(), info.dst());

            List<Path> mdFiles = new ArrayList<>(series.listMarkdown(info.src()));
            for (Path sub : series.listSubDirs(info.src())) {
                copyImages(sub, info.dst());
                mdFiles.addAll(series.listMarkdown(sub));
            }

            List<ArticleInfo> articles = new ArrayList<>();
            for (int i = 0; i < mdFiles.size(); i++) {
                Path mdFile = mdFiles.get(i);
                String filename = SeriesService.stripExtension(mdFile.getFileName().toString());
                String slug = SeriesService.makeSlug(filename);
                ArticleRef prev = i > 0 ? refOf(mdFiles.get(i - 1)) : null;
                ArticleRef next = i + 1 < mdFiles.size() ? refOf(mdFiles.get(i + 1)) : null;

                Converted converted = convertMdToHtml(mdFile, catKey, info.title(), prev, next, cssVersion);
                String desc = firstParagraph(mdFile);
                Path out = info.dst().resolve(slug + ".html");
                writeString(out, converted.html());
                articles.add(new ArticleInfo(converted.title(), slug, desc, catKey, info.title()));
                log.debug("生成文章 {}/{}.html", catKey, slug);
            }

            allArticles.put(catKey, articles);
            total += articles.size();
        }

        List<String> pages = new ArrayList<>();
        pages.add(writePage("articles.html", generateArticlesList(allArticles, notes, cssVersion)));
        pages.add(writePage("index.html", generateIndex(allArticles, notes, cssVersion)));
        pages.add(writePage("about.html", generateAbout(cssVersion)));
        pages.add(writePage("gallery.html", generateGallery(cssVersion)));
        pages.add(writePage("message.html", generateMessage(cssVersion)));

        log.info("构建完成：{} 篇文章 + {} 个页面", total, pages.size());
        return new BuildSummary(total, pages);
    }

    private record ArticleRef(String slug, String title) { }

    private ArticleRef refOf(Path mdFile) {
        String filename = SeriesService.stripExtension(mdFile.getFileName().toString());
        return new ArticleRef(SeriesService.makeSlug(filename), series.readTitle(mdFile));
    }

    private record Converted(String title, String html) { }

    // 将单个 Markdown 笔记转换为文章页 HTML：pandoc 渲染（MathJax 定界符）后修正图片路径与
    // 公式定界符，填充 article.html 模板并生成面包屑与上一篇/下一篇导航。
    Converted convertMdToHtml(Path mdPath, String categoryKey, String categoryTitle,
                              ArticleRef prev, ArticleRef next, String cssVersion) {
        String mdContent = series.readString(mdPath);
        Matcher h1 = H1.matcher(mdContent);
        String title = h1.find()
                ? h1.group(1).trim()
                : SeriesService.stripExtension(mdPath.getFileName().toString());

        String html = pandoc.toHtml(MathNormalizer.normalizeInlineMath(mdContent));

        // 笔记里的图片放在 .image/ 下，构建后统一位于 images/
        html = html.replace("src=\"" + IMAGE_DIR_NAME + "/", "src=\"images/")
                .replace("src='" + IMAGE_DIR_NAME + "/", "src='images/");

        // align 等环境被 pandoc 当作行内公式时，升级为 display 才能被 KaTeX 渲染
        for (String env : DISPLAY_ENVS) {
            Pattern pattern = Pattern.compile(
                    "<span\\s+class=\"math inline\">\\\\\\((\\\\begin\\{" + env + "\\}.*?\\\\end\\{" + env
                            + "\\})\\\\\\)</span>", Pattern.DOTALL);
            html = pattern.matcher(html).replaceAll("<span class=\"math display\">\\\\[$1\\\\]</span>");
        }

        // KaTeX 不支持 flalign，统一转成 align
        html = html.replace("flalign*", "align*").replace("flalign", "align");

        html = promoteTaggedMath(html);
        html = unwrapMathSpans(html);

        String breadcrumb = "<a href=\"../../articles.html\" style=\"color:white;\">文章</a> / "
                + "<a href=\"../../articles.html#" + categoryKey + "\" style=\"color:white;\">" + categoryTitle
                + "</a> / " + title;

        String navPrev = prev != null
                ? "<a href=\"" + prev.slug() + ".html\" class=\"btn btn-nav\">&laquo; " + prev.title() + "</a>"
                : "<span class=\"btn btn-nav btn-disabled\"></span>";
        String navNext = next != null
                ? "<a href=\"" + next.slug() + ".html\" class=\"btn btn-nav\">" + next.title() + " &raquo;</a>"
                : "<span class=\"btn btn-nav btn-disabled\"></span>";

        String page = TemplateFormatter.format(articleTemplate, Map.of(
                "title", title,
                "breadcrumb", breadcrumb,
                "content", html,
                "nav_prev", navPrev,
                "nav_next", navNext,
                "logo", siteLogo(),
                "year", String.valueOf(Year.now().getValue())
        ));
        return new Converted(title, withCssVersion(page, cssVersion));
    }

    // 含 \tag 的行内公式升级为 display（pandoc 会在 HTML 标签内折行，且公式内容不得跨 </span>）。
    private String promoteTaggedMath(String html) {
        Matcher m = TAGGED_INLINE_MATH.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replaced = m.group()
                    .replace("math inline", "math display")
                    .replace(">\\(", ">\\[")
                    .replace("\\)</span>", "\\]</span>");
            m.appendReplacement(sb, Matcher.quoteReplacement(replaced));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // 去掉 math span 外壳，让定界符独占一行便于 auto-render 识别。
    //
    // 不能把 &amp;/&lt;/&gt; 反转义回 &/</>：KaTeX 读的是 textContent（浏览器已解码），
    // 反转义会让公式里的 j<i 被解析成 <i> 标签，把公式在 DOM 里切断导致整条无法渲染。
    private String unwrapMathSpans(String html) {
        Matcher m = MATH_SPAN.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String content = m.group(1);
            if (content.startsWith("\\[")) {
                content = "\n" + content;
            }
            if (content.endsWith("\\]")) {
                content = content + "\n";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(content));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // 提取 Markdown 的首个正文段落作为摘要：去掉标记符号，超过 80 字符截断。
    String firstParagraph(Path mdPath) {
        for (String rawLine : series.readString(mdPath).split("\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty() || startsWithAny(line, "#", "|", ">", "-", "*", "```", "<img", "!")) {
                continue;
            }
            String clean = MD_LINK.matcher(line).replaceAll("$1");
            clean = MD_MARKS.matcher(clean).replaceAll("");
            if (clean.codePointCount(0, clean.length()) > 80) {
                clean = trimCodePoints(clean, 80) + "...";
            }
            return clean;
        }
        return "";
    }

    // 生成文章列表页 articles.html：按系列分组渲染筛选标签与文章卡片。
    String generateArticlesList(Map<String, List<ArticleInfo>> allArticles,
                                Map<String, SeriesInfo> notes, String cssVersion) {
        List<String> sections = new ArrayList<>();
        List<String> tabs = new ArrayList<>();

        for (Map.Entry<String, SeriesInfo> entry : notes.entrySet()) {
            String catKey = entry.getKey();
            SeriesInfo info = entry.getValue();
            if (!Files.isDirectory(info.src())) {
                continue;
            }
            tabs.add("                <button class=\"filter-btn\" data-series=\"" + catKey
                    + "\" onclick=\"selectSeries('" + catKey + "')\">" + info.title() + "</button>");

            List<String> cards = new ArrayList<>();
            for (ArticleInfo article : allArticles.getOrDefault(catKey, List.of())) {
                String desc = article.desc().isEmpty() ? "点击查看完整笔记..." : article.desc();
                cards.add(cardHtml(info.title(), article.title(), desc,
                        "articles/" + catKey + "/" + article.slug() + ".html"));
            }
            String cardsHtml = cards.isEmpty()
                    ? "                <div class=\"card empty-placeholder\"><div class=\"card-body\">"
                            + "<p style=\"text-align:center;color:#999;\">暂无笔记</p></div></div>"
                    : String.join("\n", cards);
            String descHtml = info.desc().isEmpty() ? "" : "\n            <p class=\"section-desc\">" + info.desc() + "</p>";

            sections.add("            <div class=\"article-section\" data-series=\"" + catKey + "\">\n"
                    + "            <h2 class=\"section-title\" id=\"" + catKey + "\">" + info.title() + "</h2>"
                    + descHtml + "\n"
                    + "            <div class=\"card-box\">\n"
                    + cardsHtml + "\n"
                    + "            </div>\n"
                    + "            </div>");
        }

        String page = TemplateFormatter.format(articlesListTemplate, Map.of(
                "sections", String.join("\n\n", sections),
                "series_tabs", String.join("\n", tabs),
                "logo", siteLogo(),
                "year", String.valueOf(Year.now().getValue())
        ));
        return withCssVersion(page, cssVersion);
    }

    // 生成首页 index.html：展示最新 3 篇文章卡片。
    String generateIndex(Map<String, List<ArticleInfo>> allArticles,
                         Map<String, SeriesInfo> notes, String cssVersion) {
        List<ArticleInfo> flat = new ArrayList<>();
        for (String catKey : notes.keySet()) {
            flat.addAll(allArticles.getOrDefault(catKey, List.of()));
        }
        List<ArticleInfo> latest = new ArrayList<>(flat.subList(Math.max(0, flat.size() - 3), flat.size()));
        Collections.reverse(latest);

        String cards;
        if (latest.isEmpty()) {
            cards = "                <p style=\"text-align:center;color:#999;\">暂无文章</p>";
        } else {
            List<String> cardList = new ArrayList<>();
            for (ArticleInfo a : latest) {
                cardList.add(cardHtml(a.categoryTitle(), a.title(),
                        a.desc().isEmpty() ? "点击查看完整笔记..." : a.desc(),
                        "articles/" + a.category() + "/" + a.slug() + ".html"));
            }
            cards = String.join("\n", cardList);
        }

        String page = TemplateFormatter.format(indexTemplate, Map.of(
                "cards", cards,
                "logo", siteLogo(),
                "year", String.valueOf(Year.now().getValue())
        ));
        return withCssVersion(page, cssVersion);
    }

    // 生成关于页 about.html：读取 conf/.about.json，缺失时使用默认介绍。
    String generateAbout(String cssVersion) {
        About about = storage.readAboutOrNull();
        String name = about != null && about.getName() != null ? about.getName() : "云山蓝";
        List<String> texts = about != null && about.getTexts() != null ? about.getTexts() : List.of();
        String paragraphs = texts.isEmpty()
                ? "                    <p>暂无介绍</p>"
                : texts.stream().map(t -> "                    <p>" + t + "</p>")
                        .reduce((a, b) -> a + "\n" + b).orElse("");

        String page = TemplateFormatter.format(aboutTemplate, Map.of(
                "name", name,
                "text_paragraphs", paragraphs,
                "logo", siteLogo(),
                "year", String.valueOf(Year.now().getValue())
        ));
        return withCssVersion(page, cssVersion);
    }

    // 生成相册页 gallery.html：读取 conf/.gallery.json 渲染图片列表。
    String generateGallery(String cssVersion) {
        List<GalleryImage> images = storage.loadGallery();
        String imagesHtml = images.isEmpty()
                ? "                <p style=\"text-align:center;color:#999;\">暂无照片</p>"
                : images.stream()
                        .map(img -> "                <img src=\"gallery_images/" + img.getFilename()
                                + "\" alt=\"" + (img.getAlt() != null ? img.getAlt() : "")
                                + "\" class=\"gallery-img\">")
                        .reduce((a, b) -> a + "\n" + b).orElse("");

        String page = TemplateFormatter.format(galleryTemplate, Map.of(
                "images", imagesHtml,
                "logo", siteLogo(),
                "year", String.valueOf(Year.now().getValue())
        ));
        return withCssVersion(page, cssVersion);
    }

    // 生成留言页 message.html：静态模板仅注入 logo 与年份、CSS 版本（无 {} 占位符，不走格式化器）。
    String generateMessage(String cssVersion) {
        return withCssVersion(messageTemplate
                .replace("%%LOGO%%", siteLogo())
                .replace("%%YEAR%%", String.valueOf(Year.now().getValue())), cssVersion);
    }

    // 导航栏左上角 logo 文字："姓名"的博客
    String siteLogo() {
        About about = storage.readAboutOrNull();
        String name = about != null && about.getName() != null && !about.getName().isEmpty()
                ? about.getName() : "黄颢";
        return name + "的博客";
    }

    // CSS 缓存版本号：取 website/css/ 目录内文件的最新修改时间（秒）
    String cssVersion() {
        Path cssDir = props.cssDir();
        long max = 0;
        if (Files.isDirectory(cssDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(cssDir, "*.css")) {
                for (Path f : stream) {
                    max = Math.max(max, Files.getLastModifiedTime(f).toInstant().getEpochSecond());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return max > 0 ? String.valueOf(max) : "1";
    }

    // 文章卡片 HTML：固定缩进 16/20/24 空格。
    // 不用文本块，因为文本块会剥离公共缩进，无法保留这 16 个前导空格。
    private static String cardHtml(String tag, String title, String desc, String href) {
        return "                <div class=\"card\">\n"
                + "                    <div class=\"card-body\">\n"
                + "                        <span class=\"card-tag\">" + tag + "</span>\n"
                + "                        <h3>" + title + "</h3>\n"
                + "                        <p>" + desc + "</p>\n"
                + "                        <a href=\"" + href + "\" class=\"btn\">阅读更多</a>\n"
                + "                    </div>\n"
                + "                </div>";
    }

    // 把笔记目录的 .image/ 图片复制到输出目录的 images/ 下。
    private void copyImages(Path srcDir, Path dstDir) {
        Path imgSrc = srcDir.resolve(IMAGE_DIR_NAME);
        if (!Files.isDirectory(imgSrc)) {
            return;
        }
        Path imgDst = dstDir.resolve("images");
        createDirectories(imgDst);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(imgSrc)) {
            for (Path f : stream) {
                if (Files.isRegularFile(f)) {
                    Files.copy(f, imgDst.resolve(f.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String writePage(String filename, String content) {
        writeString(props.websiteDir().resolve(filename), content);
        return filename;
    }

    private String withCssVersion(String page, String cssVersion) {
        return page.replace(CSS_VERSION_TOKEN, "?v=" + cssVersion);
    }

    private static boolean startsWithAny(String s, String... prefixes) {
        for (String p : prefixes) {
            if (s.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private static String trimCodePoints(String s, int max) {
        int end = s.offsetByCodePoints(0, Math.min(max, s.codePointCount(0, s.length())));
        return s.substring(0, end);
    }

    private String loadTemplate(String name) {
        try {
            return new ClassPathResource("build-templates/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取构建模板失败: " + name, e);
        }
    }

    private void writeString(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("写入文件失败: " + file, e);
        }
    }

    private void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // pandoc 子进程封装：markdown+tex_math_single_backslash 输入，html5 + MathJax 定界符输出。
    // tex_math_single_backslash 用于识别 \( \) 与 \[ \]（Typora 笔记常用），
    // 否则这些定界符会被当成普通文本吃掉。
    @Service
    public static class PandocRunner {

        String toHtml(String markdown) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "pandoc", "-", "-f", "markdown+tex_math_single_backslash", "-t", "html5", "--mathjax");
                pb.redirectErrorStream(false);
                Process process = pb.start();

                ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
                Thread errDrain = new Thread(() -> {
                    try (var err = process.getErrorStream()) {
                        err.transferTo(errBuf);
                    } catch (IOException ignored) {
                        // 进程已结束时读不到 stderr 属正常情况
                    }
                });
                errDrain.setDaemon(true);
                errDrain.start();

                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write(markdown.getBytes(StandardCharsets.UTF_8));
                }
                String stdout;
                try (var out = process.getInputStream()) {
                    stdout = new String(out.readAllBytes(), StandardCharsets.UTF_8);
                }
                int exit = process.waitFor();
                errDrain.join(2000);
                if (exit != 0) {
                    throw new IllegalStateException("pandoc 转换失败(exit=" + exit + "): "
                            + errBuf.toString(StandardCharsets.UTF_8));
                }
                return stdout;
            } catch (IOException e) {
                throw new UncheckedIOException("无法调用 pandoc，请确认已安装并在 PATH 中", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("pandoc 转换被中断", e);
            }
        }
    }
}
