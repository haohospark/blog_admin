package com.myblog.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// conf/.series_meta.json 中单个系列的条目。
// 缺省字段序列化时省略，保持文件内容简洁、可比对。
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeriesMeta {

    @JsonProperty("title")
    private String title;

    @JsonProperty("desc")
    private String desc;

    // note/ 下的目录名（系列重命名时同步变更）
    @JsonProperty("src_dir")
    private String srcDir;

    @JsonProperty("dynamic")
    private Boolean dynamic;

    @JsonProperty("order")
    private Integer order;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public String getSrcDir() { return srcDir; }
    public void setSrcDir(String srcDir) { this.srcDir = srcDir; }

    public Boolean getDynamic() { return dynamic; }
    public void setDynamic(Boolean dynamic) { this.dynamic = dynamic; }

    public Integer getOrder() { return order; }
    public void setOrder(Integer order) { this.order = order; }
}
