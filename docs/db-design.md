# CloudLog 数据库设计文档（db-design.md）

> **用途**：面向 AI Agent 的数据库规范。本文可直接指导 schema / migration 的生成，无需再回读代码。
> **依据**：业务语义来自 [`business-model.md`](./business-model.md) **Part A**；字段、类型、索引、枚举取值的最高真源是 `docs/schema.sql`。本文与 `docs/schema.sql` 逐项对齐，**不引入任何 `docs/schema.sql` 中不存在的表或列**。
> **范围**：博客业务明确需要的 **12 张表**（§2.0 清单为唯一口径）。目标态功能（点赞幂等对、定时发布调度器、邮箱修改）**都不需要新的表或列**——这是本文的一条硬约束。第 11 张 `follows` 不是为某个目标态功能预留的，它随 2026-10-01 用户裁决新增的关注能力一并建立（business-model.md §2.8、§4.7），裁决前它属于 §6 的「不得创建」清单。
> **数据库**：MySQL 8，单实例单库，InnoDB，`utf8mb4`。
> **核对基准**：2026-09-29。

---

## 1. 设计原则

### 1.1 主键策略

| 规则 | 内容 |
|---|---|
| 默认主键 | `id INT NOT NULL AUTO_INCREMENT`，代理主键，无业务含义，永不复用。**`users` 与 `posts` 两张表不走这条规则**（见例外三），其余表照旧 |
| 例外一 | `post_tags` 用 `(post_id, tag_id)` 联合主键，不另加 `id`（纯关联表，行本身无独立身份） |
| 例外二 | `site_settings` 用 `setting_key VARCHAR(64)` 自然主键（KV 表，键即身份） |
| 例外三 | `users.id` 与 `posts.id` 都是 `BIGINT` **且无 `AUTO_INCREMENT`**，由应用层雪花 ID 生成（规则见 §1.1.1）。引用它们的 10 个列同为 `BIGINT`：4 个 `post_id`（`post_tags`/`comments`/`likes`/`visit_logs`）与 6 个用户列（`posts.author_id`、`comments.user_id`、`likes.user_id`、`follows.author_id`、`follows.follower_id`、`login_logs.user_id`）。**这些表自身的 `id`（如 `likes.id`、`follows.id`、`comments.id`）仍按默认规则自增** |
| 不自增语义键 | 邮箱、用户名、slug 一律不作为主键。文章**没有 slug**，路由直接用它那串雪花 `id` |
| 主键不更新 | `id` 一旦写入永不修改，不作为可更新列出现在任何语句中 |
| `likes` 保留 `id` | `likes` 同时有代理主键 `id` 和唯一键 `uk_post_user`，属现状：**去重语义由唯一键承担，`id` 保持为稳定行标识，两者都不删** |

#### 1.1.1 `users` 与 `posts` 雪花 ID 的生成与传输

| 环节 | 规则 |
|---|---|
| 生成方 | **应用层**，不是数据库：`ArticleService.createPost` 与 `AuthService.register` 在 `insert` 前调 `IdUtil.getSnowflakeNextId()`（Hutool `cn.hutool:hutool-all`，已有依赖，不为此新增任何库）分别给 `Post.id`、`User.id` 赋值 |
| 写入方式 | `PostMapper.xml` 与 `UserMapper.xml` 的 `insert` 显式列出 `id` 列，**不带 `useGeneratedKeys` / `keyProperty`**；主键既不自增也不回填。其余表（comments/likes/follows/tags/categories/users 之外的）保留自增回填 |
| 机器位 | 单机部署，直接用 Hutool 默认 `workerId` / `datacenterId`，**不做多节点分配配置**；将来上多实例必须先解决机器位，否则同毫秒可能撞号 |
| 取值形态 | 64 位整数，当前约 19 位十进制；同实例内随时间**递增且不重复**，但**不连续**（中间空洞是正常现象，不是丢数据） |
| 传输形态 | 19 位超出 JS `Number.MAX_SAFE_INTEGER`，直传必然丢精度。Jackson 全局把 `Long` / `long` 序列化为**字符串**（`cloudlog_api` 的 `JacksonConfig`），因此 JSON 里文章 ID 一律是 `"7123456789012345678"` 这种字符串 |
| 前端契约 | 路由参数、store、API 入参与组件 prop 中的文章 ID **全程 `string`**；不得出现 `Number(article.id)` 或任何把它当数值参与运算的代码。同一规则也覆盖因此变字符串的其它 `Long` 字段（分页 `total`、后台工作台计数），这些字段在**消费侧**按需 `Number()` |
| 动机 | 自增 ID 会暴露文章总数与注册用户数，并可被逐号遍历抓取；雪花 ID 不可预测、不泄露规模 |
| 迁移口径 | 项目处于开发阶段：**直接改 `docs/schema.sql` 并清库重建**，不写存量数据的 `ALTER`/回填脚本，仓库内也不保留增量升级脚本 |

### 1.2 命名规范

- 表名：小写 `snake_case`，实体复数（`users`、`posts`、`comments`、`tags`、`categories`、`likes`）；关联表 `post_tags`；无时间戳的补充表用单数（这一档原先只有 `seo_meta`，该表已于 2026-10-03 删除，规则留着是为下一张同型表不必再议）；日志表复数（`visit_logs`、`login_logs`）；配置表 `site_settings`。**不得引入新的命名风格**。
- 列名：小写 `snake_case`，单数。外键列 `引用表单数 + _id`（`author_id`、`post_id`、`user_id`、`category_id`、`tag_id`、`parent_id`）。
- 布尔列：`is_` 前缀 + `TINYINT(1)` 语义（现存 `posts.is_top`、`banned_words.is_enabled`）。
- 计数列：`_count` 后缀（`view_count`、`like_count`、`comment_count`）。
- 时间列：`_at` 后缀，只用 `created_at` / `updated_at` / `published_at` 三个名字。
- 索引：普通索引 `idx_` 前缀，唯一索引 `uk_` 前缀，主键 `PRIMARY KEY`。列名沿用被索引列。
- 不使用保留字做列名；必须出现时用反引号包裹（`status`、`content`、`key` 类）。

### 1.3 时间字段

| 规则 | 内容 |
|---|---|
| 类型 | 一律 `TIMESTAMP`，由 **DB 默认值维护**，应用层不显式赋值 `created_at` / `updated_at` |
| `created_at` | `TIMESTAMP DEFAULT CURRENT_TIMESTAMP`，除 `post_tags`、`site_settings` 外每表都有 |
| `updated_at` | `TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`，**只有** `users`、`posts`、`site_settings`、`banned_words` 四张表有（见 §5.7 评论无 `updated_at` 的后果） |
| `published_at` | 业务时间戳，仅 `posts` 有，`TIMESTAMP NULL DEFAULT NULL`，语义见 §5.3 |
| 时区 | 服务端 `GMT+8`，不做 UTC 存储、不做应用层时区换算。时刻列都是 `TIMESTAMP`——MySQL 按**会话时区**换算存取，`NOW()` 与 `CURRENT_TIMESTAMP` 报的也是会话时区的墙钟，所以「服务端 GMT+8」这句话实际是靠会话时区成立的，而不是靠列类型。基由**连接**钉：`connectionTimeZone=%2B08:00` + `forceConnectionTimeZoneToSession=true` 让驱动在建连时发 `SET time_zone='+08:00'`（只写 `serverTimezone` 不够——它只告诉驱动怎么解释字节，不改会话，会话仍是主机的 `SYSTEM`）。钉住之后 `NOW()` / `CURRENT_TIMESTAMP` / `ON UPDATE` 与 Java 侧 `businessNow()` 写的北京墙钟同基，SQL 里继续用 `NOW()` 即可；**不要**换成写死的 `CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00')`——那样列值仍随会话换算、基准却被钉死，会话一旦不是 +08 反而凭空差 8 小时。实际会话值由 `DataSourceTimeZoneTest` 断言，不靠读配置推定。Java 侧一律 `businessNow()` / `ArticleService.BUSINESS_ZONE`，不写裸 `now()` 与 `ZoneId.systemDefault()` |
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

