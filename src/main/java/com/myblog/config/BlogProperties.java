package com.myblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
            if (codeSource != null && codeSource.getLocation() != null) {
                Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
                if (Files.isDirectory(location)) {
                    // 本地开发：编译产物在 <module>/target/classes，回退到 <module>
                    Path classes = "classes".equals(fileName(location)) ? location.getParent() : location;
                    if (classes != null && "target".equals(fileName(classes))) {
                        return classes.getParent();
                    }
                    return location;
                }
                // 打包运行：jar 所在目录
                return location.getParent();
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // 定位失败时退回当前工作目录
        }
        return Path.of("").toAbsolutePath().normalize();
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
