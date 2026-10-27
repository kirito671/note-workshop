# Note-Workshop

面向在校学生的读书笔记社区项目。用户可以创建和管理读书笔记、上传多张笔记图片、控制私有/公开可见性，并参与后续的点赞、评论、关注和 AI 阅读辅助功能。

## 项目概况

项目采用 Java 后端分层架构：

```text
Controller -> Service -> Mapper -> MySQL
```

当前按照以下优先级迭代：

```text
P0 基础要求 -> P1 进阶功能 -> P2 AI 加分功能 -> P3 提交与答辩
```

最终展示和验收以答辩时提交的仓库版本为准。

## 当前进度

当前已完成 P0-5 读书笔记与图片模块。

已完成功能：

### 基础框架与规范

- Spring Boot 项目骨架
- Maven 构建配置
- MyBatis-Plus 与 MySQL 配置
- HikariCP 数据库连接池
- 统一响应结构 `ApiResponse<T>`
- 分页响应结构 `PageResponse<T>`
- 错误码枚举 `ErrorCode`
- 自定义业务异常 `BusinessException`
- 全局异常处理
- 参数校验异常处理
- 文件上传超限异常处理
- SLF4J + Logback 日志

### 用户与鉴权

- 用户注册
- BCrypt 密码哈希
- 用户登录与 JWT 生成
- JWT Interceptor 鉴权
- `UserContext` 保存当前登录用户
- 当前用户资料查询
- 昵称修改
- 头像上传
- 本地头像存储与 `/uploads/**` 静态访问
- 作者与管理员权限判断
- 禁用账号拒绝登录

### 读书笔记与图片

- 创建读书笔记
- 编辑和删除读书笔记
- 私有/公开可见性
- 作者本人或管理员操作权限
- 我的笔记分页查询
- 公开笔记分页查询
- 标题或图书名称关键词搜索
- 笔记详情查询
- 多图上传
- 图片格式、大小和真实内容校验
- 图片排序
- 单图删除
- 数据库记录与磁盘文件补偿清理
- 图片保存路径为 `uploads/notes/{noteId}/{UUID}.{ext}`

### 自动化测试

当前测试结果：

```text
Tests run: 55, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

覆盖注册、登录、JWT、拦截器、用户资料、头像上传、笔记 CRUD、分页、私有笔记隔离、多图上传、图片删除、图片排序和文件补偿删除。

### 尚未实现

- 公开笔记点赞、取消点赞、我点赞的笔记和点赞排行
- 管理员后台与公开笔记审核
- 关注作者、粉丝统计和热门作者
- 公开笔记评论与多级回复
- AI 阅读辅助
- AI 智能客服
- 正式前端页面
- 正式接口文档

## 技术栈

当前已使用：

- Java 25
- Spring Boot 3.5.6
- MyBatis-Plus 3.5.12
- MySQL 8
- Maven
- JJWT 0.12.6
- Spring Security Crypto（BCrypt）
- Jakarta Validation
- SLF4J + Logback
- 本地文件存储

后续计划：

- Spring AI（P2，可选加分项）
- HTML / CSS / JavaScript 前端

本项目不引入 Redis、消息队列、微服务或其他未要求中间件。

## 当前接口

### 公开接口

```text
POST /api/auth/register
POST /api/auth/login
GET  /uploads/**
```

### 用户资料接口

```text
GET  /api/users/me
PUT  /api/users/me
POST /api/users/me/avatar
```

### 读书笔记接口

```text
POST   /api/notes
GET    /api/notes/my
GET    /api/notes/public
GET    /api/notes/{id}
PUT    /api/notes/{id}
DELETE /api/notes/{id}
```

### 笔记图片接口

```text
POST   /api/notes/{id}/images
DELETE /api/notes/{id}/images/{imageId}
PUT    /api/notes/{id}/images/order
```

除注册、登录和静态文件访问外，其余接口需要携带：

```http
Authorization: Bearer <token>
```

## 数据库

初始建表脚本：

- [docs/sql/schema.sql](docs/sql/schema.sql)

当前表：

- `user`
- `note`
- `note_image`
- `note_like`

说明：脚本包含 `DROP TABLE`，只适合本地初始化或开发阶段重置数据库，不要直接用于已有数据的环境。

## 上传限制

- 头像单文件最大 5MB。
- 单张笔记图片最大 5MB。
- 一条笔记最多保存 6 张图片。
- 仅支持 `jpg`、`jpeg`、`png`。
- multipart 请求最大 32MB。
- 上传文件不会提交到 Git。

## 环境变量

禁止将数据库密码、JWT secret、OSS accessKey 或 AI API key 写入并提交到仓库。

当前支持的环境变量：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
SPRING_PROFILES_ACTIVE

JWT_SECRET
JWT_EXPIRATION_MINUTES

UPLOAD_DIR
MAX_AVATAR_SIZE_BYTES
MAX_NOTE_IMAGE_SIZE_BYTES
MAX_NOTE_IMAGE_COUNT
```

说明：

- `JWT_SECRET` 至少需要 32 字节，不得提交到 Git。
- `JWT_EXPIRATION_MINUTES` 默认 120 分钟。
- `UPLOAD_DIR` 默认 `uploads`。
- `MAX_AVATAR_SIZE_BYTES` 默认 5242880。
- `MAX_NOTE_IMAGE_SIZE_BYTES` 默认 5242880。
- `MAX_NOTE_IMAGE_COUNT` 默认 6。
- 本地数据库密码可以放在 `src/main/resources/application-local.yml`，该文件已被 `.gitignore` 排除。

## 项目结构

```text
.
├── docs/
│   └── sql/
│       └── schema.sql
├── src/main/java/com/example/noteworkshop/
│   ├── common/
│   ├── config/
│   ├── controller/
│   ├── dto/
│   ├── entity/
│   ├── exception/
│   ├── mapper/
│   ├── security/
│   ├── service/
│   └── vo/
├── src/main/resources/
│   └── application.yml
├── src/test/java/
├── .gitignore
├── pom.xml
└── README.md
```

## 运行方式

### IDEA

1. 导入项目 `pom.xml`。
2. 刷新 Maven 依赖。
3. 启动本地 MySQL。
4. 使用 `docs/sql/schema.sql` 初始化数据库。
5. 配置数据库密码和 JWT 环境变量。
6. 运行 `NoteworkshopApplication`。

### Maven

```powershell
mvn spring-boot:run
```

## 测试

运行全部测试：

```powershell
mvn test
```

PowerShell 示例：

```powershell
$env:JWT_SECRET="test-only-jwt-secret-please-change-1234567890"
$env:JWT_EXPIRATION_MINUTES="120"

mvn test
```

当前结果：

```text
Tests run: 55, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 说明

当前版本已完成用户注册登录、JWT 鉴权、个人资料、头像上传、读书笔记 CRUD、分页、公开/私有控制、多图上传、图片删除和图片排序。

点赞、管理员后台、审核、社区互动、AI 和正式前端尚未完成。仓库会继续更新，最终以答辩时提交的版本为准。