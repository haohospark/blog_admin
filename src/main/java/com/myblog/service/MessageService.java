package com.myblog.service;

import org.springframework.stereotype.Service;
import com.myblog.model.Message;
import com.myblog.repository.JsonStorageRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// 留言业务层：处理留言的提交与删除。
//
// 三层架构中的位置：Controller → Service → Repository。
@Service
public class MessageService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/M/d");

    private final JsonStorageRepository storage;

    public MessageService(JsonStorageRepository storage) {
        this.storage = storage;
    }

    // 访客提交留言，插入到列表头部。
    public void submitMessage(String name, String content) {
        List<Message> messages = new ArrayList<>(storage.loadMessages());
        messages.add(0, new Message(
                String.valueOf(System.currentTimeMillis()),
                name,
                content,
                LocalDateTime.now().format(TIME_FORMAT)
        ));
        storage.saveMessages(messages);
    }

    // 按 id 删除指定留言。
    public void deleteMessage(String msgId) {
        List<Message> messages = storage.loadMessages().stream()
                .filter(m -> !msgId.equals(m.getId()))
                .toList();
        storage.saveMessages(messages);
    }
}
