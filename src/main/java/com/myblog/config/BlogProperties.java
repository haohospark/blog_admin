package com.myblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

// 应用路径与管理员账号配置。
@ConfigurationProperties(prefix = "blog")
public record BlogProperties(
        Path dataDir,      // 数据根目录，默认代码所在的模块目录
        Path confDir,      // JSON 配置目录，默认 dataDir/conf
        Path noteDir,      // 笔记目录，默认 dataDir/note
        Path websiteDir,   // 构建输出与静态资源目录，默认 dataDir/website
        String adminUsername,
        String adminPassword
) {
    public Path dataDir() {
        return dataDir != null ? dataDir : codeBaseDir();
    }

    // 以本类的编译产物/jar 位置定位模块根目录，不依赖当前工作目录：
    // IDE 从工程根启动时 cwd 会落到工程外，若按 cwd 相对定位会误建同级 blog_admin/。
    private static Path codeBaseDir() {
        try {
            var codeSource = BlogProperties.class.getProtectionDomain().getCodeSource();
            URI uri = codeSource != null ? codeSource.getLocation().toURI() : null;
            if (uri != null) {
                // 打包运行：fat jar 内 CodeSource 是 jar:nested 虚拟路径（Path.of 无法解析），
                // 从 URI 中取出外层 jar，dataDir 即 jar 所在目录
                if ("jar".equalsIgnoreCase(uri.getScheme())) {
                    Path jar = nestedJarPath(uri.toString());
                    if (jar != null) {
                        return jar.getParent();
                    }
                } else {
                    Path location = Path.of(uri).toAbsolutePath().normalize();
                    if (Files.isDirectory(location)) {
                        // 本地开发：编译产物在 <module>/target/classes，回退到 <module>
                        Path classes = "classes".equals(fileName(location)) ? location.getParent() : location;
                        if (classes != null && "target".equals(fileName(classes))) {
                            return classes.getParent();
                        }
                        return location;
                    }
                }
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // 定位失败时退回当前工作目录
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    // 解析 jar:nested:/path/app.jar/!BOOT-INF/classes/!/ 形式的 URI，返回外层 jar 的真实路径；
    // 兼容旧式 jar:file:/path/app.jar!/... 。非 jar URI 或文件不存在时返回 null。
    private static Path nestedJarPath(String uri) {
        String path = null;
        int nested = uri.indexOf("jar:nested:");
        if (nested >= 0) {
            int start = nested + "jar:nested:".length();
            int end = uri.toLowerCase().indexOf(".jar", start); // 外层 jar 以 .jar 结束
            if (end < 0) return null;
            path = decodePercent(uri.substring(start, end + 4));
        } else if (uri.startsWith("jar:file:")) {
            int bang = uri.indexOf("!/");
            String file = bang >= 0 ? uri.substring("jar:".length(), bang) : uri.substring("jar:".length());
            path = decodePercent(file);
        }
        if (path == null || !path.toLowerCase().endsWith(".jar")) {
            return null;
        }
        // Windows 下 URI 内路径形如 /C:/dir/app.jar，需去掉前导斜杠
        if (path.length() > 3 && path.charAt(0) == '/' && path.charAt(2) == ':') {
            path = path.substring(1);
        }
        Path jar = Path.of(path).toAbsolutePath().normalize();
        return Files.isRegularFile(jar) ? jar : null;
    }

    private static String decodePercent(String s) {
        return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name != null ? name.toString() : "";
    }

    public Path confDir() {
        return confDir != null ? confDir : dataDir().resolve("conf");
    }

    public Path noteDir() {
        return noteDir != null ? noteDir : dataDir().resolve("note");
    }

    public Path websiteDir() {
        return websiteDir != null ? websiteDir : dataDir().resolve("website");
    }

    public Path metaFile() {
        return confDir().resolve(".series_meta.json");
    }

    public Path messagesFile() {
        return confDir().resolve(".messages.json");
    }

    public Path aboutFile() {
        return confDir().resolve(".about.json");
    }

    public Path galleryFile() {
        return confDir().resolve(".gallery.json");
    }

    public Path galleryImageDir() {
        return websiteDir().resolve("gallery_images");
    }

    public Path articlesDir() {
        return websiteDir().resolve("articles");
    }

    public Path cssDir() {
        return websiteDir().resolve("css");
    }
}
