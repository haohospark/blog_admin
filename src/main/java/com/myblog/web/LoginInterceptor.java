package com.myblog.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

// 鉴权拦截器：对标注 @LoginRequired 的接口检查登录会话，
// 未登录时返回 401 {"error": "未登录"}。
@Component
public class LoginInterceptor implements HandlerInterceptor {

    public static final String SESSION_KEY = "logged_in";

    private final ObjectMapper mapper;

    public LoginInterceptor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method) || !requiresLogin(method)) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_KEY))) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(mapper.writeValueAsString(Map.of("error", "未登录")));
        return false;
    }

    private boolean requiresLogin(HandlerMethod method) {
        return method.hasMethodAnnotation(LoginRequired.class)
                || method.getBeanType().isAnnotationPresent(LoginRequired.class);
    }
}
