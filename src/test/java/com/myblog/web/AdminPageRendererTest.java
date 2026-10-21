package com.myblog.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPageRendererTest {

    private static final String SYNTHETIC = """
            <body>
            <!--@IF_NOT_LOGGED_BEGIN@--><div class="login-box">LOGIN</div><script src="/admin/login.js"></script><!--@IF_NOT_LOGGED_ELSE@--><div class="container">MAIN</div><script src="/admin/admin-core.js"></script><!--@IF_NOT_LOGGED_END@-->
            </body>
            """;

    @Test
    void anonymousKeepsLoginBoxAndLoginScript() {
        String html = AdminPageRenderer.renderAdminPage(SYNTHETIC, false);
        assertTrue(html.contains("LOGIN"));
        assertFalse(html.contains("MAIN"));
        assertTrue(html.contains("login.js"));
        assertFalse(html.contains("admin-core.js"));
        assertNoMarkers(html);
    }

    @Test
    void loggedInKeepsAdminBoxAndAdminScript() {
        String html = AdminPageRenderer.renderAdminPage(SYNTHETIC, true);
        assertTrue(html.contains("MAIN"));
        assertFalse(html.contains("LOGIN"));
        assertTrue(html.contains("admin-core.js"));
        assertFalse(html.contains("login.js"));
        assertNoMarkers(html);
    }

    @Test
    void realTemplateKeepsContentOutsideMarkers() throws Exception {
        String template = new ClassPathResource("templates/admin.html").getContentAsString(StandardCharsets.UTF_8);
        String anon = AdminPageRenderer.renderAdminPage(template, false);
        String user = AdminPageRenderer.renderAdminPage(template, true);

        // 未登录：只有登录框与登录脚本；后台界面与后台脚本都不该出现在响应里
        assertTrue(anon.contains("class=\"login-box\""));
        assertTrue(anon.contains("loginForm"));
        assertTrue(anon.contains("login.js"));
        assertFalse(anon.contains("admin-series.js"));
        assertFalse(anon.contains("class=\"container\""));
        // 已登录：管理界面与三个后台脚本；不含登录框
        assertTrue(user.contains("admin-core.js"));
        assertTrue(user.contains("admin-series.js"));
        assertTrue(user.contains("admin-content.js"));
        assertFalse(user.contains("class=\"login-box\""));
        assertFalse(user.contains("login.js"));
        assertNoMarkers(anon);
        assertNoMarkers(user);
        // 页面骨架（title/标题文案/共享样式）在两种状态下都应保留
        assertTrue(anon.contains("<title>"));
        assertTrue(user.contains("<title>"));
        assertTrue(anon.contains("博客管理后台"));
        assertTrue(user.contains("博客管理后台"));
        assertTrue(anon.contains("/admin/admin.css"));
        assertTrue(user.contains("/admin/admin.css"));
        // 脚本一律外链，模板内不再有内联 JS：401 兜底只随 admin-core.js 进入已登录页面
        assertFalse(anon.contains("<script>"));
        assertFalse(user.contains("<script>"));
        assertFalse(user.contains("_origFetch"));
    }

    private void assertNoMarkers(String html) {
        assertFalse(html.contains("@IF_NOT_LOGGED"), "残留 HTML 标记");
    }
}
