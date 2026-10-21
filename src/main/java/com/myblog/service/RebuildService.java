package com.myblog.service;

import org.springframework.stereotype.Service;

// "重建站点"编排：重新生成静态页面。
@Service
public class RebuildService {

    private final SiteBuildService build;

    public RebuildService(SiteBuildService build) {
        this.build = build;
    }

    // 重建的结果：success 为 false 时 message 为失败原因。
    public record Result(boolean success, String message) { }

    // 重建静态站；构建过程中的任何异常都转换为失败结果返回，不向上抛。
    public Result rebuild() {
        try {
            build.buildAll();
            return new Result(true, "重建完成");
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            return new Result(false, msg != null && !msg.isBlank() ? msg : e.getClass().getSimpleName());
        }
    }
}
