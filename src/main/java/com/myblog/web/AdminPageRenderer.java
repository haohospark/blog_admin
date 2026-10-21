package com.myblog.web;

// admin.html 的轻量渲染器：按登录态在模板的三段式标记间二选一。
//
// 模板中的纯文本标记（HTML 语境用 <!--@..@-->）：
//   BEGIN / ELSE / END 三段式，第一段为"未登录"内容，第二段为"已登录"内容。
// 渲染时按登录态只保留其中一段，再清除全部标记。
// 后台与登录页的脚本各为一个 <script src>，随所在段一起被选中，无需额外标记。
final class AdminPageRenderer {

    private static final String HTML_BEGIN = "<!--@IF_NOT_LOGGED_BEGIN@-->";
    private static final String HTML_ELSE = "<!--@IF_NOT_LOGGED_ELSE@-->";
    private static final String HTML_END = "<!--@IF_NOT_LOGGED_END@-->";

    private AdminPageRenderer() { }

    static String renderAdminPage(String template, boolean loggedIn) {
        // HTML 片段：未登录 -> 登录框；已登录 -> 管理界面
        String body = loggedIn ? keepSecond(template, HTML_BEGIN, HTML_ELSE, HTML_END)
                : keepFirst(template, HTML_BEGIN, HTML_ELSE, HTML_END);
        return body.replace(HTML_BEGIN, "").replace(HTML_ELSE, "").replace(HTML_END, "");
    }

    // 保留第一段（BEGIN..ELSE），删除第二段。
    private static String keepFirst(String s, String begin, String elseMark, String end) {
        int b = s.indexOf(begin), e = s.indexOf(elseMark), f = s.indexOf(end);
        if (b < 0 || e < 0 || f < 0) {
            return s;
        }
        return s.substring(0, b) + s.substring(b + begin.length(), e) + s.substring(f + end.length());
    }

    // 删除第一段，保留第二段（ELSE..END）。
    private static String keepSecond(String s, String begin, String elseMark, String end) {
        int b = s.indexOf(begin), e = s.indexOf(elseMark), f = s.indexOf(end);
        if (b < 0 || e < 0 || f < 0) {
            return s;
        }
        return s.substring(0, b) + s.substring(e + elseMark.length(), f) + s.substring(f + end.length());
    }
}
