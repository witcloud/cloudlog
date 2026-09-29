# CloudLog 数据库设计文档（db-design.md）

> **用途**：面向 AI Agent 的数据库规范。本文可直接指导 schema / migration 的生成，无需再回读代码。
> **依据**：业务语义来自 [`business-model.md`](./business-model.md) **Part A**；字段、类型、索引、枚举取值的最高真源是 `cloudlog_api/src/main/resources/schema.sql`。本文与 `schema.sql` 逐项对齐，**不引入任何 `schema.sql` 中不存在的表或列**。
> **范围**：博客业务明确需要的 **10 张表**。目标态功能（点赞幂等对、定时发布调度器、邮箱修改）**都不需要新的表或列**——这是本文的一条硬约束。
> **数据库**：MySQL 8，单实例单库，InnoDB，`utf8mb4`。
> **核对基准**：2026-09-29。

---

## 1. 设计原则

### 1.1 主键策略

| 规则 | 内容 |
|---|---|
| 默认主键 | `id INT NOT NULL AUTO_INCREMENT`，代理主键，无业务含义，永不复用 |
| 例外一 | `post_tags` 用 `(post_id, tag_id)` 联合主键，不另加 `id`（纯关联表，行本身无独立身份） |
| 例外二 | `site_settings` 用 `setting_key VARCHAR(64)` 自然主键（KV 表，键即身份） |
| 不自增语义键 | 邮箱、用户名、slug 一律不作为主键。文章**没有 slug**，路由按数值 `id` |
| 主键不更新 | `id` 一旦写入永不修改，不作为可更新列出现在任何语句中 |
| `likes` 保留 `id` | `likes` 同时有代理主键 `id` 和唯一键 `uk_post_user`，属现状：**去重语义由唯一键承担，`id` 保持为稳定行标识，两者都不删** |

### 1.2 命名规范

- 表名：小写 `snake_case`，实体复数（`users`、`posts`、`comments`、`tags`、`categories`、`likes`）；关联表 `post_tags`；无时间戳的补充表用单数（`seo_meta`）；日志表复数（`visit_logs`）；配置表 `site_settings`。**不得引入新的命名风格**。
- 列名：小写 `snake_case`，单数。外键列 `引用表单数 + _id`（`author_id`、`post_id`、`user_id`、`category_id`、`tag_id`、`parent_id`）。
- 布尔列：`is_` 前缀 + `TINYINT(1)` 语义（现存仅 `is_top`）。
- 计数列：`_count` 后缀（`view_count`、`like_count`、`comment_count`）。
- 时间列：`_at` 后缀，只用 `created_at` / `updated_at` / `published_at` 三个名字。
- 索引：普通索引 `idx_` 前缀，唯一索引 `uk_` 前缀，主键 `PRIMARY KEY`。列名沿用被索引列。
- 不使用保留字做列名；必须出现时用反引号包裹（`status`、`content`、`key` 类）。

### 1.3 时间字段

| 规则 | 内容 |
|---|---|
| 类型 | 一律 `TIMESTAMP`，由 **DB 默认值维护**，应用层不显式赋值 `created_at` / `updated_at` |
| `created_at` | `TIMESTAMP DEFAULT CURRENT_TIMESTAMP`，除 `post_tags`、`seo_meta`、`site_settings` 外每表都有 |
| `updated_at` | `TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`，**只有** `users`、`posts`、`site_settings` 三张表有（见 §5.7 评论无 `updated_at` 的后果） |
| `published_at` | 业务时间戳，仅 `posts` 有，`TIMESTAMP NULL DEFAULT NULL`，语义见 §5.3 |
| 时区 | 服务端 `GMT+8`，不做 UTC 存储、不做应用层时区换算 |
| 不新增时间列 | 不引入 `deleted_at`、`approved_at`、`last_login_at`、`expires_at` 等。需要时先改本文 |

### 1.4 软删除

**本项目不采用软删除，这是明确决定，不是遗漏。**

- 无 `deleted_at`、无 `is_deleted`、无任何删除标记列。
- 删除是物理 `DELETE`：文章、评论、分类、标签、关联行、点赞均为硬删。
- 「不对外可见」用**状态**表达而不是删除标记：`posts.status`、`comments.status`、`users.status`（见 §5.1）。草稿/定时文章的可见性由状态门禁实现，不靠删除标记。
- 生成 schema 时不要为「可恢复性」自行补软删除列。