- 封闭取值集合用 `ENUM`，共 4 处：`users.role`、`posts.status`、`comments.status`、`banned_words.action`。ENUM 取值集合是对外契约的一部分，**增删取值必须先改本文与 `schema.sql`**，不得在 migration 里悄悄加值。
- 布尔语义用 `TINYINT`，取值 `1/0`，不用 `BOOLEAN`/`BIT`。
- 哨兵值优先于 `NULL` 表达「无父」：`comments.parent_id = 0` 表示根评论（不是 `NULL`）。而 `NULL` 只用于「确实不存在」：`category_id`、`published_at`、匿名评论的 `user_id`、`visit_logs.post_id`。

### 1.7 冗余与派生值

- `posts` 的三列计数（`view_count`、`like_count`、`comment_count`）是**有意的冗余派生缓存**，服务于读多写少；其中 `like_count`、`comment_count` 可由 `likes`、`comments` 表重算，`view_count` 无可重算的真源（DB 列即展示与排序的真源）；维护规则见 §5.2，属于业务模型明确规定要冗余的三列。
- `posts.excerpt` 是**落列存储的派生值**（2026-10-03 起，此前由作者在手填）：服务端在状态流转进 `published` / `scheduled` 且该列为空时从 `content` 生成，**已有值不重算**，所以它不是一条可重算的缓存，而是「对外可见那一刻冻结的摘录」——正文后续改动不会改写它，删列也无法从别处还原出当初那一份。列宽仍是 `TEXT`（§1.8），150 码点这个上限由展示口径定，不靠列宽兜。算法、生成时机与不重算的理由见 business-model.md §2.3、§4.2 第 6 条。
- **除此之外不存储任何可计算值**：分类/标签的文章数在查询里用子查询即时统计（`CategoryMapper`、`TagMapper`），**不落列** `post_count` / `article_count`，也不建汇总表。
- 不做物化视图、统计快照表、日汇总表。

### 1.8 容量与字符集

- 全表 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`（中文 + emoji），**不显式指定 collation**，随 MySQL 8 库默认。
- URL 列（`users.avatar`、`posts.cover`）与文本短列统一 `VARCHAR(255)`；名称 `VARCHAR(100)`；正文 `LONGTEXT`；摘要/评论内容 `TEXT`。容量按 §2 逐列声明，不做「顺手放宽」。
- `visit_logs.ip` 为 `VARCHAR(45)`，按 IPv6 文本最大长度取值，**不得缩短**。
- `banned_words.word` 为 `VARCHAR(50)`：违禁词是短词条，50 字符远超实际词长；`comments.hit_word` 取同一宽度，因为它是命中词的值拷贝。**词库不再有总量上限**——它是一行一个词，容量由表承担而不是由某个 `VARCHAR(255)` 列承担；此前寄居 `site_settings.banned_words` 时「约 80 个双字词」的上限已随该键删除而消失（见 §2.10、§2.12）。

### 1.9 必填性的口径

§2 的「必填」列严格照 `schema.sql` 的现值。**不要为「更严谨」而自行加 `NOT NULL`**（如 `users.status`、`posts.is_top`、各计数列在现库定义中确实可空但都带默认值）。收紧可空性是独立裁决事项，不在生成 schema 的自由发挥范围内。

---

## 2. 完整表结构

### 2.0 表清单

| # | 表 | 用途 | 主键形态 | 有 `created_at` | 有 `updated_at` |
|---|---|---|---|---|---|
| 1 | `users` | 账号、角色、公开资料 | `id` 雪花（无自增） | 是 | 是 |
| 2 | `categories` | 文章分类（扁平一层） | `id` 自增 | 是 | 否 |
| 3 | `tags` | 文章标签 | `id` 自增 | 是 | 否 |
| 4 | `posts` | 文章主体 + 状态 + 计数 | `id` 雪花（无自增） | 是 | 是 |
| 5 | `post_tags` | 文章↔标签关联 | `(post_id, tag_id)` | 否 | 否 |
| 6 | `comments` | 评论与一层回复 | `id` 自增 | 是 | 否 |
| 7 | `likes` | 用户对文章的点赞 | `id` 自增 | 是 | 否 |
| 9 | `visit_logs` | 文章访问日志（追加表） | `id` 自增 | 是 | 否 |
| 10 | `site_settings` | 站点级 KV 配置 | `setting_key` | 否 | 是 |
| 11 | `follows` | 读者↔作者的关注关系 | `id` 自增 | 是 | 否 |
| 12 | `banned_words` | 评论违禁词库（一词一行） | `id` 自增 | 是 | 是 |
| 13 | `login_logs` | 登录尝试日志（追加表） | `id` 自增 | 是 | 否 |

`visit_logs` 于 2026-10-03 建成完整读写链路（§2.9，业务侧口径见 business-model.md §2.11），business-model.md §2.7 那份预留表位清单因此清空。**序号 8 是空位**：`seo_meta` 已于 2026-10-03 下线删除，详情页的 SEO 三元组改由服务端按回落链合成（business-model.md §2.10）。序号不回收——§2.x 这套编号被三份文档交叉引用，挪号等于把所有引用一起改一遍，得不偿失。`likes` 链路已启用，其读写与删除规则见 §3.1。`follows` 随关注能力一并启用，读写规则同见 §3.1。`banned_words` 是**内容数据不是站点配置**：它在「评论管理」内维护、逐行增删启停，因此不入 `site_settings`（理由见 §2.12 与 §6）。

以下所有表均带 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` 与中文表注释；§2 各表只列字段，不再重复。

### 2.1 `users` — 系统用户表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 是 | — | 主键；**应用层雪花 ID，无 `AUTO_INCREMENT`**（§1.1.1） |
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
| `id` | `BIGINT` | 是 | — | 主键；**应用层雪花 ID，无 `AUTO_INCREMENT`**（§1.1.1）；前台路由直接使用它，无 slug |
| `title` | `VARCHAR(255)` | 是 | — | 标题 |
| `content` | `LONGTEXT` | 是 | — | **Markdown 源文**，不在库内渲染 |
| `excerpt` | `TEXT` | 否 | `NULL` | 摘要；**服务端由 `content` 派生**，作者侧不填（§1.7、business-model.md §4.2 第 6 条） |
| `cover` | `VARCHAR(255)` | 否 | `NULL` | 封面图 MinIO **绝对 URL** |
| `author_id` | `BIGINT` | 是 | — | 逻辑引用 `users.id`（用户雪花 ID）；实际只有博主（`admin`）发文 |
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
| `post_id` | `BIGINT` | 是 | — | 联合主键之一，逻辑引用 `posts.id`（雪花值） |
| `tag_id` | `INT` | 是 | — | 联合主键之一，逻辑引用 `tags.id` |

无时间列、无额外列。文章的标签集合以「全删该 `post_id` 的行 → 重新插入」方式整体替换。

### 2.6 `comments` — 文章评论表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键 |
| `post_id` | `BIGINT` | 是 | — | 逻辑引用 `posts.id`（雪花值） |
| `user_id` | `BIGINT` | 否 | `NULL` | 逻辑引用 `users.id`（用户雪花 ID）；**`NULL` = 匿名访客**；值由服务端从安全上下文回填，请求体携带的该值一律被覆盖 |
| `content` | `TEXT` | 是 | — | 评论内容 |
| `parent_id` | `INT` | 否 | `0` | **`0` = 根评论**；非 0 为父评论 `id`（自引用，无物理外键）；**回复只支持两级**，不嵌套 |
| `status` | `ENUM('pending','approved','rejected')` | 是 | `'approved'` | 审核状态；DB 默认 `'approved'` **只对绕过服务层的直插 SQL 生效**，服务层按开关显式写 `'pending'` 或 `'approved'`（§5.6） |
| `hit_word` | `VARCHAR(50)` | 否 | `NULL` | 机审命中的**第一个**违禁词，值拷贝自 `banned_words.word`。只有落 `pending` 的评论会写：命中 `reject` 词的评论直接被拒、根本不入库，未命中为 `NULL`。**不是引用也不随词库变更**——删词、改词、停用词都不回写存量评论（§2.12） |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 评论时间 |

### 2.7 `likes` — 文章点赞表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键（保留理由见 §1.1） |
| `post_id` | `BIGINT` | 是 | — | 逻辑引用 `posts.id`（雪花值） |
| `user_id` | `BIGINT` | 是 | — | 逻辑引用 `users.id`（用户雪花 ID）；**点赞必须登录，本列不可空** |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 点赞时间 |

