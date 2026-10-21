// 图片主题自适应：暗色主题下，检测白底/浅色为主的图片（截图、文字图等）并加 .img-adapt 反色适配，
// 照片等明暗均衡的图片不受影响。采样失败（跨域画布污染等）时保持原样
(function () {
    var EDGE_LUMA = 185;   // 边缘平均亮度阈值（0-255），白底图边缘接近 255
    var OVERALL_LUMA = 150; // 整体平均亮度阈值，防止仅白边框的照片被误判
    var SCALE = 48;         // 缩采样尺寸，足够判断明暗且开销极小

    function luma(r, g, b) {
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    function analyze(img) {
        var w = img.naturalWidth, h = img.naturalHeight;
        if (!w || !h) return false;
        var ratio = Math.min(SCALE / w, SCALE / h, 1);
        var sw = Math.max(1, Math.round(w * ratio));
        var sh = Math.max(1, Math.round(h * ratio));
        var canvas = document.createElement('canvas');
        canvas.width = sw;
        canvas.height = sh;
        var ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, sw, sh);
        var data = ctx.getImageData(0, 0, sw, sh).data;
        var band = Math.max(1, Math.round(Math.min(sw, sh) * 0.08));
        var edgeSum = 0, edgeN = 0, allSum = 0, allN = 0;
        for (var y = 0; y < sh; y++) {
            for (var x = 0; x < sw; x++) {
                var i = (y * sw + x) * 4;
                if (data[i + 3] < 200) continue; // 透明像素不参与
                var l = luma(data[i], data[i + 1], data[i + 2]);
                allSum += l;
                allN++;
                var isEdge = x < band || x >= sw - band || y < band || y >= sh - band;
                if (isEdge) {
                    edgeSum += l;
                    edgeN++;
                }
            }
        }
        if (!allN || edgeN < allN * 0.1) return false;
        return edgeSum / edgeN >= EDGE_LUMA && allSum / allN >= OVERALL_LUMA;
    }

    function adapt(img) {
        function check() {
            try {
                if (analyze(img)) img.classList.add('img-adapt');
            } catch (e) {
                // 跨域图片画布被污染时会抛异常，跳过该图
            }
        }
        if (img.complete && img.naturalWidth) {
            check();
        } else {
            img.addEventListener('load', check);
        }
    }

    function run() {
        var imgs = document.querySelectorAll('.article-content img, .gallery-img');
        Array.prototype.forEach.call(imgs, adapt);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', run);
    } else {
        run();
    }
})();
