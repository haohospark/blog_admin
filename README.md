# 个人博客网站

## 部署过程

若想要部署，首先需要准备以下环境

1. Java环境

   你可以执行：`java --version` 来判断当前电脑是否具备 Java 环境

2. pandoc环境

   pandoc 是一款可以将 .md 文件转换为网页的工具

   这需要你去下载 pandoc，并把其添加到环境变量中，可以执行 `pandoc --version` 来判断是否具备 pandoc 环境

## 管理后台

`blog_admin/` 是一个博客管理后台程序，基于 Spring Boot 3.5（Java 17）

前台为静态生成，数据以文件（JSON + Markdown）存储

```
blog_admin/
├── pom.xml                    # Maven 工程
└── src/main/
    ├── java/com/myblog/
    │   ├── BlogAdminApplication.java
    │   ├── config/            # 路径与账号配置、路由注册
    │   ├── model/             # 数据模型
    │   ├── repository/        # JSON 文件读写
    │   ├── service/           # 业务逻辑与站点构建
    │   └── web/               # Controller 与登录鉴权
    └── resources/
        ├── application.yml    # 端口与管理员账号
        ├── templates/         # 后台页面骨架与它的 CSS/JS
        └── build-templates/   # 前台页面模板
```

各位置的关键文件与职责：

| 位置 | 关键文件与职责 |
|---|---|
| `web/` | `AuthController` / `SeriesController` / `ArticleController` / `AboutController` / `GalleryController` / `MessageController`；鉴权基础设施 `LoginInterceptor`、`@LoginRequired`；`AdminPageRenderer`、`Api` |
| `service/` | `SeriesService`（系列 CRUD + 文章查询解析）、`ArticleService`（笔记上传/删除）、`GalleryService`（相册上传/删除/排序）、`MessageService`（留言提交/删除）、`AboutService`（关于我读写）、`SiteBuildService`（静态站点构建引擎，含 `PandocRunner`）、`RebuildService`（重建编排）、`MathNormalizer` / `TemplateFormatter`（构建辅助工具） |
| `repository/` | `JsonStorageRepository`（JSON 配置文件的读写与持久化） |
| `config/` | `BlogProperties`（路径与账号）、`WebConfig`（拦截器注册 + 后台 CSS/JS 挂到 `/admin/*.css`、`/admin/*.js` + 构建产物挂到根路径 `/**`） |
| `model/` | `SeriesMeta` / `Message` / `About` / `GalleryImage`（字段名与 `conf/*.json` 一致） |
| `resources/` | `application.yml`（全部配置项）；`build-templates/`（`article` / `articles_list` / `index` / `about` / `gallery` / `message`） |
| `templates/` | 后台页面与其同目录资源，资源经 `/admin/*.css`、`/admin/*.js` 访问（只放行这两种后缀，模板原文不外泄）：`admin.html`（页面骨架，登录态由 HTML 注释标记的三段式块切换，见 `AdminPageRenderer`）、`admin.css`（样式）、`login.js`（未登录：登录表单）、`admin-core.js`（401 兜底 + 状态提示 / 标签页 / 转义 / 重建 / 退出 + 首屏拉取）、`admin-series.js`（系列与文章、笔记上传）、`admin-content.js`（留言、关于我、相册） |
| `pom.xml` | Spring Boot 3.5 / Java 17，`groupId=com.myblog`、`artifactId=blog_admin` |

## 三层架构

- 表现层 `web/`：HTTP 请求映射、参数校验、响应封装、重建编排
- 业务层 `service/`：业务规则、文件操作、站点构建、数据转换
- 数据层 `repository/`：JSON 配置文件的序列化与持久化

Controller 不包含业务逻辑，仅做请求→服务→响应的转发与编排

- 前台为静态生成，数据以文件存储；接口路径与响应结构需与前端 `admin.html` 硬编码的调用保持一致

- 需要登录的接口标注 `@LoginRequired`，未登录返回 `401 {"error": "未登录"}`

- `conf/*.json` 采用两空格缩进、中文不转义、null 字段省略、键顺序稳定，diff 友好

- 系列含多个子目录时，文章按子目录名排序遍历

- 路由（单端口 5050）：`/admin` 为管理后台页面，`/api/**` 为全部 JSON 接口，网站根路径 `/**` 直接对外提供 `website/` 静态站（`/` 重定向到 `/index.html`）

- 本地运行（未显式配置 `blog.data-dir` 时，数据目录默认代码所在的 `blog_admin/` 模块目录；该默认按类路径定位，与启动时的工作目录无关）：

  ```bash
  cd blog_admin
  mvn spring-boot:run            # 管理后台 http://127.0.0.1:5050/admin
                                 # 前台博客站 http://127.0.0.1:5050/
  ```

  前台页面由构建生成：登录后台后触发一次重建（`POST /api/rebuild`，或在后台做任意写操作），
  产物输出到 `blog_admin/website/`，根路径即时可访问。

- 离线打包分发：`mvn -DskipTests package` 生成 `target/blog_admin-1.0.0.jar`，与数据目录并排拷贝即可独立运行：

  ```text
  blog_admin/
  ├── blog_admin-1.0.0.jar
  ├── conf/            # JSON 数据（含 .series_meta.json 等点文件，拷贝时勿漏）
  ├── note/            # 笔记 Markdown
  ├── website/         # 前台静态站
  └── start.bat        # java -jar blog_admin-1.0.0.jar
  ```

  未配置 `blog.data-dir` 时，jar 会自动定位到自身所在目录（fat jar 内 CodeSource 为
  `jar:nested:` 虚拟路径，需解析出外层 jar 的真实路径），因此从任意工作目录启动均可。

- 配置项：全部在 `application.yml`（`server.port`、`blog.admin-username`、`blog.admin-password`），账号默认 `user` / `123`

本项目仅做本地演示，不包含服务器部署与同步功能
