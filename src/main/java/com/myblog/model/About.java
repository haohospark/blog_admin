package com.myblog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// conf/.about.json 的结构：姓名 + 介绍段落列表。
public class About {

    @JsonProperty("name")
    private String name;

    @JsonProperty("texts")
    private List<String> texts;

    public About() { }

    public About(String name, List<String> texts) {
        this.name = name;
        this.texts = texts;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getTexts() { return texts; }
    public void setTexts(List<String> texts) { this.texts = texts; }
}