唯一键 `uk_post_user (post_id, user_id)` 是「一人一赞」的**唯一硬保证**，去重不依赖应用层判断。点赞目标只有文章，**不引入 `target_type` 列**。

### 2.8 （已删除）`seo_meta` — SEO 信息表

本表已于 2026-10-03 下线删除。序号与本节位置保留，是为了不让 §2.9 以下的编号集体位移——那会把三份文档里的交叉引用全部改一遍。

详情页的 SEO 三元组改由服务端现算、不入库（business-model.md §2.10）：标题 = `posts.title` 拼站点名（站点名取 `site_settings.site_name`）、描述 = `posts.excerpt`（2026-10-03 起它是正文的派生物，见 §1.7）、关键词 = 该文章标签名按「, 」拼接。后台的「待补全 SEO」清单与 `seoConfigured` 派生列同轮删除——手动录入面消失之后，「哪篇还没填 SEO」这个问题没有主语，谁都没有可执行的动作。

### 2.9 `visit_logs` — 访问日志表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键 |
| `post_id` | `BIGINT` | 否 | `NULL` | 逻辑引用 `posts.id`（雪花值）；**`NULL` = 非文章页访问**。当前唯一的写入方是文章详情（business-model.md §2.11），所以实际行恒非空；列仍留可空，非文章页访问将来若要记账不必动表 |
| `ip` | `VARCHAR(45)` | 是 | — | 访问者 IP，按 IPv6 文本长度取值。**存原始值**，脱敏只发生在读取侧（见下方「脱敏与检索口径」） |
| `user_agent` | `VARCHAR(255)` | 否 | `NULL` | UA，原样存文本，不做解析列。写入侧按列宽截断到 255（伪造的超长串不该让整条 INSERT 失败）；归类是读时算的，不落列 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 访问时间。应用层不赋值，一律走库默认值 |

**这是纯追加表**：只 INSERT，只有保留期任务 DELETE，没有 UPDATE。不参与前台展示与任何列表排序，不建聚合派生表、不建日汇总（§1.7）。

**写入判据与 `view_count` 同源**：只在 `published` 文章详情命中 `article:view_dedup:{postId}` 小时窗口首访时落一行（§5.2），同一次判定里 `view_count` +1。所以「窗口内本表行数 ≈ 阅读增量」成立，但**不得反过来用本表回算或矫正 `view_count`**（§5.2 那条真源规则不变）：超保留期的行已删，异步写失败的行根本没落库，两处必然对不齐。bot（含空 UA）在写之前就被拒掉，所以本表里没有爬虫行。

**保留期 180 天**：`LogRetentionService` 每天 03:30 执行 `DELETE FROM visit_logs WHERE created_at < #{threshold} LIMIT #{batchSize}`，`threshold` 由服务层按业务时区算出（`LocalDateTime.now(ArticleService.BUSINESS_ZONE).minusDays(180)`）当参数绑进——不是 SQL 里的 `NOW() - INTERVAL`，因为 `LIMIT` 分批要服务层循环驱动，阈值也就随之只算一次（`@Scheduled`，属 Spring 内建能力，business-model.md §5.7 第 5 条允许）。幂等性由谓词本身保证（第二次执行影响 0 行，§5.7）；不建任务执行记录表。后续登录日志的清理并入同一个任务，不再各起一条 cron。

**脱敏与检索口径**（后台分析页的唯一出口是 admin 接口）：

- 展示形如 `112.10.*.*`：保留前两段，IPv4 逐段等宽补 `*`，IPv6 有 `::` 压缩、段数不可信，折成一枚尾随 `*`（`2001:db8:1::5 → 2001:db8:*`）。分不出两段就整串打码。
- 按地址检索输入脱敏前缀（`112.10`），服务端补一个分隔符再接通配去匹配**原始列**：`LIKE '112.10.%'`。补的那个分隔符是硬保证——不补的话 `112.1` 会连 `112.100.x.x` 一起命中。
- 两条规则同在 `IpMaskUtils`（`mask()` 与 `likePattern()`），**不在 SQL 里另写一份 `LEFT_INDEX`**：规则记两处迟早漂。响应类型里没有能装下原始地址的字段，出参一律先过 `mask()`。
- Top IP 榜按原始地址分组取回后**先脱敏合并、再截前 10**：同一 /16 下的几个地址在博主眼里是同一格，先截断会把最大的一格判漏。因此这条查询不带 LIMIT，返回的是窗口内的全部地址分组（行数由保留期与去重窗口共同兜住）。

**聚合口径**（后台分析页四条查询共用同一对 `start` / `end`，含起不含止，与后台文章列表的 `startDate`/`endDate` 同规则）：

| 板块 | 口径 |
|---|---|
| PV / UV | `COUNT(*)` / `COUNT(DISTINCT ip)` |
| 篇均访问 | PV ÷ `COUNT(DISTINCT post_id)`，即**窗口内被访问过的文章数**，不是全站文章数——分母恒不随窗口变动的数字放在这里没有意义 |
| 涨跌 | 同一条 SQL 里把窗口平移到前一等长区间（`[start - 天数, start)`），按时刻落在哪一侧条件聚合，两个数出自同一次扫描。前一区间为 0 次时百分比求不出来，回 `null`（前端显示 `—`），不写成 0 也不造一个数 |
| 趋势 | 多日窗口 `GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d')`；单日窗口按小时（`'%H'`）。**没有访问的桶由服务层补 0**，缺柱会让高峰看着比实际更高 |
| Top 文章 | `GROUP BY post_id`，**左连 `posts`**：文章已删的行标题为 `NULL`，仍然留在榜上（访问确实发生过），否则各行的分母之和就不再等于 PV |
| Top 分类 | 两次左连 `posts` → `categories`，`GROUP BY c.id, c.name`。文章已删、分类已删、文章本就无分类三种情况都落在 `c.id IS NULL` 的同一格里，读取侧容忍孤儿引用（§1.5）后由前端渲染成「未分类」 |
| 明细 | 时间 / 文章 / 地址前缀 / UA 关键词四个条件可组合；`ORDER BY created_at DESC, id DESC`，尾键不可省（同一秒内多次访问缺它会翻页重复或漏取）。UA 关键词是 `LIKE '%kw%'`，前缀通配走不上索引，靠时间范围先收窄 |

时间范围跨度在服务层限 **90 天**（前端也校验）：这个上限是为了让每次分析都落在 `idx_created_at` 的一段范围扫描里，而不是把整张表扫一遍（§4.1、§4.3）。

### 2.10 `site_settings` — 站点配置表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `setting_key` | `VARCHAR(64)` | 是 | — | 主键，**下划线命名**的配置键 |
| `setting_value` | `VARCHAR(255)` | 否 | `NULL` | 配置值，一律以字符串存储 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间（本表无 `created_at`） |

当前有效键如下（前两个是布尔语义字符串，`about_*` 是文本）：

| 键 | 含义 | 键未配置时的默认（在代码里，不在库里） |
|---|---|---|
| `comment_moderation` | 评论是否需要先审后发 | `true`（开审核） |
| `registration_enabled` | 是否开放注册 | `true` |
| `about_name` | 关于页主标题（作者名） | `tcloud`（内置默认文案） |
| `about_role` | 关于页身份说明 | 「记录技术与生活」 |
| `about_lead` | 关于页导语（RSS channel 的描述也取这一条，不再抄第二份措辞） | 内置默认段落 |
| `about_avatar` | 关于页头像地址（完整 URL） | 空（前端回落随发布的 default-avatar.svg） |
| `login_max_failures` | 登录防爆破：触发锁定的连续失败次数（合法 3–10） | `5` |
| `login_lock_minutes` | 登录防爆破：锁定时长分钟数（合法 5–60） | `15` |
| `site_name` | 站点名称：前台标题后缀、页脚落款、SEO 标题合成 | `CloudLog` |
| `icp_number` | 页脚备案号 | 空串（页脚据此不渲染那一行） |
| `site_url` | 站点对外域名（含协议、末尾不带斜杠），`SeoPublicService` 拼 sitemap / RSS 的绝对地址与 robots 的 `Sitemap:` 行 | 空串——此时按当次请求的 Host 兜底，故正式上线必须填 |
| `comment_max_length` | 单条评论字数上限（0 为不限制，合法 0–5000） | `500` |
| `comment_interval_seconds` | 同一用户评论发布间隔秒数（0 为不限制，合法 0–86400） | `60` |