### 1.5 外键与完整性

- **全库零物理外键**：`author_id`、`post_id`、`user_id`、`category_id`、`tag_id`、`parent_id` 全部是逻辑引用，完整性由应用层（Service + Mapper SQL）维护。
- schema / migration 中**不得出现 `FOREIGN KEY`、`REFERENCES`、`ON DELETE CASCADE`**。级联清理以显式 SQL 表达（例：删标签前先删其 `post_tags` 行；删文章时按 `post_id` 删评论）。
- 理由：写路径短、无锁放大、避免建表顺序依赖；单库量级下完整性靠服务层事务足够。
- 因此**允许出现孤儿引用**（如 `posts.category_id` 指向已删分类），读取侧必须容忍——这是既有设计，不是待修 bug。

### 1.6 枚举与布尔

- 封闭取值集合用 `ENUM`，共 3 处：`users.role`、`posts.status`、`comments.status`。ENUM 取值集合是对外契约的一部分，**增删取值必须先改本文与 `schema.sql`**，不得在 migration 里悄悄加值。
- 布尔语义用 `TINYINT`，取值 `1/0`，不用 `BOOLEAN`/`BIT`。
- 哨兵值优先于 `NULL` 表达「无父」：`comments.parent_id = 0` 表示根评论（不是 `NULL`）。而 `NULL` 只用于「确实不存在」：`category_id`、`published_at`、匿名评论的 `user_id`、`visit_logs.post_id`。

### 1.7 冗余与派生值

- `posts` 的三列计数（`view_count`、`like_count`、`comment_count`）是**有意的冗余派生缓存**，服务于读多写少；其中 `like_count`、`comment_count` 可由 `likes`、`comments` 表重算，`view_count` 无可重算的真源（DB 列即展示与排序的真源）；维护规则见 §5.2，属于业务模型明确规定要冗余的三列。
- **除此之外不存储任何可计算值**：分类/标签的文章数在查询里用子查询即时统计（`CategoryMapper`、`TagMapper`），**不落列** `post_count` / `article_count`，也不建汇总表。
- 不做物化视图、统计快照表、日汇总表。

### 1.8 容量与字符集

- 全表 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`（中文 + emoji），**不显式指定 collation**，随 MySQL 8 库默认。
- URL 列（`users.avatar`、`posts.cover`）与文本短列统一 `VARCHAR(255)`；名称 `VARCHAR(100)`；正文 `LONGTEXT`；摘要/评论内容 `TEXT`。容量按 §2 逐列声明，不做「顺手放宽」。
- `visit_logs.ip` 为 `VARCHAR(45)`，按 IPv6 文本最大长度取值，**不得缩短**。

### 1.9 必填性的口径

§2 的「必填」列严格照 `schema.sql` 的现值。**不要为「更严谨」而自行加 `NOT NULL`**（如 `users.status`、`posts.is_top`、各计数列在现库定义中确实可空但都带默认值）。收紧可空性是独立裁决事项，不在生成 schema 的自由发挥范围内。

---

## 2. 完整表结构

### 2.0 表清单

| # | 表 | 用途 | 主键形态 | 有 `created_at` | 有 `updated_at` |
|---|---|---|---|---|---|
| 1 | `users` | 账号、角色、公开资料 | `id` 自增 | 是 | 是 |
| 2 | `categories` | 文章分类（扁平一层） | `id` 自增 | 是 | 否 |
| 3 | `tags` | 文章标签 | `id` 自增 | 是 | 否 |
| 4 | `posts` | 文章主体 + 状态 + 计数 | `id` 自增 | 是 | 是 |
| 5 | `post_tags` | 文章↔标签关联 | `(post_id, tag_id)` | 否 | 否 |
| 6 | `comments` | 评论与一层回复 | `id` 自增 | 是 | 否 |
| 7 | `likes` | 用户对文章的点赞 | `id` 自增 | 是 | 否 |
| 8 | `seo_meta` | 文章 SEO 补充元数据（1:1） | `id` 自增 | 否 | 否 |
| 9 | `visit_logs` | 访问日志（预留，当前零写入） | `id` 自增 | 是 | 否 |
| 10 | `site_settings` | 站点级 KV 配置 | `setting_key` | 否 | 是 |

`likes`、`seo_meta`、`visit_logs` 在代码中零读写链路，视为预留表位；启用哪张先建完整链路（business-model.md §2.7）。

以下所有表均带 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` 与中文表注释；§2 各表只列字段，不再重复。

