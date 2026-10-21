// 内容管理：访客留言、关于我、相册（上传/排序/删除）。

let messagesData = [];
let galleryData = [];

async function fetchMessages() {
    const res = await fetch('/api/messages');
    messagesData = await res.json();
    renderMessages();
}

function renderMessages() {
    const container = document.getElementById('messagesList');
    if (!messagesData.length) {
        container.innerHTML = '<p style="text-align:center;color:#999;padding:40px 0;">暂无留言</p>';
        return;
    }
    let html = '';
    for (const m of messagesData) {
        html += `<div class="series-card">
            <div class="series-header">
                <div class="series-title-group">
                    <h3>${escapeHtml(m.name)}</h3>
                    <span class="count">${escapeHtml(m.time)}</span>
                </div>
                <div class="series-actions">
                    <button class="btn btn-danger btn-sm" onclick="deleteMessage('${m.id}')">删除</button>
                </div>
            </div>
            <div class="article-item">
                <span class="article-title">${escapeHtml(m.content)}</span>
            </div>
        </div>`;
    }
    container.innerHTML = html;
}

async function deleteMessage(msgId) {
    if (!confirm('确定要删除这条留言吗？')) return;
    showStatus('正在删除...', 'info');
    try {
        const res = await fetch(`/api/messages/${msgId}`, { method: 'DELETE' });
        const data = await res.json();
        if (data.success) {
            showStatus('留言已删除', 'success');
            await fetchMessages();
        } else {
            showStatus(data.message || '删除失败', 'error');
        }
    } catch (e) {
        showStatus('请求失败: ' + e.message, 'error');
    }
}

async function fetchAbout() {
    try {
        const res = await fetch('/api/about');
        const data = await res.json();
        document.getElementById('aboutName').value = data.name || '';
        document.getElementById('aboutTexts').value = (data.texts || []).join('\n');
    } catch (e) {}
}

async function saveAbout() {
    const name = document.getElementById('aboutName').value.trim();
    if (!name) { showStatus('姓名不能为空', 'error'); return; }
    const texts = document.getElementById('aboutTexts').value.split('\n').map(t => t.trim()).filter(t => t);
    showStatus('正在保存...', 'info');
    try {
        const res = await fetch('/api/about', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, texts })
        });
        const data = await res.json();
        showStatus(data.message, data.success ? 'success' : 'error');
    } catch (e) {
        showStatus('请求失败: ' + e.message, 'error');
    }
}

async function fetchGallery() {
    try {
        const res = await fetch('/api/gallery');
        galleryData = await res.json();
        renderGallery();
    } catch (e) {}
}

function renderGallery() {
    const container = document.getElementById('galleryList');
    if (!galleryData.length) {
        container.innerHTML = '<p style="color:#999;">暂无照片</p>';
        return;
    }
    let html = '';
    for (let i = 0; i < galleryData.length; i++) {
        const img = galleryData[i];
        html += `<div style="position:relative;width:150px;text-align:center;">
            <img src="/gallery_images/${img.filename}" style="width:150px;height:100px;object-fit:cover;border-radius:4px;">
            <div style="font-size:12px;color:#666;margin-top:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(img.alt)}</div>
            <div style="margin-top:4px;display:flex;gap:4px;justify-content:center;">
                <button class="btn btn-secondary btn-sm" onclick="moveGalleryImage(${i},-1)" ${i===0?'disabled':''} style="padding:2px 8px;">↑</button>
                <button class="btn btn-secondary btn-sm" onclick="moveGalleryImage(${i},1)" ${i===galleryData.length-1?'disabled':''} style="padding:2px 8px;">↓</button>
                <button class="btn btn-danger btn-sm" onclick="deleteGalleryImage('${img.id}')" style="padding:2px 8px;">删除</button>
            </div>
        </div>`;
    }
    container.innerHTML = html;
}

async function uploadGalleryImage() {
    const input = document.getElementById('galleryFileInput');
    if (!input.files.length) { showStatus('请选择图片', 'error'); return; }
    showStatus(`正在上传 ${input.files.length} 张图片...`, 'info');
    try {
        const formData = new FormData();
        for (const file of input.files) {
            formData.append('images', file);
        }
        formData.append('alt', document.getElementById('galleryAltInput').value.trim());
        const res = await fetch('/api/gallery/upload', { method: 'POST', body: formData });
        const data = await res.json();
        if (data.error) {
            showStatus(data.error, 'error');
            return;
        }
        if (data.success) {
            showStatus(data.message || '上传成功', 'success');
            input.value = '';
            document.getElementById('galleryAltInput').value = '';
            document.getElementById('galleryFileLabel').textContent = '点击选择图片（可多选）';
            await fetchGallery();
        } else {
            showStatus(data.message || '上传失败', 'error');
        }
    } catch (e) {
        showStatus('请求失败: ' + e.message, 'error');
    }
}

function handleGalleryFile(input) {
    const label = document.getElementById('galleryFileLabel');
    if (input.files.length === 1) {
        label.textContent = `已选择: ${input.files[0].name}`;
    } else if (input.files.length > 1) {
        label.textContent = `已选择 ${input.files.length} 张图片`;
    } else {
        label.textContent = '点击选择图片（可多选）';
    }
}

async function deleteGalleryImage(imgId) {
    if (!confirm('确定要删除这张照片吗？')) return;
    showStatus('正在删除...', 'info');
    try {
        const res = await fetch(`/api/gallery/${imgId}`, { method: 'DELETE' });
        const data = await res.json();
        if (data.success) {
            showStatus('照片已删除', 'success');
            await fetchGallery();
        } else {
            showStatus(data.message || '删除失败', 'error');
        }
    } catch (e) {
        showStatus('请求失败: ' + e.message, 'error');
    }
}

async function moveGalleryImage(idx, direction) {
    const newIdx = idx + direction;
    if (newIdx < 0 || newIdx >= galleryData.length) return;
    const ids = galleryData.map(img => img.id);
    [ids[idx], ids[newIdx]] = [ids[newIdx], ids[idx]];
    showStatus('正在调整顺序...', 'info');
    try {
        const res = await fetch('/api/gallery/reorder', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ids })
        });
        const data = await res.json();
        if (data.success) {
            showStatus('顺序已调整', 'success');
            await fetchGallery();
        } else {
            showStatus(data.message || '调整失败', 'error');
        }
    } catch (e) {
        showStatus('请求失败: ' + e.message, 'error');
    }
}