上表后五代键（`site_name` 起）是补记：它们早已在 `SettingsService` 读写、此前却没进这份清单，与本文「新键必须先写入本文再使用」的规则相违。2026-10-03 随 `site_url` 一并补齐。

**登录锁定这两个键的读写不对称是有意的**：读取时把越界值夹回区间（库里手改的 999 不该把登录接口顶成 500，0 更不该等于「不锁」），保存时越界直接 400（保存是明确动作，静默改写会让人以为没生效）。二者都在 `SettingsService`，区间常量只此一份，后台说明文字里的范围由接口随响应下发、前端不抄第二份数字。语义见 business-model.md §2.12。

表是稀疏的：缺行等同缺省值，**不要为「显式化」而预插默认行**。新键必须先写入本文再使用。

**本表不承载内容数据。** 曾寄居这里的 `banned_words` 键已于 2026-10-03 删除：违禁词改为独立词库表 `banned_words`（§2.12），旧值**直接丢弃、不做迁移**——词库量小，由博主在「评论管理 → 违禁词库」重新录入或批量导入。删掉该键后 `setting_value VARCHAR(255)` 只服务开关与关于页短文案，本节不再有容量边界要守；**也不要把新的内容型数据（词库、名单、文案集合）塞回这张 KV 表**，判定口径见 §6。

### 2.11 `follows` — 用户关注关系表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键 |
| `author_id` | `BIGINT` | 是 | — | 被关注用户，逻辑指向 `users.id`（用户雪花 ID） |
| `follower_id` | `BIGINT` | 是 | — | 关注者，逻辑指向 `users.id`（用户雪花 ID）；服务端由登录态回填 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 关注时间（本表无 `updated_at`：取关是删行，不是改状态） |

- `(author_id, follower_id)` 唯一，是**业务规则本身**：一个读者对一个作者至多一条关系，重复关注由 `INSERT IGNORE` 静默成为幂等成功。
- **不建 `users` 侧的关注数冗余列**。关注数按本表实际条数即时 `COUNT`，与 `tags` 的文章计数同口径（§1.7）。单博主量级下没有为此建汇总表的空间，`page_views_daily` 一类预聚合表仍在 §6 的禁止清单里。
- 与 `posts` **无关**：删文章不清理关注行（business-model.md §5.7 第 7 条只覆盖随文章消亡的行）。
- 无 `status`、无软删、无 `target_type`；取关即硬删该行（§1.4）。

### 2.12 `banned_words` — 评论违禁词库表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键 |
| `word` | `VARCHAR(50)` `UNIQUE` | 是 | — | 违禁词本体，唯一；存 trim 后的原样大小写，匹配时才两边转小写（business-model.md §4.4 第 2 条） |
| `action` | `ENUM('pending','reject')` | 是 | `'pending'` | 命中后的处置动作：`pending` 落待审等人工裁决 / `reject` 直接拒绝、不入库 |
| `is_enabled` | `TINYINT` | 否 | `1` | `1` 参与匹配 / `0` 停用；停用即保留词条但机审跳过它 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 添加时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 启停、改动作的时间 |

- **它是内容数据，不是站点配置**，所以不入 `site_settings`（§2.10、§6）：一词一行、可分页检索、可逐条启停、可批量导入，这些都是 KV 单值给不了的；而 `setting_value VARCHAR(255)` 还给词库总量压了约 80 词的硬上限。管理入口在「评论管理」内，不在「系统设置」（ui-design.md §3.11）。
- `word` 唯一是**去重的唯一硬保证**（定位同 §2.7 `uk_post_user`、§2.11 `uk_author_follower`）：单条添加撞词与批量导入的重复项都由它判定，不依赖应用层「先查后插」。唯一性随库默认 collation（§1.8 不显式指定），MySQL 8 默认的 `utf8mb4_0900_ai_ci` 大小写不敏感，故 `AD` 与 `ad` 是同一个词——这与「匹配时两边都转小写」的口径一致，不是巧合。
- **停用不是删除**：`is_enabled = 0` 保留词条本身，机审只取 `is_enabled = 1` 的行；删除是硬删（§1.4），且**不回写** `comments.hit_word`——那一列是命中当刻的值拷贝，词库此后的增删启停一律不追溯（§2.6）。
- 与 `comments` **无引用关系**：`hit_word` 存字符串而不是 `banned_words.id`，本表因此不与任何表连接，和 `site_settings` 一样是独立表（§3.1）。
- 机审侧有**进程内缓存**：词库读多写极少，服务层缓存 `is_enabled = 1` 的词表，任何写操作（添加 / 批量导入 / 启停 / 删除）后整体失效重载，避免每条评论都查库。缓存不落 Redis、不引入缓存框架（business-model.md §5.7 第 4、5 条），**只在单实例部署假设下成立**：多实例必须换共享缓存或退回每次查库。

### 2.13 `login_logs` — 登录日志表

| 字段 | 类型建议 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `INT` `AUTO_INCREMENT` | 是 | 自增 | 主键 |
| `email` | `VARCHAR(255)` | 是 | — | **尝试登录时输入的邮箱，原样记录**，不要求账号存在（穷举者试的就是不存在的邮箱）。列宽与 `users.email` 一致，但**它不是引用**：`users` 里有没有这一行、此后改不改邮箱，都不回写本列（§1.5 的孤儿容忍在此不适用，因为压根没有引用） |
| `user_id` | `BIGINT` | 否 | `NULL` | 逻辑引用 `users.id`（雪花值，§1.1 例外三）。**只有 `result='success'` 的行回填**，三种失败恒为 `NULL` |
| `ip` | `VARCHAR(45)` | 是 | — | 来源地址，与 `visit_logs.ip` 同宽同规则（§1.8 不得缩短、§2.9 脱敏口径）。**存原始值**，脱敏只发生在读取侧，复用同一对 `IpMaskUtils.mask()` / `likePattern()` |
| `user_agent` | `VARCHAR(255)` | 否 | `NULL` | UA 原样存文本，不做解析列；写入侧按列宽截断，归类是读时算的（与 §2.9 同一条规则，共用 `UserAgentClassifier`） |
| `result` | `ENUM('success','wrong_password','locked','disabled')` | 是 | — | 四值是封闭集合（§1.6），与 `AuthService.login()` 的四个出口一一对应，见下方「四种结局」 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 尝试时刻。应用层不赋值 |

**这是纯追加表**：只 INSERT，只有保留期任务 DELETE，没有 UPDATE，也没有 DELETE 单行的入口（后台不提供删日志的能力——那正是留痕的意义）。

**四种结局与代码出口**（口径与既有分支一一对应，**不新增业务判断**）：

| `result` | 出口 | 计不计爆破数 | 说明 |
|---|---|---|---|
| `locked` | 锁定期内的尝试 | 否（本就因已达阈值才走到这里） | 该出口不再校验口令，一次尝试一行 |
| `wrong_password` | `BadCredentialsException` | **是** | 邮箱未注册在 Spring Security 里就被折成同一个异常（`hideUserNotFoundExceptions` 默认开），**因此不区分「口令错」与「账号不存在」**——区分等于开一条泄露邮箱存在性的口子，与登录失败反馈自始不泄露的口径冲突 |
| `disabled` | 其他 `AuthenticationException`（当前实际来源是 `UserDetailsServiceImpl` 对 `status != 1` 抛的 `DisabledException`） | 否 | 不是口令猜错，不该消耗阈值。异常原样抛出，401 与提示语仍由全局处理器给 |
| `success` | 认证成功 | 清键 | 唯一回填 `user_id` 的一行 |

**写入是异步的**（`@Async`，与 §2.9 同一模式）：原始地址与 UA 必须在请求线程取好传进去。写失败只记一条 error，**不影响登录接口的响应与状态码**。

**保留期 180 天**：并入 §2.9 那条 `LogRetentionService` 的同一个每日任务（该类的设计就是「新增一类日志 = 加一个天数常量 + 一个 `purgeXxx()`」），不另起 cron、不建任务执行记录表；幂等性同样由谓词保证。

**脱敏与检索口径**：与 §2.9 完全一致，且**必须一致**——两个页签在同一张后台里，同一个地址不能一处显示 `112.10.*.*`、另一处显示别的形态。响应类型里没有能装下原始地址的字段。

