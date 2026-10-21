package com.myblog.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

// 让 Markdown 中跨行书写的行内公式能被 pandoc 识别。
//
// pandoc 要求闭合 $ 前不能是空白、开头 $ 后不能是空白，而 Typora 笔记常把长公式折行、
// 闭合 $ 落在下一行行首，导致整条公式被当成普通文本。这里按"段落块"配对 $，删除定界符
// 内侧的空白（不跨空行、不动代码块）。仅当内容看起来像公式（含 \ ^ _ { }）时才改写，
// 避免误伤正文里的美元符号。
final class MathNormalizer {

    private static final Pattern LOOKS_LIKE_MATH = Pattern.compile("[\\\\^_{}]");

    private MathNormalizer() { }

    // 收集一个文本块内所有"行内公式定界符"（单个 $）的位置。
    // 跳过转义 \$、行内代码 `...`、以及成对的 $$ 显示公式，避免误判。
    static List<Integer> findInlineDollars(String block) {
        List<Integer> positions = new ArrayList<>();
        int i = 0;
        int n = block.length();
        while (i < n) {
            char ch = block.charAt(i);
            if (ch == '\\') {
                i += 2;
                continue;
            }
            if (ch == '`') {
                int j = block.indexOf('`', i + 1);
                i = j < 0 ? n : j + 1;
                continue;
            }
            if (ch == '$') {
                if (block.startsWith("$$", i)) {
                    int j = block.indexOf("$$", i + 2);
                    i = j < 0 ? n : j + 2;
                    continue;
                }
                positions.add(i);
            }
            i++;
        }
        return positions;
    }

    // 规范化整篇 Markdown：跳过代码块与空行，按段落块配对 $ 并删除定界符内侧空白。
    static String normalizeInlineMath(String md) {
        String[] lines = md.split("\n", -1);
        List<String> out = new ArrayList<>();
        int i = 0;
        int n = lines.length;
        while (i < n) {
            if (isFence(lines[i])) {
                out.add(lines[i]);
                i++;
                while (i < n && !isFence(lines[i])) {
                    out.add(lines[i]);
                    i++;
                }
                if (i < n) {
                    out.add(lines[i]);
                    i++;
                }
                continue;
            }
            if (lines[i].trim().isEmpty()) {
                out.add(lines[i]);
                i++;
                continue;
            }

            int start = i;
            while (i < n && !lines[i].trim().isEmpty() && !isFence(lines[i])) {
                i++;
            }
            String block = String.join("\n", Arrays.copyOfRange(lines, start, i));

            StringBuilder chars = new StringBuilder(block);
            for (int[] cut : computeCuts(block)) {
                chars.delete(cut[0], cut[1]);
            }
            block = chars.toString();

            out.addAll(List.of(block.split("\n", -1)));
        }
        return String.join("\n", out);
    }

    // 计算需要删除的空白区间 [lo, hi)，按起点降序返回（从后往前删不会错位）。
    private static List<int[]> computeCuts(String block) {
        List<Integer> positions = findInlineDollars(block);
        List<int[]> cuts = new ArrayList<>();
        for (int k = 0; k + 1 < positions.size(); k += 2) {
            int a = positions.get(k);
            int b = positions.get(k + 1);
            String inner = block.substring(a + 1, b);
            if (inner.trim().isEmpty() || !LOOKS_LIKE_MATH.matcher(inner).find()) {
                continue;
            }
            // 闭合 $ 后紧跟数字时视为价格写法（如 $5 与 $10），不改写
            if (b + 1 < block.length() && Character.isDigit(block.charAt(b + 1))) {
                continue;
            }
            int p = a + 1;
            while (p < b && isInlineSpace(block.charAt(p))) {
                p++;
            }
            if (p > a + 1) {
                cuts.add(new int[]{a + 1, p});
            }
            int q = b;
            while (q > a + 1 && isInlineSpace(block.charAt(q - 1))) {
                q--;
            }
            if (q < b) {
                cuts.add(new int[]{q, b});
            }
        }
        cuts.sort((x, y) -> Integer.compare(y[0], x[0]));
        return cuts;
    }

    private static boolean isInlineSpace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    private static boolean isFence(String line) {
        String stripped = line.stripLeading();
        return stripped.startsWith("```") || stripped.startsWith("~~~");
    }
}
