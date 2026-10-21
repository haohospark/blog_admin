package com.myblog.web;

import com.myblog.config.BlogProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// 登录鉴权与后台首页。
// 后台页面挂在 /admin，接口统一在 /api/**，前端 admin.html 直接调用 /api/...。
@RestController
public class AuthController {

    private final BlogProperties props;
    private final String adminTemplate;

    public AuthController(BlogProperties props) {
        this.props = props;
        try {
            this.adminTemplate = new ClassPathResource("templates/admin.html")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取 admin.html 模板失败", e);
        }
    }

    // 后台首页：按登录态渲染 admin.html。
    @GetMapping(value = {"/admin", "/admin/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> index(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        boolean loggedIn = session != null && Boolean.TRUE.equals(session.getAttribute(LoginInterceptor.SESSION_KEY));
        return ResponseEntity.ok(AdminPageRenderer.renderAdminPage(adminTemplate, loggedIn));
    }

    // 登录：校验管理员账号，成功时写入登录会话；失败返回 401。
    @PostMapping("/api/login")
    public ResponseEntity<Map<String, Object>> login(HttpServletRequest request, @RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        if (username.equals(props.adminUsername()) && password.equals(props.adminPassword())) {
            request.getSession(true).setAttribute(LoginInterceptor.SESSION_KEY, Boolean.TRUE);
            return ResponseEntity.ok(Api.result(true, "登录成功"));
        }
        return ResponseEntity.status(401).body(Api.result(false, "用户名或密码错误"));
    }

    // 登出：移除登录会话标记。
    @PostMapping("/api/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(LoginInterceptor.SESSION_KEY);
        }
        return Api.result(true, "已退出登录");
    }
}