**聚合口径与 §2.9 的一处有意差异**：Top 榜与「近一小时失败来源数」都按 **`(email, ip)` 原始对**分组，**不做脱敏后合并**。安全场景要区分的是「哪个地址在试哪个邮箱」，同一 /16 下的两个来源在爆破视角不是同一个格；而访问日志那一榜回答的是「多少流量来自哪一段」，合并才对症。代价是：两个原始地址若落在同一 /16，榜上会出现两行同样的 `112.10.*.*`，此时两行的次数才是那一格的总量。

**索引**：`idx_email_time (email, created_at)` 服务「某邮箱的尝试轨迹」这条主查询（明细分页里 `email` 是前缀通配 `LIKE '%kw%'`，走不上该索引，索引服务的是按邮箱精确定位与时间收敛）；`idx_created_at` 服务三张计数卡、Top 榜的全表聚合与保留期 DELETE（§4.1）。排序一律 `ORDER BY created_at DESC, id DESC`，尾键不可省（同一秒内多次尝试缺它会翻页重复或漏取）。

---

## 3. 表之间的关系

`schema.sql` 无任何物理外键，以下全部是**逻辑关系**，由应用层维护（§1.5）。

### 3.1 关系总表

| 源 | 目标 | 基数 | 关联列 | 可空 | 删除行为（应用层） |
|---|---|---|---|---|---|
| `users` | `posts` | 1 : N | `posts.author_id` | 否 | 不级联；发文者是博主，账号删除场景不在业务内 |
| `users` | `comments` | 1 : N | `comments.user_id` | **是**（`NULL`=匿名） | 评论独立于账号存在 |
| `users` | `likes` | 1 : N | `likes.user_id` | 否 | 不级联（业务无删号） |
| `users` | `follows` | 1 : N | `follows.follower_id` | 否 | 不级联（业务无删号）；账号只有启停，禁用不取其关注行 |
| `users` | `follows` | 1 : N | `follows.author_id` | 否 | 同上；博主侧被关注数按 `author_id` 即时统计 |
| `categories` | `posts` | 1 : N | `posts.category_id` | **是** | 删分类不删文章；分类计数即时统计 |
| `posts` | `comments` | 1 : N | `comments.post_id` | 否 | 删文章时按 `post_id` 显式删其评论 |
| `posts` | `post_tags` | 1 : N | `post_tags.post_id` | 否 | 删除文章时先按 `post_id` 删其关联行（business-model.md §4.4、§5.7 第 7 条）；保存文章时全删重插该 `post_id` 的关联行 |
| `tags` | `post_tags` | 1 : N | `post_tags.tag_id` | 否 | 删标签前显式删其 `post_tags` 行 |
| `posts` | `likes` | 1 : N | `likes.post_id` | 否 | 点赞行随取消点赞按 `(post_id, user_id)` 删除；删文章时按 `post_id` 删点赞行（已实现） |
| `posts` | `visit_logs` | 1 : N | `visit_logs.post_id` | **是**（`NULL`=非文章页） | **不随文章删除**：文章删了，它的访问行留着（后台榜上那行变成「已删除文章」，§2.9）。行只由 180 天保留期任务删 |
| `comments` | `comments` | 自引用，**两级** | `comments.parent_id` | 否（`0`=根） | 删根评论时连同 `parent_id = 根id` 的回复一起删 |
| `site_settings` | — | 无关联 | — | — | 独立 KV，不与任何实体连接 |
| `banned_words` | — | 无关联 | — | — | 独立词库表；`comments.hit_word` 是命中词的**值拷贝**而不是引用，删词/停用不回写存量评论（§2.12） |
| `users` | `login_logs` | 1 : N | `login_logs.user_id` | **是**（三种失败为 `NULL`） | **不随用户删除**：登录尝试是发生过的事实。`login_logs.email` 更不是引用——它记的是当时输入了什么串，与 `users` 里有没有这行无关（§2.13） |

### 3.2 需要明确的关系语义

- **多对多经由 `post_tags`**：文章与标签是 N : M，全部经由 `post_tags (post_id, tag_id)` 联合主键，同一 `(post, tag)` 至多一行（主键天然去重）。
- **评论只有两级**：根评论 `parent_id = 0`，回复 `parent_id = 根评论id`。**不存在三级或更深的合法数据**，读取侧按「根 + 其直接回复」装载，不需要闭包表、路径列或递归查询。
- **回复的可见性独立判定**：每条评论（含回复）各自持有 `status`；前台只展示 `approved`，只有 `approved` 计入 `comment_count`（见 business-model.md §4.4）。
- **匿名有两处不同含义**：评论的匿名 = `comments.user_id IS NULL`；点赞与关注都没有匿名（`likes.user_id`、`follows.follower_id` 必填）。二者不可混用同一种空值判断。
- **关注是单向的，不存在「互关」这一行**：`(author_id, follower_id)` 与 `(follower_id, author_id)` 是两条互不相关的记录，业务上不判定它们同时出现，也不派生任何好友语义（business-model.md §5.7 第 12 条）。
- **`comments.hit_word` 不是关系**：它记的是机审命中当刻的词面字符串，与 `banned_words` 无 id 关联、无级联、无一致性要求。词库后来删词、改词、停用，存量评论的命中词照旧显示——这是**有意的**：审核台要能回答「当初为什么被拦下」，而不是「现在还拦不拦」。
- **SEO 不入库**：详情页那三个值是响应时按回落链现算的（标题拼站点名、描述取摘要、关键词取标签名，见 business-model.md §2.10），库里唯一相关的列是 `posts.excerpt`，而它本身也是正文的派生物（§1.7）。2026-10-03 之前它独立成 1:1 的 `seo_meta` 补充表，那张表已删除（§2.8）。
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
| `posts` | `ft_title_content` | `(title, content)` | **FULLTEXT**（`WITH PARSER ngram`） | 站内搜索不含 ASCII 字母与符号、满 2 字的关键词；依赖 `ngram_token_size`（MySQL 8 默认 2），且默认停用词表的单字母 `a`/`i` 会被 ngram 当分隔符、含英文/符号的关键词切不出词元，这类关键词与单字查询一样退回 `idx_*` + LIKE，见 §4.3 |
| `post_tags` | 主键 | `(post_id, tag_id)` | PRIMARY | 按文章装载标签；同文章同标签去重 |
| `comments` | 主键 | `id` | PRIMARY | 按 id 定位评论 |
| `comments` | `idx_post_id` | `post_id` | 普通 | 前台文章评论列表（根 + 回复装载） |
| `comments` | `idx_status_time` | `(status, created_at)` | 普通 | 后台待审列表（`pending` 按时间）、Dashboard 待审计数 |
| `likes` | 主键 | `id` | PRIMARY | 行标识 |
| `likes` | `uk_post_user` | `(post_id, user_id)` | **UNIQUE** | **防重复点赞的唯一硬保证**；`INSERT` 忽略重复；`DELETE` 按此前缀定位；`COUNT(*)` 按 `post_id` 前缀走索引 |
| `visit_logs` | 主键 | `id` | PRIMARY | 行标识 |
| `visit_logs` | `idx_post_time` | `(post_id, created_at)` | 普通 | 明细的「按文章筛选」走最左列；后台分析里按篇查访问 |
| `visit_logs` | `idx_created_at` | `created_at` | 普通 | **一切时间范围扫描**：概览、趋势、三个 Top 榜、明细与保留期删除的谓词都是 `created_at` 的一个区间。`idx_post_time` 的最左列是 `post_id`，救不了不带文章的窗口查询，2026-10-03 随访问日志启用一并建 |
| `site_settings` | 主键 | `setting_key` | PRIMARY | 按键读配置 |
| `follows` | 主键 | `id` | PRIMARY | 行标识 |
| `follows` | `uk_author_follower` | `(author_id, follower_id)` | **UNIQUE** | **防重复关注的唯一硬保证**；`INSERT IGNORE` 靠它成为幂等成功；`DELETE` 按该对精确定位；`COUNT(*)` 按 `author_id` 前缀走索引 |
| `banned_words` | 主键 | `id` | PRIMARY | 行标识；启停与删除按 id 定位 |
| `banned_words` | `uk_word` | `word` | **UNIQUE** | **词库去重的唯一硬保证**：单条添加撞词与批量导入的重复项都由它判定（§2.12） |
| `login_logs` | 主键 | `id` | PRIMARY | 行标识 |
| `login_logs` | `idx_email_time` | `(email, created_at)` | 普通 | 「某个邮箱被试了什么」是这张表的主问题：按邮箱定位 + 时间收敛。明细里那格邮箱关键词是 `LIKE '%kw%'`，走不上它（同 §4.3 对 `visit_logs` 的判断，保留是因为按邮箱精确定位这一路真实存在） |
| `login_logs` | `idx_created_at` | `created_at` | 普通 | 三张计数卡（`>= CURDATE()`、`>= NOW() - 1 HOUR`）、Top 榜的全表聚合与保留期 DELETE 的谓词都是时间区间 |