### 2.1 `users` — 系统用户表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键 |
| `email` | `VARCHAR(255)` `UNIQUE` | 是 | — | 登录名；**同时是 JWT subject**（见 §5.4）；唯一 |
| `password` | `VARCHAR(255)` | 是 | — | BCrypt 密文，永不返回给任何接口 |
| `username` | `VARCHAR(100)` `UNIQUE` | 是 | — | 展示昵称，唯一 |
| `avatar` | `VARCHAR(255)` | 否 | `NULL` | MinIO **绝对 URL**（见 §5.5） |
| `bio` | `VARCHAR(255)` | 否 | `NULL` | 个人简介 |
| `role` | `ENUM('admin','user')` | 是 | `'user'` | `admin`=博主，`user`=普通用户；角色变更即时生效（§5.1） |
| `status` | `TINYINT` | 否 | `1` | `1` 正常 / `0` 禁用；禁用即鉴权链抛账号禁用异常 → 401 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 注册时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 资料更新时间 |

### 2.2 `categories` — 文章分类表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键 |
| `name` | `VARCHAR(100)` `UNIQUE` | 是 | — | 分类名，唯一；**无父子关系，扁平一层** |
| `description` | `VARCHAR(255)` | 否 | `NULL` | 分类描述 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |

### 2.3 `tags` — 文章标签表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键 |
| `name` | `VARCHAR(100)` `UNIQUE` | 是 | — | 标签名，唯一 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间；标签合并（source→target）在应用层改写 `post_tags` 后删本行 |

### 2.4 `posts` — 文章表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键；前台路由直接使用它，无 slug |
| `title` | `VARCHAR(255)` | 是 | — | 标题 |
| `content` | `LONGTEXT` | 是 | — | **Markdown 源文**，不在库内渲染 |
| `excerpt` | `TEXT` | 否 | `NULL` | 摘要 |
| `cover` | `VARCHAR(255)` | 否 | `NULL` | 封面图 MinIO **绝对 URL** |
| `author_id` | `INT` | 是 | — | 逻辑引用 `users.id`；实际只有博主（`admin`）发文 |
| `category_id` | `INT` | 否 | `NULL` | 逻辑引用 `categories.id`；定时/立即发布要求已选分类，但 DB 不设非空 |
| `status` | `ENUM('draft','published','scheduled')` | 是 | `'draft'` | 状态机见 §5.1 |
| `is_top` | `TINYINT` | 否 | `0` | `1` 置顶 / `0` 普通；参与列表默认排序 |
| `view_count` | `INT` | 否 | `0` | 冗余计数，规则见 §5.2 |
| `like_count` | `INT` | 否 | `0` | 冗余计数，规则见 §5.2 |
| `comment_count` | `INT` | 否 | `0` | 冗余计数，仅 `approved` 计入，规则见 §5.2 |
| `published_at` | `TIMESTAMP NULL` | 否 | `NULL` | **首次对外可见的时刻**，语义见 §5.3 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 编辑时间；后台草稿列表按它排序 |

### 2.5 `post_tags` — 文章与标签关联表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `post_id` | `INT` | 是 | — | 联合主键之一，逻辑引用 `posts.id` |
| `tag_id` | `INT` | 是 | — | 联合主键之一，逻辑引用 `tags.id` |

无时间列、无额外列。文章的标签集合以「全删该 `post_id` 的行 → 重新插入」方式整体替换。

### 2.6 `comments` — 文章评论表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键 |
| `post_id` | `INT` | 是 | — | 逻辑引用 `posts.id` |
| `user_id` | `INT` | 否 | `NULL` | 逻辑引用 `users.id`；**`NULL` = 匿名访客**；值由服务端从安全上下文回填，请求体携带的该值一律被覆盖 |
| `content` | `TEXT` | 是 | — | 评论内容 |
| `parent_id` | `INT` | 否 | `0` | **`0` = 根评论**；非 0 为父评论 `id`（自引用，无物理外键）；**回复只支持两级**，不嵌套 |
| `status` | `ENUM('pending','approved','rejected')` | 是 | `'approved'` | 审核状态；DB 默认 `'approved'` **只对绕过服务层的直插 SQL 生效**，服务层按开关显式写 `'pending'` 或 `'approved'`（§5.6） |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 评论时间 |

