package com.myblog.service;

import org.springframework.stereotype.Service;
import com.myblog.model.About;
import com.myblog.repository.JsonStorageRepository;

import java.util.List;

// "关于我"业务层：处理页面数据的读取与更新。
//
// 三层架构中的位置：Controller → Service → Repository。
@Service
public class AboutService {

    private final JsonStorageRepository storage;

    public AboutService(JsonStorageRepository storage) {
        this.storage = storage;
    }

    // 获取"关于我"数据，文件不存在时返回默认值并写盘。
    public About getAbout() {
        return storage.loadAbout();
    }

    // 更新"关于我"的姓名与介绍文本。
    public void updateAbout(String name, List<String> texts) {
        storage.saveAbout(new About(name, texts));
    }
}
