// 系列与文章管理：列表渲染、折叠、排序、增删改，以及笔记文件夹上传。

let articlesData = {};
let expandedSeries = {};  // 系列展开状态，默认折叠
let categories = [];
let pendingDelete = null;
let pendingEdit = null;

async function fetchArticles() {
    const res = await fetch('/api/articles');
    articlesData = await res.json();
    renderArticles();
}

async function fetchCategories() {
    const res = await fetch('/api/categories');
    categories = await res.json();
    const uploadSelect = document.getElementById('uploadCategory');
    const opts = categories.map(c => `<option value="${c.key}">${c.title}</option>`).join('');
    uploadSelect.innerHTML = opts;
}

async function moveSeries(catKey, direction) {
    const keys = Object.keys(articlesData);
    const idx = keys.indexOf(catKey);
    if (idx === -1) return;
    const newIdx = idx + direction;
    if (newIdx < 0 || newIdx >= keys.length) return;

    const newKeys = [...keys];
    const [moved] = newKeys.splice(idx, 1);
    newKeys.splice(newIdx, 0, moved);

    showStatus('正在调整顺序...', 'info');
    try {
        await fetch('/api/series/reorder', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ keys: newKeys })
        });
        await fetchArticles();
        await fetchCategories();
        showStatus('顺序已调整', 'success');
    } catch (e) {
        showStatus('请求失败: ' + e.message, 'error');
    }
}

function renderArticles() {
    const container = document.getElementById('articlesList');
    let html = '';
    const keys = Object.keys(articlesData);
    for (let i = 0; i < keys.length; i++) {
        const key = keys[i];
        const data = articlesData[key];
        const desc = data.desc || '';
        const expanded = !!expandedSeries[key];
        html += `<div class="series-card">
            <div class="series-header collapsible" onclick="toggleSeries('${key}')">
                <div class="series-title-group">
                    <span class="collapse-icon">${expanded ? '▼' : '▶'}</span>
                    <h3>${data.title}</h3>
                    <span class="count">(${data.count} 篇)</span>
                </div>
                <div class="series-actions" onclick="event.stopPropagation()">
                    <button class="btn btn-secondary btn-sm" onclick="moveSeries('${key}', -1)" ${i === 0 ? 'disabled' : ''}>↑</button>
                    <button class="btn btn-secondary btn-sm" onclick="moveSeries('${key}', 1)" ${i === keys.length - 1 ? 'disabled' : ''}>↓</button>
                    <button class="btn btn-warning btn-sm" onclick="showEditSeriesModal('${key}', '${data.title.replace(/'/g, "\\'")}', '${desc.replace(/'/g, "\\'")}')">编辑</button>
                    <button class="btn btn-danger btn-sm" onclick="confirmDeleteSeries('${key}', '${data.title.replace(/'/g, "\\'")}')">删除系列</button>
                </div>
            </div>
            <ul class="article-list" ${expanded ? '' : 'style="display:none"'}>`;
        for (const article of data.articles) {
            html += `<li class="article-item">
                <span class="article-title">${article.title}</span>
                <div class="article-actions">
                    <button class="btn btn-danger btn-sm" onclick="confirmDeleteArticle('${key}', '${article.slug}', '${article.title.replace(/'/g, "\\'")}')">删除</button>
                </div>
            </li>`;
        }
        html += `</ul></div>`;
    }
    container.innerHTML = html || '<p style="text-align:center;color:#999;">暂无系列</p>';
}

function toggleSeries(key) {
    expandedSeries[key] = !expandedSeries[key];
    renderArticles();
}

function confirmDeleteArticle(catKey, slug, title) {
    pendingDelete = { type: 'article', catKey, slug };
    document.getElementById('deleteMessage').textContent = `确定要删除文章"${title}"吗？此操作不可恢复。`;
    document.getElementById('deleteOptions').innerHTML = `
        <div class="modal-option" onclick="selectDeleteOption('keep')">
            <input type="radio" name="deleteImgOpt" value="keep" checked>
            <div>
                <div class="modal-option-label">保留图片</div>
                <div class="modal-option-desc">只删除文章，保留所有图片文件</div>
            </div>
        </div>
        <div class="modal-option" onclick="selectDeleteOption('refs')">
            <input type="radio" name="deleteImgOpt" value="refs">
            <div>
                <div class="modal-option-label">删除引用的图片</div>
                <div class="modal-option-desc">删除文章及其引用的图片文件</div>
            </div>
        </div>
    `;
    document.getElementById('deleteModal').classList.add('active');
}

function selectDeleteOption(value) {
    document.querySelector(`input[name="deleteImgOpt"][value="${value}"]`).checked = true;
}

function confirmDeleteSeries(catKey, title) {
    pendingDelete = { type: 'series', catKey };
    document.getElementById('deleteMessage').textContent = `确定要删除整个系列"${title}"吗？此操作不可恢复。`;
    document.getElementById('deleteOptions').innerHTML = '';
    document.getElementById('deleteModal').classList.add('active');
}

function closeModal() {
    document.getElementById('deleteModal').classList.remove('active');
    pendingDelete = null;
}