### 2.7 `likes` — 文章点赞表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键（保留理由见 §1.1） |
| `post_id` | `INT` | 是 | — | 逻辑引用 `posts.id` |
| `user_id` | `INT` | 是 | — | 逻辑引用 `users.id`；**点赞必须登录，本列不可空** |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 点赞时间 |

唯一键 `uk_post_user (post_id, user_id)` 是「一人一赞」的**唯一硬保证**，去重不依赖应用层判断。点赞目标只有文章，**不引入 `target_type` 列**。

### 2.8 `seo_meta` — SEO 信息表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键 |
| `post_id` | `INT` `UNIQUE` | 是 | — | 逻辑引用 `posts.id`；唯一约束使其与文章构成 **1:1** |
| `title` | `VARCHAR(255)` | 否 | `NULL` | SEO 标题 |
| `description` | `VARCHAR(255)` | 否 | `NULL` | SEO 描述 |
| `keywords` | `VARCHAR(255)` | 否 | `NULL` | SEO 关键词，逗号分隔文本，不建关键词表 |

无时间列（现状）：SEO 内容随文章一起更新，不需要独立时间戳。

### 2.9 `visit_logs` — 访问日志表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键 |
| `post_id` | `INT` | 否 | `NULL` | 逻辑引用 `posts.id`；**`NULL` = 非文章页访问** |
| `ip` | `VARCHAR(45)` | 是 | — | 访问者 IP，按 IPv6 文本长度取值 |
| `user_agent` | `VARCHAR(255)` | 否 | `NULL` | UA，原样存文本，不做解析列 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 访问时间 |

预留表位，当前零写入；纯追加埋点表，不参与前台展示，不建聚合派生表。

### 2.10 `site_settings` — 站点配置表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `setting_key` | `VARCHAR(64)` | 是 | — | 主键，**下划线命名**的配置键 |
| `setting_value` | `VARCHAR(255)` | 否 | `NULL` | 配置值，一律以字符串存储 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间（本表无 `created_at`） |

当前有效键**只有两个**，均为布尔语义字符串：

| 键 | 含义 | 键未配置时的默认（在代码里，不在库里） |
|---|---|---|
| `comment_moderation` | 评论是否需要先审后发 | `true`（开审核） |
| `registration_enabled` | 是否开放注册 | `true` |

表是稀疏的：缺行等同缺省值，**不要为「显式化」而预插默认行**。新键必须先写入本文再使用。

---

## 3. 表之间的关系

`schema.sql` 无任何物理外键，以下全部是**逻辑关系**，由应用层维护（§1.5）。

### 3.1 关系总表

| 源 | 目标 | 基数 | 关联列 | 可空 | 删除行为（应用层） |
|---|---|---|---|---|---|
| `users` | `posts` | 1 : N | `posts.author_id` | 否 | 不级联；发文者是博主，账号删除场景不在业务内 |
| `users` | `comments` | 1 : N | `comments.user_id` | **是**（`NULL`=匿名） | 评论独立于账号存在 |
| `users` | `likes` | 1 : N | `likes.user_id` | 否 | 不级联（业务无删号） |
| `categories` | `posts` | 1 : N | `posts.category_id` | **是** | 删分类不删文章；分类计数即时统计 |
| `posts` | `comments` | 1 : N | `comments.post_id` | 否 | 删文章时按 `post_id` 显式删其评论 |
| `posts` | `post_tags` | 1 : N | `post_tags.post_id` | 否 | 删除文章时先按 `post_id` 删其关联行（business-model.md §4.4、§5.7 第 7 条）；保存文章时全删重插该 `post_id` 的关联行 |
| `tags` | `post_tags` | 1 : N | `post_tags.tag_id` | 否 | 删标签前显式删其 `post_tags` 行 |
| `posts` | `likes` | 1 : N | `likes.post_id` | 否 | 点赞行随取消点赞按 `(post_id, user_id)` 删除；删文章时按 `post_id` 删点赞行（目标态） |
| `posts` | `seo_meta` | **1 : 1** | `seo_meta.post_id` | 否 | 由 `seo_meta.post_id` 的 UNIQUE 保证；一文章最多一行 SEO；删文章时删其 SEO 行（目标态） |
| `posts` | `visit_logs` | 1 : N | `visit_logs.post_id` | **是**（`NULL`=非文章页） | 日志不清理，不随文章删除 |
| `comments` | `comments` | 自引用，**两级** | `comments.parent_id` | 否（`0`=根） | 删根评论时连同 `parent_id = 根id` 的回复一起删 |
| `site_settings` | — | 无关联 | — | — | 独立 KV，不与任何实体连接 |

