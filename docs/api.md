# Note-Workshop API 文档

本文档是 Note-Workshop 当前后端接口的主说明。接口实现与仓库中 Controller、DTO、VO 和集成测试保持一致。

## 1. 基本约定

### 1.1 基础地址

本地默认地址：

```text
http://localhost:8080
```

### 1.2 请求格式

- JSON 接口：`Content-Type: application/json`
- 文件上传接口：`Content-Type: multipart/form-data`
- 查询参数：普通 URL Query String

### 1.3 鉴权

注册、登录和静态文件访问不需要 JWT。其余 `/api/**` 接口需要请求头：

```http
Authorization: Bearer <token>
```

登录成功后，从响应中的 `data.token` 获取 JWT。

### 1.4 统一响应

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

失败响应：

```json
{
  "code": 400,
  "message": "参数校验失败",
  "data": null
}
```

- `code`：项目业务码，成功固定为 `0`
- `message`：结果说明
- `data`：业务数据，没有数据时为 `null`

### 1.5 HTTP 状态码与业务码

| HTTP 状态码 | 业务 code | 含义 |
|---:|---:|---|
| 200 | 0 | 成功 |
| 400 | 400 | 参数错误、文件格式错误、分页参数错误 |
| 401 | 401 | 未登录、JWT 缺失、JWT 无效或过期 |
| 403 | 403 | 已登录但无操作权限 |
| 404 | 404 | 笔记、图片或其他资源不存在 |
| 409 | 409 | 用户名已存在 |
| 500 | 500 | 服务器内部错误 |

### 1.6 分页规则

分页参数：

| 参数 | 默认值 | 限制 |
|---|---:|---|
| `page` | 1 | `page >= 1` |
| `size` | 10 | `1 <= size <= 50` |

分页响应：

```json
{
  "records": [],
  "total": 0,
  "page": 1,
  "size": 10,
  "pages": 0
}
```

## 2. 环境变量与启动

### 2.1 必需环境变量

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
JWT_EXPIRATION_MINUTES
```

### 2.2 上传相关环境变量

```text
UPLOAD_DIR
MAX_AVATAR_SIZE_BYTES
MAX_NOTE_IMAGE_SIZE_BYTES
MAX_NOTE_IMAGE_COUNT
```

默认限制：

- 头像：5MB
- 单张笔记图片：5MB
- 单条笔记最多 6 张图片
- 图片格式：`jpg`、`jpeg`、`png`
- multipart 最大请求：32MB

### 2.3 PowerShell 启动示例

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/note_workshop?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USERNAME="<数据库用户名>"
$env:DB_PASSWORD="<数据库密码>"
$env:JWT_SECRET="<至少 32 字节的随机字符串>"
$env:JWT_EXPIRATION_MINUTES="120"
$env:UPLOAD_DIR="uploads"

mvn spring-boot:run
```

不要将真实密码、JWT secret、OSS accessKey 或 AI API key 提交到 Git。

## 3. 认证接口

### 3.1 用户注册

```http
POST /api/auth/register
Content-Type: application/json
```

请求体：

```json
{
  "username": "student01",
  "password": "123456",
  "nickname": "测试学生"
}
```

字段限制：

| 字段 | 必填 | 限制 |
|---|---|---|
| `username` | 是 | 3-50 个字符 |
| `password` | 是 | 6-72 个字符，保存 BCrypt 哈希 |
| `nickname` | 是 | 最多 50 个字符 |

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 2,
    "username": "student01",
    "nickname": "测试学生",
    "avatarUrl": null,
    "role": 0,
    "status": 1,
    "createdAt": "2026-10-28T10:00:00"
  }
}
```

常见错误：

- `400`：参数校验失败
- `409`：用户名已经存在

### 3.2 用户登录

```http
POST /api/auth/login
Content-Type: application/json
```

请求体：

```json
{
  "username": "student01",
  "password": "123456"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "<JWT>",
    "tokenType": "Bearer",
    "expiresInMinutes": 120
  }
}
```

常见错误：

- `400`：参数校验失败
- `401`：用户名或密码错误
- `403`：账号已被禁用

## 4. 用户资料接口

### 4.1 查询当前用户

```http
GET /api/users/me
Authorization: Bearer <token>
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 2,
    "username": "student01",
    "nickname": "测试学生",
    "avatarUrl": "/uploads/avatars/2/example.png",
    "role": 0,
    "status": 1,
    "createdAt": "2026-10-28T10:00:00"
  }
}
```

### 4.2 修改昵称

```http
PUT /api/users/me
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "nickname": "新的昵称"
}
```

成功响应：返回修改后的 `UserProfileVO`。

### 4.3 上传头像

```http
POST /api/users/me/avatar
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

