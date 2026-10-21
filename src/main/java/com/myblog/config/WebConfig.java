package com.myblog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.myblog.web.LoginInterceptor;

import java.nio.file.Path;

// MVC 装配：注册鉴权拦截器（是否需要登录由接口上的 @LoginRequired 决定），
// 把后台页面自己的 CSS/JS 挂到 /admin 下，并把构建产物目录挂到根路径 /**，直接以端口访问前台静态站。
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final BlogProperties props;

    public WebConfig(LoginInterceptor loginInterceptor, BlogProperties props) {
        this.loginInterceptor = loginInterceptor;
        this.props = props;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor).addPathPatterns("/**");
    }

    // 前台静态站：http://127.0.0.1:5050/ 直接访问 websiteDir 下的构建产物。
    // Controller 映射优先于静态资源，/admin 与 /api/** 不受影响。
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 后台的 CSS/JS 与 admin.html 放在同一目录，故只放行样式与脚本两种后缀，
        // 避免 /admin/admin.html 把带三段式标记的模板原文吐给未登录访客
        registry.addResourceHandler("/admin/*.css", "/admin/*.js")
                .addResourceLocations("classpath:/templates/");
        registry.addResourceHandler("/**")
                .addResourceLocations(toFileLocation(props.websiteDir()));
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/index.html");
    }

    private static String toFileLocation(Path dir) {
        String uri = dir.toAbsolutePath().normalize().toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