### 3.2 需要明确的关系语义

- **多对多经由 `post_tags`**：文章与标签是 N : M，全部经由 `post_tags (post_id, tag_id)` 联合主键，同一 `(post, tag)` 至多一行（主键天然去重）。
- **评论只有两级**：根评论 `parent_id = 0`，回复 `parent_id = 根评论id`。**不存在三级或更深的合法数据**，读取侧按「根 + 其直接回复」装载，不需要闭包表、路径列或递归查询。
- **回复的可见性独立判定**：每条评论（含回复）各自持有 `status`；前台只展示 `approved`，只有 `approved` 计入 `comment_count`（见 business-model.md §4.4）。
- **匿名有两处不同含义**：评论的匿名 = `comments.user_id IS NULL`；点赞无匿名（`likes.user_id` 必填）。二者不可混用同一种空值判断。
- **`SEO` 不塞进 `posts`**：SEO 三元组独立成 `seo_meta`，通过 `post_id` UNIQUE 与文章 1:1。（目标态）读取时左连接；无 SEO 行的文章属正常情况。
- **`content` 里的图片不是关系**：正文内联图片是 Markdown 文本里的绝对 URL，**不与 `posts` 建立任何引用关系**，没有图片附件表（见 §6）。

---

## 4. 索引建议

### 4.1 必须存在（与 `schema.sql` 现值一致，逐条不可缺）

| 表 | 索引 | 字段 | 类型 | 支撑的查询 / 作用 |
|---|---|---|---|---|
| `users` | 主键 | `id` | PRIMARY | 鉴权链每次请求按 id 重载用户（无用户缓存，故禁用即时生效） |
| `users` | 唯一 | `email` | UNIQUE | 登录查询主路径；同时是邮箱修改时的冲突判据 |
| `users` | 唯一 | `username` | UNIQUE | 注册时的昵称冲突判据 |
| `categories` | 主键 | `id` | PRIMARY | 分类被 `posts.category_id` 按 id 引用 |
| `categories` | 唯一 | `name` | UNIQUE | 分类名冲突判据 |
| `tags` | 主键 | `id` | PRIMARY | 标签经 `post_tags.tag_id` 按 id 引用 |
| `tags` | 唯一 | `name` | UNIQUE | 标签名冲突判据 |
| `posts` | 主键 | `id` | PRIMARY | 详情、编辑回显 |
| `posts` | `idx_status_time` | `(status, published_at)` | 普通 | **前台列表可见性过滤 + 定时发布扫描**；支撑「`status='published'` 按发布时刻排序」 |
| `posts` | `idx_author` | `author_id` | 普通 | 后台按作者筛选、`countByAuthor` |
| `posts` | `idx_category` | `category_id` | 普通 | 分类维度列表、分类文章数即时统计 |
| `post_tags` | 主键 | `(post_id, tag_id)` | PRIMARY | 按文章装载标签；同文章同标签去重 |
| `comments` | 主键 | `id` | PRIMARY | 按 id 定位评论 |
| `comments` | `idx_post_id` | `post_id` | 普通 | 前台文章评论列表（根 + 回复装载） |
| `comments` | `idx_status_time` | `(status, created_at)` | 普通 | 后台待审列表（`pending` 按时间）、Dashboard 待审计数 |
| `likes` | 主键 | `id` | PRIMARY | 行标识 |
| `likes` | `uk_post_user` | `(post_id, user_id)` | **UNIQUE** | **防重复点赞的唯一硬保证**；`INSERT` 忽略重复；`DELETE` 按此前缀定位；`COUNT(*)` 按 `post_id` 前缀走索引 |
| `seo_meta` | 主键 | `id` | PRIMARY | 行标识 |
| `seo_meta` | 唯一 | `post_id` | UNIQUE | 使 SEO 与文章 1:1；（目标态）左连接的驱动列 |
| `visit_logs` | 主键 | `id` | PRIMARY | 行标识 |
| `visit_logs` | `idx_post_time` | `(post_id, created_at)` | 普通 | 按文章 + 时间维度查访问 |
| `site_settings` | 主键 | `setting_key` | PRIMARY | 按键读配置 |

### 4.2 唯一约束即索引，不得遗漏