表单字段：

```text
file: 单个 jpg/jpeg/png 文件
```

PowerShell 示例：

```powershell
curl.exe -X POST "http://localhost:8080/api/users/me/avatar" `
  -H "Authorization: Bearer <token>" `
  -F "file=@C:\images\avatar.png"
```

成功响应：返回更新后的 `UserProfileVO`，`avatarUrl` 格式类似：

```text
/uploads/avatars/{userId}/{uuid}.png
```

常见错误：

- `400`：文件为空、超过 5MB、格式不支持或不是真实图片
- `401`：未携带 JWT

## 5. 读书笔记接口

### 5.1 创建笔记

```http
POST /api/notes
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "title": "我的第一本读书笔记",
  "bookName": "示例图书",
  "content": "这本书主要讲了……",
  "visibility": 1
}
```

字段限制：

| 字段 | 必填 | 限制 |
|---|---|---|
| `title` | 是 | 最多 120 个字符 |
| `bookName` | 是 | 最多 200 个字符 |
| `content` | 是 | 最多 10000 个字符 |
| `visibility` | 是 | `0` 私有，`1` 公开 |

成功响应：返回 `NoteDetailVO`。

### 5.2 分页查询我的笔记

```http
GET /api/notes/my?page=1&size=10
Authorization: Bearer <token>
```

返回当前用户创建的全部笔记，包括公开和私有笔记。

### 5.3 分页查询公开笔记

```http
GET /api/notes/public?page=1&size=10&keyword=图书
Authorization: Bearer <token>
```

查询条件：

- `visibility = 1`
- `status = 1`
- `keyword` 可选，匹配标题或图书名称

### 5.4 分页查询我点赞的笔记

```http
GET /api/notes/liked?page=1&size=10
Authorization: Bearer <token>
```

规则：

- 只返回当前用户点赞过的笔记
- 只返回仍然公开且状态正常的笔记
- 按点赞时间倒序排列

### 5.5 点赞排行

```http
GET /api/notes/ranking?page=1&size=10
Authorization: Bearer <token>
```

规则：

- 只统计公开且状态正常的笔记
- 零点赞笔记也参与排行
- 按 `likeCount` 降序
- 点赞数相同时按笔记创建时间、笔记 ID 倒序

### 5.6 查看笔记详情

```http
GET /api/notes/{id}
Authorization: Bearer <token>
```

权限：

- 私有笔记仅作者或管理员可见
- 管理员隐藏的笔记仅作者或管理员可见
- 其他用户访问不可见笔记时返回 403 或 404

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 15,
    "userId": 2,
    "title": "我的第一本读书笔记",
    "bookName": "示例图书",
    "content": "这本书主要讲了……",
    "visibility": 1,
    "status": 1,
    "likeCount": 3,
    "likedByMe": true,
    "createdAt": "2026-10-28T10:00:00",
    "updatedAt": "2026-10-28T10:10:00",
    "images": [
      "/uploads/notes/15/example.png"
    ],
    "aiResult": null
  }
}
```

### 5.7 修改笔记

```http
PUT /api/notes/{id}
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "title": "修改后的标题",
  "bookName": "示例图书",
  "content": "修改后的正文",
  "visibility": 0
}
```

只有笔记作者或管理员可以修改。

### 5.8 删除笔记

```http
DELETE /api/notes/{id}
Authorization: Bearer <token>
```

只有笔记作者或管理员可以删除。

删除成功后：

- 删除 `note_image` 数据库记录
- 删除 `note_like` 点赞记录
- 删除 `note` 笔记记录
- 数据库事务提交后清理磁盘图片文件

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

## 6. 笔记图片接口

### 6.1 多图上传

```http
POST /api/notes/{id}/images
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

表单字段：

```text
files: 可重复，1-6 张图片
```

PowerShell 示例：

