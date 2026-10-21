// 右下角悬浮工具：滚动超过阈值后显示"回到顶部"，点击用缓动曲线滚回页顶
// （不用 window.scrollTo({behavior:'smooth'})：浏览器在系统"减少动态效果"下会退化成瞬移）
(function () {
    var THRESHOLD = 300;   // 超过多少像素才显示按钮
    var DURATION = 480;    // 回顶动画时长（毫秒）
    var rafId = 0;
    var animating = false;

    function currentY() {
        return window.pageYOffset || document.documentElement.scrollTop || 0;
    }

    function easeOutCubic(p) {
        return 1 - Math.pow(1 - p, 3);
    }

    function update(btn) {
        if (animating) return;
        if (currentY() > THRESHOLD) {
            btn.classList.add('visible');
        } else {
            btn.classList.remove('visible');
        }
    }

    function scrollToTop(btn) {
        var startY = currentY();
        if (startY <= 0) return;
        cancelAnimationFrame(rafId);
        animating = true;
        var startTime = 0;

        function step(ts) {
            if (!startTime) startTime = ts;
            var p = (ts - startTime) / DURATION;
            if (p > 1) p = 1;
            window.scrollTo(0, Math.round(startY * (1 - easeOutCubic(p))));
            if (p < 1) {
                rafId = requestAnimationFrame(step);
            } else {
                animating = false;
                update(btn);
            }
        }
        rafId = requestAnimationFrame(step);
    }

    document.addEventListener('DOMContentLoaded', function () {
        var btn = document.getElementById('back-to-top');
        if (!btn) return;
        btn.addEventListener('click', function () {
            scrollToTop(btn);
        });
        window.addEventListener('scroll', function () {
            update(btn);
        }, { passive: true });
        update(btn);
    });
})();