`users.email`、`users.username`、`categories.name`、`tags.name`、`seo_meta.post_id`、`likes (post_id, user_id)` 共 6 处唯一约束是**业务规则本身**，不是性能优化。少了它们，注册重名、重复点赞、SEO 多行等约束在库层面失守。

### 4.3 明确不要建的索引

- 不给 `status`、`is_top`、`role` 这类低基数列**单独**建索引（值域极小，选择性不足，只会增加写放大）。`status` 只出现在联合索引的最左列。
- 不给 `content`、`excerpt`、`bio`、`description` 建全文索引。站内搜索是 business-model.md §3.2 的既有 P1 功能，复用列表接口；不引入搜索引擎（business-model.md §5.7 第 5 条）。
- 不给 `visit_logs.ip`、`user_agent` 建索引（只写与粗粒度查询，无按 IP 检索的业务）。
- 不为「以后可能按 X 查」预留索引。

### 4.4 已知的索引代价（如实记录，不要自作优化）

- 定时发布扫描谓词形如 `status='scheduled' AND published_at <= NOW()`，可利用 `idx_status_time`；不为此加生成列。
- `post_tags` 只有 `(post_id, tag_id)` 联合主键，**无 `tag_id` 方向的索引**（已核对 `schema.sql`）。按标签筛选、合并标签（按 `tag_id` 改写关联行）、删标签前清关联都要按 `tag_id` 查，走不到主键最左列。当前量级下可接受，**不新增索引**；要加先给出慢查询证据。
- 后台草稿列表按 `updated_at` 排序、最热列表按 `view_count` 排序，均无专属索引。当前量级下可接受，新增索引前先给出慢查询证据。

---

## 5. 约束与注意事项

### 5.1 状态即权限，不要新增权限列

| 列 | 状态机 | 对外可见性 |
|---|---|---|
| `posts.status` | `draft → published`（`published_at` 为空时写 `NOW()`）；`draft → scheduled`（`published_at` = 计划时刻）；`scheduled → published`（调度器到点只改 `status`，不改 `published_at`）；`published → draft`、`scheduled → draft` 允许经编辑器更新接口（`PUT /articles/{id}` 带 `status`）改回 / 撤下，后者清空 `published_at` | `draft` 与 `scheduled` **对外不可见**；读取侧做状态门禁：匿名拒绝，作者本人或 `admin` 放行 |
| `comments.status` | `pending → approved`（`comment_count +1`）；`pending → rejected`（计数不变）；提交时的初始状态由 `comment_moderation` 开关决定。仅上述两条裁决路径，不做回审；删除评论见 §5.2 | 只有 `approved` 计入 `comment_count` 与前台展示 |
| `users.status` | `1 ⇄ 0` | 禁用者仍可匿名读 `published` 内容，但不能评论、点赞、改资料 |
| `users.role` | `admin ⇄ user`（`ENUM`，无角色表） | 后台写接口要求 `admin`；**普通登录用户不能发文**，只有博主可发文 |

- `draft` 与 `scheduled` 都对外不可见，区别只在 `scheduled` 带到期时刻；后台草稿筛选里二者合并出现，不要新增 `visibility` 之类列。
- 角色与状态的变更**即时生效**，因为鉴权链每次携带 token 的请求都回库重载用户。所以**不需要 token 黑名单表、不需要 session 表**（§6）。

### 5.2 冗余计数的一致性规则（写路径必须遵守）

| 字段 | 维护规则 | 说明 |
|---|---|---|
| `view_count` | 每次文章详情访问 +1 | 现状：Redis 与 DB **各自独立自增、互不回读**，Redis 键只写不读。**保持现状**，不要在生成 schema/migration 时引入回写列或计数器表。若将来改为批量回写，正确动机是削减写放大，而非修复不一致 |
| `like_count` | 每次点赞/取消后按 `likes` 表**绝对重算**：`like_count = COUNT(likes WHERE post_id = posts.id)` | 不用 `+1/-1`：绝对重算天然幂等、重试不漂移、顺带修正历史脏数据。存量盲增可能已抬高该值，重算即修正 |
| `comment_count` | 仅 `approved` 计入；跨越 `approved` 边界时增减；扣减必须下限截断（等价 `GREATEST(comment_count - n, 0)`）；删评论按「自身 + 其回复」中已通过条数批量扣 | **禁止出现负数计数** |
| 三列共同点 | 是派生缓存，**不是真源**。`view_count` 无可重算的真源（访问行为不入库，见 business-model.md §2.7、§5.6），DB 列即展示与排序的真源；`like_count`、`comment_count` 才分别以 `likes` 表、`comments` 表重算 | 不要为「对账」新增校正日志表 |

