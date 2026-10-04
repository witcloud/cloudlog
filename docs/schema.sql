-- CloudLog 数据库唯一真源：全新安装直接执行本文件（只含建表 DDL，需先自行 CREATE DATABASE 并 USE 到目标库）
-- 变更口径：开发阶段直接改本文件并清库重建，不维护面向存量库的增量升级脚本（见 docs/db-design.md §1.1.1）
-- 环境：MySQL 8 / InnoDB / utf8mb4；全文索引依赖 ngram 分词器，ngram_token_size 默认 2；
-- 默认停用词表的单字母 a/i 会被 ngram 当成分隔符，含英文/符号的关键词切不出词元，所以搜索侧只把
-- 不含 ASCII 字母与符号的关键词交给全文索引，其余（连同单字关键词）退回 LIKE，见 PostMapper.xml findAllBrief

-- 1. 用户表
CREATE TABLE `users` (
  `id` BIGINT NOT NULL COMMENT '用户ID，应用层雪花 ID（Hutool IdUtil）生成，不使用数据库自增',
  `email` VARCHAR(255) NOT NULL UNIQUE COMMENT '用户邮箱，唯一',
  `password` VARCHAR(255) NOT NULL COMMENT '加密后的用户密码',
  `username` VARCHAR(100) NOT NULL UNIQUE COMMENT '登录用户名',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '用户头像URL',
  `bio` VARCHAR(255) DEFAULT NULL COMMENT '个人简介',
  `occupation` VARCHAR(30) DEFAULT NULL COMMENT '职业',
  `role` ENUM('admin', 'user') NOT NULL DEFAULT 'user' COMMENT '角色：admin(博主), user(普通用户)',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1-正常, 0-禁用',
  `last_login_at` TIMESTAMP NULL DEFAULT NULL COMMENT '上次登录时间，NULL 表示从未登录',
  `last_login_ip` VARCHAR(45) DEFAULT NULL COMMENT '上次登录IP，NULL 表示从未登录',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 2. 文章分类表
CREATE TABLE `categories` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name` VARCHAR(100) NOT NULL UNIQUE COMMENT '分类名称',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '分类描述',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章分类表';

-- 3. 文章标签表
CREATE TABLE `tags` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name` VARCHAR(100) NOT NULL UNIQUE COMMENT '标签名称',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签表';

-- 4. 文章表
CREATE TABLE `posts` (
  `id` BIGINT NOT NULL COMMENT '文章ID，应用层雪花 ID（Hutool IdUtil）生成，不使用数据库自增',
  `title` VARCHAR(255) NOT NULL COMMENT '文章标题',
  `content` LONGTEXT NOT NULL COMMENT '文章正文(Markdown格式)',
  `excerpt` TEXT COMMENT '文章摘要：服务端在本次落库目标状态为 published/scheduled 且该列为空时由正文派生（Markdown 去标记后按码点截 150，含尾部省略号），作者侧不填、已有值不重算',
  `cover` VARCHAR(255) DEFAULT NULL COMMENT '文章封面图',
  `author_id` BIGINT NOT NULL COMMENT '作者ID（用户雪花 ID）',
  `category_id` INT DEFAULT NULL COMMENT '所属分类ID',
  `status` ENUM('draft', 'published', 'scheduled', 'offline') NOT NULL DEFAULT 'draft' COMMENT '状态：draft-草稿, published-已发布, scheduled-定时发布, offline-已下线（仅作者与管理员可见）',
  `is_top` TINYINT DEFAULT 0 COMMENT '是否置顶：1-是, 0-否',
  `top_order` INT NOT NULL DEFAULT 0 COMMENT '置顶排序权重，越大越靠前；仅 is_top=1 时参与排序',
  `view_count` INT DEFAULT 0 COMMENT '阅读量',
  `like_count` INT DEFAULT 0 COMMENT '点赞数',
  `comment_count` INT DEFAULT 0 COMMENT '评论数',
  `published_at` TIMESTAMP NULL DEFAULT NULL COMMENT '发布时间，兼任首次对外可见的时刻，下线与重新上线都不改写',
  `offlined_by` BIGINT DEFAULT NULL COMMENT '最近一次执行「下线」的操作者用户ID（雪花 ID）；重新上线不清空，作为下架留痕',
  `offlined_at` TIMESTAMP NULL DEFAULT NULL COMMENT '最近一次执行「下线」的时刻；重新上线不清空，执行者无法经由文章接口抹掉它',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_author` (`author_id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status_time` (`status`, `published_at`),
  -- 标题与正文的全文索引，用 ngram 分词支持中文（依赖 ngram_token_size，MySQL 8 默认 2，
  -- 单个汉字的词进不了索引；默认停用词表的单字母 a/i 还会被当成分隔符，含英文/符号的关键词
  -- 同样切不出词元——搜索侧只把不含 ASCII 字母与符号的关键词交给 MATCH，其余退回 LIKE，
  -- 判定见 com.ayun.common.FulltextKeywordUtils 与 PostMapper.xml findAllBrief）
  FULLTEXT KEY `ft_title_content` (`title`, `content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章表';

-- 5. 文章与标签关联表
CREATE TABLE `post_tags` (
  `post_id` BIGINT NOT NULL COMMENT '文章ID（雪花 ID）',
  `tag_id` INT NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`post_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签关联表';

-- 6. 评论表
CREATE TABLE `comments` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `post_id` BIGINT NOT NULL COMMENT '所属文章ID（雪花 ID）',
  `user_id` BIGINT DEFAULT NULL COMMENT '评论者用户ID(匿名则为NULL，用户雪花 ID)',
  `content` TEXT NOT NULL COMMENT '评论内容',
  `parent_id` INT DEFAULT 0 COMMENT '父评论ID(用于回复功能)',
  `status` ENUM('pending', 'approved', 'rejected') NOT NULL DEFAULT 'approved' COMMENT '审核状态：pending-待审核, approved-已通过, rejected-已拒绝',
  `hit_word` VARCHAR(50) DEFAULT NULL COMMENT '机审命中的首个违禁词，值拷贝自 banned_words.word，不随词库增删启停回写；仅落 pending 时写入',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_status_time` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章评论表';

-- 7. 点赞表
CREATE TABLE `likes` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '点赞ID',
  `post_id` BIGINT NOT NULL COMMENT '文章ID（雪花 ID）',
  `user_id` BIGINT NOT NULL COMMENT '用户ID（雪花 ID）',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章点赞表';

-- 8. （空位）SEO 信息表 seo_meta 已于 2026-10-03 下线删除：详情页的 SEO 三元组改由服务端
--    按「文章标题 + 站点名 / posts.excerpt / 标签名」合成，库里不再存可填的 SEO 行。
--    序号不回收，因为 §2.x 那套锚点被三份文档互相引用（business-model.md §2.10、db-design.md §2.0）

-- 9. 访问日志表
CREATE TABLE `visit_logs` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `post_id` BIGINT DEFAULT NULL COMMENT '文章ID（雪花 ID）',
  `ip` VARCHAR(45) NOT NULL COMMENT '访问者IP',
  `user_agent` VARCHAR(255) DEFAULT NULL COMMENT '浏览器UA',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_time` (`post_id`, `created_at`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访问日志表';

-- 10. 站点配置表
CREATE TABLE `site_settings` (
  `setting_key` VARCHAR(64) NOT NULL COMMENT '配置键',
  `setting_value` VARCHAR(255) DEFAULT NULL COMMENT '配置值',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点配置表';

-- 11. 用户关注关系表
CREATE TABLE `follows` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '关注ID',
  `author_id` BIGINT NOT NULL COMMENT '被关注用户ID（雪花 ID）',
  `follower_id` BIGINT NOT NULL COMMENT '关注者用户ID（雪花 ID）',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_author_follower` (`author_id`, `follower_id`),
  KEY `idx_follower` (`follower_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注关系表';

-- 12. 评论违禁词库表
CREATE TABLE `banned_words` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '违禁词ID',
  `word` VARCHAR(50) NOT NULL COMMENT '违禁词，存 trim 后的原样大小写；机审匹配时两边转小写做子串包含',
  `action` ENUM('pending', 'reject') NOT NULL DEFAULT 'pending' COMMENT '命中处置：pending-转人工待审, reject-直接拒绝且不入库',
  `is_enabled` TINYINT DEFAULT 1 COMMENT '是否启用：1-参与匹配, 0-停用（保留词条，机审跳过）',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论违禁词库表';

-- 13. 登录日志表
CREATE TABLE `login_logs` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `email` VARCHAR(255) NOT NULL COMMENT '尝试登录时输入的邮箱，原样记录：账号不存在也照样是一条尝试记录，所以它不是指向 users 的引用',
  `user_id` BIGINT DEFAULT NULL COMMENT '登录成功时回填的用户ID（雪花 ID），失败为 NULL（db-design.md §1.1 例外三）',
  `ip` VARCHAR(45) NOT NULL COMMENT '来源IP，与 visit_logs.ip 同宽；对外只出脱敏值，原始值不出接口',
  `user_agent` VARCHAR(255) DEFAULT NULL COMMENT '浏览器UA，原样存文本不做解析列；归类在读取侧算',
  `result` ENUM('success', 'wrong_password', 'locked', 'disabled') NOT NULL COMMENT '结果：success-登录成功, wrong_password-口令校验失败（含邮箱未注册，不区分二者）, locked-锁定期内的尝试, disabled-账号禁用等其他认证失败',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '尝试时刻',
  PRIMARY KEY (`id`),
  KEY `idx_email_time` (`email`, `created_at`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录尝试日志表';
