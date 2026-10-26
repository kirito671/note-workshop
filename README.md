# Note-Workshop

面向在校学生的读书笔记社区项目。用户可以记录读书笔记、上传书页或手写笔记照片，并在后续版本中支持公开分享、点赞、评论、关注、管理员审核和 AI 阅读辅助。

## 项目概况

本项目是 Java 后端项目，采用分层架构：

```text
Controller -> Service -> Mapper -> MySQL
```

当前版本按照 P0 基础功能、P1 进阶功能、P2 AI 加分功能逐步迭代，最终以答辩时提交的版本为准。

## 当前进度

当前已完成 P0-4 用户资料与头像上传模块，已具备：

- Spring Boot 项目骨架
- Maven 构建配置
- MyBatis-Plus 与 MySQL 配置
- HikariCP 数据库连接池
- 统一返回结构 `ApiResponse<T>`
- 错误码枚举 `ErrorCode`
- 自定义业务异常 `BusinessException`
- 全局异常处理
- 参数校验异常处理
- 上传文件超限异常处理
- SLF4J + Logback 控制台与滚动文件日志
- 用户注册与 BCrypt 密码加密
- 用户登录与 JWT 生成
- JWT Interceptor 鉴权
- `UserContext` 当前用户线程上下文
- 当前用户资料查询和昵称修改
- 头像上传、格式校验、大小校验和 UUID 文件名
- 本地头像存储与 `/uploads/**` 静态访问
- 注册、登录、JWT、资料、头像上传相关 MockMvc 测试

P0-5 读书笔记模块目前只创建了空的 `Note`、`NoteImage` 实体和 Mapper 骨架，CRUD 和接口尚未实现。

### 尚未实现

- 读书笔记 CRUD、分页、私有/公开和多图上传
- 点赞、点赞列表和排行
- 管理员后台和审核流
- 关注、评论和多级回复
- AI 阅读辅助和 AI 客服
- 正式前端页面
- 正式业务接口文档

## 技术栈

当前已使用：

- Java 25
- Spring Boot 3.5.6
- MyBatis-Plus 3.5.12
- MySQL 8
- Maven
- SLF4J + Logback
- Jakarta Validation
- BCrypt（spring-security-crypto）
- JJWT 0.12.6
- 本地文件存储

后续计划：

- Spring AI（P2，可选加分）
- HTML / CSS / JavaScript 前端

## 当前接口

公开接口：

```text
POST /api/auth/register
POST /api/auth/login
GET  /uploads/**
```

受保护接口：

```text
/api/**
```

用户资料接口：

```text
GET  /api/users/me
PUT  /api/users/me
POST /api/users/me/avatar
```

访问受保护接口时需要携带：

```text
Authorization: Bearer <token>
```

## 目录结构

```text
.
├─ docs/
│  └─ sql/
│     └─ schema.sql
├─ src/main/java/com/example/noteworkshop/
│  ├─ common/
│  ├─ config/
│  ├─ controller/
│  ├─ dto/
│  ├─ entity/
│  ├─ exception/
│  ├─ mapper/
│  ├─ security/
│  ├─ service/
│  └─ vo/
├─ src/main/resources/
│  ├─ application.yml
│  └─ application-local.yml  # 本地配置，不提交
├─ src/test/java/
└─ pom.xml
```

## 初始 SQL

数据库初始化脚本：

- [docs/sql/schema.sql](docs/sql/schema.sql)

脚本创建数据库 `note_workshop` 和以下四张基础表：

- `user`
- `note`
- `note_image`
- `note_like`

注意：脚本包含 `DROP TABLE`，只适合本地初始化或开发阶段重置数据库，不要在已有数据的环境直接执行。

## 环境变量

不要将数据库密码、JWT secret、OSS accessKey 或 AI key 写入仓库。

当前使用的环境变量：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
SPRING_PROFILES_ACTIVE
JWT_SECRET
JWT_EXPIRATION_MINUTES
UPLOAD_DIR
MAX_AVATAR_SIZE_BYTES
```

说明：

- `JWT_SECRET` 至少需要 32 字节，不能提交到 Git。
- `JWT_EXPIRATION_MINUTES` 默认 120 分钟。
- `UPLOAD_DIR` 默认 `uploads`，上传目录不会提交到 Git。
- `MAX_AVATAR_SIZE_BYTES` 默认 5242880。
- 本地数据库密码可以放在 `src/main/resources/application-local.yml`，该文件已被 `.gitignore` 忽略。

## 运行方式

### IDEA

1. 导入项目 `pom.xml`。
2. 刷新 Maven 依赖。
3. 启动本地 MySQL。
4. 设置数据库密码与 JWT 环境变量。
5. 运行 `NoteworkshopApplication`。

### Maven

```powershell
mvn spring-boot:run
```

## 测试

```powershell
mvn test
```

当前测试覆盖：

- Spring 上下文启动
- 统一返回和全局异常处理
- 用户注册与 BCrypt 密码哈希
- 重复用户名 409
- 用户登录与 JWT 返回
- 错误密码 401
- 禁用账号 403
- JWT Interceptor 无 token、有效 token、无效 token
- ThreadLocal 设置和清理
- 当前用户资料查询和昵称修改
- 有效 PNG 头像上传和静态 URL 访问
- 非图片文件、无 token、超大文件上传失败场景

当前测试结果：

```text
Tests run: 20, Failures: 0, Errors: 0
BUILD SUCCESS
```

## 说明

当前版本已完成用户认证、个人资料和头像上传，不包含读书笔记、点赞、管理员、社区互动、AI 和正式前端。仓库会继续更新，最终验收以答辩时提交的版本为准。