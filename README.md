# Note-Workshop

面向在校学生的读书笔记社区。用户可以注册登录、维护个人资料、创建私有或公开读书笔记、上传多张配图、浏览公开书架、点赞笔记、查看自己点赞过的笔记，并按点赞数查看笔记排行。

项目当前已完成 **P0 基础要求**，并提供可直接运行的完整前端页面。

## 当前状态

```text
P0 基础功能：已完成
全量自动化测试：90 个通过
前端页面：已完成
API 文档：已完成
Postman Collection：已完成
答辩截图与演示指南：已完成
```

已实现：

- 用户注册、登录、JWT 鉴权；
- BCrypt 密码哈希；
- 用户资料查询和昵称修改；
- 头像上传与静态访问；
- 读书笔记新增、修改、删除；
- 图书名称字符串字段；
- 私有/公开笔记权限；
- 作者与管理员操作权限；
- 我的笔记分页；
- 公开笔记分页与关键词搜索；
- 笔记详情和全部配图；
- 多图上传、删除和排序；
- 图片格式、大小和真实内容校验；
- 点赞、取消点赞；
- 我点赞的笔记分页；
- 点赞数与点赞排行；
- 删除笔记时清理图片、点赞和物理文件；
- 统一响应、参数校验和全局异常处理；
- 完整 HTML/CSS/JavaScript 前端；
- Markdown API 文档和 Postman Collection。

当前版本未实现管理员审核、关注、评论、Spring AI 阅读辅助和 AI 客服。这些属于后续 P1/P2 扩展范围。

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
Controller：接收请求、参数校验、调用 Service、返回统一结果
        ↓
Service：业务逻辑、权限判断、事务、业务组装
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

当前表：

- `user`：用户、BCrypt 密码、角色、状态；
- `note`：笔记、可见性、状态；
- `note_image`：笔记图片、访问 URL、排序；
- `note_like`：点赞关系，唯一索引防止重复点赞。

SQL 中包含 `DROP TABLE`，只适合本地初始化和开发环境重置，不要直接在有数据的生产库执行。

## 主要接口

### 认证

```text
POST /api/auth/register
POST /api/auth/login
```

### 用户

```text
GET  /api/users/me
PUT  /api/users/me
POST /api/users/me/avatar
```

### 笔记

```text
POST   /api/notes
GET    /api/notes/my
GET    /api/notes/public
GET    /api/notes/liked
GET    /api/notes/ranking
GET    /api/notes/{id}
PUT    /api/notes/{id}
DELETE /api/notes/{id}
```

### 图片

```text
POST   /api/notes/{id}/images
DELETE /api/notes/{id}/images/{imageId}
PUT    /api/notes/{id}/images/order
```

### 点赞

```text
POST   /api/notes/{id}/like
DELETE /api/notes/{id}/like
```

### 静态文件

```text
GET /uploads/**
```

除注册、登录和静态文件访问外，其余 `/api/**` 接口需要：

```http
Authorization: Bearer <token>
```

完整接口说明见：

- [API 文档](docs/api.md)
- [Postman Collection](docs/postman/note-workshop.postman_collection.json)
## 环境变量

数据库密码、JWT secret 和未来可能出现的 OSS/AI 密钥都不应写入仓库。

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

- `DB_PASSWORD`：本地数据库密码，已配置为用户级环境变量；
- `JWT_SECRET`：至少 32 字节的随机字符串；
- `JWT_EXPIRATION_MINUTES`：默认 120 分钟；
- `UPLOAD_DIR`：默认 `uploads`；
- 头像单文件最大 5MB；
- 笔记单图最大 5MB；
- 一条笔记最多 6 张图片；
- multipart 请求最大 32MB。

`src/main/resources/application-local.yml` 已经被 `.gitignore` 排除，并且只保留环境变量引用，不保存明文密码。

如果修改了用户级环境变量，需要重新启动 IDEA 或终端后才会生效。

## 启动方式

### IDEA

