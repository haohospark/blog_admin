package com.myblog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.myblog.config.BlogProperties;
import com.myblog.model.About;
import com.myblog.model.Message;
import com.myblog.model.SeriesMeta;
import com.myblog.repository.JsonStorageRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonStorageRepositoryTest {

    @TempDir
    Path tmp;

    JsonStorageRepository storage;

    @BeforeEach
    void setUp() {
        BlogProperties props = new BlogProperties(tmp, null, null, null, "admin", "123456");
        storage = new JsonStorageRepository(props, new ObjectMapper());
        storage.ensureDirs();
    }

    @Test
    void metaRoundTripKeepsOrderAndChinese() throws IOException {
        LinkedHashMap<String, SeriesMeta> meta = new LinkedHashMap<>();
        SeriesMeta first = new SeriesMeta();
        first.setTitle("课程笔记");
        first.setSrcDir("课程笔记");
        first.setDynamic(true);
        first.setOrder(1);
        SeriesMeta second = new SeriesMeta();
        second.setTitle("生活随笔");
        second.setSrcDir("生活随笔");
        second.setDynamic(true);
        second.setOrder(2);
        meta.put("课程笔记", first);
        meta.put("生活随笔", second);

        storage.saveMeta(meta);
        LinkedHashMap<String, SeriesMeta> loaded = storage.loadMeta();

        assertEquals(List.of("课程笔记", "生活随笔"), List.copyOf(loaded.keySet()));
        assertEquals("课程笔记", loaded.get("课程笔记").getTitle());
        assertEquals(2, loaded.get("生活随笔").getOrder());
        // desc 为空时应省略键
        assertNull(loaded.get("课程笔记").getDesc());

        String content = Files.readString(props().metaFile());
        assertTrue(content.contains("课程笔记"), "中文未原样写出");
        assertTrue(content.contains("\n  \"课程笔记\""), "缩进应为两空格");
    }

    @Test
    void aboutFallsBackToDefaultAndWritesFile() throws IOException {
        About about = storage.loadAbout();
        assertEquals("云山蓝", about.getName());
        assertEquals(3, about.getTexts().size());
        assertTrue(Files.exists(props().aboutFile()));
        About reloaded = storage.loadAbout();
        assertEquals(about.getName(), reloaded.getName());
        assertEquals(about.getTexts(), reloaded.getTexts());
    }

    @Test
    void messagesInsertAtHeadAndReload() {
        storage.saveMessages(new ArrayList<>(List.of(new Message("1", "张三", "第一层", "2026/10/17"))));
        List<Message> loaded = storage.loadMessages();
        loaded.add(0, new Message("2", "李四", "最新留言", "2026/10/18"));
        storage.saveMessages(loaded);

        List<Message> reloaded = storage.loadMessages();
        assertEquals(2, reloaded.size());
        assertEquals("李四", reloaded.get(0).getName());
    }

    // 校验序列化风格：两空格缩进、中文不转义、键顺序稳定，使 conf/ 下 JSON diff 友好。
    @Test
    void jsonOutputUsesTwoSpaceIndentAndUnescapedUnicode() throws IOException {
        storage.saveMessages(new ArrayList<>(List.of(new Message("1", "张三", "第一层", "2026-10-17"))));
        String lf = System.lineSeparator();
        assertEquals("[" + lf
                + "  {" + lf
                + "    \"id\": \"1\"," + lf
                + "    \"name\": \"张三\"," + lf
                + "    \"content\": \"第一层\"," + lf
                + "    \"time\": \"2026-10-17\"" + lf
                + "  }" + lf
                + "]", Files.readString(props().messagesFile()));

        storage.saveMessages(new ArrayList<>());
        assertEquals("[]", Files.readString(props().messagesFile()));

        storage.saveMeta(new LinkedHashMap<>());
        assertEquals("{}", Files.readString(props().metaFile()));
    }

    private BlogProperties props() {
        return new BlogProperties(tmp, null, null, null, "admin", "123456");
    }
}