### 5.3 `published_at` 的语义（易错点）

- 含义是**首次对外可见的时刻**：文章首次成为 `published` 之后不再变化；`scheduled` 期间它是可覆盖的计划时刻，`scheduled → draft` 时清空。
- `scheduled` 文章存的是**未来**的计划时刻，这是设计而非脏数据。
- 创建/更新路径：`status='published'` 且原值为空 → 写 `NOW()`；`status='scheduled'` → 写计划时刻（必填且晚于当前时间）；由 `scheduled` 撤下为 `draft` → 清空该值；`draft` 保持期间不动原值。
- 调度器只把到期的 `scheduled` 改为 `published`，不改写 `published_at`；存量 `published_at` 为空的 `scheduled` 行需人工处理，不由调度器兜底。
- **未裁决项（不要自行实现）**：管理员在到点前把 `scheduled` 手工改为 `published` 时，`published_at` 仍是未来时刻，前台会显示「发布于 未来」。是否在提前发布时重写 `published_at` 属业务语义选择（`business-model.md` C5 的 D8），**等裁决，本文不定义规则，也不为此加列**。

### 5.4 身份相关的硬约束

- **JWT subject 是邮箱，不是用户 id**。因此 `users.email` 的 UNIQUE 兼具冲突判据作用，且修改邮箱后旧 token 的 subject 再也查不到用户 → **改邮箱必须强制重新登录**。不改 subject（会让全站现存 token 一次性失效）。
- 评论的 `user_id` **只由服务端从安全上下文回填**，请求体携带的值一律覆盖；查不到对应用户时拒绝写入。生成接口时不要留「客户端可传 user_id」的口子。
- 注册验证码**只在 Redis 中**（键 `auth:code:{email}`，TTL 5min，一次性消费）。**库里无对应表**，见 §6。邮箱修改验证码键 `user:email:code:{email}` 属 P2 目标态，当前未实现，键名以实现时为准。

### 5.5 URL 存储的既有事实（重要）

- `users.avatar`、`posts.cover` 以及正文内联图片落库的都是 MinIO **绝对地址**（`endpoint/bucket/fileName` 拼接结果），不是相对路径。
- 因此**换 MinIO 域名需要回刷库**（三处列：`users.avatar`、`posts.cover`、`posts.content` 内嵌 URL）。任何 schema 变更或迁移脚本都必须把这条列入影响面。
- 不要把 URL 列改成相对路径以「解耦域名」——那与既有前端/后端实现冲突，属独立裁决。

### 5.6 DB 默认值与服务层默认值的分工

- `comments.status` 的 DB 默认 `'approved'` **只在绕过服务层的直插 SQL 生效**；正常链路按 `comment_moderation` 显式写 `pending`/`approved`。不要因为看到 DB 默认值就推断「评论默认免审」。
- `site_settings` 的缺省值（两个 `true`）在**代码里**，不在库里；库里可能是空表或稀疏行。读取逻辑必须处理「键不存在 = 用代码默认」。
- 计数列默认 `0`、`is_top` 默认 `0`、`users.status` 默认 `1`、`users.role` 默认 `'user'`——首任博主账号需手工提升 `role`，属部署步骤，不在建表数据里。

### 5.7 其他必须知道的口径

- **`comments` 无 `updated_at`**：审核只改 `status`，审核动作时间不可追溯。这是现状，**不要补列**。
- 分类与标签的文章数是查询期即时统计的子查询结果，**不是列**；实体里出现的 `articleCount` 是查询载体。
- 实体对象上的关联装载字段（文章的作者/分类/标签/SEO、评论的用户/回复列表）**都不是数据库列**，不要为它们建列，也不要据它们建表。
- 时间序列化：`TIMESTAMP` 列映射为 `LocalDateTime`，响应为 ISO 格式；`publishTime` 的对外契约格式是 `yyyy-MM-dd HH:mm:ss`（属接口契约，不改变列类型）。不引入 `java.util.Date`，不用 `DATETIME`。
- 删除均为硬删（§1.4），跨表删除必须在同一事务里按「先删关联/子行，再删主行」顺序显式执行。
- `schema-upgrade-admin.sql` 只用于「按旧版 `schema.sql` 建过的库」补 3 处漂移（评论审核状态与索引、文章封面、站点配置表）；**全新安装只执行 `schema.sql`**。生成 migration 时不要把它当增量清单重复套用。
- 单实例部署：**不引入分布式锁、幂等表、任务执行记录表**。定时任务的幂等性由 SQL 谓词本身保证（第二次执行影响 0 行）。

