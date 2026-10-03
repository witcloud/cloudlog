package com.ayun.mapper;

import com.ayun.entity.Comment;
import com.ayun.model.vo.AdminCommentVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评论读取侧的两条真 SQL：回复的批量查询、后台关键词的字面量化。
 * 都只能对真库跑——IN 的展开与 REPLACE 转义是 SQL 里的事，mock 桩出来的一定是绿的
 */
@SpringBootTest
class CommentListQueryTest {

    @Autowired
    private CommentMapper commentMapper;

    @Test
    @DisplayName("按一批父评论 ID 取回的回复，与后台列表里「已过审且带父评论」的行不多不少")
    void batchReplyQueryReturnsEveryApprovedReply() {
        List<AdminCommentVO> approvedReplies = commentMapper.findAdminList("approved", null).stream()
                .filter(row -> row.getParentId() != null && row.getParentId() != 0)
                .toList();
        assertFalse(approvedReplies.isEmpty(), "本地库该有回复行，否则这条 IN 查询等于没测");

        List<Integer> parentIds = approvedReplies.stream()
                .map(AdminCommentVO::getParentId)
                .distinct()
                .toList();
        Set<Integer> expectedIds = approvedReplies.stream()
                .map(AdminCommentVO::getId)
                .collect(Collectors.toSet());

        List<Comment> replies = commentMapper.findRepliesByParentIds(parentIds);

        assertEquals(expectedIds, replies.stream().map(Comment::getId).collect(Collectors.toSet()),
                "漏一条就少显示一条回复，多一条就是把待审的捞出来了");
        assertTrue(IntStream.range(1, replies.size())
                        .allMatch(i -> !replies.get(i).getCreatedAt().isBefore(replies.get(i - 1).getCreatedAt())),
                "回复按创建时刻正序返回，服务层按到达顺序回填进各条根评论下");
    }

    @Test
    @DisplayName("关键词里的 % 按字面量匹配，不再当通配符命中全部评论")
    void percentInKeywordIsNotTreatedAsWildcard() {
        int total = commentMapper.findAdminList(null, null).size();
        assertFalse(total == 0, "本地库该有评论，否则这条比对说明不了任何事");

        List<AdminCommentVO> hits = commentMapper.findAdminList(null, "%");

        // 未转义时 CONCAT('%','%','%') 就是 '%'，等于匹配所有行；转义后只该命中真正带 % 的那几行
        assertTrue(hits.size() < total, "一个 % 就命中了全部 " + total + " 行，说明转义没生效");
        hits.forEach(row -> assertTrue(
                row.getContent().contains("%")
                        || (row.getPostTitle() != null && row.getPostTitle().contains("%"))
                        || (row.getUser() != null && row.getUser().getUsername() != null
                        && row.getUser().getUsername().contains("%")),
                "命中行必须真的带着一个 %"));
    }
}
