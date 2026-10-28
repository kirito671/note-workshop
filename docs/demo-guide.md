# Note-Workshop 最终演示与答辩指南

## 1. 演示目标

在有限时间内完整展示 Note-Workshop 的核心闭环：

```text
注册/登录
→ 浏览公开笔记
→ 查看详情和图片
→ 点赞
→ 我的书架
→ 创建/编辑笔记
→ 上传图片
→ 我点赞的笔记
→ 热度排行
→ 个人资料
→ 响应式页面
```

## 2. 环境准备

### 必需环境

- JDK 25
- MySQL 8
- 已执行 `docs/sql/schema.sql`
- 已配置数据库和 JWT 环境变量

### PowerShell 启动

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/note_workshop?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USERNAME="<数据库用户名>"
$env:DB_PASSWORD="<数据库密码>"
$env:JWT_SECRET="<至少 32 字节的随机字符串>"
$env:JWT_EXPIRATION_MINUTES="120"
$env:UPLOAD_DIR="uploads"

java -jar target/note-workshop-0.0.1-SNAPSHOT.jar
```

也可以使用：

```powershell
mvn spring-boot:run
```

### 浏览器地址

```text
http://localhost:8080/
```

### 演示账号

```text
用户名：student
密码：123456
```

## 3. 截图清单

### 登录页

![登录页](screenshots/auth-view.png)

讲解重点：

- 同源部署；
- JWT 登录；
- 页面视觉方向；
- 响应式布局。

### 公开书架

![公开书架](screenshots/frontend-desktop.png)

讲解重点：

- 公开笔记分页；
- 标题和图书名称搜索；
- 笔记点赞数；
- `likedByMe` 状态。

### 笔记详情

![笔记详情](screenshots/note-detail.png)

讲解重点：

- 详情接口；
- 多图展示；
- 点赞和取消点赞；
- 作者编辑与删除权限；
- 私有/隐藏笔记权限控制。

### 创建笔记

![创建笔记](screenshots/create-note.png)

讲解重点：

- 私有/公开可见性；
- 参数校验；
- 创建后写入 MySQL；
- 图片上传限制。

### 点赞排行

![点赞排行](screenshots/ranking.png)

讲解重点：

- 实时点赞数统计；
- 点赞数降序；
- 零点赞笔记也参与排行；
- `LEFT JOIN` 与稳定排序。

### 个人资料

![个人资料](screenshots/profile.png)

讲解重点：

- JWT 当前用户；
- 昵称修改；
- 头像上传；
- BCrypt 密码哈希与 JWT 分离。

### 移动端

![移动端](screenshots/frontend-mobile.png)

讲解重点：

- 响应式布局；
- 导航切换；
- 移动端无横向溢出；
- 键盘焦点和可访问性。

## 4. 建议现场演示顺序

### 第 1 步：登录

1. 打开首页；
2. 输入 `student / 123456`；
3. 点击“进入阅读工作台”；
4. 展示左侧导航和用户状态。

### 第 2 步：公开书架

1. 展示公开笔记；
2. 在搜索框输入图书关键词；
3. 说明标题和图书名称都会参与匹配；
4. 指出点赞数和当前用户点赞状态。

### 第 3 步：笔记详情

1. 点击“Java 集合学习笔记”；
2. 展示正文和配图；
3. 点击喜欢/取消喜欢；
4. 展示点赞数同步变化；
5. 说明只有作者或管理员可以编辑和删除。

### 第 4 步：创建笔记

1. 进入“我的书架”；
2. 点击“写一篇笔记”；
3. 填写标题、图书名称和正文；
4. 选择公开或私密；
5. 保存后展示分页列表中的新笔记。

### 第 5 步：图片上传

1. 打开自己创建的笔记；
2. 选择一张 jpg/png 图片；
3. 点击“上传图片”；
4. 展示新图片、删除、上移和下移；
5. 说明单张最大 5MB、每条笔记最多 6 张。

### 第 6 步：点赞排行

1. 进入“热度排行”；
2. 展示按点赞数降序排列；
3. 说明零点赞笔记仍然保留；
4. 展示当前用户的 `likedByMe`。

### 第 7 步：用户资料

1. 点击左下角个人资料；
2. 展示昵称和头像；
3. 可选：修改昵称后保存；
4. 说明头像 URL 保存在数据库，文件保存在上传目录。

### 第 8 步：移动端

1. 打开浏览器开发者工具；
2. 切换到手机尺寸；
3. 刷新页面；
4. 展示顶部导航和单列笔记布局。

## 5. 后端知识点讲解顺序

### 分层架构

```text
Controller → Service → Mapper → MySQL
```

- Controller：参数接收、校验、调用 Service；
- Service：权限、事务、业务规则；
- Mapper：数据库 CRUD；
- JWT：认证与用户上下文。

### 数据库设计

- `user`：用户、BCrypt 密码、角色、状态；
- `note`：笔记、可见性、状态；
- `note_image`：多图和排序；
- `note_like`：点赞关系，唯一索引防重复。

### 文件上传

- 校验扩展名、MIME 类型和真实图片内容；
- UUID 文件名防止冲突和路径穿越；
- 单张 5MB；
- 每条笔记最多 6 张；
- 删除事务提交后清理物理文件。

### 点赞与排行

- `note_like(user_id, note_id)` 唯一索引；
- 点赞和取消点赞幂等；
- `likedByMe` 来自当前 JWT 用户；
- 排行使用 `LEFT JOIN`，零赞笔记不丢失；
- 列表批量查询点赞数据，避免 N+1。

### 删除级联

- 删除 `note_image`；
- 删除 `note_like`；
- 删除 `note`；
- 事务提交后删除磁盘图片；
- 文件删除失败只记录日志，不回滚数据库。

## 6. 常见提问与回答

### 为什么密码不存明文？

使用 BCrypt 哈希，登录时通过哈希比对验证密码。

### 为什么使用 JWT？

JWT 可以无状态传递用户身份，后端通过拦截器解析并写入 `UserContext`。

### 为什么点赞使用唯一索引？

同一个用户对同一篇笔记只能有一条点赞记录，数据库唯一索引可以处理并发重复点赞。

### 为什么点赞排行使用 LEFT JOIN？

需要保留零点赞的公开笔记，`INNER JOIN` 会把这些笔记过滤掉。

### 为什么删除图片要放在事务提交后？

数据库事务无法回滚文件系统；先提交数据库再删除文件，避免数据库回滚后文件已经丢失。

### 为什么列表批量查询点赞？

逐篇查询会产生 N+1 SQL；批量查询当前页笔记的点赞记录，再在内存中聚合。

## 7. 演示备用方案

如果现场网络或 MySQL 临时不可用：

1. 打开本文档中的截图；
2. 按截图顺序讲解前端流程；
3. 使用 `docs/api.md` 说明接口；
4. 使用 `docs/postman/note-workshop.postman_collection.json` 展示接口测试设计；
5. 展示 `mvn test` 的 90 个通过用例结果。

## 8. 演示结束语

```text
当前已完成 P0 用户、笔记、图片和点赞模块，
前端可以完整展示主要业务流程，
后端包含参数校验、JWT 鉴权、事务、文件补偿清理和 90 个自动化测试。
后续可扩展管理员审核、关注、评论和 AI 阅读辅助。
```