```powershell
curl.exe -X POST "http://localhost:8080/api/notes/15/images" `
  -H "Authorization: Bearer <token>" `
  -F "files=@C:\images\a.png" `
  -F "files=@C:\images\b.jpg"
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": 101,
      "imageUrl": "/uploads/notes/15/a.png",
      "sortOrder": 0
    },
    {
      "id": 102,
      "imageUrl": "/uploads/notes/15/b.jpg",
      "sortOrder": 1
    }
  ]
}
```

常见错误：

- `400`：没有文件、超过 6 张、单张超过 5MB、格式错误或伪造图片
- `403`：不是作者或管理员
- `404`：笔记不存在

### 6.2 删除单张图片

```http
DELETE /api/notes/{id}/images/{imageId}
Authorization: Bearer <token>
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

删除后同时清理 `note_image` 记录和对应磁盘文件。

### 6.3 调整图片顺序

```http
PUT /api/notes/{id}/images/order
Authorization: Bearer <token>
Content-Type: application/json
```

请求体必须包含该笔记全部图片 ID 的完整顺序：

```json
{
  "imageIds": [102, 101, 103]
}
```

规则：

- 不能重复图片 ID
- 不能缺少该笔记已有图片
- 不能包含其他笔记的图片
- 排序值从 0 开始

成功响应：返回重新排序后的 `NoteImageVO` 列表。

## 7. 点赞接口

### 7.1 点赞

```http
POST /api/notes/{id}/like
Authorization: Bearer <token>
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "liked": true,
    "likeCount": 3
  }
}
```

规则：

- 只允许点赞公开且状态正常的笔记
- 重复点赞是幂等的，不重复增加点赞数

### 7.2 取消点赞

```http
DELETE /api/notes/{id}/like
Authorization: Bearer <token>
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "liked": false,
    "likeCount": 2
  }
}
```

未点赞时重复取消也返回成功。

## 8. 静态资源访问

```http
GET /uploads/**
```

不需要 JWT。

示例：

```text
http://localhost:8080/uploads/avatars/2/example.png
http://localhost:8080/uploads/notes/15/example.png
```

数据库只保存访问 URL，不保存服务器绝对路径。

## 9. 主要响应对象

### 9.1 NoteSummaryVO

```json
{
  "id": 15,
  "userId": 2,
  "title": "读书笔记",
  "bookName": "示例图书",
  "visibility": 1,
  "status": 1,
  "likeCount": 3,
  "likedByMe": true,
  "createdAt": "2026-10-28T10:00:00"
}
```

### 9.2 NoteDetailVO

在 `NoteSummaryVO` 基础上增加：

- `content`
- `updatedAt`
- `images`
- `aiResult`

### 9.3 NoteImageVO

```json
{
  "id": 101,
  "imageUrl": "/uploads/notes/15/example.png",
  "sortOrder": 0
}
```

### 9.4 UserProfileVO

```json
{
  "id": 2,
  "username": "student01",
  "nickname": "测试学生",
  "avatarUrl": null,
  "role": 0,
  "status": 1,
  "createdAt": "2026-10-28T10:00:00"
}
```

### 9.5 LoginResponse

```json
{
  "token": "<JWT>",
  "tokenType": "Bearer",
  "expiresInMinutes": 120
}
```

### 9.6 LikeStatusVO

```json
{
  "liked": true,
  "likeCount": 3
}
```

## 10. 接口测试流程

推荐顺序：

1. 注册用户
2. 登录并保存 JWT
3. 修改昵称
4. 上传头像
5. 创建笔记
6. 分页查询我的笔记和公开笔记
7. 上传多张图片
8. 调整图片排序
9. 点赞、取消点赞
10. 查询我点赞的笔记和点赞排行
11. 查看笔记详情
12. 删除单张图片
13. 删除整篇笔记

### 10.1 登录并保存 token

Postman 登录接口 Tests 脚本可使用：

```javascript
pm.collectionVariables.set("token", pm.response.json().data.token);
```

后续请求使用：

```http
Authorization: Bearer {{token}}
```

### 10.2 建议的集合变量

```text
baseUrl
token
noteId
imageId
```

### 10.3 curl 完整示例

```powershell
# 登录
curl.exe -X POST "http://localhost:8080/api/auth/login" `
  -H "Content-Type: application/json" `
  -d '{"username":"student01","password":"123456"}'

# 创建笔记
curl.exe -X POST "http://localhost:8080/api/notes" `
  -H "Authorization: Bearer <token>" `
  -H "Content-Type: application/json" `
  -d '{"title":"测试笔记","bookName":"示例图书","content":"正文","visibility":1}'

# 查询公开笔记
curl.exe "http://localhost:8080/api/notes/public?page=1&size=10" `
  -H "Authorization: Bearer <token>"

# 点赞
curl.exe -X POST "http://localhost:8080/api/notes/15/like" `
  -H "Authorization: Bearer <token>"

