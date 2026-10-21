package com.myblog.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// conf/.messages.json 中的单条留言。
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("content")
    private String content;

    // 展示格式：yyyy/M/d
    @JsonProperty("time")
    private String time;

    public Message() { }

    public Message(String id, String name, String content, String time) {
        this.id = id;
        this.name = name;
        this.content = content;
        this.time = time;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
}