function showEditSeriesModal(catKey, currentName, currentDesc) {
    pendingEdit = { catKey };
    document.getElementById('editSeriesName').value = currentName;
    document.getElementById('editSeriesDesc').value = currentDesc || '';
    document.getElementById('editSeriesModal').classList.add('active');
}

function closeEditModal() {
    document.getElementById('editSeriesModal').classList.remove('active');
    pendingEdit = null;
}

function showCreateSeriesModal() {
    document.getElementById('newSeriesName').value = '';
    document.getElementById('newSeriesDesc').value = '';
    document.getElementById('createSeriesModal').classList.add('active');
}

function closeCreateModal() {
    document.getElementById('createSeriesModal').classList.remove('active');
}

document.getElementById('confirmDeleteBtn').onclick = async () => {
    if (!pendingDelete) return;
    const target = pendingDelete;
    closeModal();
    showStatus('正在删除...', 'info');
    try {
        let url, opts = { method: 'DELETE' };
        if (target.type === 'article') {
            const imgOpt = document.querySelector('input[name="deleteImgOpt"]:checked');
            const deleteImages = imgOpt && imgOpt.value === 'refs';
            url = `/api/articles/${target.catKey}/${target.slug}?delete_images=${deleteImages}`;
        } else {
            url = `/api/series/${target.catKey}`;
        }
        const res = await fetch(url, opts);
        const data = await res.json();
        if (data.success) {
            showStatus(data.message, 'success');
            await fetchArticles();
            await fetchCategories();
        } else {
            showStatus(data.message || '删除失败', 'error');
        }
    } catch (e) {
        showStatus('请求失败: ' + e.message, 'error');
    }
};

document.getElementById('confirmEditBtn').onclick = async () => {
    if (!pendingEdit) return;
    const edit = pendingEdit;
    const newName = document.getElementById('editSeriesName').value.trim();
    const newDesc = document.getElementById('editSeriesDesc').value.trim();
    if (!newName) {
        showStatus('名称不能为空', 'error');
        return;
    }
    closeEditModal();
    showStatus('正在更新...', 'info');
    try {
        const res = await fetch(`/api/series/${edit.catKey}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: newName, desc: newDesc })
        });
        const data = await res.json();
        if (data.success) {
            showStatus(data.message, 'success');
            await fetchArticles();
            await fetchCategories();
        } else {
            showStatus(data.message || '更新失败', 'error');
        }
    } catch (e) {
        showStatus('请求失败: ' + e.message, 'error');
    }
};

document.getElementById('confirmCreateBtn').onclick = async () => {
    const name = document.getElementById('newSeriesName').value.trim();
    const desc = document.getElementById('newSeriesDesc').value.trim();
    if (!name) {
        showStatus('名称不能为空', 'error');
        return;
    }
    closeCreateModal();
    showStatus('正在创建...', 'info');
    try {
        const res = await fetch('/api/series', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, desc })
        });
        const data = await res.json();
        if (data.success) {
            showStatus(data.message, 'success');
            await fetchArticles();
            await fetchCategories();
        } else {
            showStatus(data.message || '创建失败', 'error');
        }
    } catch (e) {
        showStatus('请求失败: ' + e.message, 'error');
    }
};

function handleUploadFolder(input) {
    const list = document.getElementById('uploadFolderFileList');
    const label = document.getElementById('uploadFolderLabel');
    if (input.files.length > 0) {
        label.textContent = `已选择文件夹（${input.files.length} 个文件）`;
        list.innerHTML = Array.from(input.files).slice(0, 10).map(f =>
            `<div class="file-list-item"><span>${f.webkitRelativePath}</span><span>${(f.size/1024).toFixed(1)} KB</span></div>`
        ).join('');
        if (input.files.length > 10) {
            list.innerHTML += `<div class="file-list-item" style="color:#666;">...还有 ${input.files.length - 10} 个文件</div>`;
        }
    } else {
        label.textContent = '点击选择文件夹（自动扫描 Markdown 和图片）';
        list.innerHTML = '';
    }
}

async function doUploadFolder() {
    const catKey = document.getElementById('uploadCategory').value;
    const input = document.getElementById('uploadFolderInput');
    if (!input.files.length) {
        showStatus('请选择文件夹', 'error');
        return;
    }
    showStatus('正在上传...', 'info');
    try {
        const formData = new FormData();
        formData.append('category', catKey);
        for (const f of input.files) {
            if (f.name.endsWith('.md')) {
                formData.append('md_files', f);
            } else if (/\.(png|jpe?g|gif|svg|webp|bmp|ico)$/i.test(f.name)) {
                formData.append('img_files', f);
            }
        }
        const res = await fetch('/api/upload', { method: 'POST', body: formData });
        const data = await res.json();
        if (data.success) {
            showStatus(`上传成功！${(data.uploaded_md || []).length} 个 Markdown 文件，${data.uploaded_images} 个图片`, 'success');
            input.value = '';
            document.getElementById('uploadFolderFileList').innerHTML = '';
            document.getElementById('uploadFolderLabel').textContent = '点击选择文件夹（自动扫描 Markdown 和图片）';
            await fetchArticles();
        } else {
            showStatus(data.error || data.message || '上传失败', 'error');
        }
    } catch (e) {
        showStatus('请求失败: ' + e.message, 'error');
    }
}
