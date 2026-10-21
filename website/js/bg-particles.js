// 全站背景粒子连线特效：粒子缓慢漂移并互相连线；鼠标附近粒子只在绘制时朝光标方向偏移，
// 使线条向光标收拢，粒子真实位置不受影响，鼠标移开后线条立刻散开
(function () {
    var AREA_PER_PARTICLE = 14000; // 每约 14000 平方像素放 1 个粒子，控制整体密度
    var MAX_PARTICLES = 110;
    var MIN_PARTICLES = 26;
    var LINK_DIST = 130; // 粒子之间连线的最大距离
    var MOUSE_RADIUS = 250; // 朝光标收拢的影响半径
    var MOUSE_LINK_DIST = 215; // 粒子与鼠标之间连线的最大距离
    var GATHER = 0.24; // 收拢幅度（0~1，越大越靠近光标）
    var BASE_SPEED = 0.25; // 粒子基础漂移速度
    // 粒子配色跟随主题：暗色用浅灰蓝 + 主色连线，浅色沿用原来的深蓝 + 亮蓝
    function themeRGB() {
        var light = document.documentElement.getAttribute('data-theme') === 'light';
        return light
            ? { dot: '44, 62, 80', link: '44, 62, 80', mouse: '52, 152, 219' }
            : { dot: '133, 147, 168', link: '133, 147, 168', mouse: '77, 163, 224' };
    }

    var canvas = document.getElementById('bg-particles');
    if (!canvas || !canvas.getContext) return;
    var ctx = canvas.getContext('2d');
    var particles = [];
    var view = { w: 0, h: 0, ox: 0, oy: 0 };
    var mouse = { x: 0, y: 0, active: false };
    var running = false;

    function rand(min, max) {
        return min + Math.random() * (max - min);
    }

    function createParticle() {
        var angle = Math.random() * Math.PI * 2;
        return {
            x: rand(0, view.w),
            y: rand(0, view.h),
            vx: Math.cos(angle) * BASE_SPEED,
            vy: Math.sin(angle) * BASE_SPEED,
            r: rand(1, 2.4)
        };
    }

    function resize() {
        var dpr = window.devicePixelRatio || 1;
        // 必须以 canvas 实际渲染尺寸为准：fixed 元素的 CSS 100% 不含滚动条，
        // 若用 window.innerWidth 会导致画面被横向压缩、鼠标收拢中心与光标错位
        var rect = canvas.getBoundingClientRect();
        view.w = rect.width || window.innerWidth;
        view.h = rect.height || window.innerHeight;
        view.ox = rect.left;
        view.oy = rect.top;
        canvas.width = Math.round(view.w * dpr);
        canvas.height = Math.round(view.h * dpr);
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.lineWidth = 1;

        var want = Math.round(view.w * view.h / AREA_PER_PARTICLE);
        if (want > MAX_PARTICLES) want = MAX_PARTICLES;
        if (want < MIN_PARTICLES) want = MIN_PARTICLES;
        while (particles.length < want) particles.push(createParticle());
        if (particles.length > want) particles.length = want;
        for (var i = 0; i < particles.length; i++) {
            if (particles[i].x > view.w) particles[i].x = rand(0, view.w);
            if (particles[i].y > view.h) particles[i].y = rand(0, view.h);
        }
    }

    function move() {
        for (var i = 0; i < particles.length; i++) {
            var p = particles[i];
            p.x += p.vx;
            p.y += p.vy;
            if (p.x < 0) { p.x = 0; p.vx = -p.vx; }
            else if (p.x > view.w) { p.x = view.w; p.vx = -p.vx; }
            if (p.y < 0) { p.y = 0; p.vy = -p.vy; }
            else if (p.y > view.h) { p.y = view.h; p.vy = -p.vy; }

            // px/py 是本帧实际绘制坐标：默认等于真实位置，鼠标附近才朝光标偏移
            p.px = p.x;
            p.py = p.y;
            if (mouse.active) {
                var dx = mouse.x - p.x;
                var dy = mouse.y - p.y;
                var d2 = dx * dx + dy * dy;
                if (d2 < MOUSE_RADIUS * MOUSE_RADIUS) {
                    // 按固定比例朝光标收缩（比例恒小于 1，所以永远不会收成一个点）；
                    // 外圈用 edge 渐出，避免粒子进出影响半径时线条突然跳变
                    var ratio = Math.sqrt(d2) / MOUSE_RADIUS;
                    var edge = ratio > 0.7 ? (1 - ratio) / 0.3 : 1;
                    var k = GATHER * edge;
                    p.px = p.x + dx * k;
                    p.py = p.y + dy * k;
                }
            }
        }
    }

    function draw() {
        ctx.clearRect(0, 0, view.w, view.h);
        var tc = themeRGB();
        var DOT_RGB = tc.dot, LINK_RGB = tc.link, MOUSE_LINK_RGB = tc.mouse;
        var i, j, p, q, dx, dy, d2, d, alpha;
        for (i = 0; i < particles.length; i++) {
            p = particles[i];
            for (j = i + 1; j < particles.length; j++) {
                q = particles[j];
                dx = p.px - q.px;
                dy = p.py - q.py;
                d2 = dx * dx + dy * dy;
                if (d2 < LINK_DIST * LINK_DIST) {
                    alpha = 0.26 * (1 - Math.sqrt(d2) / LINK_DIST);
                    ctx.strokeStyle = 'rgba(' + LINK_RGB + ', ' + alpha + ')';
                    ctx.beginPath();
                    ctx.moveTo(p.px, p.py);
                    ctx.lineTo(q.px, q.py);
                    ctx.stroke();
                }
            }
        }
        if (mouse.active) {
            for (i = 0; i < particles.length; i++) {
                p = particles[i];
                dx = p.px - mouse.x;
                dy = p.py - mouse.y;
                d2 = dx * dx + dy * dy;
                if (d2 < MOUSE_LINK_DIST * MOUSE_LINK_DIST) {
                    d = Math.sqrt(d2);
                    alpha = 0.42 * (1 - d / MOUSE_LINK_DIST);
                    ctx.strokeStyle = 'rgba(' + MOUSE_LINK_RGB + ', ' + alpha + ')';
                    ctx.beginPath();
                    ctx.moveTo(p.px, p.py);
                    ctx.lineTo(mouse.x, mouse.y);
                    ctx.stroke();
                }
            }
        }
        ctx.fillStyle = 'rgba(' + DOT_RGB + ', 0.42)';
        for (i = 0; i < particles.length; i++) {
            p = particles[i];
            ctx.beginPath();
            ctx.arc(p.px, p.py, p.r, 0, Math.PI * 2);
            ctx.fill();
        }
    }

    function frame() {
        if (!running) return;
        move();
        draw();
        window.requestAnimationFrame(frame);
    }

    function start() {
        if (running) return;
        running = true;
        window.requestAnimationFrame(frame);
    }

    function stop() {
        running = false;
    }

    function setMouse(e) {
        mouse.x = e.clientX - view.ox;
        mouse.y = e.clientY - view.oy;
        mouse.active = true;
    }

    var resizeTimer = null;
    window.addEventListener('resize', function () {
        window.clearTimeout(resizeTimer);
        resizeTimer = window.setTimeout(resize, 150);
    });
    window.addEventListener('pointermove', setMouse);
    window.addEventListener('pointerdown', setMouse);
    window.addEventListener('pointerleave', function () { mouse.active = false; });
    window.addEventListener('blur', function () { mouse.active = false; });
    document.addEventListener('visibilitychange', function () {
        if (document.hidden) stop();
        else start();
    });

    resize();
    start();
})();
