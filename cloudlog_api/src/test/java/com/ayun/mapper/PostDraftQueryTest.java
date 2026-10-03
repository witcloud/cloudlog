package com.ayun.mapper;

import com.ayun.entity.Post;
import com.ayun.model.vo.StatusCountVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 草稿箱的状态筛选与分状态计数。两条查询共用 draftScope 谓词段，绑定与列名映射都在这里过一遍
 */
@SpringBootTest
class PostDraftQueryTest {

    @Autowired
    private PostMapper postMapper;

    @Test
    @DisplayName("findDrafts 叠加状态后只剩该状态")
    void findDraftsFiltersByStatus() {
        List<Post> draftsOnly = postMapper.findDrafts(1L, null, null, null, "draft");
        assertTrue(draftsOnly.stream().allMatch(post -> "draft".equals(post.getStatus())),
                "状态视图筛到草稿时不应出现其它状态");
    }

    @Test
    @DisplayName("计数不叠加状态：两个计数之和等于不筛状态时的条数")
    void countsIgnoreStatus() {
        StatusCountVO counts = postMapper.countUnpublishedByStatus(1L, null, null, null);
        int unpublished = postMapper.findDrafts(1L, null, null, null, null).size();
        assertEquals(unpublished, counts.getDraftCount() + counts.getScheduledCount());
    }
}
