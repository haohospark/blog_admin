// 后台公共能力：状态提示、标签页切换、HTML 转义、站点重建与退出，以及各模块的首屏加载。

// 会话过期兜底：后台任意接口返回 401 时提示并回登录页。
// 只挂在已登录页面上——登录页的 401 是密码错误，要走表单里的错误提示，不能被这里拦截。
const _origFetch = window.fetch;
window.fetch = async function(...args) {
    const res = await _origFetch.apply(this, args);
    if (res.status === 401) {
        alert('登录已过期，请重新登录');
        window.location.href = '/admin/';
        throw new Error('未登录');
    }
    return res;
};

function switchTab(tab) {
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
    event.target.classList.add('active');
    document.getElementById(tab + 'Tab').classList.add('active');
}

function showStatus(msg, type) {
    const el = document.getElementById('status');
    el.className = 'status ' + type;
    el.textContent = msg;
    setTimeout(() => { if (el.className.includes(type)) el.className = 'status'; }, 5000);
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

async function rebuildSite() {
    showStatus('正在重建站点...', 'info');
    try {
        const res = await fetch('/api/rebuild', { method: 'POST' });
        const data = await res.json();
        showStatus(data.message, data.success ? 'success' : 'error');
    } catch (e) {
        showStatus('请求失败: ' + e.message, 'error');
    }
}

async function logout() {
    await fetch('/api/logout', { method: 'POST' });
    location.reload();
}

// 首屏拉取各模块数据。等 DOMContentLoaded 而非直接执行：被调用的函数定义在后续加载的脚本文件里。
document.addEventListener('DOMContentLoaded', () => {
    fetchArticles();
    fetchCategories();
    fetchMessages();
    fetchAbout();
    fetchGallery();
});
