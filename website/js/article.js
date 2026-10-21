// 文章页交互：为正文中的代码块添加行号、语言标签和复制按钮，并给二/三/四级标题自动编号
(function () {
    function fallbackCopy(text) {
        var ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.top = '-10px';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.focus();
        ta.select();
        var ok = false;
        try {
            ok = document.execCommand('copy');
        } catch (e) {
            ok = false;
        }
        document.body.removeChild(ta);
        return ok;
    }

    function copyText(text) {
        if (navigator.clipboard && window.isSecureContext) {
            return navigator.clipboard.writeText(text).then(function () {
                return true;
            }).catch(function () {
                return fallbackCopy(text);
            });
        }
        return Promise.resolve(fallbackCopy(text));
    }

    function showResult(btn, ok) {
        btn.textContent = ok ? '已复制' : '复制失败';
        btn.classList.toggle('copied', ok);
        window.setTimeout(function () {
            btn.textContent = '复制';
            btn.classList.remove('copied');
        }, 1500);
    }

    // pandoc 会输出 <pre class="sourceCode cpp">，其他渲染器常见 <code class="language-cpp">
    function detectLanguage(pre) {
        var candidates = [pre.className];
        var code = pre.querySelector('code');
        if (code) candidates.push(code.className);
        var classes = [];
        candidates.forEach(function (cls) {
            String(cls || '').split(/\s+/).forEach(function (c) {
                if (c && classes.indexOf(c) === -1) classes.push(c);
            });
        });
        for (var i = 0; i < classes.length; i++) {
            var c = classes[i];
            if (c === 'sourceCode' || c === 'code-block' || c === 'hljs' || c === 'highlight') continue;
            if (/^\./.test(c)) continue;  // pandoc 属性类，如 .numberLines
            var m = /^language-(.+)$/i.exec(c);
            if (m) return m[1];
            if (/^(output|error|input)$/.test(c)) continue;
            return c.toLowerCase();
        }
        return '';
    }

    // 行号栏放在 pre 内部并用 sticky 固定：横向滚动时不跟随移动，
    // 且滚动条横跨整个代码块（若行号在 pre 外，滚动条左侧会缺一截）
    function addLineNumbers(pre, hasLabel, codeText) {
        var lines = codeText.replace(/\n$/, '').split('\n');
        if (lines.length < 1) return;
        var gutter = document.createElement('div');
        gutter.className = 'line-numbers';
        if (hasLabel) gutter.className += ' under-label';
        var nums = [];
        for (var i = 1; i <= lines.length; i++) nums.push(i);
        gutter.textContent = nums.join('\n');
        pre.insertBefore(gutter, pre.firstChild);
    }

    function wrapCodeBlock(pre) {
        var wrap = document.createElement('div');
        wrap.className = 'code-block';
        pre.parentNode.insertBefore(wrap, pre);

        var lang = detectLanguage(pre);
        if (lang) {
            var label = document.createElement('span');
            label.className = 'lang-label';
            label.textContent = lang;
            wrap.appendChild(label);
        }
        wrap.appendChild(pre);

        var codeText = pre.innerText;
        var codeHolder = document.createElement('div');
        codeHolder.className = 'code-lines';
        while (pre.firstChild) codeHolder.appendChild(pre.firstChild);
        pre.appendChild(codeHolder);
        addLineNumbers(pre, !!lang, codeText);

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'copy-btn';
        btn.textContent = '复制';
        btn.addEventListener('click', function () {
            copyText(codeText).then(function (ok) {
                showResult(btn, ok);
            });
        });
        wrap.appendChild(btn);
    }

    function decorateCodeBlocks() {
        var pres = document.querySelectorAll('.article-content pre');
        Array.prototype.forEach.call(pres, function (pre) {
            var parentCls = String(pre.parentNode.className || '');
            if (parentCls.indexOf('code-block') !== -1) return;
            wrapCodeBlock(pre);
        });
    }

    // 一级标题不编号并让计数重新开始；二级输出 1.、三级 1.1、四级 1.1.1
    function numberHeadings() {
        var content = document.querySelector('.article-content');
        if (!content) return;
        var counters = [0, 0, 0];
        var levels = { H2: 0, H3: 1, H4: 2 };
        Array.prototype.forEach.call(content.querySelectorAll('h1, h2, h3, h4'), function (h) {
            var level = levels[h.tagName];
            if (level === undefined) {
                counters = [0, 0, 0];
                return;
            }
            counters[level]++;
            for (var i = level + 1; i < counters.length; i++) counters[i] = 0;
            var num = String(counters[0]);
            if (level > 0) num += '.' + counters[1];
            if (level > 1) num += '.' + counters[2];
            if (level === 0) num += '.';
            var tag = document.createElement('span');
            tag.className = 'heading-num';
            tag.textContent = num + ' ';
            h.insertBefore(tag, h.firstChild);
        });
    }

    function init() {
        decorateCodeBlocks();
        numberHeadings();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