### 4.2 唯一约束即索引，不得遗漏

`users.email`、`users.username`、`categories.name`、`tags.name`、`likes (post_id, user_id)`、`follows (author_id, follower_id)`、`banned_words.word` 共 7 处唯一约束是**业务规则本身**，不是性能优化。少了它们，注册重名、重复点赞、重复关注、同一个词重复入库等约束在库层面失守。

### 4.3 明确不要建的索引

- 不给 `status`、`is_top`、`role` 这类低基数列**单独**建索引（值域极小，选择性不足，只会增加写放大）。`status` 只出现在联合索引的最左列。
- 不给 `banned_words.is_enabled`、`banned_words.action` 建索引：机审要的是「全部启用词」，本就是一次小表全取再进进程内缓存（§2.12），索引救不了它；后台按 `word` 的模糊搜索是 `LIKE '%kw%'`，前缀通配同样走不上索引。
- `posts.title` + `posts.content` 的全文索引**已建**（2026-10-03 由用户裁决移出本条禁令，见 §4.1 与 §4.4）：`FULLTEXT KEY ft_title_content (title, content) WITH PARSER ngram`。它只是 InnoDB 内建的倒排索引，不是外部搜索引擎，因此不触碰 business-model.md §5.7 第 5 条那条禁令。搜索侧的分支写在 `PostMapper.xml` 的 `findAllBrief`，分岔条件收敛在 `FulltextKeywordUtils.isIndexable`：只把**不含 ASCII 字母与符号、满 2 字**的关键词交给 `MATCH…AGAINST(… IN BOOLEAN MODE)`，其余退回 `LIKE`。原因有二：ngram 词元固定两个字符，单字进不了索引；更关键的是 ngram 分词会套用停用词表（`innodb_ft_enable_stopword` 默认开），默认表里的单字母 `a`/`i` 在分词时被当成分隔符——"Mac" 切不出词元、根本进不了倒排索引，含英文/符号（"C++"）或恰为双字符停用词（of/to）的关键词走 MATCH 必然 0 命中，且无法在查询侧绕开。LIKE 是子串语义，命中范围恒为 MATCH 的超集；博客量级下 `%kw%` 扫描可接受，这个取舍是 2026-10-04 验证 MySQL 8.4 实测行为后定的。
- 不给 `excerpt`、`bio`、`description` 建全文索引：它们要么是正文的摘录、要么是短文案，没有独立的检索需求。
- 不给 `visit_logs.ip`、`user_agent` 建索引。2026-10-03 启用后这两列确实进了检索条件（明细按地址前缀 `LIKE '112.10.%'`、按 UA 关键词 `LIKE '%kw%'`），但仍不建：**UA 那侧前缀通配本来就上不了 B-tree 索引**；地址那侧理论上 `LIKE '前缀%'` 能用，代价是每次访问多一列写放大，而这类查询总是先被 `idx_created_at` 的时间范围收窄到几百到几千行，扫这点行的成本远低于让索引常驻。要加，先拿出慢查询证据（同 §4.4 的口径）。同一判断适用于 `login_logs.ip` 与 `login_logs.user_agent`（§2.13）：登录尝试的量级比访问小一到两个数量级，更没有为地址前缀单开索引的理由。
- 不为「以后可能按 X 查」预留索引。

### 4.4 已知的索引代价（如实记录，不要自作优化）

- 定时发布扫描谓词形如 `status='scheduled' AND published_at <= NOW()`，可利用 `idx_status_time`；不为此加生成列。`published_at` 是 `TIMESTAMP`，`NOW()` 与它同靠**会话时区**这一个基（§1），会话由连接串显式钉成 `+08:00`，所以谓词不跟着数据库主机漂；换成写死的 `CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00')` 反而会打破这个对称。
- `post_tags` 只有 `(post_id, tag_id)` 联合主键，**无 `tag_id` 方向的索引**（已核对 `schema.sql`）。按标签筛选、合并标签（按 `tag_id` 改写关联行）、删标签前清关联都要按 `tag_id` 查，走不到主键最左列。当前量级下可接受，**不新增索引**；要加先给出慢查询证据。
- 后台草稿列表按 `updated_at` 排序、前台最热列表按**加权衰减表达式**排序（`business-model.md` §3.2「排序参数」），二者都无专属索引。热榜的排序键是每行现算的表达式，**任何索引都命中不了，必然 filesort**；当前量级下可接受，新增索引前先给出慢查询证据。
- `ft_title_content` 覆盖 `content` 这张 LONGTEXT 列：每次保存文章都要重算该行词元并写进倒排索引与 InnoDB 的 FTS 辅助表，这是**本索引带来的写放大**（换取搜索不再全表扫 LONGTEXT）。索引就在 `docs/schema.sql` 的 `posts` 建表语句里，随全新安装一并建立。库中缺这条索引时，满 2 字的 `MATCH…AGAINST` 直接报 1191，表现为搜索页 500——按口径这属于「库没跟上 `docs/schema.sql`」，清库重建解决，不补增量脚本。
- 访问日志的三条后台查询各有明确代价，都是**接受了换来的东西**（口径见 §2.9）：① 概览、趋势、Top 榜都要按时间窗口聚合，走 `idx_created_at` 拿到区间行之后再分组，窗口上限 90 天就是这个分组的成本闸门；② Top IP 那条不带 `LIMIT`，返回窗口内的全部地址分组——先脱敏合并才能截前 10（§2.9），代价是行数由访问者数决定而不是由榜单长度决定；③ 明细按地址前缀与 UA 关键词的两个 `LIKE` 都不走索引，靠时间范围先收窄。行数由「180 天保留期 + 每人每小时最多一篇一行」共同兜住，本量级下都不必再动索引。
- 每次窗口首访多一行 `visit_logs` 的 INSERT，这是访问日志启用带来的写放大：详情页原先只有 `view_count` 一次自增。写在 `@Async` 线程上做掉了一次往返，异步队列就是线程池——不引消息中间件（business-model.md §5.7 第 5 条）。

---

## 5. 约束与注意事项

### 5.1 状态即权限，不要新增权限列

| 列 | 状态机 | 对外可见性 |
|---|---|---|
| `posts.status` | `draft → published`（`published_at` 为空时由服务层写业务时区的当前时刻）；`draft → scheduled`（`published_at` = 计划时刻）；`scheduled → published`（调度器到点只改 `status`，不改 `published_at`）；`published → draft`、`scheduled → draft` 允许经编辑器更新接口（`PUT /articles/{id}` 带 `status`）改回 / 撤下，后者清空 `published_at` | `draft` 与 `scheduled` **对外不可见**；读取侧做状态门禁：匿名拒绝，作者本人或 `admin` 放行 |
| `comments.status` | `pending → approved`（`comment_count +1`）；`pending → rejected`（计数不变）；**允许回审**：`approved`/`rejected` → `pending`（`comment_count -1`，下限 0）；提交时的初始状态由 `comment_moderation` 开关与 `banned_words` 词库机审共同决定（§2.10、§2.12、business-model.md §4.4）：**关审核一律 `approved`；开审核先做机审——命中 `action='reject'` 的词直接抛业务异常（400），评论根本不入库、也就没有状态可写；命中 `action='pending'` 的词落 `pending` 待人工裁决，并把首个命中词写进 `comments.hit_word`；未命中直接 `approved`**。计数只在跨越 `approved` 边界时增减；删除评论见 §5.2 | 只有 `approved` 计入 `comment_count` 与前台展示 |
| `banned_words.is_enabled` | `1 ⇄ 0` | 停用只让机审跳过该词，词条本身与存量评论的 `hit_word` 都不受影响；本表只有博主能读写，无对外可见性问题 |
| `users.status` | `1 ⇄ 0` | 禁用者仍可匿名读 `published` 内容，但不能评论、点赞、改资料 |
| `users.role` | `admin ⇄ user`（`ENUM`，无角色表） | 后台写接口要求 `admin`；**普通登录用户不能发文**，只有博主可发文 |