# 查询点赞排行
curl.exe "http://localhost:8080/api/notes/ranking?page=1&size=10" `
  -H "Authorization: Bearer <token>"
```

## 11. 注意事项

- 不要提交 `application-local.yml`、真实数据库密码、JWT secret、OSS key 或 AI API key。
- `uploads/` 和 `logs/` 已由 `.gitignore` 排除。
- 图片上传必须同时检查扩展名、MIME 类型和真实图片内容。
- `GET /api/notes/public`、`/liked` 和 `/ranking` 返回摘要数据，不返回笔记正文。
- 笔记删除后物理图片文件在数据库事务提交后清理。
- 本文件随接口变化持续更新。
---

## 12. P1 社区功能接口

以下接口均需要 JWT。管理员接口还会经过 `AdminInterceptor` 角色校验。

### 12.1 关注与作者主页

```http
POST   /api/users/{id}/follow
DELETE /api/users/{id}/follow
GET    /api/users/{id}/follow-status
GET    /api/users/{id}/profile
GET    /api/users/{id}/stats
GET    /api/users/{id}/notes?page=1&size=10
GET    /api/users/hot-authors?page=1&size=10
```

`FollowStatusVO`：

```json
{
  "following": true,
  "followerCount": 8,
  "followingCount": 3
}
```

`AuthorStatsVO`：

```json
{
  "followerCount": 8,
  "followingCount": 3,
  "publicNoteCount": 12
}
```

规则：

- 不能关注自己；
- 重复关注幂等；
- 热门作者只统计正常普通用户，按粉丝数降序；
- 作者笔记只返回审核通过的公开笔记。

### 12.2 审核状态

公开笔记创建或编辑后进入 `auditStatus = 0` 待审核状态。私有笔记不需要审核。

```text
0 = 待审核
1 = 审核通过
2 = 审核驳回
```

只有审核通过的公开笔记会进入公开书架、点赞排行、作者公开笔记列表和评论接口。

作者重新提交：

```http
POST /api/notes/{id}/submit
Authorization: Bearer <token>
```

返回的 `NoteSummaryVO` 和 `NoteDetailVO` 包含：

```json
{
  "auditStatus": 0,
  "auditRemark": null
}
```

### 12.3 评论与多级回复

```http
GET    /api/notes/{noteId}/comments
POST   /api/notes/{noteId}/comments
DELETE /api/notes/{noteId}/comments/{commentId}
```

创建一级评论：

```json
{
  "content": "这个对比很清楚。"
}
```

创建回复：

```json
{
  "content": "我补充一个实际使用场景。",
  "parentId": 101
}
```

`CommentVO`：

```json
{
  "id": 102,
  "noteId": 15,
  "userId": 3,
  "nickname": "阅读者",
  "avatarUrl": null,
  "parentId": 101,
  "content": "我补充一个实际使用场景。",
  "createdAt": "2026-10-28T20:00:00",
  "replies": []
}
```

规则：

- 只有审核通过的公开笔记可以评论；
- 支持任意层级回复；
- 只能删除自己的评论，管理员可以删除任意违规评论；
- 删除父评论时，全部子孙评论级联逻辑删除；
- 已删除评论不会出现在列表中。

### 12.4 管理员用户与内容管理

用户列表：

```http
GET /api/admin/users?page=1&size=10
```

禁用或启用账号：

```http
PUT /api/admin/users/{id}/status
Content-Type: application/json

{
  "status": 0
}
```

公开笔记列表：

```http
GET /api/admin/notes?auditStatus=0&page=1&size=10
GET /api/admin/notes/pending?page=1&size=10
```

审核操作：

```http
PUT /api/admin/notes/{id}/approve

PUT /api/admin/notes/{id}/reject
Content-Type: application/json
{
  "remark": "请补充书籍版本和阅读说明。"
}

PUT /api/admin/notes/{id}/hide
```

删除违规评论：

```http
DELETE /api/admin/comments/{id}
```

JWT 拦截器每次请求都会读取数据库账号状态。账号禁用后，旧 token 再访问受保护接口会返回 403。

## 13. P2 AI 功能接口

### 13.1 AI 笔记分析

```http
POST /api/notes/{id}/ai-analysis
Authorization: Bearer <token>
```

只有笔记作者或管理员可以调用。分析结果不会自动写入笔记主表。