---

## 6. 明确不需要的表

以下表**业务模型中没有需求，一律不得创建**。列出是为了阻止 Agent 过度设计。

| 不建 | 原因 / 现有替代 |
|---|---|
| `email_verifications` / `verification_codes` / `captcha` | 验证码只在 Redis，TTL 5min、一次性消费；落库反而留下无用敏感数据 |
| `password_reset_tokens` / `password_recoveries` | 业务当前只有注册验证码一条链路（邮箱修改验证码属 P2 目标态），无忘记密码流程 |
| `sessions` / `token_blacklist` / `revoked_tokens` | 无状态 JWT；禁用与角色变更靠「每次请求回库重载用户」即时生效 |
| `roles` / `permissions` / `user_roles` / `role_permissions`（RBAC 三件套） | 角色是 `users.role` 单列 `ENUM('admin','user')`，权限模型只有「博主 / 读者」两级 |
| `attachments` / `media` / `uploads` / `files`（媒体库表） | 上传直接进 MinIO 并返回绝对 URL，URL 落在 `avatar`/`cover`/`content` 里，无独立附件记录 |
| `post_revisions` / `draft_versions` / `autosave` | 编辑器 30s 自动保存是**原地 update 同一行**，不保留历史版本 |
| `post_images`（正文图片关联表） | 图片是 Markdown 文本内的 URL，业务不需要按图片反查文章 |
| `likes` 的 `target_type` 扩展 / `article_likes` 之外的点赞表 | 点赞目标只有文章；`business-model.md` C4 明确不引入多目标点赞抽象 |
| `favorites` / `bookmarks` / `follows` / `subscriptions` / `notifications` / `messages` | 业务无收藏、关注、订阅、站内信、通知 |
| `comment_likes` / `reactions` | 评论无点赞/表情回应 |
| `page_views_daily` / `stats_daily` / `dashboard_metrics`（统计汇总表） | Dashboard 聚合直接对 `posts`/`users`/`comments` 即时聚合；不做预聚合 |
| `categories` 的 `parent_id` / `path` / `sort_order` / `slug`；独立 `category` 树表 | 分类扁平一层，无层级、无排序、无别名 |
| `tags` 的 `slug` / `post_count` / `color` | 标签只有 `id`/`name`/`created_at`；计数即时统计 |
| `posts` 的 `slug` / `url_alias` / `canonical` 列或别名表 | 前台路由用数值 `id` |
| `post_categories`（文章↔分类多对多） | 一篇文章**至多一个**分类，即 `posts.category_id` |
| `comments_closure` / 评论 `level` / `root_id` / `path` 列 | 评论只有两级（`parent_id`，`0` 为根），无需递归结构 |
| `oauth_accounts` / `social_logins` / 第三方绑定表 | 只有邮箱 + 密码注册登录 |
| `pages` / `nav_menus` / `links`（友情链接）/ `banners` / `ads` / `themes` | 站点级可配置项走 `site_settings` KV；**新配置加键，不加表** |
| `site_settings` 之外的配置表（如 `configs`、`options`、按域拆分的设置表） | 单表 KV 已覆盖；键名与默认值见 §2.10 |
| `tenants` / `blogs`（多站点）/ `sites` | 单博主单站点，无多租户维度，任何表都不加 `tenant_id` |
| `schema_migrations` / `flyway_schema_history` / `liquibase_changelog` | 全新库由 `schema.sql` 初始化（当前仓库无 Dockerfile / 编排文件，见 business-model.md §3.3）；引入迁移框架是独立工程决策，不在数据库设计内自行添加 |
| 审计表（`audit_logs` / `operation_logs`） | 业务未要求操作审计；不要为「后台更安全」自行加审计落库 |
| `seo_meta` 之外的元数据表 / 把 SEO 三列并进 `posts` | SEO 独立成 1:1 补充表，两个方向都不改 |

**判定口径**：若某张表既不在这 10 张里，也不在 `business-model.md` Part A 的实体关系图中，它就是**过度设计**，不要生成，应先回到本文提出变更。