1. 导入项目根目录的 `pom.xml`；
2. 刷新 Maven 依赖；
3. 启动本地 MySQL；
4. 使用 `docs/sql/schema.sql` 初始化数据库；
5. 确认 IDEA Run Configuration 能读取 `DB_PASSWORD` 和 `JWT_SECRET`；
6. 运行 `NoteworkshopApplication`；
7. 浏览器访问 `http://localhost:8080/`。

### PowerShell

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/note_workshop?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USERNAME="note_app"
$env:DB_PASSWORD="<数据库密码>"
$env:JWT_SECRET="<至少 32 字节的随机字符串>"
$env:JWT_EXPIRATION_MINUTES="120"
$env:UPLOAD_DIR="uploads"

mvn spring-boot:run
```

也可以使用打包后的 JAR：

```powershell
mvn -DskipTests package
java -jar target/note-workshop-0.0.1-SNAPSHOT.jar
```

## 演示账号

初始化 SQL 中提供两个演示账号，密码均为：

```text
123456
```

```text
student / 123456
admin   / 123456
```

前端演示主要使用 `student` 账号。

## 测试

运行全量测试：

```powershell
$env:JWT_SECRET="test-only-jwt-secret-please-change-1234567890"
$env:JWT_EXPIRATION_MINUTES="120"
mvn test
```

当前结果：

```text
Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试覆盖：

- 注册、重复用户名、参数校验；
- BCrypt 密码存储；
- 登录、禁用用户、错误密码；
- JWT 拦截器；
- 当前用户资料；
- 头像上传与静态访问；
- 笔记 CRUD；
- 私有笔记和用户隔离；
- 作者与管理员权限；
- 笔记分页与搜索；
- 多图上传、边界校验和文件补偿删除；
- 图片删除和排序；
- 点赞幂等、取消点赞；
- 点赞数字段；
- 我点赞的笔记分页；
- 点赞排行；
- 删除笔记数据库级联和物理文件清理。

## 前端页面

前端文件位于：

```text
src/main/resources/static
```

页面无需单独启动 Node 服务，Spring Boot 会直接提供静态资源。

功能包括：

- 登录、注册、退出；
- 登录状态恢复；
- 用户资料与头像；
- 公开书架；
- 我的书架；
- 创建、编辑、删除笔记；
- 搜索；
- 多图上传、删除和排序；
- 点赞；
- 我点赞的笔记；
- 点赞排行；
- 响应式移动端布局。

## 答辩演示

演示步骤、讲解重点、常见问答和备用方案：

```text
docs/demo-guide.md
```

截图目录：

```text
docs/screenshots
```

当前包含：

- 登录页；
- 公开书架；
- 笔记详情；
- 创建笔记；
- 点赞排行；
- 个人资料；
- 移动端页面。

## 文件上传限制

- 头像：`jpg`、`jpeg`、`png`，最大 5MB；
- 笔记图片：`jpg`、`jpeg`、`png`，最大 5MB；
- 每条笔记最多 6 张图片；
- 使用 UUID 文件名，避免重名和路径穿越；
- 数据库只保存访问 URL；
- 删除笔记时数据库事务提交后清理物理图片；
- `uploads/` 不提交 Git。

## 安全说明

- 密码只保存 BCrypt 哈希；
- JWT secret 通过环境变量注入；
- 数据库密码通过环境变量注入；
- `.gitignore` 排除本地配置、日志和上传文件；
- 前端使用 `textContent` 渲染用户内容，避免直接拼接 HTML；
- 受保护接口统一经过 JWT Interceptor；
- 私有笔记仅作者或管理员可见；
- 点赞关系使用数据库唯一索引防止并发重复。

## 项目结构

```text
.
├── docs/
│   ├── api.md
│   ├── demo-guide.md
│   ├── postman/
│   ├── screenshots/
│   └── sql/schema.sql
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
│   ├── application.yml
│   └── static/
├── src/test/java/
├── .gitignore
├── pom.xml
└── README.md
```

## 后续计划

当前已按要求停止继续扩展 P1/P2 功能，优先准备最终演示和答辩。

后续可选方向：

- 管理员用户管理和笔记审核；
- 关注作者、粉丝统计；
- 评论与多级回复；
- Spring AI 阅读辅助；
- AI 智能客服与调用审计。