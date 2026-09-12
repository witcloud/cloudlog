-- 1. 用户表
CREATE TABLE `users` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `email` VARCHAR(255) NOT NULL UNIQUE COMMENT '用户邮箱，唯一',
  `password` VARCHAR(255) NOT NULL COMMENT '加密后的用户密码',
  `username` VARCHAR(100) NOT NULL UNIQUE COMMENT '登录用户名',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '用户头像URL',
  `bio` VARCHAR(255) DEFAULT NULL COMMENT '个人简介',
  `role` ENUM('admin', 'user') NOT NULL DEFAULT 'user' COMMENT '角色：admin(博主), user(普通用户)',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1-正常, 0-禁用',
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
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '文章ID',
  `title` VARCHAR(255) NOT NULL COMMENT '文章标题',
  `content` LONGTEXT NOT NULL COMMENT '文章正文(Markdown格式)',
  `excerpt` TEXT COMMENT '文章摘要',
  `author_id` INT NOT NULL COMMENT '作者ID',
  `category_id` INT DEFAULT NULL COMMENT '所属分类ID',
  `status` ENUM('draft', 'published', 'scheduled') NOT NULL DEFAULT 'draft' COMMENT '状态：draft-草稿, published-已发布, scheduled-定时发布',
  `is_top` TINYINT DEFAULT 0 COMMENT '是否置顶：1-是, 0-否',
  `view_count` INT DEFAULT 0 COMMENT '阅读量',
  `like_count` INT DEFAULT 0 COMMENT '点赞数',
  `comment_count` INT DEFAULT 0 COMMENT '评论数',
  `published_at` TIMESTAMP NULL DEFAULT NULL COMMENT '发布时间',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_author` (`author_id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status_time` (`status`, `published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章表';

-- 5. 文章与标签关联表
CREATE TABLE `post_tags` (
  `post_id` INT NOT NULL COMMENT '文章ID',
  `tag_id` INT NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`post_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签关联表';

-- 6. 评论表
CREATE TABLE `comments` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `post_id` INT NOT NULL COMMENT '所属文章ID',
  `user_id` INT DEFAULT NULL COMMENT '评论者用户ID(匿名则为NULL)',
  `content` TEXT NOT NULL COMMENT '评论内容',
  `parent_id` INT DEFAULT 0 COMMENT '父评论ID(用于回复功能)',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章评论表';

-- 7. 点赞表
CREATE TABLE `likes` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '点赞ID',
  `post_id` INT NOT NULL COMMENT '文章ID',
  `user_id` INT NOT NULL COMMENT '用户ID',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章点赞表';

-- 8. SEO优化表
CREATE TABLE `seo_meta` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT 'SEO ID',
  `post_id` INT NOT NULL UNIQUE COMMENT '文章ID',
  `title` VARCHAR(255) DEFAULT NULL COMMENT 'SEO标题',
  `description` VARCHAR(255) DEFAULT NULL COMMENT 'SEO描述',
  `keywords` VARCHAR(255) DEFAULT NULL COMMENT 'SEO关键词',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SEO信息表';

-- 9. 访问日志表
CREATE TABLE `visit_logs` (
  `id` INT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `post_id` INT DEFAULT NULL COMMENT '文章ID',
  `ip` VARCHAR(45) NOT NULL COMMENT '访问者IP',
  `user_agent` VARCHAR(255) DEFAULT NULL COMMENT '浏览器UA',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_time` (`post_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访问日志表';
