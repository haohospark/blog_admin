package com.myblog.service;

import java.util.Map;
import java.util.regex.Pattern;

// 构建模板的占位符格式化器：{identifier} 替换为变量值，{{ 与 }} 表示字面量花括号。
// 值不做转义处理，调用方需自行保证内容可安全嵌入 HTML。
final class TemplateFormatter {

    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private TemplateFormatter() { }

    // 单遍扫描模板完成全部替换，未知或非法占位符直接报错，避免产出残缺页面。
    static String format(String template, Map<String, String> vars) {
        StringBuilder out = new StringBuilder(template.length() + 1024);
        int i = 0;
        int n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            if (c == '{') {
                if (i + 1 < n && template.charAt(i + 1) == '{') {
                    out.append('{');
                    i += 2;
                    continue;
                }
                int close = template.indexOf('}', i + 1);
                if (close < 0) {
                    throw new IllegalStateException("模板占位符未闭合: " + template.substring(i, Math.min(i + 40, n)));
                }
                String key = template.substring(i + 1, close);
                if (!IDENT.matcher(key).matches()) {
                    throw new IllegalStateException("模板占位符非法: {" + key + "}");
                }
                if (!vars.containsKey(key)) {
                    throw new IllegalStateException("模板缺少占位符取值: " + key);
                }
                out.append(vars.get(key));
                i = close + 1;
                continue;
            }
            if (c == '}') {
                if (i + 1 < n && template.charAt(i + 1) == '}') {
                    out.append('}');
                    i += 2;
                    continue;
                }
                throw new IllegalStateException("模板出现未配对的 }: 位置 " + i);
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
