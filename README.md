# Note-Workshop

Note-Workshop 是一个面向在校学生的读书笔记社区。用户可以注册登录、维护个人资料、创建私有或公开笔记、上传多张配图、点赞和评论、关注作者；管理员可以审核公开笔记、管理用户和内容、查看 AI 调用统计。

项目已经完成 P0 基础功能、P1 进阶功能和 P2 AI 加分功能，并提供可直接运行的原生 HTML/CSS/JavaScript 前端。

## 当前状态

```text
P0 基础功能：已完成
P1 进阶功能：已完成
P2 AI 功能：已完成
自动化测试：97 个通过
前端页面：已完成
API 文档：已完成
Postman Collection：已完成
```

## 功能清单

### 用户与鉴权

- 用户注册、登录、JWT 鉴权；
- BCrypt 密码哈希，数据库不保存明文密码；
- 修改昵称、上传头像；
- JWT 拦截器每次请求校验数据库中的账号状态；
- 账号被禁用后，已有 token 立即失效；
- 管理员接口使用独立拦截器校验角色。

### 读书笔记

- 新建、编辑、删除笔记；
- 图书名称使用字符串保存；
- 私有 / 公开两种可见性；
- 公开笔记提交后进入待审核状态；
- 作者可查看审核意见、修改并重新提交；
- 管理员通过、驳回或下架公开笔记；
- 我的笔记、公开书架、关键词搜索均支持分页；
- 笔记详情返回正文、全部配图、点赞状态、审核状态和已保存的 AI 结果；
- 多图上传、单图删除、图片排序；
- 删除笔记时清理图片、点赞、评论和 AI 结果，并在事务提交后删除物理文件。

### 点赞与作者社区

- 公开且审核通过的笔记支持点赞和取消点赞；
- 查看自己点赞过的笔记，支持分页；
- 笔记按点赞数排行；
- 点赞使用唯一索引避免重复数据；
- 关注 / 取消关注作者；
- 作者粉丝数、关注数、公开笔记数统计；
- 热门作者排行；
- 查看作者全部公开笔记。

### 评论

- 公开且审核通过的笔记支持评论；
- 支持多级回复；
- 只能删除自己的评论，管理员可以删除违规评论；
- 删除父评论时，所有子评论级联逻辑删除；
- 评论删除与级联处理在事务中完成。

### AI 阅读辅助

- 为笔记生成摘要、适宜人群、完整性、相关性和深度评分；
- AI 结果不会自动写入笔记主表；
- 用户明确选择保存或丢弃；
- 一篇笔记只保留一份 AI 结果，再次保存会更新原结果；
- 笔记详情只读取 `note_ai_result`，不污染笔记正文；
- 每次分析都写入 `ai_audit_log`；
- 管理员可查看 AI 调用总数、成功数和失败数。

### AI 智能客服

- 新建、查看、切换和删除会话；
- 用户消息与 AI 回复全部持久化；
- 每个会话归属唯一用户，后端逐次校验越权；
- 不同会话上下文互相隔离；
- 使用最近 10 轮消息作为滑动窗口；
- system prompt 限制为平台客服范围；
- 删除会话时同时清理消息；
- 每次客服调用写入 AI 审计日志。

## 技术栈

- Java 25
- Spring Boot 3.5.6
- MyBatis-Plus 3.5.12
- MySQL 8
- Maven
- JJWT 0.12.6
- Spring Security Crypto（BCrypt）
- Jakarta Validation
- SLF4J + Logback
- HikariCP
- HTML / CSS / 原生 JavaScript

项目未引入 Redis、消息队列、微服务或其他未要求的中间件。

## 分层架构

```text
浏览器 / HTML / JavaScript
        ↓ HTTP
Controller：接收请求、参数校验、调用 Service、返回统一响应
        ↓
Service：业务逻辑、权限判断、事务、业务对象组装
        ↓
Mapper：数据库 CRUD
        ↓
MySQL
```

## 数据库

初始脚本：

```text
docs/sql/schema.sql
```

增量脚本：

```text
docs/sql/migration_v2_features.sql
```

主要表：

- `user`：用户、BCrypt 密码、角色、状态；
- `note`：笔记、可见性、审核状态、隐藏状态；
- `note_image`：笔记图片和排序；
- `note_like`：点赞关系；
- `user_follow`：关注关系；
- `note_comment`：评论、父评论和逻辑删除状态；
- `note_ai_result`：一篇笔记最多一份已保存的 AI 结果；
- `ai_audit_log`：AI 调用审计日志；
- `ai_conversation`：AI 客服会话；
- `ai_message`：AI 客服消息。

`docs/sql/schema.sql` 会重建演示数据库，只适合本地开发和答辩环境，不要直接对生产库执行。

## 主要接口

### 认证与用户

```text
POST   /api/auth/register
POST   /api/auth/login

GET    /api/users/me
PUT    /api/users/me
POST   /api/users/me/avatar

POST   /api/users/{id}/follow
DELETE /api/users/{id}/follow
GET    /api/users/{id}/follow-status
GET    /api/users/{id}/profile
GET    /api/users/{id}/stats
GET    /api/users/{id}/notes
GET    /api/users/hot-authors
```

