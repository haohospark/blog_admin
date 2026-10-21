package com.myblog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// conf/.gallery.json 中的单张相册图片元数据。
public class GalleryImage {

    @JsonProperty("id")
    private String id;

    // gallery_images/ 下的文件名（YYYYMMDD_HHMMSS[.ext|_n.ext]）
    @JsonProperty("filename")
    private String filename;

    @JsonProperty("alt")
    private String alt;

    public GalleryImage() { }

    public GalleryImage(String id, String filename, String alt) {
        this.id = id;
        this.filename = filename;
        this.alt = alt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getAlt() { return alt; }
    public void setAlt(String alt) { this.alt = alt; }
}