- `draft` 与 `scheduled` 都对外不可见，区别只在 `scheduled` 带到期时刻；后台草稿筛选里二者合并出现，不要新增 `visibility` 之类列。
- 角色与状态的变更**即时生效**，因为鉴权链每次携带 token 的请求都回库重载用户。所以**不需要 token 黑名单表、不需要 session 表**（§6）。口令重置额外推一枚 Redis 失效水位（§5.4 的 `auth:token-epoch:{email}`），把改密之前签发的 token 一并作废——靠键位而非表，这条不变。

### 5.2 冗余计数的一致性规则（写路径必须遵守）

| 字段 | 维护规则 | 说明 |
|---|---|---|
| `view_count` | **只对 `published` 文章的详情访问 +1**，且同一访问者在 1 小时窗口内只计一次（去重键见下方 Redis 键位）；作者预览草稿/定时文章一律不计 | DB 列是计数的**唯一存储**。Redis 侧那枚只写不读的 `article:view_count:` 计数器已于 2026-10-03 删除（business-model.md §6 的这条技术债已还），Redis 只剩去重判定一件事。不要引入回写列或计数器表；将来若要批量回写，正确动机是削减写放大，而非修复不一致 |
| `like_count` | 每次点赞/取消后按 `likes` 表**绝对重算**：`like_count = COUNT(likes WHERE post_id = posts.id)` | 不用 `+1/-1`：绝对重算天然幂等、重试不漂移、顺带修正历史脏数据。存量盲增可能已抬高该值，重算即修正 |
| `comment_count` | 仅 `approved` 计入；跨越 `approved` 边界时增减；扣减必须下限截断（等价 `GREATEST(comment_count - n, 0)`）；删评论按「自身 + 其回复」中已通过条数批量扣 | **禁止出现负数计数** |
| 三列共同点 | 是派生缓存，**不是真源**。`view_count` 没有可重算的真源：访问行为自 2026-10-03 起确实入库（`visit_logs`，§2.9），但那张表不是它的真源——超保留期的行已删、异步写失败的行根本没落库，拿它回算只会把数压小，所以 **DB 列即展示与排序的真源**（business-model.md §2.11、§5.6）；`like_count`、`comment_count` 才分别以 `likes` 表、`comments` 表重算 | 不要为「对账」新增校正日志表，也不要用 `visit_logs` 反推 `view_count` |

**阅读链路上的 Redis 键位**（前缀统一为 `article:`，与 §5.4 的 `auth:` 键位并列；库里无对应表，见 §6）：

- `article:view_dedup:{postId}` — Set，成员是访问者标识：登录用户 `user:{userId}`（与点赞、关注同源，都从安全上下文回库取用户），匿名 `ip:{客户端地址}`（`X-Forwarded-For` 首值优先，无该头时取 `remoteAddr`）。**`SADD` + 判 `TTL` + `EXPIRE` 三步由一条 Lua 脚本一次往返完成**，返回的新成员数 > 0 才算本窗口首次访问、才允许 `view_count` 自增。合成一条是因为分三条发存在崩溃缝：`SADD` 之后、`EXPIRE` 之前进程挂掉会留下一枚永不过期的集合，此后这篇文章的窗口永不关闭，阅读计数静默停摆且不报错。窗口 3600 秒，**只在键尚无 TTL 时设置**（脚本判 `TTL < 0` 才 `EXPIRE`，顺带把改造前遗留的无 TTL 旧键补上）——若每个新访客都续期，持续流量下窗口永不关闭，先来的访客反而永久不再计数。
- 去重是**尽力而为的防刷，不是硬保证**：`X-Forwarded-For` 客户端可控，恶意访客换申报值即可绕开。地址在这枚 Set 里只作成员存在 1 小时，Set 本身不是存储；但**同一次判定会异步落一行 `visit_logs`**，那里留 180 天（§2.9），只服务后台「日志管理」那一页，且出参一律脱敏成 `112.10.*.*`，完整地址不进任何响应（business-model.md §2.11）。

### 5.3 `published_at` 的语义（易错点）

- 含义是**首次对外可见的时刻**：文章首次成为 `published` 之后不再变化；`scheduled` 期间它是可覆盖的计划时刻，`scheduled → draft` 时清空。
- `scheduled` 文章存的是**未来**的计划时刻，这是设计而非脏数据。
- 创建/更新路径：`status='published'` 且原值为空 → 写业务时区的当前时刻；由 `scheduled` 手工转为 `published` 且原值还晚于当前 → 覆写为当前时刻（business-model.md §4.2 第 3 条）；`status='scheduled'` → 写计划时刻（必填且晚于当前时间）；由 `scheduled` 撤下为 `draft` → 清空该值；`draft` 保持期间不动原值。
- 调度器只把到期的 `scheduled` 改为 `published`，不改写 `published_at`；存量 `published_at` 为空的 `scheduled` 行需人工处理，不由调度器兜底。
- **未裁决项（不要自行实现）**：管理员在到点前把 `scheduled` 手工改为 `published` 时，`published_at` 仍是未来时刻，前台会显示「发布于 未来」。是否在提前发布时重写 `published_at` 属业务语义选择（`business-model.md` C5 的 D8），**等裁决，本文不定义规则，也不为此加列**。

### 5.4 身份相关的硬约束

- **JWT subject 是邮箱，不是用户 id**。因此 `users.email` 的 UNIQUE 兼具冲突判据作用，且修改邮箱后旧 token 的 subject 再也查不到用户 → **改邮箱必须强制重新登录**。不改 subject（会让全站现存 token 一次性失效）。
- 评论的 `user_id` **只由服务端从安全上下文回填**，请求体携带的值一律覆盖；查不到对应用户时拒绝写入。生成接口时不要留「客户端可传 user_id」的口子。
- 认证面**只在 Redis 中，库里无对应表**（见 §6），前缀统一为 `auth:`，键位如下（`purpose` 取 `registration` / `email-change` / `password-reset`；**2026-10-03 增量：所有含 `{email}` 的键统一用 `trim + 转小写` 的归一化邮箱分键**——库表是 ci 排序规则、查库不区分大小写，键不归一就等于每个大小写变体各领一套额度，具体见 business-model.md §3.2「验证码与注册的防刷」）：
  - 码值键 `auth:code:{email}`（注册）、`auth:code-change:{email}`（改邮箱）、`auth:code-reset:{email}`（重置口令）：TTL 5min、一次性消费，命中即删。
  - 发码频控键 `auth:code-limit:{purpose}:{email}`（TTL 60s）与 `auth:code-daily:{purpose}:{email}`（TTL 至当日 24:00），频控口径见 business-model.md §3.2。**2026-10-03 增量**：另加两层兜底日额度——`auth:code-ip-daily:{客户端地址}`（每来源地址每日 30 次）与 `auth:code-global-daily`（全站每日 500 次），堵「持邮箱列表绕开邮箱维度发码」的邮件轰炸口子。
  - 防穷举计数键 `auth:code-try:{purpose}:{email}`：首错时设 TTL 5min（与码值键同寿），错满 5 次连带删掉码值键，校验成功时删除。按用途分键，注册码错满不牵连改邮箱码。
  - 登录防爆破计数键 `auth:login-fail:{email}:{客户端地址}`：`INCR` 计数，首错设 TTL 15min，计满 5 次那一下把窗口重新拉满，登录成功即删键。地址口径与阅读去重同源（`X-Forwarded-For` 首值优先，无该头取 `remoteAddr`），二者共用 `IpUtils.clientIp()`。
  - token 失效水位键 `auth:token-epoch:{email}`：值为置位时刻的秒级时间戳，TTL = token 有效期（7 天——那之前签发的 token 早已自然过期）。`iat` 早于水位的 token 按未登录处理，见 §5.1。**2026-10-03 增量**：置位点从「口令的两处写入」扩为三处——新增登出 `POST /auth/logout` 同样推水位（作废该账号全部在存 token）。
  - **2026-10-03 增量（上传额度）**：`upload:daily:{userId}`（TTL 24h）按用户计当日上传次数，上限 100 次，计满后 `/upload/*` 全部端点拒绝转存。

