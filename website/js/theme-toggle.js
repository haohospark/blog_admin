// 主题切换：暗色为默认，用户可选浅色并持久化到 localStorage。
// 本脚本在 <head> 中同步执行，先恢复偏好再渲染，避免主题闪烁
(function () {
    var KEY = 'blog-theme';

    function isLight() {
        return document.documentElement.getAttribute('data-theme') === 'light';
    }

    function apply(theme) {
        if (theme === 'light') {
            document.documentElement.setAttribute('data-theme', 'light');
        } else {
            document.documentElement.removeAttribute('data-theme');
        }
        try {
            localStorage.setItem(KEY, theme);
        } catch (e) {
            // 隐私模式等场景 localStorage 不可用，本次浏览内仍可切换
        }
    }

    try {
        if (localStorage.getItem(KEY) === 'light') {
            document.documentElement.setAttribute('data-theme', 'light');
        }
    } catch (e) { /* 读取失败保持默认暗色 */ }

    document.addEventListener('DOMContentLoaded', function () {
        // 导航栏与右下角悬浮组各有一个切换按钮，共用同一套切换逻辑
        var buttons = document.querySelectorAll('.theme-toggle-btn');
        for (var i = 0; i < buttons.length; i++) {
            buttons[i].addEventListener('click', function () {
                apply(isLight() ? 'dark' : 'light');
            });
        }
    });
})();
