package com.ayun.mapper;

import com.ayun.model.vo.AdminCommentVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评论防护取计时用的那一列：MAX(created_at) 按 user_id 聚合，回复与待审评论都要算进来。
 * 这条只能对真库跑——聚合与列映射是 SQL 里的事，mock 桩出来的一定是绿的
 */
@SpringBootTest
class CommentGuardQueryTest {

    @Autowired
    private CommentMapper commentMapper;

    @Test
    @DisplayName("有评论的用户能取到最近一条的创建时间，且落在该用户全部评论的时间区间内")
    void returnsLatestCommentTimeForRealCommenter() {
        List<AdminCommentVO> rows = commentMapper.findAdminList(null, null).stream()
                .filter(row -> row.getUser() != null && row.getUser().getId() != null)
                .toList();
        assertFalse(rows.isEmpty(), "本地库该有带用户的评论，否则这条查询等于没测");

        Long userId = rows.get(0).getUser().getId();
        LocalDateTime lastAt = commentMapper.findLastCreatedAtByUserId(userId);

        assertNotNull(lastAt, "该用户有评论，MAX(created_at) 不该是空");
        assertTrue(rows.stream().allMatch(row -> !row.getCreatedAt().isAfter(lastAt)),
                "取到的必须是该用户最晚的一条，否则会少算等待时间");
    }

    @Test
    @DisplayName("没发过评论的用户返回 null，服务侧据此直接放行首条")
    void returnsNullForUserWithoutComments() {
        assertNull(commentMapper.findLastCreatedAtByUserId(-1L), "查不到行时该回 null，而不是抛异常或回 0");
    }
}
