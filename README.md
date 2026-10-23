# Note-Workshop

面向在校学生的读书笔记社区项目。用户可以记录读书笔记、上传书页或手写笔记照片，并在后续版本中支持公开分享、点赞、评论、关注、管理员审核和 AI 阅读辅助。

## 项目概况

本项目是 Java 后端项目，采用分层架构：

```text
Controller -> Service -> Mapper -> MySQL
```

当前仓库是可继续迭代的基础版本。业务模块会按照 P0 基础功能、P1 进阶功能、P2 AI 加分功能的顺序逐步实现；最终以答辩时的版本为准。

## 当前进度

当前完成到 P0-2 基础能力，已具备：

- Spring Boot 项目骨架
- Maven 构建配置
- MyBatis-Plus 基础配置
- MySQL 连接配置与 HikariCP 连接池
- 统一返回结构 `ApiResponse<T>`
- 错误码枚举 `ErrorCode`
- 自定义业务异常 `BusinessException`
- 全局异常处理
- SLF4J + Logback 控制台与文件日志
- MockMvc 基础测试

### 尚未实现

以下功能仍在后续阶段，不应视为当前已完成功能：

- 用户注册、登录、BCrypt、JWT 鉴权
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

后续计划：

- JWT
- Spring AI（P2，可选加分）
- HTML / CSS / JavaScript 前端

## 目录结构

```text
.
├─ docs/
│  └─ sql/
│     └─ schema.sql          # 初始数据库脚本
├─ src/main/java/com/example/noteworkshop/
│  ├─ NoteworkshopApplication.java
│  ├─ common/
│  │  └─ ApiResponse.java
│  ├─ config/
│  │  └─ MybatisPlusConfig.java
│  └─ exception/
│     ├─ BusinessException.java
│     ├─ ErrorCode.java
│     └─ GlobalExceptionHandler.java
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

注意：该脚本包含 `DROP TABLE`，只适合本地初始化或重置数据库，不要在已有数据的环境直接执行。

## 环境变量

不要把密码、JWT secret、OSS accessKey 或 AI key 写入仓库。

当前使用的环境变量：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
SPRING_PROFILES_ACTIVE
```

默认配置：

- 数据库：`note_workshop`
- 用户名：`note_app`
- 启动 profile：`local`

本地数据库密码可以放在：

```text
src/main/resources/application-local.yml
```

该文件已在 `.gitignore` 中忽略，不应提交。

## 运行方式

### IDEA

1. 导入项目 `pom.xml`。
2. 刷新 Maven 依赖。
3. 确保本地 MySQL 已启动。
4. 设置 `application-local.yml` 或 `DB_PASSWORD` 环境变量。
5. 运行 `NoteworkshopApplication`。

### Maven

```powershell
mvn spring-boot:run
```

如果本机没有把 Maven 加入 PATH，请在 IDEA 中执行 Maven 命令，或使用本机 Maven 的完整路径。

## 测试

```powershell
mvn test
```

当前测试覆盖：

- Spring 上下文启动
- 统一成功返回
- 业务异常返回 HTTP 403
- JSON 字段 `code/message/data` 断言

## 说明

当前版本是基础框架和基础设施阶段，不包含完整业务功能。仓库会继续更新，最终验收以答辩时提交的版本为准。