### 5.5 URL 存储的既有事实（重要）

- `users.avatar`、`posts.cover` 以及正文内联图片落库的都是 MinIO **绝对地址**（`endpoint/bucket/objectKey` 拼接结果），不是相对路径。
- 因此**换 MinIO 域名需要回刷库**（三处列：`users.avatar`、`posts.cover`、`posts.content` 内嵌 URL）。任何 schema 变更或迁移脚本都必须把这条列入影响面。
- 不要把 URL 列改成相对路径以「解耦域名」——那与既有前端/后端实现冲突，属独立裁决。

**对象 key 的分类约定**（由 `FileService.upload(file, category)` 生成）：

```
<minio.bucketName>/
├── avatar/{yyyyMM}/{uuid}.ext          # 用户头像      ← POST /upload/avatar
├── site/avatar/{yyyyMM}/{uuid}.ext     # 站点级图片（关于页头像等）← POST /upload/site-image
├── post/cover/{yyyyMM}/{uuid}.ext      # 文章封面      ← POST /upload/cover-image
└── post/content/{yyyyMM}/{uuid}.ext    # 正文配图      ← POST /upload/article-image
```

- `yyyyMM` 是上传时刻的年月，`category` 由上述四个接口在服务端固定传入，**不接受客户端参数**。bucket 名取配置项 `minio.bucketName`。
- 前缀只作用于**新上传**的对象。历史数据仍是桶根平铺的旧绝对 URL（`{uuid}.ext`），**不做回刷**——对象不迁移、已落库的 URL 不变，因此上面「换域名需回刷库」的影响面结论**不变**：新增前缀只是让新对象的 key 更长，URL 的第一段仍是 `endpoint/bucket`。
- 全仓无按 URL 反解 object key 的代码，也无 `removeObject` 链路（上传只写不删），故 key 加深不影响任何读/删路径。

### 5.6 DB 默认值与服务层默认值的分工

- `comments.status` 的 DB 默认 `'approved'` **只在绕过服务层的直插 SQL 生效**；正常链路按 `comment_moderation` 与 `banned_words` 词库机审显式写 `pending`/`approved`（见 §5.1）。不要因为看到 DB 默认值就推断「评论默认免审」。
- `comments.hit_word` 无 DB 默认（`NULL`），只由机审在落 `pending` 时写入；`NULL` 的含义是「未被机审拦下」，包括关审核、未命中、以及本列上线前的存量评论——三者不必区分，审核台统一显示为 `—`。
- `banned_words.action` 的 DB 默认 `'pending'`、`is_enabled` 的默认 `1` 同样只是直插兜底；服务层的单条添加与批量导入都**显式**写这两列（批量导入省略 `action` 时由服务层填 `'pending'`，不依赖 DB 默认），以免「默认动作」在两处各说一遍。
- `site_settings` 的缺省值（§2.10 末列）在**代码里**，不在库里；库里可能是空表或稀疏行。读取逻辑必须处理「键不存在 = 用代码默认」。
- 计数列默认 `0`、`is_top` 默认 `0`、`users.status` 默认 `1`、`users.role` 默认 `'user'`——首任博主账号需手工提升 `role`，属部署步骤，不在建表数据里。

### 5.7 其他必须知道的口径

- **`comments` 无 `updated_at`**：审核只改 `status`，审核动作时间不可追溯。这是现状，**不要补列**。
- 分类与标签的文章数是查询期即时统计的子查询结果，**不是列**；实体里出现的 `articleCount` 是查询载体。
- 实体对象上的关联装载字段（文章的作者/分类/标签、评论的用户/回复列表）**都不是数据库列**，不要为它们建列，也不要据它们建表。
- 时间序列化：`TIMESTAMP` 列映射为 `LocalDateTime`，响应为 ISO 格式；`publishTime` 的对外契约格式是 `yyyy-MM-dd HH:mm:ss`（属接口契约，不改变列类型）。不引入 `java.util.Date`，不用 `DATETIME`。
- 删除均为硬删（§1.4），跨表删除必须在同一事务里按「先删关联/子行，再删主行」顺序显式执行。
- **schema 只有一个真源：`docs/schema.sql`**，它是最终态全量 DDL，全新安装直接执行。曾经配套的两个增量升级脚本（给按旧版建过的库补漂移用）已删除——它们的内容早已并进全量建表语句，留着只会让同一件事记两遍账。开发阶段的库表变更一律**直接改 `docs/schema.sql` 并清库重建**（§1.1.1），不要生成「全量 + 增量清单」两套脚本。演示/测试数据与此分开，单独放在 `docs/data.sql`：只含 INSERT、不承载任何结构，必须在空库导入 schema 之后执行一次（事务包裹），重复导入撞主键；它不参与结构真源，也不改变本节口径。
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
| `favorites` / `bookmarks` / `subscriptions` / `notifications` / `messages` | 业务无收藏、订阅、站内信、通知。关注不在此列——它已于 2026-10-01 按用户裁决建为 `follows`（§2.11），且只做计数与按钮状态，不派生通知表 |
| `comment_likes` / `reactions` | 评论无点赞/表情回应 |
| `page_views_daily` / `stats_daily` / `dashboard_metrics`（统计汇总表） | Dashboard 聚合直接对 `posts`/`users`/`comments` 即时聚合；不做预聚合 |
| `categories` 的 `parent_id` / `path` / `sort_order` / `slug`；独立 `category` 树表 | 分类扁平一层，无层级、无排序、无别名 |
| `tags` 的 `slug` / `post_count` / `color` | 标签只有 `id`/`name`/`created_at`；计数即时统计 |
| `posts` 的 `slug` / `url_alias` / `canonical` 列或别名表 | 前台路由用数值 `id` |
| `post_categories`（文章↔分类多对多） | 一篇文章**至多一个**分类，即 `posts.category_id` |
| `comments_closure` / 评论 `level` / `root_id` / `path` 列 | 评论只有两级（`parent_id`，`0` 为根），无需递归结构 |
| `oauth_accounts` / `social_logins` / 第三方绑定表 | 只有邮箱 + 密码注册登录 |
| `pages` / `nav_menus` / `links`（友情链接）/ `banners` / `ads` / `themes` | 站点级可配置项走 `site_settings` KV；**新配置加键，不加表** |
| `site_settings` 之外的配置表（如 `configs`、`options`、按域拆分的设置表） | 单表 KV 已覆盖**开关与短文案**；键名与默认值见 §2.10。分界是「一个值 vs 一批要逐条管理的行」：违禁词属后者，故建为 `banned_words`（§2.12）而不是塞进 KV——那既压出 80 词上限，也让「启停一个词」变成改写整串文本 |
| `tenants` / `blogs`（多站点）/ `sites` | 单博主单站点，无多租户维度，任何表都不加 `tenant_id` |
| `schema_migrations` / `flyway_schema_history` / `liquibase_changelog` | 全新库由 `schema.sql` 初始化（本机走 `mysql ... < docs/schema.sql`，容器化时同一份文件挂进 MySQL 的 initdb 目录，只在数据目录为空的首次起跑执行——见 business-model.md §3.3「自托管部署」）；引入迁移框架是独立工程决策，不在数据库设计内自行添加 |
| 审计表（`audit_logs` / `operation_logs`） | 业务未要求操作审计；不要为「后台更安全」自行加审计落库 |
| SEO 元数据表（任何形态），或把 SEO 三列并进 `posts` | 详情页那三个值是响应时按回落链现算的，落库既没有真源也没有编辑面。原 `seo_meta` 1:1 补充表已于 2026-10-03 删除（§2.8），两个方向都不再建 |
| `word_hits` / 违禁词命中日志 / 命中计数列 | 命中词就是 `comments.hit_word` 一列值拷贝，一条评论只记**首个**命中词；不做命中统计、不做按词反查评论、不给 `banned_words` 加 `hit_count` |
| `banned_words` 的 `category` / `level` / `sort_order` / 分组表 | 词库只有词面、动作、启停三件事；分级已经由 `action` 两值承担，不再引入第二套分级维度 |

**判定口径**：若某张表既不在这 12 张里，也不在 `business-model.md` Part A 的实体关系图中，它就是**过度设计**，不要生成，应先回到本文提出变更。