`AiAnalysisVO`：

```json
{
  "noteId": 15,
  "summary": "笔记比较了数组和链表在查询、插入与删除上的差异。",
  "audience": "具备一定基础、希望深入理解集合原理的读者",
  "completenessScore": 82,
  "relevanceScore": 91,
  "depthScore": 78
}
```

用户确认保存：

```http
POST /api/notes/{id}/ai-result
Content-Type: application/json

{
  "summary": "笔记比较了数组和链表在查询、插入与删除上的差异。",
  "audience": "具备一定基础、希望深入理解集合原理的读者",
  "completenessScore": 82,
  "relevanceScore": 91,
  "depthScore": 78
}
```

删除已保存结果：

```http
DELETE /api/notes/{id}/ai-result
```

规则：

- AI 输出先返回给用户，不会自动保存；
- 一篇笔记通过唯一索引最多保存一份 AI 结果；
- 再次保存会更新原结果；
- 笔记详情通过 `aiResult` 返回已保存结果；
- 每次分析调用成功或失败都会写入 `ai_audit_log`。

### 13.2 AI 智能客服

新建会话：

```http
POST /api/ai/conversations
Content-Type: application/json

{
  "title": "公开笔记审核咨询"
}
```

`title` 可省略，默认标题为“新的阅读咨询”。

会话列表：

```http
GET /api/ai/conversations?page=1&size=20
```

会话详情与历史消息：

```http
GET /api/ai/conversations/{id}
```

发送消息：

```http
POST /api/ai/conversations/{id}/messages
Content-Type: application/json

{
  "content": "公开笔记提交后多久能出现在书架？"
}
```

响应：

```json
{
  "conversationId": 7,
  "userMessage": {
    "id": 21,
    "role": "USER",
    "content": "公开笔记提交后多久能出现在书架？",
    "createdAt": "2026-10-28T20:10:00"
  },
  "assistantMessage": {
    "id": 22,
    "role": "ASSISTANT",
    "content": "公开笔记需要管理员审核通过后才会出现在公开书架。",
    "createdAt": "2026-10-28T20:10:01"
  }
}
```

删除会话：

```http
DELETE /api/ai/conversations/{id}
```

规则：

- 会话归属唯一用户；
- 每次访问详情、发送消息、删除会话都会校验当前用户是否为会话所有者；
- 越权返回自定义 403 业务异常；
- 历史消息持久化；
- 上下文只携带最近 10 轮；
- 不同会话上下文隔离；
- system prompt 限定平台客服范围；
- 删除会话时同时删除其全部消息；
- 每次客服调用写入 AI 审计日志。

### 13.3 管理员 AI 统计

```http
GET /api/admin/ai/stats
Authorization: Bearer <admin-token>
```

响应：

```json
{
  "total": 20,
  "success": 18,
  "failure": 2
}
```

统计来自 `ai_audit_log`，覆盖 AI 笔记分析和 AI 客服两个场景。

## 14. AI 配置

默认没有 API key 时，使用本地可演示分析器和规则客服。配置 OpenAI 兼容接口后会切换到真实大模型。

```text
AI_API_KEY
AI_BASE_URL=https://api.openai.com/v1
AI_MODEL=gpt-4o-mini
AI_CONNECT_TIMEOUT_SECONDS=10
AI_READ_TIMEOUT_SECONDS=45
```

密钥必须通过环境变量注入，禁止写死在代码或配置文件中。

## 15. 扩展后的核心响应字段

`NoteSummaryVO` 新增：

```text
authorNickname
authorAvatarUrl
auditStatus
auditRemark
```

`NoteDetailVO` 在此基础上新增：

```text
content
images
aiResult
```

`NoteAiResultVO`：

```json
{
  "id": 5,
  "summary": "AI 摘要",
  "audience": "适合的读者",
  "completenessScore": 80,
  "relevanceScore": 90,
  "depthScore": 75
}
```

## 16. 推荐完整测试顺序

1. 登录普通用户；
2. 创建公开笔记；
3. 登录管理员并通过审核；
4. 普通用户点赞、评论和回复；
5. 关注作者并查看作者统计；
6. 作者生成 AI 分析并手动保存；
7. 新建 AI 客服会话并发送两轮问题；
8. 使用另一个账号访问会话，确认返回 403；
9. 管理员查看用户、待审核笔记和 AI 统计；
10. 管理员禁用测试账号，确认旧 token 立即失效。