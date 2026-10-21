// 未登录页面的登录表单处理。
document.getElementById('loginForm').onsubmit = async (e) => {
    e.preventDefault();
    const status = document.getElementById('loginStatus');
    status.className = 'status info';
    status.textContent = '登录中...';
    status.style.display = 'block';
    try {
        const res = await fetch('/api/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                username: document.getElementById('username').value,
                password: document.getElementById('password').value
            })
        });
        const data = await res.json();
        if (data.success) {
            location.reload();
        } else {
            status.className = 'status error';
            status.textContent = data.message;
        }
    } catch (e) {
        status.className = 'status error';
        status.textContent = '请求失败: ' + e.message;
    }
};