### 笔记、图片与点赞

```text
POST   /api/notes
GET    /api/notes/my
GET    /api/notes/public
GET    /api/notes/liked
GET    /api/notes/ranking
GET    /api/notes/{id}
PUT    /api/notes/{id}
DELETE /api/notes/{id}
POST   /api/notes/{id}/submit

GET    /api/notes/{id}/images
POST   /api/notes/{id}/images
DELETE /api/notes/{id}/images/{imageId}
PUT    /api/notes/{id}/images/order

POST   /api/notes/{id}/like
DELETE /api/notes/{id}/like
```

### 评论

```text
GET    /api/notes/{noteId}/comments
POST   /api/notes/{noteId}/comments
DELETE /api/notes/{noteId}/comments/{commentId}
```

### AI

```text
POST   /api/notes/{id}/ai-analysis
POST   /api/notes/{id}/ai-result
DELETE /api/notes/{id}/ai-result

POST   /api/ai/conversations
GET    /api/ai/conversations
GET    /api/ai/conversations/{id}
POST   /api/ai/conversations/{id}/messages
DELETE /api/ai/conversations/{id}
```

### 管理员

```text
GET    /api/admin/users
PUT    /api/admin/users/{id}/status

GET    /api/admin/notes
GET    /api/admin/notes/pending
PUT    /api/admin/notes/{id}/approve
PUT    /api/admin/notes/{id}/reject
PUT    /api/admin/notes/{id}/hide
DELETE /api/admin/comments/{id}

GET    /api/admin/ai/stats
```

除注册、登录和静态资源外，其余 `/api/**` 接口都需要：

```http
Authorization: Bearer <token>
```

完整接口说明：

- [API 文档](docs/api.md)
- [Postman Collection](docs/postman/note-workshop.postman_collection.json)
- [演示与答辩指南](docs/demo-guide.md)

## AI 运行模式

默认没有配置 `AI_API_KEY` 时，项目使用本地可演示分析器与规则客服，保证答辩环境不依赖外网。

配置 OpenAI 兼容接口后，会自动切换到真实大模型：

```text
AI_API_KEY
AI_BASE_URL=https://api.openai.com/v1
AI_MODEL=gpt-4o-mini
AI_CONNECT_TIMEOUT_SECONDS=10
AI_READ_TIMEOUT_SECONDS=45
```

API key 只能通过环境变量注入，禁止写入仓库。

## 环境变量

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

AI_API_KEY
AI_BASE_URL
AI_MODEL
AI_CONNECT_TIMEOUT_SECONDS
AI_READ_TIMEOUT_SECONDS
```

说明：

- `DB_PASSWORD`：数据库密码；
- `JWT_SECRET`：至少 32 字节的随机字符串；
- `JWT_EXPIRATION_MINUTES`：默认 120 分钟；
- `UPLOAD_DIR`：上传根目录，默认 `uploads`；
- 头像单文件最大 5MB；
- 笔记单图最大 5MB；
- 一条笔记最多 6 张图片；
- multipart 请求最大 32MB。

`src/main/resources/application-local.yml` 已被 `.gitignore` 排除，并且只引用环境变量。

## 启动方式

### 1. 初始化数据库

在 MySQL 8 中执行：

```text
docs/sql/schema.sql
```

已有 P0 数据库时执行：

```text
docs/sql/migration_v2_features.sql
```

### 2. PowerShell 启动

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/note_workshop?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USERNAME="note_app"
$env:DB_PASSWORD="<数据库密码>"
$env:JWT_SECRET="<至少 32 字节的随机字符串>"
$env:JWT_EXPIRATION_MINUTES="120"
$env:UPLOAD_DIR="uploads"

mvn spring-boot:run
```

访问：

```text
http://localhost:8080/
```

### 3. JAR 启动

```powershell
mvn -DskipTests package
java -jar target/note-workshop-0.0.1-SNAPSHOT.jar
```

## 演示账号

初始 SQL 提供两个账号，密码均为：

```text
123456
```

```text
student / 123456
admin   / 123456
```

## 测试

```powershell
$env:DB_PASSWORD=[Environment]::GetEnvironmentVariable('DB_PASSWORD','User')
$env:JWT_SECRET="test-only-jwt-secret-please-change-1234567890"
$env:JWT_EXPIRATION_MINUTES="120"
mvn test
```

当前结果：

```text
Tests run: 97, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试覆盖注册登录、JWT、账号禁用、笔记 CRUD、审核状态、图片处理、点赞、排行、关注、评论级联、AI 结果保存、AI 会话隔离、AI 审计统计等核心链路。

## 安全注意事项

- 不要提交 `application-local.yml`、数据库密码、JWT secret、OSS key 或 AI API key；
- `uploads/`、`logs/`、`target/` 已由 `.gitignore` 排除；
- 图片上传同时校验扩展名、MIME 类型和真实图片内容；
- 删除笔记时先提交数据库事务，再清理物理文件；
- 会话接口每次校验当前登录用户与会话归属用户一致。