package com.myblog.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.myblog.model.Message;
import com.myblog.repository.JsonStorageRepository;
import com.myblog.service.MessageService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 访客留言接口（表现层）。
//
// 职责：HTTP 请求映射、参数校验、响应封装；
// 留言的存储与时间格式化委托给 MessageService。
//
// 列表与删除需要登录，公开列表与提交留言对访客开放（故按方法标注 @LoginRequired）。
@RestController
public class MessageController {

    private final JsonStorageRepository storage;
    private final MessageService messageService;

    public MessageController(JsonStorageRepository storage, MessageService messageService) {
        this.storage = storage;
        this.messageService = messageService;
    }

    // 返回全部留言（含 id，管理后台用）。
    @GetMapping("/api/messages")
    @LoginRequired
    public ResponseEntity<List<Message>> listMessages() {
        return ResponseEntity.ok(storage.loadMessages());
    }

    // 公开接口：返回留言的展示字段（姓名、内容、时间），供留言板页面调用。
    @GetMapping("/api/messages/public")
    public ResponseEntity<List<Map<String, String>>> publicMessages() {
        List<Map<String, String>> body = new ArrayList<>();
        for (Message m : storage.loadMessages()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("name", m.getName());
            item.put("content", m.getContent());
            item.put("time", m.getTime());
            body.add(item);
        }
        return ResponseEntity.ok(body);
    }

    // 公开接口：访客提交留言，插入到列表头部。
    @PostMapping("/api/messages")
    public ResponseEntity<Map<String, Object>> submitMessage(@RequestBody Map<String, String> body) {
        String name = trim(body.get("name"));
        String content = trim(body.get("content"));
        if (name.isEmpty() || content.isEmpty()) {
            return Api.error(400, "姓名和留言内容不能为空");
        }
        messageService.submitMessage(name, content);
        return ResponseEntity.ok(Api.result(true, "留言提交成功"));
    }

    // 按 id 删除指定留言。
    @DeleteMapping("/api/messages/{msgId}")
    @LoginRequired
    public ResponseEntity<Map<String, Object>> deleteMessage(@PathVariable String msgId) {
        messageService.deleteMessage(msgId);
        return ResponseEntity.ok(Api.result(true, "留言已删除"));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
