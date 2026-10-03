-- 2026-10-03 全量审查修复：对已有库执行的增量 SQL。
-- docs/schema.sql 已同步为同等定义；全新建库无需执行本文件。
-- 执行前备份：mysqldump -u root -p cloudlog > cloudlog_backup_$(date +%F).sql

-- 1) 补缺失索引：三类「按用户查」的路径此前只能全表扫
--    - likes.user_id      → PostMapper.findLikedByUser（我的点赞 / 作者主页点赞栏）
--    - follows.follower_id→ FollowMapper.findFollowing / countByFollower（我的关注列表与计数）
--    - comments.user_id   → CommentMapper.findLastCreatedAtByUserId（每次发评论都走）
ALTER TABLE `likes`    ADD KEY `idx_user` (`user_id`);
ALTER TABLE `follows`  ADD KEY `idx_follower` (`follower_id`);
ALTER TABLE `comments` ADD KEY `idx_user` (`user_id`);

-- 2) 存量数据修复：修复前客户端不带 parentId 发表评论时 parent_id 落 NULL，
--    公开列表只认 parent_id = 0，这些行永远不可见。写入侧已归一化，这里一次性收敛存量
UPDATE `comments` SET `parent_id` = 0 WHERE `parent_id` IS NULL;

-- 3) （可选）business-model.md 记录的已知漂移：visit_logs / login_logs 的 idx_created_at
--    只进了 docs/schema.sql，早期建的本地库可能没有。先查再补：
--    SHOW INDEX FROM visit_logs WHERE Key_name = 'idx_created_at';
--    SHOW INDEX FROM login_logs WHERE Key_name = 'idx_created_at';
-- 缺哪条补哪条：
-- ALTER TABLE `visit_logs` ADD KEY `idx_created_at` (`created_at`);
-- ALTER TABLE `login_logs` ADD KEY `idx_created_at` (`created_at`);
